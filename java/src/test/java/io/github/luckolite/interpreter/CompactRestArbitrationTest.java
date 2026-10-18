// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;

/** Authored computed-conflict boundary, independent of rest classifier geometry. */
public final class CompactRestArbitrationTest {
    static void check(boolean yes) {
        if (!yes) throw new AssertionError();
    }

    static Object[] proof(int mode) throws Exception {
        var p = scene(mode);
        Class<?> o = OmrScoreInterpreter.class;
        Class<?> c = Class.forName(o.getName() + "$Component"),
                d = Class.forName(o.getName() + "$DetectedNote");
        Constructor<?> cc = c.getDeclaredConstructors()[0], dc = d.getDeclaredConstructors()[0];
        cc.setAccessible(true);
        dc.setAccessible(true);
        List<Object> det = new ArrayList<>(), heads = new ArrayList<>();
        List<int[]> boxes = new ArrayList<>(p.boxes);
        boxes.sort(Comparator.comparingInt(v -> v[5]));
        for (int[] b : boxes) {
            Object h = cc.newInstance(b[0], b[1], b[2], b[3], b[4], (float) b[5], (float) b[6]);
            heads.add(h);
            ScoreNoteEvent e =
                    new ScoreNoteEvent(
                            mode == 9 && b[5] == 160 ? 1 : 0,
                            b[5] / 700f,
                            2,
                            0,
                            1,
                            b[6] / 320f,
                            false,
                            0,
                            b[5] == 160 ? 0 : 1);
            det.add(dc.newInstance(e, h, 16f));
        }
        Method m =
                o.getDeclaredMethod(
                        "compactPrefixRestOwnership",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        List.class);
        m.setAccessible(true);
        byte[] g = p.g.clone(), l = p.l.clone();
        var policy = (CompactRestArbitration) m.invoke(null, p.l, p.g, 700, 320, det);
        check(Arrays.equals(g, p.g) && Arrays.equals(l, p.l));
        return new Object[] {policy, heads};
    }

    static final int W = 700, H = 320;
    static final List<MeasureRegion> M = List.of(new MeasureRegion(0, 1, .05f, .95f));

    static class Page {
        byte[] g = new byte[W * H], l = new byte[W * H];
        List<int[]> boxes = new ArrayList<>();

        Page() {
            Arrays.fill(g, (byte) 255);
            for (int y : new int[] {112, 128, 144, 160, 176})
                for (int x = 15; x < 685; x++) {
                    pixel(x, y, 4);
                    pixel(x, y + 1, 4);
                }
        }

        void pixel(int x, int y, int label) {
            l[y * W + x] = (byte) label;
            g[y * W + x] = 0;
        }

        void oval(int cx, int cy, int rx, int ry) {
            int area = 0;
            for (int y = cy - ry; y <= cy + ry; y++)
                for (int x = cx - rx; x <= cx + rx; x++)
                    if ((x - cx) * (x - cx) / (double) (rx * rx)
                                    + (y - cy) * (y - cy) / (double) (ry * ry)
                            <= 1) {
                        pixel(x, y, 2);
                        area++;
                    }
            boxes.add(new int[] {area, cx - rx, cx + rx, cy - ry, cy + ry, cx, cy});
        }

        void stem(int x, int bottom, int top) {
            for (int y = top; y <= bottom; y++) pixel(x, y, 1);
        }

        void beam(int left, int right, int top) {
            for (int x = left; x <= right; x++) for (int y = top; y <= top + 3; y++) pixel(x, y, 1);
        }
    }

    static Page scene(int mode) {
        Page p = new Page();
        p.oval(100, 152, 9, 8);
        p.oval(129, 152, 9, 8);
        p.oval(160, 160, 12, 9);
        if (mode != 5) p.stem(109, 152, 114);
        p.stem(138, 152, 114);
        p.stem(172, 160, 105);
        if (mode == 6) {
            p.beam(109, 117, 114);
            p.beam(134, 138, 114);
        } else p.beam(109, 138, 114);
        if (mode == 7) {
            p.oval(65, 152, 12, 9);
            p.stem(77, 152, 114);
            p.beam(77, 109, 114);
        }
        if (mode == 8) {
            Arrays.fill(p.l, (byte) 0);
            Arrays.fill(p.g, (byte) 255);
            p = new Page();
            p.oval(100, 152, 9, 8);
            p.oval(129, 152, 9, 8);
            p.oval(160, 152, 9, 8);
            for (int x : new int[] {109, 138, 169}) p.stem(x, 152, 114);
            p.beam(109, 169, 114);
        }
        if (mode == 1) {
            byte[] a = p.g.clone(), b = p.l.clone();
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) {
                    p.g[(H - 1 - y) * W + x] = a[y * W + x];
                    p.l[(H - 1 - y) * W + x] = b[y * W + x];
                }
            for (int[] v : p.boxes) {
                int t = v[3];
                v[3] = H - 1 - v[4];
                v[4] = H - 1 - t;
                v[6] = H - 1 - v[6];
            }
        }
        return p;
    }

    private final Object a = new Object();

    @org.junit.Test
    public void ownershipListIsDefensivelySnapshotted() {
        List<Object> input = new ArrayList<>(List.of(a));
        var x = CompactRestArbitration.proved(input);
        input.clear();
        check(!x.mayRemove(a) && x.mayRemove(new Object()));
    }

    @org.junit.Test
    public void structuralEqualityDoesNotConferOwnership() {
        record EqualHead(int n) {}
        var x = new EqualHead(1);
        var y = new EqualHead(1);
        check(CompactRestArbitration.proved(List.of(x)).mayRemove(y));
    }

    @SuppressWarnings("unchecked")
    @org.junit.Test
    public void rawOwnedPairSurvivesComputedRestConflict() throws Exception {
        var raw = proof(0);
        var policy = (CompactRestArbitration) raw[0];
        var heads = (List<Object>) raw[1];
        Object first = heads.get(0), second = heads.get(1), principal = heads.get(2);
        check(!policy.mayRemove(first) && !policy.mayRemove(second) && policy.mayRemove(principal));
        check(CompactRestArbitration.empty().mayRemove(first));
        check(!policy.mayRemove(first));
    }

    @SuppressWarnings("unchecked")
    private void abstains(int mode) throws Exception {
        var x = proof(mode);
        for (Object h : (List<Object>) x[1]) check(((CompactRestArbitration) x[0]).mayRemove(h));
    }

    @org.junit.Test
    public void missingOwnShaftCannotProtect() throws Exception {
        abstains(5);
    }

    @org.junit.Test
    public void materialBeamGapCannotProtect() throws Exception {
        abstains(6);
    }

    @org.junit.Test
    public void noLargerPrincipalCannotProtect() throws Exception {
        abstains(8);
    }

    @org.junit.Test
    public void differentMeasurePrincipalCannotProtect() throws Exception {
        abstains(9);
    }

    private static void line(byte[] gray, int w, int x0, int y0, int x1, int y1, int thick) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i++) {
            float f = steps == 0 ? 0 : i / (float) steps;
            int x = Math.round(x0 + f * (x1 - x0)), y = Math.round(y0 + f * (y1 - y0));
            for (int dx = 0; dx < thick; dx++)
                for (int dy = 0; dy < thick; dy++) gray[(y + dy) * w + x + dx] = 0;
        }
    }

    private static void oval(byte[] gray, int w, int cx, int cy, int rx, int ry) {
        for (int y = cy - ry; y <= cy + ry; y++)
            for (int x = cx - rx; x <= cx + rx; x++)
                if (Math.pow((x - cx) / (double) rx, 2) + Math.pow((y - cy) / (double) ry, 2) <= 1)
                    gray[y * w + x] = 0;
    }

    @org.junit.Test
    public void genuineEighthRestUnderPrefixRemainsInFullCaller() {
        int w = 800, h = 360;
        byte[] gray = new byte[w * h], labels = new byte[w * h];
        Arrays.fill(gray, (byte) 255);
        for (int y = 80; y <= 144; y += 16) {
            line(gray, w, 20, y, 780, y, 1);
            for (int x = 20; x <= 780; x++) labels[y * w + x] = 4;
        }
        oval(gray, w, 180, 165, 7, 5);
        line(gray, w, 190, 160, 178, 190, 2);
        oval(gray, w, 255, 128, 11, 7);
        oval(gray, w, 335, 112, 11, 7);
        line(gray, w, 245, 128, 245, 178, 2);
        line(gray, w, 325, 112, 325, 174, 2);
        line(gray, w, 245, 178, 325, 174, 5);
        for (int j = 0; j < 3; j++) {
            int cx = 166 + j * 29,
                    cy = 112 + (j == 2 ? 8 : 0),
                    rx = j == 2 ? 12 : 9,
                    ry = j == 2 ? 9 : 8;
            for (int dy = -ry; dy <= ry; dy++)
                for (int dx = -rx; dx <= rx; dx++)
                    if (dx * dx / (double) (rx * rx) + dy * dy / (double) (ry * ry) <= 1) {
                        int at = (cy + dy) * w + cx + dx;
                        gray[at] = 0;
                        labels[at] = 2;
                    }
            for (int y = cy - (j == 2 ? 55 : 38); y <= cy; y++) {
                int at = y * w + cx + rx;
                gray[at] = 0;
                labels[at] = 1;
            }
        }
        for (int x = 175; x <= 204; x++)
            for (int y = 74; y <= 77; y++) {
                gray[y * w + x] = 0;
                labels[y * w + x] = 1;
            }
        for (int[] c : new int[][] {{255, 128}, {335, 112}})
            for (int dy = -7; dy <= 7; dy++)
                for (int dx = -11; dx <= 11; dx++)
                    if (dx * dx / 121. + dy * dy / 49. <= 1)
                        labels[(c[1] + dy) * w + c[0] + dx] = 2;
        byte[] g = gray.clone(), l = labels.clone();
        var page =
                OmrScoreInterpreter.analyze(
                        labels, gray, w, h, List.of(new MeasureRegion(0, 1, .05f, .95f)));
        check(
                page.rests().stream()
                        .anyMatch(
                                r ->
                                        Math.abs(r.positionInMeasure() - 180f / w) < .04f
                                                && r.pageY() > 150f / h
                                                && r.durationBeats() == .5));
        check(Arrays.equals(g, gray) && Arrays.equals(l, labels));
    }
}
