// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

/** Source-free end-to-end proof for the real paired detector's AA/body binding. */
public final class PairedTremoloFiveRailRoleOriginDiagnostic {
    @org.junit.BeforeClass
    public static void enableBoundedTrace() {
        System.setProperty("sheet.interpreter.physicalDiagnostics", "true");
    }

    @org.junit.AfterClass
    public static void disableBoundedTrace() {
        System.clearProperty("sheet.interpreter.physicalDiagnostics");
    }

    private static final int W = 240, H = 150;

    private record Page(
            byte[] gray,
            byte[] labels,
            List<PairedTremoloInk.Head> heads,
            List<PairedTremoloInk.Head> middles) {}

    private static Page page(int adverse) {
        byte[] gray = new byte[W * H], labels = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int y : new int[] {20, 34, 48, 62, 76})
            for (int x = 1; x < W - 1; x++) {
                gray[y * W + x] = 0;
                labels[y * W + x] = OmrMeasurePostProcessor.STAFF;
            }
        int[] centers = {60, 130};
        for (int cx : centers) {
            for (int y = 85; y <= 95; y++)
                for (int x = cx - 7; x <= cx + 7; x++)
                    if (Math.pow((x - cx) / 7.0, 2) + Math.pow((y - 90) / 5.0, 2) <= 1) {
                        gray[y * W + x] = 0;
                        labels[y * W + x] = OmrMeasurePostProcessor.NOTEHEAD;
                    }
            int sx = cx + 8;
            for (int y = 24; y <= 90; y++) {
                gray[y * W + sx] = 0;
                labels[y * W + sx] = 1;
            }
        }
        int[] bars = {37, 51, 65};
        for (int bi = 0; bi < bars.length; bi++) {
            int cy = bars[bi];
            double ax = 82,
                    ay = cy + 2.1,
                    bx = 122,
                    by = cy - 1.9,
                    dx = bx - ax,
                    dy = by - ay,
                    den = dx * dx + dy * dy;
            Set<Integer> owned = new HashSet<>();
            for (int x = 81; x <= 122; x++) {
                int center = (int) Math.rint(ay + (x - ax) * dy / dx);
                for (int y = center - 1; y <= center + 1; y++) {
                    gray[y * W + x] = 0;
                    owned.add(y * W + x);
                }
            }
            for (int x : new int[] {81, 82, 101, 102, 121, 122}) {
                int lo = H, hi = -1;
                for (int pixel : owned)
                    if (pixel % W == x) {
                        int y = pixel / W;
                        lo = Math.min(lo, y);
                        hi = Math.max(hi, y);
                    }
                if (x > 76 && x <= 122 && hi >= lo) {
                    int center = (lo + hi) / 2;
                    int[] ys = {center - 2, center + 4, center + 2, center + 3};
                    int[] shades = {210, 240, 220, 245};
                    for (int k = 0; k < ys.length; k++) {
                        int at = ys[k] * W + x;
                        if ((gray[at] & 255) == 255) {
                            gray[at] = (byte) shades[k];
                            owned.add(at);
                        }
                    }
                }
            }
            if (adverse == 5 && bi == 0) {
                for (int x = 90; x <= 100; x++) {
                    List<Integer> column = new ArrayList<>();
                    for (int pixel : owned)
                        if (pixel % W == x && (gray[pixel] & 255) < 185) column.add(pixel);
                    for (int pixel : column) {
                        owned.remove(pixel);
                        gray[pixel] = (byte) 255;
                    }
                    for (int pixel : column) {
                        int shifted = pixel + 2 * W;
                        gray[shifted] = 0;
                        owned.add(shifted);
                    }
                }
            }
            for (int pixel : owned) {
                int shade = gray[pixel] & 255, x = pixel % W;
                if (labels[pixel] != OmrMeasurePostProcessor.STAFF
                        && (shade < 185
                                || ((x == 101 || x == 102) && shade >= 210 && shade <= 245)))
                    labels[pixel] = OmrMeasurePostProcessor.NOTEHEAD;
            }
            if (adverse == 1) { // exact observed paper value, not a contrasted fringe
                for (int x = 74; x <= 124; x++) {
                    int center = (int) Math.rint(ay + (x - ax) * dy / dx);
                    gray[(center - 4) * W + x] = (byte) 245;
                    gray[(center + 4) * W + x] = (byte) 245;
                }
            } else if (adverse == 2) {
                int center = (int) Math.rint(ay + (89 - ax) * dy / dx);
                gray[(center + 4) * W + 89] = (byte) 195;
            } // separate pale lobe outside the exact raw body token
            else if (adverse == 3) {
                int center = (int) Math.rint(ay + (89 - ax) * dy / dx);
                gray[(center - 3) * W + 89] = 0;
            } // connected foreign dark branch
            else if (adverse == 6) {
                for (int x = 81; x <= 122; x++) {
                    int lo = H, hi = -1;
                    for (int pixel : owned)
                        if (pixel % W == x && (gray[pixel] & 255) < 185) {
                            int y = pixel / W;
                            lo = Math.min(lo, y);
                            hi = Math.max(hi, y);
                        }
                    if (hi >= lo) {
                        int up = lo - 1, down = hi + 1;
                        if (labels[up * W + x] != OmrMeasurePostProcessor.STAFF) {
                            gray[up * W + x] = (byte) 150;
                            labels[up * W + x] = OmrMeasurePostProcessor.NOTEHEAD;
                        }
                        if (labels[down * W + x] != OmrMeasurePostProcessor.STAFF) {
                            gray[down * W + x] = (byte) 150;
                            labels[down * W + x] = OmrMeasurePostProcessor.NOTEHEAD;
                        }
                    }
                }
            } // exterior profile is ink/unknown on both sides, so no independent paper witness
        }
        if (adverse == 4) { // one semantic component aliases two bars through unobserved paper
            int x = 89;
            for (int y = 31; y <= 37; y++) labels[y * W + x] = OmrMeasurePostProcessor.NOTEHEAD;
        }
        // Flood the actual NOTEHEAD-label components, then mint tokens only from
        // their exact row runs. These are the same object-bound inputs used by caller.
        var components = new ArrayList<List<Integer>>();
        boolean[] seen = new boolean[labels.length];
        for (int at = 0; at < labels.length; at++)
            if (!seen[at] && labels[at] == OmrMeasurePostProcessor.NOTEHEAD) {
                var found = new ArrayList<Integer>();
                var q = new ArrayDeque<Integer>();
                q.add(at);
                seen[at] = true;
                while (!q.isEmpty()) {
                    int p = q.removeFirst();
                    found.add(p);
                    int x = p % W, y = p / W;
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++)
                            if (dx != 0 || dy != 0) {
                                int xx = x + dx, yy = y + dy;
                                if (xx >= 0 && xx < W && yy >= 0 && yy < H) {
                                    int n = yy * W + xx;
                                    if (!seen[n] && labels[n] == OmrMeasurePostProcessor.NOTEHEAD) {
                                        seen[n] = true;
                                        q.addLast(n);
                                    }
                                }
                            }
                }
                components.add(found);
            }
        var collector =
                new ExactComponentMembers.Collector(
                        gray,
                        labels,
                        W,
                        H,
                        new ExactComponentMembers.Budget(2L * gray.length + 4096, 4096),
                        new ExactComponentMembers.Budget(500_000, 8L * 1024 * 1024));
        assertTrue(collector.bindInput(labels, gray, W, H));
        var ids = new ArrayList<Object>();
        var stats = new ArrayList<ExactComponentMembers.Stats>();
        for (var pixels : components) {
            Object id = new Object();
            ids.add(id);
            var b = collector.begin();
            int n = 0, minX = W, maxX = -1, minY = H, maxY = -1;
            long sx = 0, sy = 0;
            for (int y = 0; y < H; y++) {
                int x = 0;
                while (x < W) {
                    while (x < W && !pixels.contains(y * W + x)) x++;
                    if (x >= W) break;
                    int left = x;
                    while (x + 1 < W && pixels.contains(y * W + x + 1)) x++;
                    int right = x;
                    b.add(y, left, right);
                    for (int px = left; px <= right; px++) {
                        n++;
                        minX = Math.min(minX, px);
                        maxX = Math.max(maxX, px);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                        sx += px;
                        sy += y;
                    }
                    x++;
                }
            }
            var st =
                    new ExactComponentMembers.Stats(
                            n, minX, maxX, minY, maxY, (float) sx / n, (float) sy / n);
            stats.add(st);
            collector.record(id, st, OmrMeasurePostProcessor.NOTEHEAD, b);
        }
        assertTrue(collector.seal(new ExactComponentMembers.Budget(2L * gray.length + 4096, 4096)));
        var endpoints = new ArrayList<PairedTremoloInk.Head>();
        var middles = new ArrayList<PairedTremoloInk.Head>();
        for (int i = 0; i < components.size(); i++) {
            var token = collector.acceptedProbeHead(ids.get(i), 0, 0);
            assertNotNull(token);
            int[] owned = token.pixels(new ExactComponentMembers.Budget(100_000, 4L * 1024 * 1024));
            assertNotNull(owned);
            List<Integer> footprint = Arrays.stream(owned).boxed().toList();
            var st = stats.get(i);
            if (st.centerY() > 80) {
                int cx = st.centerX() < 100 ? 60 : 130;
                endpoints.add(
                        new PairedTremoloInk.Head(
                                cx == 60 ? 1 : 2,
                                0,
                                0,
                                cx,
                                90,
                                14,
                                1,
                                footprint,
                                true,
                                20,
                                token,
                                cx + 8,
                                90,
                                24));
            } else {
                int cy = (int) Math.rint(st.centerY());
                int id = 10 + middles.size();
                middles.add(
                        new PairedTremoloInk.Head(
                                id, 0, 0, 91, cy, 14, 0, footprint, true, 20, token, -1, -1, -1));
            }
        }
        endpoints.sort(Comparator.comparingDouble(PairedTremoloInk.Head::x));
        middles.sort(Comparator.comparingDouble(PairedTremoloInk.Head::y));
        var all = new ArrayList<PairedTremoloInk.Head>(endpoints);
        all.addAll(middles);
        return new Page(gray, labels, List.copyOf(all), List.copyOf(middles));
    }

    private static List<PairedTremoloInk.Pair> detect(Page p) {
        return detect(p.gray(), p.heads());
    }

    private static List<PairedTremoloInk.Pair> detect(
            byte[] gray, List<PairedTremoloInk.Head> heads) {
        return PairedTremoloInk.pairs(gray, W, H, heads);
    }

    @Test
    public void productionPairDiscoveryBindsEveryExactSemanticMemberToItsOwnBody() {
        Page p = page(0);
        var pairs = detect(p);
        assertEquals(
                "five-rail typed producer must yield one authenticated pair; heads="
                        + p.heads().size(),
                1,
                pairs.size());
        var pair = pairs.get(0);
        assertEquals(3, pair.strokeCount());
        assertTrue(pair.unresolvedPhysicalSource());
        assertEquals(1, pair.marks().size());
        var mark = pair.marks().get(0);
        for (var head : p.middles()) {
            var assignment = mark.assignFringe(head);
            assertNotNull("whole exact component assignment", assignment);
            assertTrue(assignment.coreMembers > 0);
            assertTrue(assignment.fringeMembers > 0);
            Set<Integer> grayValues = new HashSet<>();
            for (int pixel : head.footprint()) grayValues.add(p.gray()[pixel] & 255);
            assertTrue("210 AA member", grayValues.contains(210));
            assertTrue("240 AA member", grayValues.contains(240));
            assertTrue("245 AA member", grayValues.contains(245));
            assertTrue(
                    PairedTremoloInk.ownsExactBodyFootprint(
                            p.gray(), W, H, mark, head.footprint(), 14, null, head));
        }
    }

    @Test
    public void authenticatedFiveRailProfileKeepsThreeAAOwnedBodiesAndMeasuredEndpointShafts()
            throws Exception {
        Page p = page(0);
        String sha =
                java.util.HexFormat.of()
                        .formatHex(
                                java.security.MessageDigest.getInstance("SHA-256")
                                        .digest(p.gray()));
        var lines =
                RawStaffLineDetector.detect(p.gray(), W, H).stream()
                        .filter(q -> q.rows().length == 5 && Math.abs(q.gap() - 14) < 1)
                        .min(Comparator.comparingInt(q -> Math.abs(q.top() - 20)))
                        .orElseThrow();
        assertEquals(5, lines.rows().length);
        Set<Integer> staff = new HashSet<>();
        for (int row : lines.rows()) for (int x = 1; x < W - 1; x++) staff.add(row * W + x);
        var lane = new MaximalRawBarExtractor.Lane(68, 138, 10, 104, 14);
        var discovery =
                MaximalRawBarExtractor.discoverJoined(
                        p.gray(), W, H, lane, 8_000_000, 64_000_000, staff);
        assertEquals(
                discovery.toString(), MaximalRawBarExtractor.Status.CANDIDATES, discovery.status());
        assertEquals(3, discovery.bodies().size());
        assertTrue(discovery.authenticFor(sha, W, H, lane, staff, true));
        var snap =
                SparseStaffContact.Snapshot.capture(
                        p.gray(),
                        W,
                        H,
                        sha,
                        new SparseStaffContact.Rect(1, 1, W - 2, H - 2),
                        new SparseStaffContact.Budget(2_000_000, 4096),
                        new SparseStaffContact.Budget(8_000_000, 64_000_000));
        var profile =
                ObservedBarRasterFringe.produce(
                        snap,
                        lane,
                        staff,
                        discovery,
                        true,
                        new SparseStaffContact.Budget(8_000_000, 64_000_000));
        assertTrue(
                "complete same-source body/profile ownership: " + profile.refusal(),
                profile.complete());
        assertTrue(ObservedBarRasterFringe.witnessedOpenBoundaries(true, true, true));
        assertFalse(ObservedBarRasterFringe.witnessedOpenBoundaries(false, true, true));
        assertFalse(ObservedBarRasterFringe.witnessedOpenBoundaries(true, false, true));
        assertFalse(ObservedBarRasterFringe.witnessedOpenBoundaries(false, false, true));
        assertEquals(3, profile.evidence().bodyCount());
        for (int i = 0; i < 3; i++)
            assertTrue(
                    "all members/caps/AA roles accounted",
                    profile.evidence().bodyComplete(profile.evidence().body(i)));
        for (var endpoint : p.heads().subList(0, 2)) {
            var measured =
                    PairedTremoloInk.stem(
                            p.gray(), W, H, endpoint.x(), endpoint.y(), endpoint.gap(), 1);
            assertNotNull("independently measured physical shaft", measured);
            assertEquals(endpoint.shaftX(), measured.x());
            assertEquals(endpoint.shaftHeadY(), measured.headY());
            assertEquals(endpoint.shaftEndY(), measured.endY());
        }
        for (var head : p.middles()) {
            var token = head.memberToken();
            assertNotNull(token);
            Set<Integer> shades = new HashSet<>();
            for (int pixel : head.footprint()) shades.add(p.gray()[pixel] & 255);
            assertTrue("body token owns AA 210", shades.contains(210));
            assertTrue("body token owns AA 240", shades.contains(240));
            assertTrue("body token owns AA 245", shades.contains(245));
        }
    }

    @Test
    public void productionCallerRejectsStaleComponentToken() {
        Page current = page(0), stale = page(1);
        var heads = new ArrayList<PairedTremoloInk.Head>(current.heads());
        var staleHead = stale.middles().get(0);
        int replace = -1;
        for (int i = 0; i < heads.size(); i++) if (heads.get(i).id() == staleHead.id()) replace = i;
        assertTrue(replace >= 0);
        heads.set(replace, staleHead);
        assertTrue(
                "stale token cannot participate in production pairing",
                detect(current.gray(), heads).isEmpty());
    }

    @Test
    public void productionCallerRefusesExactPaperMembers() {
        Page p = page(1);
        assertTrue(detect(p).isEmpty());
    }

    @Test
    public void productionCallerRefusesForeignPaleLobe() {
        Page p = page(2);
        assertTrue(detect(p).isEmpty());
    }

    @Test
    public void productionCallerRefusesForeignDarkBranch() {
        Page p = page(3);
        assertTrue(detect(p).isEmpty());
    }

    @Test
    public void productionCallerRefusesOneColumnPhaseShift() {
        Page p = page(5);
        assertTrue(detect(p).isEmpty());
    }

    @Test
    public void productionCallerRejectsOneSemanticComponentAliasingTwoBodies() {
        Page p = page(4);
        assertTrue(detect(p).isEmpty());
    }

    @Test
    public void traceExactSameCallMemberRoleAndStaffRows() throws Exception {
        Page p = page(0);
        Class<?> diag = Class.forName("io.github.luckolite.interpreter.FringeProfileDiagnostics");
        var linesField = diag.getDeclaredField("LINES");
        linesField.setAccessible(true);
        ((List<?>) linesField.get(null)).clear();
        var bytesField = diag.getDeclaredField("bytes");
        bytesField.setAccessible(true);
        bytesField.setInt(null, 0);
        var found = detect(p);
        assertEquals(1, found.size());
        var mark = found.get(0).marks().get(0);
        var fringeField = mark.getClass().getDeclaredField("fringe");
        fringeField.setAccessible(true);
        Object binding = fringeField.get(mark);
        var evidence =
                (ObservedBarRasterFringe.Evidence)
                        binding.getClass().getDeclaredMethod("evidence").invoke(binding);
        var snapshot =
                (SparseStaffContact.Snapshot)
                        binding.getClass().getDeclaredMethod("snapshot").invoke(binding);
        var bodies =
                (List<ObservedBarRasterFringe.BodyIdentity>)
                        binding.getClass().getDeclaredMethod("bodies").invoke(binding);
        System.out.println(
                "TRACE_CLOSURE graySha="
                        + snapshot.rawSha
                        + " dimensions="
                        + snapshot.width
                        + "x"
                        + snapshot.height
                        + " lane="
                        + bodies.size()
                        + " bodies");
        for (Object item : (List<?>) linesField.get(null)) {
            String line = item.toString();
            if (line.startsWith("BODY") || line.contains("P{x=101,"))
                System.out.println("PROFILE_SAME_CALL " + line);
        }
        for (var head : p.middles()) {
            var assignment = mark.assignFringe(head);
            System.out.println(
                    "HEAD id="
                            + head.id()
                            + " members="
                            + head.footprint().size()
                            + " assignment="
                            + (assignment != null));
            assertNotNull(assignment);
            var body = assignment.bodyIdentity();
            int ordinal = bodies.indexOf(body);
            int printed = 0;
            int roleY = 35 + 14 * ordinal, staffY = 34 + 14 * ordinal;
            int aa = roleY * W + 101, core = (roleY + 1) * W + 101, staff = staffY * W + 101;
            assertEquals(OmrMeasurePostProcessor.STAFF, p.labels()[staff]);
            assertEquals(OmrMeasurePostProcessor.NOTEHEAD, p.labels()[aa]);
            assertEquals(
                    ObservedBarRasterFringe.Role.OBSERVED_CONTRASTED_FRINGE,
                    evidence.role(
                            snapshot, body, 101, roleY, new SparseStaffContact.Budget(100, 10000)));
            assertEquals(
                    ObservedBarRasterFringe.Role.OBSERVED_BAR_CORE,
                    evidence.role(
                            snapshot,
                            body,
                            101,
                            roleY + 1,
                            new SparseStaffContact.Budget(100, 10000)));
            assertEquals(
                    ObservedBarRasterFringe.Role.UNKNOWN,
                    evidence.role(
                            snapshot,
                            bodies.get((ordinal + 1) % bodies.size()),
                            101,
                            roleY,
                            new SparseStaffContact.Budget(100, 10000)));
            boolean exactProfile = false;
            for (Object item : (List<?>) linesField.get(null)) {
                String line = item.toString();
                if (line.startsWith("BODY")
                        && line.contains("id=" + ordinal + "\t")
                        && line.contains("x=101,")
                        && line.contains((staffY) + ":0:STAFF," + (roleY) + ":210:PAPER")) {
                    exactProfile = true;
                    break;
                }
            }
            assertTrue("same producer profile agrees at staff/AA cell", exactProfile);
            for (int pixel : head.footprint()) {
                if (pixel % W != 101 && pixel % W != 102 && pixel % W != 108) continue;
                printed++;
                var role =
                        evidence.role(
                                snapshot,
                                body,
                                pixel % W,
                                pixel / W,
                                new SparseStaffContact.Budget(10000, 1000000));
                System.out.println(
                        "MEMBER page="
                                + pixel
                                + " x="
                                + (pixel % W)
                                + " y="
                                + (pixel / W)
                                + " gray="
                                + (p.gray()[pixel] & 255)
                                + " label="
                                + (p.labels()[pixel] & 255)
                                + " staffLabel="
                                + (p.labels()[pixel] == OmrMeasurePostProcessor.STAFF)
                                + " role="
                                + role
                                + " bodyOrdinal="
                                + ordinal);
            }
        }
    }

    @Test
    public void productionProducerRefusesClosedBoundaryWithoutIndependentExteriorPaper() {
        Page p = page(6);
        // The authored medium-ink bands occupy the first exterior profile sample
        // on both sides; they are not semantic staff or bar members.
        for (int cy : new int[] {37, 51, 65})
            for (int x = 81; x <= 122; x++) {
                double ax = 82, ay = cy + 2.1, bx = 122, by = cy - 1.9, dx = bx - ax, dy = by - ay;
                int center = (int) Math.rint(ay + (x - ax) * dy / dx);
                for (int y : new int[] {center - 2, center + 2}) {
                    int at = y * W + x;
                    if (p.labels()[at] == OmrMeasurePostProcessor.STAFF)
                        assertEquals(0, p.gray()[at] & 255);
                    else
                        assertEquals(
                                "closed sample cy="
                                        + cy
                                        + " x="
                                        + x
                                        + " y="
                                        + y
                                        + " label="
                                        + (p.labels()[at] & 255),
                                150,
                                p.gray()[at] & 255);
                }
            }
        assertTrue("closed/no-paper scene cannot yield a paired removal", detect(p).isEmpty());
        var lines =
                RawStaffLineDetector.detect(p.gray(), W, H).stream()
                        .filter(q -> q.rows().length == 5 && Math.abs(q.gap() - 14) < 1)
                        .min(Comparator.comparingInt(q -> Math.abs(q.top() - 20)))
                        .orElseThrow();
        Set<Integer> staff = new HashSet<>();
        for (int row : lines.rows()) for (int x = 1; x < W - 1; x++) staff.add(row * W + x);
        var lane = new MaximalRawBarExtractor.Lane(68, 138, 10, 104, 14);
        var discovery =
                MaximalRawBarExtractor.discoverJoined(
                        p.gray(), W, H, lane, 8_000_000, 64_000_000, staff);
        assertEquals(MaximalRawBarExtractor.Status.CANDIDATES, discovery.status());
        assertEquals(3, discovery.bodies().size());
        for (var body : discovery.bodies()) {
            boolean shares = false;
            for (int at : body.rawPixels())
                if (staff.contains(at)) {
                    shares = true;
                    break;
                }
            assertTrue("each exact body has typed staff coownership", shares);
        }
        var snap =
                SparseStaffContact.Snapshot.captureAuthenticated(
                        p.gray(),
                        W,
                        H,
                        p.heads().get(0).memberToken(),
                        new SparseStaffContact.Rect(1, 1, W - 2, H - 2),
                        new SparseStaffContact.Budget(8_000_000, 64_000_000));
        var result =
                ObservedBarRasterFringe.produce(
                        snap,
                        lane,
                        staff,
                        discovery,
                        true,
                        new SparseStaffContact.Budget(8_000_000, 64_000_000));
        assertTrue("producer returns bounded evidence object", result.complete());
        for (int i = 0; i < 3; i++)
            assertFalse(
                    "no independent exterior paper: body stays incomplete",
                    result.evidence().bodyComplete(result.evidence().body(i)));
    }
}

