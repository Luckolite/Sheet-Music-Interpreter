// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original geometric controls; no scanned score pixels or song coordinates. */
public final class OverlappingDynamicBodiesTest {
    static final int W = 240, H = 220;
    static final List<PlayingTechniqueDetector.Staff> STAFFS =
            List.of(new PlayingTechniqueDetector.Staff(20, 76, 14, 0, 1));

    static byte[] paper() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        return g;
    }

    static void rect(byte[] g, int x, int y, int w, int h) {
        for (int yy = y; yy < y + h; yy++) for (int xx = x; xx < x + w; xx++) g[yy * W + xx] = 0;
    }

    static void part(byte[] g, int part, int x, int y) {
        if (part == 0) {
            rect(g, x, y + 9, 6, 12);
            rect(g, x, y + 9, 16, 5);
        }
        if (part == 1) rect(g, x + 13, y + 21, 4, 7);
        if (part == 2) rect(g, x + 19, y, 12, 25);
    }

    static boolean whole(List<PlayingTechniqueDetector.Word> b, int x, int y) {
        return b.stream()
                .anyMatch(
                        a ->
                                Math.round(a.left() * W) == x
                                        && Math.round(a.top() * H) == y
                                        && Math.round(a.right() * W) == x + 31
                                        && Math.round(a.bottom() * H) == y + 28);
    }

    static List<PlayingTechniqueDetector.Word> boxes(byte[] g) {
        return ScoreDynamicsDetector.symbolBoxes(g, STAFFS, W, H);
    }

    static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }

    static int cases = 0;

    static void geometricControls() {
        int[][] orders = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
        for (int x : new int[] {25, 81})
            for (int y : new int[] {91, 118})
                for (int[] order : orders) {
                    byte[] g = paper();
                    for (int p : order) part(g, p, x, y);
                    var b = boxes(g);
                    check(whole(b, x, y), "late-expanded overlapping body union");
                    check(b.size() == 3, "preserve two original proposals plus supplemental union");
                    cases++;
                    try {
                        check(
                                GlyphResources.dynamics()
                                        .recognize(g, W, H, STAFFS, List.of())
                                        .stream()
                                        .noneMatch(
                                                a ->
                                                        Float.isFinite(
                                                                ScoreDynamicsDetector.level(
                                                                        a.text()))),
                                "unrelated geometric strokes cannot invent a dynamic");
                    } catch (Exception e) {
                        throw new AssertionError(e);
                    }
                    cases++;
                    byte[] mirror = paper();
                    for (int yy = 0; yy < H; yy++)
                        for (int xx = 0; xx < W; xx++) mirror[yy * W + W - 1 - xx] = g[yy * W + xx];
                    check(whole(boxes(mirror), W - x - 31, y), "horizontal mirror grouping order");
                    cases++;
                }
        byte[] separate = paper();
        rect(separate, 30, 95, 12, 12);
        rect(separate, 50, 95, 12, 12);
        check(boxes(separate).size() == 2, "near unrelated side-by-side ink remains separate");
        cases++;
        byte[] stacked = paper();
        rect(stacked, 30, 95, 12, 12);
        rect(stacked, 30, 118, 12, 12);
        check(boxes(stacked).size() == 2, "vertically separated ink remains separate");
        cases++;
        byte[] staff = paper();
        for (int i = 0; i < 5; i++) rect(staff, 10, 20 + i * 10, 180, 1);
        rect(staff, 70, 32, 10, 5);
        rect(staff, 79, 18, 2, 20);
        check(boxes(staff).isEmpty(), "staff rails and attached note ink are not word bodies");
        cases++;
        byte[] oversized = paper();
        rect(oversized, 20, 94, 12, 45);
        rect(oversized, 36, 94, 12, 45);
        check(boxes(oversized).isEmpty(), "staff-local height bound remains enforced");
        cases++;
    }

    static void glyphControl(String token, boolean unrelated) throws Exception {
        // Existing licensed resource glyphs generated from the bundled font; no bitmap fixture
        // copied.
        var image = GlyphResources.image("dynamic_templates/" + token + ".png");
        int iw = image.getWidth(), ih = image.getHeight();
        int dw = (iw + 1) / 2, dh = (ih + 1) / 2;
        byte[] g = paper();
        int x = 45, y = 92;
        for (int yy = 0; yy < dh; yy++)
            for (int xx = 0; xx < dw; xx++)
                g[(y + yy) * W + x + xx] =
                        (byte)
                                ((image.getRGB(Math.min(iw - 1, xx * 2), Math.min(ih - 1, yy * 2))
                                                >>> 16)
                                        & 255);
        if (unrelated) {
            rect(g, x + dw + 20, y + 2, 8, 12);
            for (int i = 0; i < 5; i++) rect(g, 10, 20 + i * 10, 180, 1);
        }
        var found = GlyphResources.dynamics().recognize(g, W, H, STAFFS, List.of());
        check(
                found.stream().anyMatch(b -> b.text().equals(token)),
                "genuine isolated " + token + " preserved with unrelated=" + unrelated);
        check(
                found.stream()
                                .filter(b -> Float.isFinite(ScoreDynamicsDetector.level(b.text())))
                                .count()
                        == 1,
                "no invented dynamic beside " + token);
        cases++;
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("glyphs")) {
            for (String s : List.of("f", "mf"))
                for (boolean nearby : new boolean[] {false, true}) glyphControl(s, nearby);
        } else geometricControls();
        System.out.println("PASS " + cases + " original synthetic cases");
    }

    @org.junit.Test
    public void fragmentedGeometryControls() throws Exception {
        main(new String[0]);
    }

    @org.junit.Test
    public void literalGlyphControls() throws Exception {
        main(new String[] {"glyphs"});
    }
}
