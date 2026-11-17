package dev.cubicdawn;

import dev.cubicdawn.world.Aabb;
import dev.cubicdawn.world.BlockIds;
import dev.cubicdawn.world.BlockTicks;
import dev.cubicdawn.world.Inventory;
import dev.cubicdawn.world.ItemIds;
import dev.cubicdawn.world.MiningProgress;
import dev.cubicdawn.world.PlayerActions;
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
        verify.fallingSand();
        verify.inventory();
        verify.trees();
        verify.woodCrafting();
        verify.mining();
        if (!verify.failures.isEmpty()) {
            throw new IllegalStateException("Failed: " + verify.failures);
        }
        System.out.println("ALL PASS " + "coordinates rays edits movement input saves scheduled-blocks inventory crafting");
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
        SnapshotIO.save(file, world, player, Inventory.starter());
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
        check("SAVE01", restored.sameSolids(world) && loaded.version() == 6, "reload");
        check("SAVE02", playerOk, "player");

        BlockWorld edited = BlockWorld.empty();
        edited.placeBlock(80L, 0, 0, 0, BlockIds.STONE, 0, (x, y, z) -> false);
        long revision = edited.revision();
        String earlier = SnapshotIO.encode(edited, player, revision, Inventory.starter());
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
        SnapshotIO.save(guarded, world, player, Inventory.starter());
        SnapshotIO.save(guarded, world, player, Inventory.starter());
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
        SnapshotIO.save(legacy, migrated, player, Inventory.starter());
        SnapshotIO.Snapshot again = SnapshotIO.load(legacy);
        BlockWorld second = BlockWorld.empty();
        second.applySnapshot(again);
        check("SAVE08",
                again.version() == 6
                        && second.getId(0, 0, 0) == 42
                        && second.getId(1, 2, 3) == BlockIds.STONE,
                "version=" + again.version());
    }

    private void fallingSand() throws Exception {
        BlockWorld world = BlockWorld.empty();
        world.placeBlock(1, 0, 0, 0, BlockIds.STONE, 0, null);
        world.placeBlock(2, 0, 3, 0, BlockIds.SAND, 0, null);
        for (int i = 0; i < 3; i++) world.tick(null);
        Path file = Files.createTempDirectory("cubic-dawn-ticks").resolve("world.txt");
        SnapshotIO.save(file, world, null, Inventory.starter());
        BlockWorld restored = BlockWorld.empty();
        restored.applySnapshot(SnapshotIO.load(file));
        check("TASK_SAVE", restored.blockTicks().tick() == 3
                && restored.blockTicks().tasks().equals(world.blockTicks().tasks()), "pending tasks lost");
        for (int i = 0; i < 2; i++) restored.tick(null);
        check("SAND_DELAY", restored.getId(0, 3, 0) == BlockIds.SAND, "fell too soon after load");
        restored.tick(null);
        check("SAND_STEP", restored.getId(0, 3, 0) == BlockIds.AIR
                && restored.getId(0, 2, 0) == BlockIds.SAND && restored.countSolid() == 2, "non-atomic fall");
        for (int i = 0; i < 18; i++) restored.tick(null);
        check("SAND_LAND", restored.getId(0, 1, 0) == BlockIds.SAND
                && restored.blockTicks().tasks().isEmpty(), "did not settle");
        restored.breakBlock(3, 0, 0, 0, restored.query(0, 0, 0).revision());
        for (int i = 0; i < 6; i++) restored.tick(null);
        check("SAND_SUPPORT", restored.getId(0, 0, 0) == BlockIds.SAND, "support removal did not wake sand");
        // A player below sand must never be buried. Leaving wakes the retry.
        for (int i = 0; i < 6; i++) restored.tick((x, y, z) -> y == -1);
        check("SAND_BODY", restored.getId(0, 0, 0) == BlockIds.SAND, "fell into player");
        restored.setSimulationLoaded(0, -1, 0, false);
        for (int i = 0; i < 6; i++) restored.tick(null);
        check("SAND_UNLOADED", restored.getId(0, 0, 0) == BlockIds.SAND, "fell into unknown space");
        restored.setSimulationLoaded(0, -1, 0, true);
        for (int i = 0; i < 6; i++) restored.tick(null);
        check("SAND_RETRY", restored.getId(0, -1, 0) == BlockIds.SAND, "did not retry");
        for (int i = 0; i < 120; i++) restored.tick(null);
        check("SAND_BOUNDARY", restored.getId(0, BlockWorld.BLOCK_MIN, 0) == BlockIds.SAND
                && restored.countSolid() == 1 && restored.blockTicks().tasks().isEmpty(), "sand lost at boundary");
        BlockWorld stale = BlockWorld.empty();
        stale.placeBlock(1, 0, 2, 0, BlockIds.SAND, 0, null);
        stale.breakBlock(2, 0, 2, 0, stale.query(0, 2, 0).revision());
        stale.placeBlock(3, 0, 2, 0, BlockIds.STONE, stale.query(0, 2, 0).revision(), null);
        for (int i = 0; i < 6; i++) stale.tick(null);
        check("SAND_STALE", stale.getId(0, 2, 0) == BlockIds.STONE && stale.countSolid() == 1,
                "stale task changed replacement block");
        BlockWorld replaced = BlockWorld.empty();
        replaced.placeBlock(1, 0, 2, 0, BlockIds.SAND, 0, null);
        for (int i = 0; i < 3; i++) replaced.tick(null);
        replaced.breakBlock(2, 0, 2, 0, replaced.query(0, 2, 0).revision());
        replaced.placeBlock(3, 0, 2, 0, BlockIds.SAND, replaced.query(0, 2, 0).revision(), null);
        for (int i = 0; i < 3; i++) replaced.tick(null);
        boolean waited = replaced.getId(0, 2, 0) == BlockIds.SAND;
        for (int i = 0; i < 3; i++) replaced.tick(null);
        check("SAND_REPLACE_DELAY", waited && replaced.getId(0, 1, 0) == BlockIds.SAND,
                "replacement inherited the removed block's timer");
        BlockWorld stack = BlockWorld.empty();
        stack.placeBlock(1, 0, 0, 0, BlockIds.STONE, 0, null);
        stack.placeBlock(2, 0, 1, 0, BlockIds.SAND, 0, null);
        stack.placeBlock(3, 0, 2, 0, BlockIds.SAND, 0, null);
        for (int i = 0; i < 6; i++) stack.tick(null);
        stack.breakBlock(4, 0, 0, 0, stack.query(0, 0, 0).revision());
        for (int i = 0; i < 12; i++) stack.tick(null);
        check("SAND_STACK", stack.getId(0, 2, 0) == BlockIds.AIR && stack.countSolid() == 2,
                "stack did not wake or lost sand");
        BlockTicks queue = new BlockTicks();
        for (int x = -16; x <= 16; x++) for (int z = -16; z <= 16; z++) {
            queue.schedule(x, 0, z);
            queue.schedule(x, 0, z);
        }
        for (int i = 0; i < 5; i++) queue.advance();
        var due = queue.advance();
        check("TASK_BUDGET", due.size() == 256 && queue.tasks().size() == 1089 - 256
                && due.get(0).x() == -16 && due.get(0).z() == -16, "budget/order/dedup");
        String v4 = checkedSave("cubic-dawn-blocks 4\nseed 20261116\nrevision 1\n0 0 0 42\n");
        SnapshotIO.Snapshot old = SnapshotIO.parse(v4);
        check("SAVE_V4", old.version() == 4 && old.tasks().isEmpty() && old.simulationTick() == 0
                && old.unknownIds().contains(42), "v4 migration");
        boolean rejected = true;
        for (String bad : new String[] {
                "tick -1\n", "tick 0\ntick 1\n", "", "tick 0\nfall -1 0 0 0\n",
                "tick 0\nfall 6 17 0 0\n", "tick 0\nfall 6 0 0 0\nfall 7 0 0 0\n"}) {
            try { SnapshotIO.parse(checkedSave("cubic-dawn-blocks 5\n" + bad)); rejected = false; }
            catch (IllegalArgumentException | IllegalStateException expected) { }
        }
        check("TASK_INVALID", rejected, "invalid scheduler state accepted");
        boolean sameRates = true;
        String reference = null;
        for (int fps : new int[] {30, 60, 120, 144}) {
            BlockWorld falling = BlockWorld.empty();
            falling.placeBlock(1, 0, 10, 0, BlockIds.SAND, 0, null);
            FixedStep clock = new FixedStep();
            for (int frame = 0; frame < fps; frame++) {
                clock.advance(1.0 / fps, true, false, (seconds, jump) -> falling.tick(null));
            }
            String state = SnapshotIO.encode(falling, null, falling.revision(), Inventory.starter());
            if (reference == null) reference = state;
            sameRates &= reference.equals(state);
            long beforePause = falling.blockTicks().tick();
            clock.advance(3600, false, false, (seconds, jump) -> falling.tick(null));
            sameRates &= beforePause == falling.blockTicks().tick();
        }
        check("TASK_RATE_PAUSE", sameRates, "frame rate or paused wall time changed tasks");
        String tampered = Files.readString(file).replace("fall 6 ", "fall 7 ");
        boolean checksumRejected = false;
        try { SnapshotIO.parse(tampered); }
        catch (IllegalStateException expected) { checksumRejected = expected.getMessage().contains("checksum"); }
        check("TASK_CHECKSUM", checksumRejected, "task not covered by checksum");
        // Resume must produce the same world and queue as uninterrupted simulation.
        for (int i = 0; i < 21; i++) world.tick(null);
        BlockWorld resumed = BlockWorld.empty();
        resumed.applySnapshot(SnapshotIO.load(file));
        for (int i = 0; i < 21; i++) resumed.tick(null);
        check("TASK_RESUME", world.sameSolids(resumed)
                && world.blockTicks().tick() == resumed.blockTicks().tick()
                && world.blockTicks().tasks().equals(resumed.blockTicks().tasks()), "save/resume diverged");
    }

    private static String checkedSave(String canonical) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        int newline = canonical.indexOf('\n');
        return canonical.substring(0, newline + 1) + "checksum sha256 "
                + java.util.HexFormat.of().formatHex(digest) + "\n" + canonical.substring(newline + 1);
    }

    private void inventory() throws Exception {
        Inventory bag = new Inventory();
        bag.add(BlockIds.STONE, 70);
        check("INV_STACK", bag.slot(0).count() == 64 && bag.slot(1).count() == 6, "stack split");
        bag.remove(0, 1);
        bag.add(BlockIds.STONE, 1);
        check("INV_MERGE", bag.slot(0).count() == 64 && bag.count(BlockIds.STONE) == 70, "merge order");
        var saved = bag.snapshot();
        bag.swap(0, 26);
        check("INV_SWAP", bag.slot(26).count() == 64 && saved.get(0).count() == 64
                && saved.get(26).count() == 0, "swap or snapshot alias");
        Inventory full = new Inventory();
        full.add(BlockIds.DIRT, Inventory.SLOTS * Inventory.STACK_LIMIT);
        var before = full.snapshot();
        check("INV_FULL", !full.add(BlockIds.STONE, 1) && before.equals(full.snapshot()), "overflow lost items");
        BlockWorld world = BlockWorld.empty();
        world.placeBlock(1, 0, 0, 0, BlockIds.STONE, 0, null);
        long revision = world.revision();
        var refused = PlayerActions.breakBlock(world, full, 2, 0, 0, 0, world.query(0, 0, 0).revision());
        check("INV_FULL_MINE", !refused.accepted() && world.revision() == revision
                && world.getId(0, 0, 0) == BlockIds.STONE && before.equals(full.snapshot()), "full mining deleted block");
        Inventory collected = new Inventory();
        int cellRevision = world.query(0, 0, 0).revision();
        var mined = PlayerActions.breakBlock(world, collected, 3, 0, 0, 0, cellRevision);
        var duplicate = PlayerActions.breakBlock(world, collected, 3, 0, 0, 0, cellRevision);
        check("INV_MINE", mined.accepted() && !duplicate.accepted() && collected.count(BlockIds.STONE) == 1
                && world.getId(0, 0, 0) == BlockIds.AIR, "mining duplicated items");
        var rejectedPlace = PlayerActions.placeBlock(world, collected, 0, 4, 0, 0, 0,
                world.query(0, 0, 0).revision(), (x, y, z) -> true);
        check("INV_PLACE_REJECT", !rejectedPlace.accepted() && collected.count(BlockIds.STONE) == 1,
                "failed placement consumed item");
        var placed = PlayerActions.placeBlock(world, collected, 0, 5, 0, 0, 0,
                world.query(0, 0, 0).revision(), null);
        var empty = PlayerActions.placeBlock(world, collected, 0, 6, 1, 0, 0, 0, null);
        check("INV_PLACE", placed.accepted() && !empty.accepted() && collected.count(BlockIds.STONE) == 0
                && world.getId(1, 0, 0) == BlockIds.AIR, "placement not finite");
        Inventory crafting = new Inventory();
        crafting.add(BlockIds.SAND, 4);
        check("CRAFT_SANDSTONE", crafting.craft(Inventory.Recipe.SANDSTONE)
                && crafting.count(BlockIds.SAND) == 0 && crafting.count(BlockIds.SANDSTONE) == 1, "recipe");
        crafting.add(BlockIds.STONE, 4);
        check("CRAFT_BRICKS", crafting.craft(Inventory.Recipe.STONE_BRICKS)
                && crafting.count(BlockIds.STONE) == 0 && crafting.count(BlockIds.STONE_BRICKS) == 4, "recipe");
        var noIngredients = crafting.snapshot();
        check("CRAFT_MISSING", !crafting.craft(Inventory.Recipe.SANDSTONE)
                && noIngredients.equals(crafting.snapshot()), "failed crafting changed items");
        Inventory packed = new Inventory();
        packed.add(BlockIds.SAND, 64);
        packed.add(BlockIds.DIRT, 26 * 64);
        var packedBefore = packed.snapshot();
        check("CRAFT_FULL", !packed.craft(Inventory.Recipe.SANDSTONE)
                && packedBefore.equals(packed.snapshot()), "craft without output space consumed ingredients");
        // Consuming the entire input stack frees the output slot even in a full inventory.
        packed.remove(0, 60);
        check("CRAFT_FREED_SLOT", packed.craft(Inventory.Recipe.SANDSTONE)
                && packed.count(BlockIds.SANDSTONE) == 1, "did not reuse freed slot");
        Inventory split = new Inventory();
        split.add(BlockIds.STONE, 66);
        split.remove(0, 62);
        check("CRAFT_SPLIT_INPUT", split.craft(Inventory.Recipe.STONE_BRICKS)
                && split.count(BlockIds.STONE) == 0 && split.count(BlockIds.STONE_BRICKS) == 4, "split ingredients");
        crafting.swap(1, 26);
        crafting.add(42, 2);
        Path file = Files.createTempDirectory("cubic-dawn-inventory").resolve("world.txt");
        SnapshotIO.save(file, world, null, crafting);
        var snapshot = SnapshotIO.load(file);
        Inventory recovered = Inventory.restore(snapshot.inventory());
        check("INV_SAVE", snapshot.version() == 6 && recovered.snapshot().equals(crafting.snapshot())
                && recovered.count(42) == 2, "inventory reload or unknown id lost");
        Inventory exhausted = new Inventory();
        SnapshotIO.save(file, world, null, exhausted);
        check("INV_EMPTY_SAVE", Inventory.restore(SnapshotIO.load(file).inventory()).count(BlockIds.SAND) == 0,
                "empty bag refilled from starter kit");
        String v5 = checkedSave("cubic-dawn-blocks 5\ntick 12\nfall 18 0 1 0\n0 1 0 8\n");
        var legacy = SnapshotIO.parse(v5);
        check("INV_MIGRATION", Inventory.restore(legacy.inventory()).count(BlockIds.SAND) == 16
                && legacy.simulationTick() == 12 && legacy.tasks().size() == 1, "v5 upgrade");
        String canonical = "cubic-dawn-blocks 6\ntick 0\n";
        for (int slot = 0; slot < Inventory.SLOTS; slot++) canonical += "item " + slot + " 0 0\n";
        boolean invalidRejected = true;
        for (String invalid : new String[] {
                canonical.replace("item 0 0 0\n", ""), canonical + "item 0 1 1\n",
                canonical.replace("item 0 0 0", "item 0 3 65"),
                canonical.replace("item 0 0 0", "item 0 0 1"),
                canonical.replace("item 0 0 0", "item 27 0 0"),
                canonical.replace("item 0 0 0", "item 0 -1 1")}) {
            try { SnapshotIO.parse(checkedSave(invalid)); invalidRejected = false; }
            catch (IllegalArgumentException | IllegalStateException expected) { }
        }
        check("INV_INVALID", invalidRejected, "invalid inventory accepted");
        String tampered = Files.readString(file).replace("item 0 0 0", "item 0 3 64");
        boolean checksum = false;
        try { SnapshotIO.parse(tampered); }
        catch (IllegalStateException expected) { checksum = expected.getMessage().contains("checksum"); }
        check("INV_CHECKSUM", checksum, "inventory not covered by checksum");
        Files.delete(file);
        var backup = SnapshotIO.load(file);
        check("INV_BACKUP", backup.restoredFromBackup()
                && backup.inventory().equals(crafting.snapshot()), "backup lost inventory");
        Inventory emptyBag = new Inventory();
        for (int x = 1; x <= 4; x++) {
            world.placeBlock(100 + x, x, 0, 0, BlockIds.STONE, 0, null);
            PlayerActions.breakBlock(world, emptyBag, 200 + x, x, 0, 0, world.query(x, 0, 0).revision());
        }
        boolean made = emptyBag.craft(Inventory.Recipe.STONE_BRICKS);
        boolean built = PlayerActions.placeBlock(world, emptyBag, 0, 300, 1, 1, 0, 0, null).accepted();
        check("INV_LOOP", made && built && emptyBag.count(BlockIds.STONE_BRICKS) == 3
                && world.getId(1, 1, 0) == BlockIds.STONE_BRICKS, "collect craft build loop");
    }

    private void trees() {
        BlockWorld first = BlockWorld.generateForestIsland();
        BlockWorld second = BlockWorld.generateForestIsland();
        check("TREE_SEED", first.sameSolids(second) && first.countSolid() == 1764 + 42
                && first.getId(-5, BlockWorld.surface(-5, -4) + 1, -4) == BlockIds.LOG, "tree generation");
        BlockWorld world = BlockWorld.empty();
        world.placeBlock(1, 0, 0, 0, BlockIds.DIRT, 0, null);
        world.placeBlock(2, 1, 3, 0, BlockIds.STONE, 0, null);
        int before = world.mutations();
        check("TREE_BUILDING", !world.plantTree(3, 0, 1, 0, null).accepted()
                && world.mutations() == before && world.getId(1, 3, 0) == BlockIds.STONE, "overwrote building");
        world.breakBlock(4, 1, 3, 0, world.query(1, 3, 0).revision());
        before = world.mutations();
        check("TREE_BODY", !world.plantTree(5, 0, 1, 0, (x,y,z) -> y == 4).accepted()
                && before == world.mutations(), "buried player");
        world.setSimulationLoaded(1, 3, 0, false);
        check("TREE_UNLOADED", !world.plantTree(6, 0, 1, 0, null).accepted(), "unloaded footprint");
        world.setSimulationLoaded(1, 3, 0, true);
        check("TREE_PLANT", world.plantTree(7, 0, 1, 0, null).accepted() && world.countSolid() == 22
                && !world.plantTree(7, 0, 1, 0, null).accepted(), "tree or duplicate");
        BlockWorld edge = BlockWorld.empty();
        edge.placeBlock(1, 16, 0, 0, BlockIds.DIRT, 0, null);
        check("TREE_BOUNDARY", !edge.plantTree(2, 16, 1, 0, null).accepted() && edge.countSolid() == 1, "boundary overwrite");
    }

    private void woodCrafting() throws Exception {
        Inventory bag = new Inventory();
        bag.add(BlockIds.LOG, 4);
        check("WOOD_PLANKS", bag.craft(Inventory.Recipe.PLANKS) && bag.count(BlockIds.PLANKS) == 4, "log recipe");
        bag.craft(Inventory.Recipe.PLANKS);
        bag.craft(Inventory.Recipe.STICKS);
        check("WOOD_STICKS", bag.count(ItemIds.STICK) == 4 && bag.count(BlockIds.PLANKS) == 6, "stick recipe");
        var before = bag.snapshot();
        check("TOOL_BENCH_GATE", !bag.craft(Inventory.Recipe.PICKAXE) && before.equals(bag.snapshot()), "no bench consumed material");
        check("TOOL_MULTIPLE_INPUTS", bag.craft(Inventory.Recipe.PICKAXE, true)
                && bag.count(BlockIds.PLANKS) == 3 && bag.count(ItemIds.STICK) == 2
                && bag.count(ItemIds.WOOD_PICKAXE) == 1, "multi-input crafting");
        bag.add(ItemIds.WOOD_PICKAXE, 2);
        check("TOOL_NONSTACK", bag.snapshot().stream().filter(t -> t.id() == ItemIds.WOOD_PICKAXE).count() == 3
                && bag.snapshot().stream().filter(t -> ItemIds.tool(t.id())).allMatch(t -> t.count() == 1), "tool stacking");
        Inventory incomplete = new Inventory(); incomplete.add(BlockIds.PLANKS, 3);
        var unchanged = incomplete.snapshot();
        check("TOOL_MISSING_INPUT", !incomplete.craft(Inventory.Recipe.AXE, true)
                && unchanged.equals(incomplete.snapshot()), "partial recipe consumed planks");
        BlockWorld world = BlockWorld.empty();
        world.placeBlock(1, 0, 0, 0, BlockIds.WORKBENCH, 0, null);
        check("BENCH_DISTANCE", world.nearWorkbench(3, 0, 0) && !world.nearWorkbench(3.01f, 0, 0), "bench radius");
        world.setSimulationLoaded(0, 0, 0, false);
        check("BENCH_UNLOADED", !world.nearWorkbench(0, 0, 0), "unloaded bench granted access");
        world.setSimulationLoaded(0, 0, 0, true);
        Path file = Files.createTempDirectory("cubic-dawn-tools").resolve("world.txt");
        SnapshotIO.save(file, world, null, bag);
        check("TOOL_SAVE", SnapshotIO.load(file).inventory().equals(bag.snapshot()), "tool save");
        Inventory seeds = new Inventory(); seeds.add(BlockIds.DIRT, 4);
        check("SAPLING_RECIPE", seeds.craft(Inventory.Recipe.SAPLING) && seeds.count(ItemIds.SAPLING) == 1, "old-island recipe");
        world.placeBlock(2, 5, 0, 0, BlockIds.DIRT, 0, null);
        int slot = 0;
        check("SAPLING_PLACE", PlayerActions.placeBlock(world, seeds, slot, 3, 5, 1, 0, 0, null).accepted()
                && seeds.count(ItemIds.SAPLING) == 0 && world.getId(5, 1, 0) == BlockIds.LOG, "planting transaction");
        seeds.add(ItemIds.SAPLING, 1);
        check("SAPLING_FAIL", !PlayerActions.placeBlock(world, seeds, 0, 4, 8, 1, 0, 0, null).accepted()
                && seeds.count(ItemIds.SAPLING) == 1, "failed planting consumed seed");
    }

    private void mining() {
        BlockWorld world = BlockWorld.empty();
        world.placeBlock(1, 0, 0, 0, BlockIds.STONE, 0, null);
        VoxelRay.Hit hit = VoxelRay.castFromEngineEye(0, 0, 2, 0, 0, -1, 5, world);
        MiningProgress progress = new MiningProgress();
        int ticks = 0;
        while (!progress.advance(1f/60, true, hit, 0, 0) && ticks < 200) ticks++;
        check("MINE_HAND", ticks + 1 == 108, "hand ticks=" + (ticks + 1));
        ticks = 0;
        while (!progress.advance(1f/60, true, hit, 0, ItemIds.WOOD_PICKAXE) && ticks < 200) ticks++;
        check("MINE_PICKAXE", ticks + 1 == 36, "tool ticks=" + (ticks + 1));
        progress.advance(.3f, true, hit, 0, 0);
        progress.advance(.1f, false, hit, 0, 0);
        check("MINE_RELEASE", progress.fraction() == 0, "release kept progress");
        progress.advance(.3f, true, hit, 0, 0);
        progress.advance(.1f, true, hit, 1, 0);
        check("MINE_SLOT", progress.fraction() < .06f, "slot change kept progress");
        progress.advance(.3f, true, hit, 1, 0);
        world.breakBlock(2, 0, 0, 0, world.query(0, 0, 0).revision());
        world.placeBlock(3, 0, 0, 0, BlockIds.STONE, world.query(0, 0, 0).revision(), null);
        var changed = VoxelRay.castFromEngineEye(0, 0, 2, 0, 0, -1, 5, world);
        progress.advance(.1f, true, changed, 1, 0);
        check("MINE_REVISION", progress.fraction() < .06f, "replacement inherited progress");
        progress.reset();
        check("MINE_PAUSE", progress.fraction() == 0, "pause reset");
        check("MINE_MATCHED_TOOL", MiningProgress.secondsFor(BlockIds.LOG, ItemIds.WOOD_AXE) == .3f
                && MiningProgress.secondsFor(BlockIds.LOG, ItemIds.WOOD_PICKAXE) == .9f
                && MiningProgress.secondsFor(BlockIds.DIRT, ItemIds.WOOD_SHOVEL) == .15f,
                "tool material matching");
        int completed = -1;
        boolean same = true;
        for (int fps : new int[] {30, 60, 120, 144}) {
            FixedStep clock = new FixedStep(); MiningProgress mine = new MiningProgress();
            int[] successes = {0};
            for (int frame = 0; frame < fps * 3; frame++) clock.advance(1.0/fps, true, false,
                    (seconds,jump) -> { if (mine.advance(seconds,true,changed,0,ItemIds.WOOD_PICKAXE)) successes[0]++; });
            if (completed < 0) completed = successes[0];
            same &= successes[0] == completed;
        }
        check("MINE_FRAMERATES", same && completed == 5, "rate changed mining completions");
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
