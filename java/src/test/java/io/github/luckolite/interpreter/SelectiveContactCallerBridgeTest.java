// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

/** Source-free bridge proving exact caller tokens can authorize only observed staff pixels. */
public class SelectiveContactCallerBridgeTest {
    private static final int W = 240, H = 112;

    private record Fixture(
            byte[] gray,
            byte[] labels,
            List<PairedTremoloInk.Head> heads,
            List<PairedTremoloInk.Head> middle) {}

    private static Fixture fixture(
            boolean includeBars, int stemSide, boolean includeStaff, int headY) {
        byte[] gray = new byte[W * H], labels = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        if (includeStaff)
            for (int y : new int[] {20, 34, 48, 62, 76})
                for (int x = 1; x < W - 1; x++) {
                    labels[y * W + x] = OmrMeasurePostProcessor.STAFF;
                    if (headY == 48) gray[y * W + x] = 0;
                }
        int[] centers = {60, 108};
        Object[] identities = {new Object(), new Object()};
        ExactComponentMembers.Stats[] statsRows = new ExactComponentMembers.Stats[2];
        for (int side = 0; side < 2; side++) {
            int cx = centers[side], cy = headY;
            for (int y = cy - 5; y <= cy + 5; y++)
                for (int x = cx - 7; x <= cx + 7; x++) {
                    double q = Math.pow((x - cx) / 7.0, 2) + Math.pow((y - cy) / 5.0, 2);
                    if (q <= 1) {
                        gray[y * W + x] = 0;
                        labels[y * W + x] = OmrMeasurePostProcessor.NOTEHEAD;
                    }
                }
            int shaftX = cx + 7 * stemSide;
            for (int y = 12; y <= cy; y++) {
                gray[y * W + shaftX] = 0;
                labels[y * W + shaftX] = 1;
            }
            labels[cy * W + shaftX] = OmrMeasurePostProcessor.NOTEHEAD;
        }
        List<PairedTremoloInk.Head> middle = new ArrayList<>();
        for (int i = 0; includeBars && i < 3; i++) {
            List<Integer> pixels = new ArrayList<>();
            double center = 27 + 14 * i;
            double ax = 70,
                    ay = center + 2.1,
                    bx = 112,
                    by = center - 2.1,
                    dx = bx - ax,
                    dy = by - ay,
                    den = dx * dx + dy * dy;
            for (int y = (int) center - 5; y <= (int) center + 5; y++)
                for (int x = 68; x <= 114; x++) {
                    double t = Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / den));
                    if (Math.pow(x - ax - t * dx, 2) + Math.pow(y - ay - t * dy, 2) <= 1.2 * 1.2) {
                        gray[y * W + x] = 0;
                        pixels.add(y * W + x);
                    }
                }
            middle.add(new PairedTremoloInk.Head(10 + i, 0, 0, 91, center, 14, 0, pixels, true));
        }
        var collector =
                new ExactComponentMembers.Collector(
                        gray,
                        labels,
                        W,
                        H,
                        new ExactComponentMembers.Budget(2L * gray.length + 4096, 4096),
                        new ExactComponentMembers.Budget(200_000, 8L * 1024 * 1024));
        assertTrue(collector.bindInput(labels, gray, W, H));
        for (int side = 0; side < 2; side++) {
            int cx = centers[side], cy = headY;
            var builder = collector.begin();
            int area = 0, minX = W, maxX = -1, minY = H, maxY = -1;
            long sx = 0, sy = 0;
            for (int y = cy - 5; y <= cy + 5; y++) {
                int left = -1, right = -1;
                for (int x = cx - 8; x <= cx + 8; x++)
                    if (labels[y * W + x] == OmrMeasurePostProcessor.NOTEHEAD) {
                        if (left < 0) left = x;
                        right = x;
                    }
                if (left >= 0) {
                    builder.add(y, left, right);
                    int n = right - left + 1;
                    area += n;
                    sx += ((long) left + right) * n / 2;
                    sy += (long) y * n;
                    minX = Math.min(minX, left);
                    maxX = Math.max(maxX, right);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
            statsRows[side] =
                    new ExactComponentMembers.Stats(
                            area, minX, maxX, minY, maxY, sx / (float) area, sy / (float) area);
            collector.record(
                    identities[side], statsRows[side], OmrMeasurePostProcessor.NOTEHEAD, builder);
        }
        assertTrue(collector.seal(new ExactComponentMembers.Budget(2L * gray.length + 4096, 4096)));
        List<PairedTremoloInk.Head> heads = new ArrayList<>();
        for (int side = 0; side < 2; side++) {
            int cx = centers[side];
            var token = collector.acceptedProbeHead(identities[side], 0, 0);
            assertNotNull(token);
            int[] pixels =
                    token.pixels(new ExactComponentMembers.Budget(100_000, 4L * 1024 * 1024));
            assertNotNull(pixels);
            List<Integer> footprint = new ArrayList<>(pixels.length);
            for (int p : pixels) footprint.add(p);
            heads.add(
                    new PairedTremoloInk.Head(
                            side,
                            0,
                            0,
                            cx,
                            headY,
                            14,
                            1,
                            footprint,
                            true,
                            20,
                            token,
                            new int[] {cx + 7 * stemSide, headY, 12}));
        }
        return new Fixture(gray, labels, List.copyOf(heads), List.copyOf(middle));
    }

    private static byte[][] mergedStackPage() {
        byte[] gray = new byte[W * H], labels = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int y : new int[] {34, 48, 62, 76, 90})
            for (int x = 1; x < W - 1; x++) {
                gray[y * W + x] = 0;
                labels[y * W + x] = OmrMeasurePostProcessor.STAFF;
            }
        for (int cx : new int[] {60, 108}) {
            for (int cy : new int[] {70, 84})
                for (int y = cy - 5; y <= cy + 5; y++)
                    for (int x = cx - 7; x <= cx + 7; x++) {
                        double q = Math.pow((x - cx) / 7.0, 2) + Math.pow((y - cy) / 5.0, 2);
                        if (q <= 1) {
                            gray[y * W + x] = 0;
                            labels[y * W + x] = OmrMeasurePostProcessor.NOTEHEAD;
                        }
                    }
            // The two lobes are one genuine NOTEHEAD parent; the normal stack
            // splitter must recover two exact children from this bridge.
            for (int y = 76; y <= 78; y++)
                for (int x = cx - 1; x <= cx + 1; x++) {
                    gray[y * W + x] = 0;
                    labels[y * W + x] = OmrMeasurePostProcessor.NOTEHEAD;
                }
            int shaftX = cx + 7;
            for (int y = 24; y <= 84; y++) {
                gray[y * W + shaftX] = 0;
                labels[y * W + shaftX] = 1;
            }
            labels[84 * W + shaftX] = OmrMeasurePostProcessor.NOTEHEAD;
        }
        for (int i = 0; i < 3; i++) {
            double center = 25 + 14 * i, ax = 70, ay = center + 2.1, bx = 112, by = center - 2.1;
            double dx = bx - ax, dy = by - ay, den = dx * dx + dy * dy;
            for (int y = (int) center - 5; y <= (int) center + 5; y++)
                for (int x = 68; x <= 114; x++) {
                    double t = Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / den));
                    if (Math.pow(x - ax - t * dx, 2) + Math.pow(y - ay - t * dy, 2) <= 1.2 * 1.2)
                        gray[y * W + x] = 0;
                }
        }
        return new byte[][] {gray, labels};
    }

    @Test
    public void callerTokenCanAuthorizeOnlyObservedCorePixels() {
        Fixture f = fixture(false, -1, true, 48);
        try {
            var method =
                    PairedTremoloInk.class.getDeclaredMethod(
                            "contactProof",
                            byte[].class,
                            int.class,
                            int.class,
                            double.class,
                            List.class,
                            List.class,
                            SparseStaffContact.Budget.class);
            method.setAccessible(true);
            Object proof =
                    method.invoke(
                            null,
                            f.gray(),
                            W,
                            H,
                            14.0,
                            List.of(f.heads().get(0)),
                            List.of(f.heads().get(1)),
                            new SparseStaffContact.Budget(6_000_000, 32L * 1024 * 1024));
            assertNotNull("complete actual raw five-rule contacts should be measurable", proof);
            var snapshotMethod = proof.getClass().getDeclaredMethod("snapshot");
            snapshotMethod.setAccessible(true);
            var evidenceMethod = proof.getClass().getDeclaredMethod("evidence");
            evidenceMethod.setAccessible(true);
            var budgetMethod = proof.getClass().getDeclaredMethod("queryBudget");
            budgetMethod.setAccessible(true);
            SparseStaffContact.Snapshot snapshot =
                    (SparseStaffContact.Snapshot) snapshotMethod.invoke(proof);
            RawMeasuredStaffTracks.Evidence evidence =
                    (RawMeasuredStaffTracks.Evidence) evidenceMethod.invoke(proof);
            SparseStaffContact.Budget budget =
                    (SparseStaffContact.Budget) budgetMethod.invoke(proof);
            assertTrue(evidence.ownsObservedNonownerPixel(snapshot, 100, 48, budget));
            assertFalse(evidence.ownsObservedNonownerPixel(snapshot, 100, 50, budget));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void integratedPairedDetectorEmitsMeasuredThreeBarPhysicalSource() {
        Fixture f = fixture(true, 1, false, 104);
        List<PairedTremoloInk.Head> all = new ArrayList<>(f.heads());
        all.addAll(f.middle());
        var pairs = PairedTremoloInk.pairs(f.gray(), W, H, all);
        assertEquals(1, pairs.size());
        assertEquals(3, pairs.get(0).strokeCount());
        assertTrue(pairs.get(0).unresolvedPhysicalSource());
    }

    @Test
    public void foreignSlurBranchCannotJoinAnOtherwiseCompletePair() {
        Fixture f = fixture(true, 1, false, 104);
        byte[] gray = f.gray().clone();
        List<PairedTremoloInk.Head> all = new ArrayList<>(f.heads());
        all.addAll(f.middle());
        List<Integer> arc = new ArrayList<>();
        for (int x = 86; x <= 96; x++) {
            int y = 60 + (int) Math.rint(.12 * Math.pow(x - 91, 2));
            gray[y * W + x] = 0;
            arc.add(y * W + x);
        }
        all.add(new PairedTremoloInk.Head(30, 0, 0, 91, 60, 14, 0, arc, true));
        assertTrue(PairedTremoloInk.pairs(gray, W, H, all).isEmpty());
    }

    @Test
    public void sourceFreeFullAnalyzeRejectsForeignSlurBranch() {
        Fixture f = fixture(true, 1, true, 72);
        byte[] gray = f.gray().clone();
        for (int x = 86; x <= 96; x++) {
            int y = 60 + (int) Math.rint(.12 * Math.pow(x - 91, 2));
            gray[y * W + x] = 0;
        }
        byte[] labels = f.labels().clone();
        for (int y = 31; y <= 37; y++)
            for (int x = 88; x <= 94; x++)
                if (Math.pow((x - 91) / 3.0, 2) + Math.pow((y - 34) / 3.0, 2) <= 1) {
                    gray[y * W + x] = 0;
                    labels[y * W + x] = OmrMeasurePostProcessor.NOTEHEAD;
                }
        var analysis =
                OmrScoreInterpreter.analyze(
                        labels, gray, W, H, List.of(new MeasureRegion(.03f, .97f, .08f, .92f)));
        assertEquals(3, analysis.notes().size());
        assertTrue(analysis.tremoloSource().pairs().isEmpty());
    }

    @Test
    public void sourceFreeSyntheticPageTraversesFullAnalyzeCaller() {
        Fixture f = fixture(true, 1, true, 72);
        var analysis =
                OmrScoreInterpreter.analyze(
                        f.labels(),
                        f.gray(),
                        W,
                        H,
                        List.of(new MeasureRegion(.03f, .97f, .08f, .92f)));
        assertEquals(2, analysis.notes().size());
        assertEquals(1, analysis.tremoloSource().pairs().size());
        ScoreTremoloSource.Pair source = analysis.tremoloSource().pairs().get(0);
        assertEquals(3, source.strokes());
        assertEquals(ScoreTremoloSource.ClockPolicy.UNRESOLVED, source.clockPolicy());
        assertEquals(1, source.first().size());
        assertEquals(1, source.second().size());
    }

    @Test
    public void sourceFreeMergedParentSplitsIntoChordEndpointsBeforePairing() {
        byte[][] page = mergedStackPage();
        var analysis =
                OmrScoreInterpreter.analyze(
                        page[1], page[0], W, H, List.of(new MeasureRegion(.03f, .97f, .08f, .92f)));
        assertEquals(4, analysis.notes().size());
        assertEquals(1, analysis.tremoloSource().pairs().size());
        var pair = analysis.tremoloSource().pairs().get(0);
        assertEquals(2, pair.first().size());
        assertEquals(2, pair.second().size());
        assertEquals(ScoreTremoloSource.ClockPolicy.UNRESOLVED, pair.clockPolicy());
    }

    @Test
    public void sourceFreeFullAnalyzeUsesMeasuredRawStaffContacts() {
        Fixture f = fixture(true, 1, true, 72);
        byte[] gray = f.gray().clone();
        for (int y : new int[] {20, 34, 48, 62, 76})
            for (int x = 1; x < W - 1; x++) gray[y * W + x] = 0;
        var analysis =
                OmrScoreInterpreter.analyze(
                        f.labels(), gray, W, H, List.of(new MeasureRegion(.03f, .97f, .08f, .92f)));
        assertEquals(2, analysis.notes().size());
        assertEquals(1, analysis.tremoloSource().pairs().size());
    }

    @Test
    public void sourceFreeRawStaffContactsStillRejectForeignHead() {
        Fixture f = fixture(true, 1, true, 72);
        byte[] gray = f.gray().clone();
        byte[] labels = f.labels().clone();
        for (int y : new int[] {20, 34, 48, 62, 76})
            for (int x = 1; x < W - 1; x++) gray[y * W + x] = 0;
        for (int x = 86; x <= 96; x++) {
            int y = 60 + (int) Math.rint(.12 * Math.pow(x - 91, 2));
            gray[y * W + x] = 0;
        }
        for (int y = 31; y <= 37; y++)
            for (int x = 88; x <= 94; x++)
                if (Math.pow((x - 91) / 3.0, 2) + Math.pow((y - 34) / 3.0, 2) <= 1) {
                    gray[y * W + x] = 0;
                    labels[y * W + x] = OmrMeasurePostProcessor.NOTEHEAD;
                }
        var analysis =
                OmrScoreInterpreter.analyze(
                        labels, gray, W, H, List.of(new MeasureRegion(.03f, .97f, .08f, .92f)));
        assertEquals(3, analysis.notes().size());
        assertTrue(analysis.tremoloSource().pairs().isEmpty());
    }

    @Test
    public void endpointOnlyBodyCandidateDoesNotCountAsIndependentRail() {
        Fixture f = fixture(false, 1, false, 72);
        var endpoint = f.heads().get(0);
        int[] members =
                endpoint.memberToken()
                        .pixels(new ExactComponentMembers.Budget(100_000, 4L * 1024 * 1024));
        assertNotNull(members);
        assertTrue(members.length >= 3);
        List<Integer> owned = java.util.Arrays.stream(members, 0, 3).boxed().sorted().toList();
        var body = new MaximalRawBarExtractor.Body(2, 54, 56, 72, owned, 1, 1, 1, 0);
        var input =
                new MaximalRawBarExtractor.Discovery(
                        MaximalRawBarExtractor.Status.CANDIDATES, List.of(body), 3);
        var filtered =
                PairedTremoloInk.removeEndpointOwnedBodyAliases(
                        input,
                        f.gray(),
                        W,
                        H,
                        List.of(endpoint),
                        List.of(f.heads().get(1)),
                        new long[] {100_000});
        assertNotNull(filtered);
        assertTrue(filtered.bodies().isEmpty());
    }

    @Test
    public void mixedEndpointAndForeignBodyPixelsRemainAmbiguous() {
        Fixture f = fixture(false, 1, false, 72);
        var endpoint = f.heads().get(0);
        int[] members =
                endpoint.memberToken()
                        .pixels(new ExactComponentMembers.Budget(100_000, 4L * 1024 * 1024));
        assertNotNull(members);
        assertTrue(members.length >= 3);
        int foreign = 71 * W + 67;
        assertFalse(java.util.Arrays.binarySearch(members, foreign) >= 0);
        List<Integer> mixed =
                new ArrayList<>(java.util.Arrays.stream(members, 0, 3).boxed().toList());
        mixed.add(foreign);
        mixed.sort(Integer::compareTo);
        var body = new MaximalRawBarExtractor.Body(2, 54, 57, 72, mixed, 1, 1, 1, 0);
        var input =
                new MaximalRawBarExtractor.Discovery(
                        MaximalRawBarExtractor.Status.CANDIDATES, List.of(body), 4);
        var filtered =
                PairedTremoloInk.removeEndpointOwnedBodyAliases(
                        input,
                        f.gray(),
                        W,
                        H,
                        List.of(endpoint),
                        List.of(f.heads().get(1)),
                        new long[] {100_000});
        assertNull(filtered);
    }
}
