package io.github.brooswitminecraft.dynamicskateboards;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Deterministic, headless tests over {@link GrindEdgeDetector} &mdash; MINECRAFT-98's acceptance
 * criteria. Every test builds its own small collision geometry (vanilla-shaped boxes, or this
 * mod's actual {@link SlopeShapes}/{@link CurvedShapes}) and feeds it through a
 * {@link FakeGrindCollisionSource}; nothing here depends on a running game or any block-id list.
 */
class GrindEdgeDetectorTest {
    private static final BlockPos ORIGIN = new BlockPos(0, 0, 0);
    private static final double EPS = 1.0e-6;

    // --- Length threshold, both sides of the boundary ---

    @Test
    void edgeBelowLengthThresholdIsNotACandidate() {
        double belowThresholdBlocks = (SkateConstants.GRIND_MIN_EDGE_LENGTH_VOXELS - 1.0) / 16.0;
        VoxelShape smallFootprint = Shapes.box(0, 0, 0, belowThresholdBlocks, 0.5, belowThresholdBlocks);
        FakeGrindCollisionSource source = new FakeGrindCollisionSource().put(ORIGIN, smallFootprint);

        List<GrindPath> candidates = GrindEdgeDetector.findCandidatePaths(source, ORIGIN, 0);

        assertTrue(candidates.isEmpty(), "an edge shorter than the usable-length threshold must not qualify");
    }

    @Test
    void edgeAtOrAboveLengthThresholdIsACandidate() {
        double aboveThresholdBlocks = (SkateConstants.GRIND_MIN_EDGE_LENGTH_VOXELS + 1.0) / 16.0;
        VoxelShape footprint = Shapes.box(0, 0, 0, aboveThresholdBlocks, 0.5, aboveThresholdBlocks);
        FakeGrindCollisionSource source = new FakeGrindCollisionSource().put(ORIGIN, footprint);

        List<GrindPath> candidates = GrindEdgeDetector.findCandidatePaths(source, ORIGIN, 0);

        assertFalse(candidates.isEmpty(), "an edge at/above the usable-length threshold must qualify");
        assertTrue(candidates.stream().anyMatch(p -> p.length() >= aboveThresholdBlocks - EPS));
    }

    // --- The decisive "this is geometric, not a list" tests: plain vanilla shapes ---

    @Test
    void vanillaSlabEdgeQualifiesWithNoModBlocksInvolved() {
        VoxelShape bottomSlab = Shapes.box(0, 0, 0, 1, 0.5, 1);
        FakeGrindCollisionSource source = new FakeGrindCollisionSource().put(ORIGIN, bottomSlab);

        List<GrindPath> candidates = GrindEdgeDetector.findCandidatePaths(source, ORIGIN, 0);

        assertFalse(candidates.isEmpty(), "a plain vanilla slab's exposed top edge must be a grind candidate");
        assertTrue(candidates.stream().anyMatch(p -> p.length() >= 1.0 - EPS),
                "the slab's full-width edge should read as one 1-block-long chord");
    }

    @Test
    void vanillaStairEdgeQualifiesWithNoModBlocksInvolved() {
        VoxelShape lowerHalf = Shapes.box(0, 0, 0, 1, 0.5, 1);
        VoxelShape upperBack = Shapes.box(0, 0.5, 0.5, 1, 1, 1);
        VoxelShape stair = Shapes.joinUnoptimized(lowerHalf, upperBack, BooleanOp.OR).optimize();
        FakeGrindCollisionSource source = new FakeGrindCollisionSource().put(ORIGIN, stair);

        List<GrindPath> candidates = GrindEdgeDetector.findCandidatePaths(source, ORIGIN, 0);

        assertFalse(candidates.isEmpty(), "a plain vanilla stair's exposed tread edge must be a grind candidate");
        double minLengthBlocks = SkateConstants.GRIND_MIN_EDGE_LENGTH_VOXELS / 16.0;
        assertTrue(candidates.stream().anyMatch(p -> p.length() >= minLengthBlocks - EPS));
    }

    // --- Interior/occluded edges must not qualify ---

    @Test
    void edgeBuriedAgainstNeighborsOnAllSidesDoesNotQualify() {
        VoxelShape bottomSlab = Shapes.box(0, 0, 0, 1, 0.5, 1);
        FakeGrindCollisionSource source = new FakeGrindCollisionSource()
                .put(ORIGIN, bottomSlab)
                .putFullBlock(ORIGIN.relative(Direction.NORTH))
                .putFullBlock(ORIGIN.relative(Direction.SOUTH))
                .putFullBlock(ORIGIN.relative(Direction.EAST))
                .putFullBlock(ORIGIN.relative(Direction.WEST));

        List<GrindPath> candidates = GrindEdgeDetector.findCandidatePaths(source, ORIGIN, 0);

        assertTrue(candidates.isEmpty(), "an edge buried against solid neighbors on every side must not qualify");
    }

    @Test
    void sameSlabWithNoNeighborsQualifies() {
        // Control for the test above: identical geometry, nothing occluding it.
        VoxelShape bottomSlab = Shapes.box(0, 0, 0, 1, 0.5, 1);
        FakeGrindCollisionSource source = new FakeGrindCollisionSource().put(ORIGIN, bottomSlab);

        List<GrindPath> candidates = GrindEdgeDetector.findCandidatePaths(source, ORIGIN, 0);

        assertFalse(candidates.isEmpty(), "the same slab with open sides must qualify");
    }

    // --- Diagonal slope side edges ---

    @Test
    void slopeSideEdgeQualifiesAndYieldsRisingPath() {
        VoxelShape slope = SlopeShapes.buildFacingShapes(8, 0, false).get(Direction.SOUTH);
        FakeGrindCollisionSource source = new FakeGrindCollisionSource().put(ORIGIN, slope);

        List<GrindPath> candidates = GrindEdgeDetector.findCandidatePaths(source, ORIGIN, 0);

        assertFalse(candidates.isEmpty(), "a slope's side edge must be a grind candidate");
        // A generous tolerance, not a tight one: stepHeight(0, 8) == 0, so the slope's own
        // leading column (zero collision height) contributes no box at all - the detected chord
        // starts at the first column with real height rather than the doc's idealized (0,0)
        // corner, a few voxels short of the full sqrt(16^2+8^2) diagonal. Still comfortably past
        // the usable-length threshold and still rising - that's what this test actually checks.
        double expectedLengthBlocks = Math.sqrt(16.0 * 16.0 + 8.0 * 8.0) / 16.0;
        GrindPath risingPath = candidates.stream()
                .filter(p -> Math.abs(p.length() - expectedLengthBlocks) < 0.3)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no candidate matched the expected ~" + expectedLengthBlocks + "-block diagonal among: " + candidates));

        double riseBlocks = risingPath.end().y - risingPath.start().y;
        assertTrue(Math.abs(riseBlocks) > 0.4, "the path must actually rise/descend, not stay flat: rise=" + riseBlocks);
    }

    // --- Curved pieces: incremental direction change ---

    @Test
    void curvedPieceYieldsIncrementallyTurningPath() {
        VoxelShape curved = CurvedShapes.buildFacingShapes(8, ChamferSide.RIGHT).get(Direction.SOUTH);
        FakeGrindCollisionSource source = new FakeGrindCollisionSource().put(ORIGIN, curved);

        List<GrindPath> candidates = GrindEdgeDetector.findCandidatePaths(source, ORIGIN, 0);
        GrindPath curvedPath = candidates.stream()
                .filter(p -> p.segments().size() > 1)
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected a multi-segment curved path among: " + candidates));

        assertTrue(curvedPath.segments().size() >= 4,
                "a curved piece's path should be made of several short segments, not one straight chord");

        Vec3 firstDirection = curvedPath.segments().get(0).direction();
        Vec3 lastDirection = curvedPath.segments().get(curvedPath.segments().size() - 1).direction();
        double headingDeltaDegrees = horizontalAngleBetweenDegrees(firstDirection, lastDirection);
        assertTrue(headingDeltaDegrees > 2.0,
                "direction must change incrementally along a curved piece's length, delta=" + headingDeltaDegrees);
        assertTrue(headingDeltaDegrees < 45.0,
                "one piece's turn must stay well short of a 90-degree jog, delta=" + headingDeltaDegrees);
    }

    @Test
    void followingACurvedPieceChangesTravelDirection() {
        VoxelShape curved = CurvedShapes.buildFacingShapes(8, ChamferSide.RIGHT).get(Direction.SOUTH);
        FakeGrindCollisionSource source = new FakeGrindCollisionSource().put(ORIGIN, curved);
        GrindPath curvedPath = GrindEdgeDetector.findCandidatePaths(source, ORIGIN, 0).stream()
                .filter(p -> p.segments().size() > 1)
                .findFirst()
                .orElseThrow();

        GrindFollower follower = new GrindFollower(curvedPath, 0.0, 1);
        double startHeading = Double.NaN;
        double endHeading = Double.NaN;
        for (int tick = 0; tick < 500; tick++) {
            GrindFollower.Step step = follower.advance(0.05, true, false);
            if (tick == 0) {
                startHeading = step.headingDegrees();
            }
            endHeading = step.headingDegrees();
            if (step.released()) {
                break;
            }
        }

        double delta = Math.abs(wrapDegrees(endHeading - startHeading));
        assertTrue(delta > 2.0, "following the full curved path must change travel direction, delta=" + delta);
    }

    // --- Every piece docs/grindable-edges.md declares grindable must be detected as such ---

    @Test
    void everySlopeKindIsDetectedAsGrindable() {
        for (SlopeKind kind : SlopeKind.values()) {
            VoxelShape shape = SlopeShapes.buildFacingShapes(kind.riseVoxels(), kind.verticalShift(), kind.inverted())
                    .get(Direction.SOUTH);
            FakeGrindCollisionSource source = new FakeGrindCollisionSource().put(ORIGIN, shape);

            List<GrindPath> candidates = GrindEdgeDetector.findCandidatePaths(source, ORIGIN, 0);

            assertFalse(candidates.isEmpty(), kind + " must be detected as grindable per docs/grindable-edges.md");
            // Generous, not tight, for the same leading-zero-height-column reason as
            // slopeSideEdgeQualifiesAndYieldsRisingPath above: a non-raised kind's first columns
            // can be exactly height 0 (no collision box at all), so the detected chord can start
            // a few voxels short of the doc's idealized full-diagonal length.
            double expectedLengthBlocks = Math.sqrt(16.0 * 16.0 + kind.riseVoxels() * (double) kind.riseVoxels()) / 16.0;
            assertTrue(candidates.stream().anyMatch(p -> p.length() >= expectedLengthBlocks * 0.7),
                    kind + " should expose a side edge close to the doc's ~" + expectedLengthBlocks + "-block length, got: " + candidates);
        }
    }

    @Test
    void everyCurvedPieceVariantIsDetectedAsGrindable() {
        for (int height : new int[] {8, 16}) {
            for (ChamferSide side : ChamferSide.values()) {
                VoxelShape shape = CurvedShapes.buildFacingShapes(height, side).get(Direction.SOUTH);
                FakeGrindCollisionSource source = new FakeGrindCollisionSource().put(ORIGIN, shape);

                List<GrindPath> candidates = GrindEdgeDetector.findCandidatePaths(source, ORIGIN, 0);

                boolean foundCurvedPath = candidates.stream().anyMatch(p -> p.segments().size() > 1);
                assertTrue(foundCurvedPath, "curved height=" + height + " side=" + side
                        + " must be detected as grindable per docs/grindable-edges.md");
            }
        }
    }

    private static double horizontalAngleBetweenDegrees(Vec3 a, Vec3 b) {
        Vec3 flatA = new Vec3(a.x, 0, a.z).normalize();
        Vec3 flatB = new Vec3(b.x, 0, b.z).normalize();
        double dot = Math.max(-1.0, Math.min(1.0, flatA.dot(flatB)));
        return Math.toDegrees(Math.acos(dot));
    }

    private static double wrapDegrees(double degrees) {
        double d = degrees % 360.0;
        if (d >= 180.0) {
            d -= 360.0;
        } else if (d < -180.0) {
            d += 360.0;
        }
        return d;
    }
}
