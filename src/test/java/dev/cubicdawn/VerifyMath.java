package dev.cubicdawn;

import dev.cubicdawn.world.Aabb;
import dev.cubicdawn.world.BlockIds;
import dev.cubicdawn.world.BlockWorld;
import dev.cubicdawn.world.ButtonEdge;
import dev.cubicdawn.world.CellKind;
import dev.cubicdawn.world.CellView;
import dev.cubicdawn.world.EditHotbar;
import dev.cubicdawn.world.EditReceipt;
import dev.cubicdawn.world.FixedStep;
import dev.cubicdawn.world.PlayerBody;
import dev.cubicdawn.world.PlayerMotion;
import dev.cubicdawn.world.PlayerState;
import dev.cubicdawn.world.SectionMath;
import dev.cubicdawn.world.SnapshotIO;
import dev.cubicdawn.world.VoxelRay;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** 无窗口验收。每一行 PASS 都是这次进程里真的跑过的。 */
public final class VerifyMath {

    private final List<String> failures = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        VerifyMath verify = new VerifyMath();
        verify.coordinates();
        verify.rays();
        verify.edits();
        verify.movement();
        verify.input();
        verify.timing();
        verify.saves();
        if (!verify.failures.isEmpty()) {
            throw new IllegalStateException("Failed: " + verify.failures);
        }
        System.out.println("ALL PASS " + "coordinates rays edits movement input saves");
    }

    private void timing() {
        PlayerMotion.Pose reference = timedMovement(30);
        boolean same = true;
        for (int fps : new int[] {60, 120, 144}) {
            PlayerMotion.Pose other = timedMovement(fps);
            same &= reference.x == other.x && reference.y == other.y
                    && reference.z == other.z && reference.velocityY == other.velocityY;
        }
        check("TIME01", same, "fixed input diverged at different render rates");
        FixedStep clock = new FixedStep();
        int[] ticks = {0};
        int[] jumps = {0};
        FixedStep.Tick action = (seconds, jump) -> { ticks[0]++; if (jump) jumps[0]++; };
        clock.advance(FixedStep.SECONDS / 2, true, true, action);
        clock.advance(3600, false, false, action);
        clock.advance(FixedStep.SECONDS / 2, true, false, action);
        check("TIME02", ticks[0] == 0 && jumps[0] == 0, "pause retained time or jump");
        clock.advance(FixedStep.SECONDS / 2, true, false, action);
        int caughtUp = clock.advance(3600, true, false, action);
        int debt = clock.advance(0, true, false, action);
        check("TIME03", ticks[0] == 4 && caughtUp == 3 && debt == 0,
                "long suspension created simulation debt");
        clock.reset();
        ticks[0] = 0;
        clock.advance(FixedStep.SECONDS / 4, true, true, action);
        clock.advance(FixedStep.SECONDS / 4, true, false, action);
        clock.advance(FixedStep.SECONDS / 2, true, false, action);
        clock.advance(FixedStep.SECONDS * 3, true, false, action);
        check("TIME_JUMP", ticks[0] == 4 && jumps[0] == 1, "jump lost or repeated across ticks");
        clock.reset();
        ticks[0] = 0;
        clock.advance(Double.NaN, true, false, action);
        clock.advance(Double.POSITIVE_INFINITY, true, false, action);
        clock.advance(-1, true, false, action);
        clock.advance(FixedStep.SECONDS, true, false, action);
        check("TIME_INVALID", ticks[0] == 1, "invalid clock sample poisoned accumulator");
    }

    private PlayerMotion.Pose timedMovement(int fps) {
        FixedStep clock = new FixedStep();
        PlayerMotion.Pose pose = new PlayerMotion.Pose(0, 2, 0, 0);
        PlayerBody.SolidAt floor = (x, y, z) -> y <= 0;
        for (int frame = 0; frame < fps * 2; frame++) {
            clock.advance(1.0 / fps, true, frame == 0, (seconds, jump) ->
                    PlayerMotion.step(pose, seconds, 1, -1, jump, floor));
        }
        return pose;
    }

    private void coordinates() {
        check("COORD01",
                SectionMath.section(-1) == -1 && SectionMath.local(-1) == 15
                        && SectionMath.section(-16) == -1 && SectionMath.local(-16) == 0
                        && SectionMath.section(-17) == -2 && SectionMath.local(-17) == 15,
                "negative floor division");
        boolean roundTrip = true;
        for (int world = -40; world <= 40; world++) {
            int section = SectionMath.section(world);
            int local = SectionMath.local(world);
            if (local < 0 || local >= SectionMath.EDGE || SectionMath.compose(section, local) != world) {
                roundTrip = false;
            }
        }
        check("COORD02", roundTrip, "compose(section, local) != world");
        boolean index = SectionMath.index(0, 0, 0) == 0
                && SectionMath.index(1, 0, 0) == 1
                && SectionMath.index(0, 0, 1) == 16
                && SectionMath.index(0, 1, 0) == 256
                && SectionMath.index(15, 15, 15) == 4095;
        boolean[] seen = new boolean[4096];
        boolean unique = true;
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int encoded = SectionMath.index(x, y, z);
                    if (encoded < 0 || encoded >= 4096 || seen[encoded]
                            || SectionMath.localX(encoded) != x
                            || SectionMath.localY(encoded) != y
                            || SectionMath.localZ(encoded) != z) {
                        unique = false;
                    } else {
                        seen[encoded] = true;
                    }
                }
            }
        }
        check("COORD03", index && unique, "local index is not a bijection");

        BlockWorld world = BlockWorld.empty();
        int before = world.mutations();
        CellView outside = world.query(-100, 0, 0);
        CellView air = world.query(0, 0, 0);
        check("COORD04",
                outside.kind() == CellKind.OUTSIDE
                        && air.kind() == CellKind.KNOWN
                        && air.id() == BlockIds.AIR
                        && world.mutations() == before
                        && world.countSolid() == 0,
                "outside/air collapsed or query wrote data");
    }

    private void rays() {
        HitProbe probe = new HitProbe();
        probe.solid(3, 0, 0);
        VoxelRay.Hit forward = VoxelRay.cast(0.5f, 0.5f, 0.5f, 1f, 0f, 0f, 5f, probe);
        check("RAY01",
                forward.kind() == VoxelRay.Kind.HIT
                        && forward.x() == 3 && forward.y() == 0 && forward.z() == 0
                        && close(forward.distance(), 2.5f)
                        && forward.normalX() == -1 && forward.normalY() == 0 && forward.normalZ() == 0,
                describe(forward));

        probe.blocks.clear();
        probe.solid(-2, 0, 0);
        VoxelRay.Hit backward = VoxelRay.cast(0.5f, 0.5f, 0.5f, -1f, 0f, 0f, 5f, probe);
        check("RAY02",
                backward.kind() == VoxelRay.Kind.HIT
                        && backward.x() == -2
                        && close(backward.distance(), 1.5f)
                        && backward.normalX() == 1,
                describe(backward));

        probe.blocks.clear();
        probe.solid(-1, 0, 0);
        VoxelRay.Hit boundary = VoxelRay.cast(0f, 0.5f, 0.5f, -1f, 0f, 0f, 5f, probe);
        check("RAY03",
                boundary.kind() == VoxelRay.Kind.HIT
                        && boundary.x() == -1
                        && close(boundary.distance(), 0f)
                        && boundary.hasNormal()
                        && boundary.normalX() == 1
                        && !boundary.startedInside(),
                describe(boundary));

        probe.blocks.clear();
        probe.solid(0, 3, 0);
        VoxelRay.Hit upward = VoxelRay.cast(0.5f, 0.5f, 0.5f, 0f, 1f, 0f, 5f, probe);
        VoxelRay.Hit tiny = VoxelRay.cast(0.5f, 0.5f, 0.5f, 1.0e-8f, 1f, 0f, 5f, probe);
        VoxelRay.Hit zero = VoxelRay.cast(0.5f, 0.5f, 0.5f, 0f, 0f, 0f, 5f, probe);
        check("RAY04",
                upward.kind() == VoxelRay.Kind.HIT
                        && upward.y() == 3
                        && tiny.kind() != null
                        && Float.isFinite(tiny.distance())
                        && zero.kind() == VoxelRay.Kind.MISS,
                "up=" + describe(upward) + " tiny=" + describe(tiny));

        probe.blocks.clear();
        probe.solid(3, 0, 0);
        VoxelRay.Hit shortReach = VoxelRay.cast(0.5f, 0.5f, 0.5f, 1f, 0f, 0f, 2.4f, probe);
        VoxelRay.Hit exactReach = VoxelRay.cast(0.5f, 0.5f, 0.5f, 1f, 0f, 0f, 2.5f, probe);
        check("RAY05",
                shortReach.kind() == VoxelRay.Kind.MISS
                        && exactReach.kind() == VoxelRay.Kind.HIT
                        && close(exactReach.distance(), 2.5f),
                "short=" + shortReach.kind() + " exact=" + describe(exactReach));

        probe.blocks.clear();
        probe.thin(2, 0, 0, new Aabb(2.25f, 0.25f, 0.25f, 2.75f, 0.75f, 0.75f));
        VoxelRay.Hit through = VoxelRay.cast(0.5f, 0.5f, 0.5f, 1f, 0f, 0f, 5f, probe);
        VoxelRay.Hit beside = VoxelRay.cast(0.5f, 0.5f, 0.05f, 1f, 0f, 0f, 5f, probe);
        check("RAY06",
                through.kind() == VoxelRay.Kind.HIT
                        && through.x() == 2
                        && close(through.distance(), 1.75f)
                        && beside.kind() == VoxelRay.Kind.MISS,
                "through=" + describe(through) + " beside=" + beside.kind());

        probe.blocks.clear();
        probe.solid(1, 1, 0);
        VoxelRay.Hit cornerA = VoxelRay.cast(0.5f, 0.5f, 0.5f, 1f, 1f, 0f, 5f, probe);
        VoxelRay.Hit cornerB = VoxelRay.cast(0.5f, 0.5f, 0.5f, 1f, 1f, 0f, 5f, probe);
        probe.blocks.clear();
        probe.solid(1, 0, 0);
        VoxelRay.Hit grazed = VoxelRay.cast(0.5f, 0.5f, 0.5f, 1f, 1f, 0f, 5f, probe);
        check("RAY07",
                cornerA.kind() == VoxelRay.Kind.HIT
                        && cornerA.x() == 1 && cornerA.y() == 1
                        && cornerA.normalX() == -1 && cornerA.normalY() == 0
                        && cornerA.distance() == cornerB.distance()
                        && cornerA.normalX() == cornerB.normalX()
                        && grazed.kind() == VoxelRay.Kind.MISS,
                "corner=" + describe(cornerA) + " grazed=" + describe(grazed));

        probe.blocks.clear();
        probe.solid(1, 0, 0);
        probe.solid(3, 0, 0);
        VoxelRay.Hit nearer = VoxelRay.cast(0.5f, 0.5f, 0.5f, 1f, 0f, 0f, 5f, probe);
        check("RAY08",
                nearer.kind() == VoxelRay.Kind.HIT && nearer.x() == 1 && close(nearer.distance(), 0.5f),
                describe(nearer));

        probe.blocks.clear();
        probe.solid(0, 0, 0);
        VoxelRay.Hit inside = VoxelRay.cast(0.5f, 0.5f, 0.5f, 1f, 0f, 0f, 5f, probe);
        check("RAY09",
                inside.kind() == VoxelRay.Kind.HIT
                        && inside.startedInside()
                        && !inside.hasNormal()
                        && VoxelRay.placeTarget(inside) == null,
                describe(inside));

        probe.blocks.clear();
        probe.unload(2, 0, 0);
        probe.solid(4, 0, 0);
        VoxelRay.Hit blocked = VoxelRay.cast(0.5f, 0.5f, 0.5f, 1f, 0f, 0f, 5f, probe);
        check("RAY10",
                blocked.kind() == VoxelRay.Kind.BLOCKED && blocked.x() == 2 && blocked.y() == 0,
                describe(blocked));

        HitProbe engine = new HitProbe();
        engine.solid(3, 0, 0);
        VoxelRay.Hit shifted = VoxelRay.castFromEngineEye(0f, 0f, 0f, 1f, 0f, 0f, 5f, engine);
        check("RAY01_ENGINE",
                shifted.kind() == VoxelRay.Kind.HIT && shifted.x() == 3 && close(shifted.distance(), 2.5f),
                describe(shifted));
    }

    private void edits() {
        BlockWorld world = BlockWorld.empty();
        EditReceipt placed = world.placeBlock(1L, 0, 0, 0, BlockIds.STONE, 0, (x, y, z) -> false);
        EditReceipt again = world.placeBlock(1L, 1, 0, 0, BlockIds.STONE, 0, (x, y, z) -> false);
        check("EDIT06",
                placed.accepted()
                        && world.getId(0, 0, 0) == BlockIds.STONE
                        && !again.accepted()
                        && "duplicate".equals(again.reason())
                        && world.getId(1, 0, 0) == BlockIds.AIR,
                again.reason());

        CellView stone = world.query(0, 0, 0);
        int solids = world.countSolid();
        long revision = world.revision();
        EditReceipt stale = world.breakBlock(2L, 0, 0, 0, stone.revision() + 9);
        check("EDIT07",
                !stale.accepted()
                        && "stale".equals(stale.reason())
                        && world.getId(0, 0, 0) == BlockIds.STONE
                        && world.countSolid() == solids
                        && world.revision() == revision,
                stale.reason());

        EditReceipt broke = world.breakBlock(3L, 0, 0, 0, stone.revision());
        EditReceipt brokeAgain = world.breakBlock(4L, 0, 0, 0, stone.revision() + 1);
        check("EDIT01",
                broke.accepted()
                        && world.getId(0, 0, 0) == BlockIds.AIR
                        && !brokeAgain.accepted()
                        && "not_solid".equals(brokeAgain.reason()),
                brokeAgain.reason());

        world = BlockWorld.empty();
        world.placeBlock(10L, 0, 0, 0, BlockIds.STONE, 0, (x, y, z) -> false);
        boolean six = true;
        int[][] rays = {
                {-2, 0, 0, 1, 0, 0, -1, 0, 0},
                {3, 0, 0, -1, 0, 0, 1, 0, 0},
                {0, -2, 0, 0, 1, 0, 0, -1, 0},
                {0, 3, 0, 0, -1, 0, 0, 1, 0},
                {0, 0, -2, 0, 0, 1, 0, 0, -1},
                {0, 0, 3, 0, 0, -1, 0, 0, 1}
        };
        long command = 20L;
        for (int[] ray : rays) {
            VoxelRay.Hit hit = VoxelRay.cast(
                    ray[0] + 0.5f, ray[1] + 0.5f, ray[2] + 0.5f,
                    ray[3], ray[4], ray[5], 5f, world
            );
            int[] target = VoxelRay.placeTarget(hit);
            if (hit.kind() != VoxelRay.Kind.HIT || hit.normalX() != ray[6]
                    || hit.normalY() != ray[7] || hit.normalZ() != ray[8]
                    || target == null) {
                six = false;
                break;
            }
            CellView cell = world.query(target[0], target[1], target[2]);
            EditReceipt receipt = world.placeBlock(
                    command++, target[0], target[1], target[2], BlockIds.DIRT, cell.revision(), (x, y, z) -> false
            );
            if (!receipt.accepted() || world.getId(target[0], target[1], target[2]) != BlockIds.DIRT) {
                six = false;
                break;
            }
        }
        check("EDIT02", six && world.getId(0, 0, 0) == BlockIds.STONE, "six-face placement");

        world = BlockWorld.empty();
        PlayerBody.SolidAt overlapping = (x, y, z) -> PlayerBody.overlapsBlock(0.3f, 0f, 0f, x, y, z);
        EditReceipt buried = world.placeBlock(30L, 1, 0, 0, BlockIds.STONE, 0, overlapping);
        check("EDIT03",
                !buried.accepted()
                        && "player_overlap".equals(buried.reason())
                        && world.countSolid() == 0,
                buried.reason());

        PlayerBody.SolidAt touching = (x, y, z) -> PlayerBody.overlapsBlock(0.2f, 0f, 0f, x, y, z);
        EditReceipt beside = world.placeBlock(31L, 1, 0, 0, BlockIds.STONE, 0, touching);
        check("EDIT04", beside.accepted() && world.getId(1, 0, 0) == BlockIds.STONE, beside.reason());

        world = BlockWorld.empty();
        world.placeBlock(40L, 0, 0, 0, BlockIds.STONE, 0, (x, y, z) -> false);
        CellView occupied = world.query(0, 0, 0);
        EditReceipt replaced = world.placeBlock(41L, 0, 0, 0, BlockIds.DIRT, occupied.revision(), (x, y, z) -> false);
        EditReceipt outside = world.placeBlock(42L, 100, 0, 0, BlockIds.STONE, 0, (x, y, z) -> false);
        world.setSimulationLoaded(2, 0, 0, false);
        int mutations = world.mutations();
        EditReceipt unloaded = world.placeBlock(43L, 2, 0, 0, BlockIds.STONE, 0, (x, y, z) -> false);
        boolean unloadedUntouched = world.mutations() == mutations
                && world.query(2, 0, 0).kind() == CellKind.UNLOADED;
        EditReceipt unsupported = world.placeBlock(
                44L, 0, 5, 0, BlockIds.SUPPORT_PROBE, world.query(0, 5, 0).revision(), (x, y, z) -> false
        );
        world.placeBlock(45L, 0, 4, 0, BlockIds.STONE, world.query(0, 4, 0).revision(), (x, y, z) -> false);
        EditReceipt supported = world.placeBlock(
                46L, 0, 5, 0, BlockIds.SUPPORT_PROBE, world.query(0, 5, 0).revision(), (x, y, z) -> false
        );
        check("EDIT05",
                !replaced.accepted() && "not_replaceable".equals(replaced.reason())
                        && !outside.accepted() && "outside".equals(outside.reason())
                        && !unloaded.accepted() && "unloaded".equals(unloaded.reason())
                        && unloadedUntouched
                        && !unsupported.accepted() && "unsupported".equals(unsupported.reason())
                        && supported.accepted(),
                replaced.reason() + "/" + outside.reason() + "/" + unloaded.reason() + "/" + unsupported.reason());
    }

    private void movement() {
        BlockWorld island = BlockWorld.generateIsland();
        island.checkGeneratedIsland();
        int top = BlockWorld.surface(2, 3);
        boolean spawnClear = !PlayerBody.overlapsSolid(2f, top + 1.4f + 0.25f, 3f, island::isSolidAt);
        boolean sunk = PlayerBody.overlapsSolid(2f, top + 1.4f - 0.02f, 3f, island::isSolidAt);
        boolean grounded = PlayerBody.onGround(2f, top + 1.4f, 3f, island::isSolidAt);
        boolean airborne = PlayerBody.onGround(2f, top + 1.4f + 0.25f, 3f, island::isSolidAt);
        float apex = PlayerBody.JUMP_SPEED * PlayerBody.JUMP_SPEED / (2f * PlayerBody.GRAVITY);
        check("MOVE_GROUND", spawnClear && sunk && grounded && !airborne && apex > 1f, "spawn or ground probe");

        PlayerBody.SolidAt floor = (x, y, z) -> y == 0 && Math.abs(x) <= 3 && Math.abs(z) <= 3;
        PlayerMotion.Pose standing = new PlayerMotion.Pose(0f, 1.4f, 0f, 0f);
        PlayerMotion.step(standing, 0.05f, 0f, 0f, false, floor);
        check("MOVE_REST", close(standing.y, 1.4f) && standing.velocityY == 0f, "y=" + standing.y);

        PlayerMotion.Pose jump = new PlayerMotion.Pose(0f, 1.4f, 0f, 0f);
        PlayerMotion.step(jump, 0.05f, 0f, 0f, true, floor);
        check("MOVE_JUMP", jump.y > 1.4f && jump.velocityY == PlayerBody.JUMP_SPEED, "y=" + jump.y);

        PlayerBody.SolidAt wall = (x, y, z) ->
                (y == 0 && Math.abs(x) <= 4 && Math.abs(z) <= 2)
                        || (x == 1 && y >= 1 && y <= 2 && Math.abs(z) <= 2);
        PlayerMotion.Pose blocked = new PlayerMotion.Pose(0f, 1.4f, 0f, 0f);
        PlayerMotion.moveAxis(blocked, 3f, 0f, wall);
        check("MOVE_WALL", blocked.x < 0.5f && blocked.x > -0.01f, "x=" + blocked.x);

        PlayerMotion.Pose slide = new PlayerMotion.Pose(0f, 1.4f, 0f, 0f);
        PlayerMotion.step(slide, 0.05f, 1f, 1f, false, wall);
        check("MOVE_SLIDE", slide.x < 0.5f && slide.z > 0.05f, "x=" + slide.x + " z=" + slide.z);

        PlayerBody.SolidAt corner = (x, y, z) ->
                y == 0 && Math.abs(x) <= 4 && Math.abs(z) <= 4
                        || ((x == 1 || z == 1) && y >= 1 && y <= 2);
        PlayerMotion.Pose boxed = new PlayerMotion.Pose(0f, 1.4f, 0f, 0f);
        PlayerMotion.moveAxis(boxed, 3f, 0f, corner);
        PlayerMotion.moveAxis(boxed, 0f, 3f, corner);
        check("MOVE_CORNER", boxed.x < 1f && boxed.z < 1f, "x=" + boxed.x + " z=" + boxed.z);

        PlayerBody.SolidAt ceiling = (x, y, z) ->
                (y == 0 && x == 0 && z == 0) || (y == 3 && x == 0 && z == 0);
        PlayerMotion.Pose rising = new PlayerMotion.Pose(0f, 1.4f, 0f, 0f);
        boolean penetrated = false;
        float peak = rising.y;
        for (int frame = 0; frame < 20; frame++) {
            PlayerMotion.step(rising, 0.05f, 0f, 0f, frame == 0, ceiling);
            peak = Math.max(peak, rising.y);
            if (PlayerBody.overlapsSolid(rising.x, rising.y, rising.z, ceiling)) {
                penetrated = true;
            }
        }
        check("MOVE_CEILING", !penetrated && peak > 1.4f && peak < 1.8f, "peak=" + peak);

        PlayerBody.SolidAt pit = (x, y, z) -> false;
        PlayerMotion.Pose falling = new PlayerMotion.Pose(0f, 1.4f, 0f, 0f);
        PlayerMotion.step(falling, 0.05f, 0f, 0f, false, pit);
        check("MOVE_DROP", falling.y < 1.4f, "y=" + falling.y);

        PlayerMotion.Pose fast = new PlayerMotion.Pose(0f, 8f, 0f, 0f);
        boolean fellThrough = false;
        for (int frame = 0; frame < 80; frame++) {
            PlayerMotion.step(fast, 0.05f, 0f, 0f, false, floor);
            if (fast.y < 1.2f) {
                fellThrough = true;
            }
        }
        check("REG01",
                !fellThrough && PlayerBody.onGround(fast.x, fast.y, fast.z, floor) && fast.y < 1.7f,
                "y=" + fast.y + " ground=" + PlayerBody.onGround(fast.x, fast.y, fast.z, floor));
    }

    private void input() {
        ButtonEdge edge = new ButtonEdge();
        int presses = 0;
        for (int frame = 0; frame < 30; frame++) {
            if (edge.poll(true)) {
                presses++;
            }
        }
        edge.disarm();
        boolean leaked = edge.poll(true);
        edge.poll(false);
        boolean afterRelease = edge.poll(true);
        check("UI01", presses == 1 && !leaked && afterRelease, "presses=" + presses + " leaked=" + leaked);

        boolean wrap = EditHotbar.shift(0, -1) == 8 && EditHotbar.shift(8, 1) == 0;
        int slot = 0;
        for (int i = 0; i < 40; i++) {
            slot = EditHotbar.shift(slot, 1);
        }
        check("REG02", wrap && slot == 4 && EditHotbar.idAt(2) == BlockIds.STONE, "slot=" + slot);

        ButtonEdge place = new ButtonEdge();
        int placements = 0;
        for (int frame = 0; frame < 144; frame++) {
            if (place.poll(frame > 0)) {
                placements++;
            }
        }
        check("TIME04", placements == 1, "placements=" + placements);
    }

    private void saves() throws Exception {
        Path directory = Files.createTempDirectory("cubic-dawn-save");
        Path file = directory.resolve("world.txt");
        BlockWorld world = BlockWorld.generateIsland();
        PlayerState player = new PlayerState(2f, 3.5f, 4f, 0.25f, -0.5f, 3);
        SnapshotIO.save(file, world, player);
        SnapshotIO.Snapshot loaded = SnapshotIO.load(file);
        BlockWorld restored = BlockWorld.empty();
        restored.applySnapshot(loaded);
        boolean playerOk = loaded.player() != null
                && loaded.player().x() == player.x()
                && loaded.player().y() == player.y()
                && loaded.player().z() == player.z()
                && loaded.player().yaw() == player.yaw()
                && loaded.player().pitch() == player.pitch()
                && loaded.player().slot() == 3;
        check("SAVE01", restored.sameSolids(world) && loaded.version() == 4, "reload");
        check("SAVE02", playerOk, "player");

        BlockWorld edited = BlockWorld.empty();
        edited.placeBlock(80L, 0, 0, 0, BlockIds.STONE, 0, (x, y, z) -> false);
        long revision = edited.revision();
        String earlier = SnapshotIO.encode(edited, player, revision);
        edited.placeBlock(81L, 1, 0, 0, BlockIds.DIRT, 0, (x, y, z) -> false);
        Path partial = directory.resolve("partial.txt");
        SnapshotIO.atomicWrite(partial, earlier.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        edited.markCleanIfUnchanged(revision);
        SnapshotIO.Snapshot earlierSnapshot = SnapshotIO.parse(earlier);
        boolean keptDirtOut = true;
        for (int[] block : earlierSnapshot.blocks()) {
            if (block[0] == 1 && block[1] == 0 && block[2] == 0) {
                keptDirtOut = false;
            }
        }
        check("SAVE06", edited.dirty() && keptDirtOut && edited.getId(1, 0, 0) == BlockIds.DIRT, "dirty=" + edited.dirty());

        Path guarded = directory.resolve("guarded.txt");
        SnapshotIO.save(guarded, world, player);
        SnapshotIO.save(guarded, world, player);
        byte[] backup = Files.readAllBytes(SnapshotIO.backupPath(guarded));
        String broken = Files.readString(guarded).replace("seed " + BlockWorld.SEED, "seed 0");
        Files.writeString(guarded, broken);
        byte[] damaged = Files.readAllBytes(guarded);
        boolean rejected = false;
        try {
            SnapshotIO.load(guarded);
        } catch (IllegalStateException exception) {
            rejected = exception.getMessage().contains("checksum");
        }
        boolean primaryUntouched = java.util.Arrays.equals(Files.readAllBytes(guarded), damaged);
        boolean backupUntouched = java.util.Arrays.equals(Files.readAllBytes(SnapshotIO.backupPath(guarded)), backup);
        check("SAVE05", rejected && primaryUntouched && backupUntouched, "rejected=" + rejected);

        Files.delete(guarded);
        SnapshotIO.Snapshot fromBackup = SnapshotIO.load(guarded);
        check("SAVE04",
                fromBackup.restoredFromBackup() && fromBackup.blocks().size() == 21 * 21 * 4,
                "backup blocks=" + fromBackup.blocks().size());

        Path legacy = directory.resolve("legacy.txt");
        Files.writeString(legacy, "cubic-dawn-blocks 3\nseed 20261116\n1 2 3 3\n0 0 0 42\n");
        SnapshotIO.Snapshot old = SnapshotIO.parse(Files.readString(legacy));
        BlockWorld migrated = BlockWorld.empty();
        migrated.applySnapshot(old);
        check("SAVE_V3",
                old.player() == null
                        && migrated.getId(1, 2, 3) == BlockIds.STONE
                        && migrated.getId(0, 0, 0) == 42
                        && old.unknownIds().contains(42),
                "unknown=" + old.unknownIds());
        SnapshotIO.save(legacy, migrated, player);
        SnapshotIO.Snapshot again = SnapshotIO.load(legacy);
        BlockWorld second = BlockWorld.empty();
        second.applySnapshot(again);
        check("SAVE08",
                again.version() == 4
                        && second.getId(0, 0, 0) == 42
                        && second.getId(1, 2, 3) == BlockIds.STONE,
                "version=" + again.version());
    }

    private void check(String id, boolean passed, String detail) {
        if (passed) {
            System.out.println("PASS " + id);
        } else {
            System.out.println("FAIL " + id + " " + detail);
            failures.add(id);
        }
    }

    private static boolean close(float value, float expected) {
        return Math.abs(value - expected) <= 1.0e-4f;
    }

    private static String describe(VoxelRay.Hit hit) {
        return hit.kind() + " (" + hit.x() + "," + hit.y() + "," + hit.z() + ") t="
                + hit.distance() + " n=(" + hit.normalX() + "," + hit.normalY() + "," + hit.normalZ() + ")";
    }

    private static final class HitProbe implements VoxelRay.WorldProbe {
        private final HashMap<Long, Aabb[]> blocks = new HashMap<>();
        private final HashMap<Long, Boolean> unloaded = new HashMap<>();

        void solid(int x, int y, int z) {
            blocks.put(key(x, y, z), new Aabb[] {Aabb.unitCell(x, y, z)});
        }

        void thin(int x, int y, int z, Aabb box) {
            blocks.put(key(x, y, z), new Aabb[] {box});
        }

        void unload(int x, int y, int z) {
            unloaded.put(key(x, y, z), Boolean.TRUE);
        }

        @Override
        public Cell inspect(int x, int y, int z) {
            long key = key(x, y, z);
            if (unloaded.containsKey(key)) {
                return Cell.unloaded();
            }
            Aabb[] boxes = blocks.get(key);
            if (boxes == null) {
                return Cell.pass();
            }
            return Cell.shape(BlockIds.STONE, 1, boxes);
        }

        private static long key(int x, int y, int z) {
            return ((long) x << 42) ^ ((long) y << 21) ^ (z & 0x1FFFFFL);
        }
    }
}
