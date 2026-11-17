package dev.cubicdawn.world;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * 快照先写入临时文件，再替换正式文件。上一份有效文件留在 .bak。
 * 校验失败时不改正式文件，也不把内存里的空世界写回去。
 */
public final class SnapshotIO {

    public static final String HEADER_V3 = "cubic-dawn-blocks 3";
    public static final String HEADER_V4 = "cubic-dawn-blocks 4";

    public static final String HEADER_V5 = "cubic-dawn-blocks 5";

    private SnapshotIO() {
    }

    public record Snapshot(
            int version,
            Integer seed,
            Long revision,
            PlayerState player,
            List<int[]> blocks,
            List<Integer> unknownIds,
            long simulationTick,
            List<BlockTicks.Task> tasks,
            boolean restoredFromBackup
    ) {
    }

    public record SaveResult(long revision, boolean coveredLaterEdits, Path path) {
    }

    public static String encode(BlockWorld world, PlayerState player, long revision) {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER_V5);
        lines.add("tick " + world.blockTicks().tick());
        for (BlockTicks.Task task : world.blockTicks().tasks()) {
            lines.add("fall " + task.due() + " " + task.x() + " " + task.y() + " " + task.z());
        }
        lines.add("seed " + BlockWorld.SEED);
        lines.add("revision " + revision);
        if (player != null) {
            lines.add("player "
                    + Float.toString(player.x()) + " "
                    + Float.toString(player.y()) + " "
                    + Float.toString(player.z()) + " "
                    + Float.toString(player.yaw()) + " "
                    + Float.toString(player.pitch()) + " "
                    + player.slot());
        }
        world.forEachSolid((x, y, z, id) -> lines.add(x + " " + y + " " + z + " " + id));
        String canonical = join(lines);
        lines.add(1, "checksum sha256 " + sha256(canonical));
        return join(lines);
    }

    public static Snapshot parse(String text) {
        List<String> lines = nonemptyLines(text);
        if (lines.isEmpty()) {
            throw new IllegalStateException("Save file is empty.");
        }
        String header = lines.get(0);
        if (HEADER_V3.equals(header)) {
            return parseBody(3, lines.subList(1, lines.size()), false);
        }
        if (!HEADER_V4.equals(header) && !HEADER_V5.equals(header)) {
            throw new IllegalStateException("Unrecognized save header: " + header);
        }
        String checksum = null;
        List<String> body = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("checksum ")) {
                if (checksum != null) {
                    throw new IllegalStateException("Save file has two checksums.");
                }
                if (!line.startsWith("checksum sha256 ")) {
                    throw new IllegalStateException("Unsupported checksum line: " + line);
                }
                checksum = line.substring("checksum sha256 ".length()).trim();
            } else {
                body.add(line);
            }
        }
        if (checksum == null || checksum.isEmpty()) {
            throw new IllegalStateException("Save file is missing a sha256 checksum.");
        }
        String actual = sha256(join(bodyWithHeader(header, body)));
        if (!checksum.equals(actual)) {
            throw new IllegalStateException("Save checksum does not match the file contents.");
        }
        return parseBody(HEADER_V5.equals(header) ? 5 : 4, body, false);
    }

    public static SaveResult save(Path path, BlockWorld world, PlayerState player) throws IOException {
        long revision = world.revision();
        String text = encode(world, player, revision);
        atomicWrite(path, text.getBytes(StandardCharsets.UTF_8));
        boolean later = world.revision() != revision;
        world.markCleanIfUnchanged(revision);
        return new SaveResult(revision, later, path);
    }

    public static Snapshot load(Path path) throws IOException {
        if (Files.exists(path)) {
            return parse(Files.readString(path));
        }
        Path backup = backupPath(path);
        if (Files.exists(backup)) {
            Snapshot snapshot = parse(Files.readString(backup));
            return new Snapshot(
                    snapshot.version(),
                    snapshot.seed(),
                    snapshot.revision(),
                    snapshot.player(),
                    snapshot.blocks(),
                    snapshot.unknownIds(),
                    snapshot.simulationTick(),
                    snapshot.tasks(),
                    true
            );
        }
        throw new IOException("No save file at " + path);
    }

    public static void atomicWrite(Path target, byte[] bytes) throws IOException {
        Path absolute = target.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = absolute.resolveSibling(absolute.getFileName().toString() + ".tmp");
        Path backup = backupPath(absolute);
        Files.write(temporary, bytes);
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        if (Files.exists(absolute)) {
            moveReplacing(absolute, backup);
        }
        moveReplacing(temporary, absolute);
    }

    public static Path backupPath(Path target) {
        Path absolute = target.toAbsolutePath();
        return absolute.resolveSibling(absolute.getFileName().toString() + ".bak");
    }

    private static Snapshot parseBody(int version, List<String> lines, boolean fromBackup) {
        Long tick = null;
        List<BlockTicks.Task> tasks = new ArrayList<>();
        Integer seed = null;
        Long revision = null;
        PlayerState player = null;
        List<int[]> blocks = new ArrayList<>();
        List<Integer> unknownIds = new ArrayList<>();
        HashSetPositions seen = new HashSetPositions();
        for (String line : lines) {
            if (version == 5 && line.startsWith("tick ")) {
                if (tick != null) throw new IllegalStateException("Duplicate simulation tick");
                tick = Long.valueOf(line.substring(5));
                continue;
            }
            if (version == 5 && line.startsWith("fall ")) {
                String[] fields = line.split(" ");
                if (fields.length != 5) throw new IllegalStateException("Bad fall task");
                tasks.add(new BlockTicks.Task(Long.parseLong(fields[1]), Integer.parseInt(fields[2]),
                        Integer.parseInt(fields[3]), Integer.parseInt(fields[4])));
                continue;
            }
            if (line.startsWith("seed ")) {
                seed = Integer.valueOf(line.substring(5).trim());
                continue;
            }
            if (line.startsWith("revision ")) {
                revision = Long.valueOf(line.substring(9).trim());
                continue;
            }
            if (line.startsWith("player ")) {
                player = parsePlayer(line.substring(7).trim());
                continue;
            }
            String[] parts = line.split(" ");
            if (parts.length != 4) {
                throw new IllegalStateException("Bad block line: " + line);
            }
            int x;
            int y;
            int z;
            int id;
            try {
                x = Integer.parseInt(parts[0]);
                y = Integer.parseInt(parts[1]);
                z = Integer.parseInt(parts[2]);
                id = Integer.parseInt(parts[3]);
            } catch (NumberFormatException exception) {
                throw new IllegalStateException("Bad block line: " + line, exception);
            }
            if (x < BlockWorld.BLOCK_MIN || x > BlockWorld.BLOCK_MAX
                    || y < BlockWorld.BLOCK_MIN || y > BlockWorld.BLOCK_MAX
                    || z < BlockWorld.BLOCK_MIN || z > BlockWorld.BLOCK_MAX) {
                throw new IllegalStateException("Block outside the stored range: " + line);
            }
            if (id < 0 || id > 65535) {
                throw new IllegalStateException("Block id cannot be stored: " + line);
            }
            if (!seen.add(x, y, z)) {
                throw new IllegalStateException("Duplicate block position: " + line);
            }
            if (!BlockIds.hotbarMaterial(id) && id != BlockIds.AIR && id != BlockIds.SUPPORT_PROBE) {
                unknownIds.add(id);
            }
            blocks.add(new int[] {x, y, z, id});
        }
        if (version == 5 && tick == null) throw new IllegalStateException("Missing simulation tick");
        BlockTicks validated = new BlockTicks();
        validated.restore(tick == null ? 0 : tick, tasks);
        return new Snapshot(version, seed, revision, player, blocks, unknownIds,
                validated.tick(), validated.tasks(), fromBackup);
    }

    private static PlayerState parsePlayer(String text) {
        String[] parts = text.split(" ");
        if (parts.length != 6) {
            throw new IllegalStateException("Bad player line: " + text);
        }
        float x = parseFinite(parts[0]);
        float y = parseFinite(parts[1]);
        float z = parseFinite(parts[2]);
        float yaw = parseFinite(parts[3]);
        float pitch = parseFinite(parts[4]);
        int slot = Integer.parseInt(parts[5]);
        if (slot < 0 || slot >= EditHotbar.SLOTS) {
            throw new IllegalStateException("Player slot is outside the hotbar: " + slot);
        }
        return new PlayerState(x, y, z, yaw, pitch, slot);
    }

    private static float parseFinite(String text) {
        float value = Float.parseFloat(text);
        if (!Float.isFinite(value)) {
            throw new IllegalStateException("Player value is not finite: " + text);
        }
        return value;
    }

    private static List<String> bodyWithHeader(String header, List<String> body) {
        List<String> lines = new ArrayList<>();
        lines.add(header);
        lines.addAll(body);
        return lines;
    }

    private static List<String> nonemptyLines(String text) {
        String[] raw = text.split("\\R", -1);
        List<String> lines = new ArrayList<>();
        for (String line : raw) {
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static String join(List<String> lines) {
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            builder.append(line).append('\n');
        }
        return builder.toString();
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(Character.forDigit((value >> 4) & 0xF, 16));
                hex.append(Character.forDigit(value & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private static void moveReplacing(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** 防止重复坐标用的小表，避免再引入一套世界数组。 */
    private static final class HashSetPositions {
        private final java.util.HashSet<Long> keys = new java.util.HashSet<>();

        boolean add(int x, int y, int z) {
            long key = ((long) (x + 32768) << 32) ^ ((long) (y + 32768) << 16) ^ (z + 32768L);
            return keys.add(key);
        }
    }
}
