// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original typed carrier controls; no optical head acquisition is claimed. */
public class RestRuleOriginalPlaneIdentityTest {
    static final int W = 760, H = 460;

    static byte[] page(int shade) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = 160; y <= 224; y += 16)
            for (int x = 20; x <= 740; x++) g[y * W + x] = (byte) shade;
        for (int y = 180; y <= 188; y++)
            for (int x = 194; x <= 206; x++)
                if ((x - 200) * (x - 200) / 49.0 + (y - 184) * (y - 184) / 16.0 <= 1)
                    g[y * W + x] = 0;
        return g;
    }

    static ScoreNoteEvent note(float position) {
        return new ScoreNoteEvent(0, position, 5, 0, 1, 184f / H, false, 0, 0);
    }

    static List<ScoreNoteEvent> carrier(ScoreNoteEvent n, byte[] original, byte[] view) {
        var runs = new ArrayList<int[]>();
        for (int y = 180; y <= 188; y++) {
            int l = -1, r = -1;
            for (int x = 194; x <= 206; x++)
                if ((original[y * W + x] & 255) < 170) {
                    if (l < 0) l = x;
                    r = x;
                }
            if (l >= 0) runs.add(new int[] {y, l, r});
        }
        // An opaque synthetic producer identity and its actual authored visited ink runs.
        var origin = RestFigureOrigins.HeadOrigin.accepted(new Object(), original, W, H, runs);
        assertNotNull(origin);
        var base = RestFigureOrigins.bind(List.of(n), Map.of(n, origin));
        return RestFigureOrigins.photometricView(base, original, view, W, H);
    }

    static boolean[] mask() {
        boolean[] m = new boolean[121];
        for (int y = 144; y <= 224; y += 16) m[y - 128] = true;
        return m;
    }

    static RestComponentRuleDomain.Domain bind(byte[] g, boolean[] m, List<ScoreNoteEvent> notes) {
        return RestComponentRuleDomain.bind(g, W, H, 210, 230, 128, 248, m, 192, 16, notes);
    }

    @Test
    public void photometricCarrierRetainsActualOriginalFive() {
        byte[] original = page(0), view = original.clone();
        var d = bind(view, mask(), carrier(note(.3f), original, view));
        assertNotNull(d);
        assertTrue(d.registered());
        assertFalse(d.line()[16]);
    }

    @Test
    public void strongCurrentViewCannotReplaceFaintOriginalRules() {
        byte[] original = page(230), view = page(0);
        boolean[] m = mask();
        var d = bind(view, m, carrier(note(.3f), original, view));
        assertNotNull(d);
        assertFalse(d.registered());
        assertSame(m, d.line());
    }

    @Test
    public void missingCarrierCannotClaimOriginalPlane() {
        byte[] view = page(0);
        boolean[] m = mask();
        var d = bind(view, m, List.of(note(.3f)));
        assertNotNull(d);
        assertFalse(d.registered());
        assertSame(m, d.line());
    }

    @Test
    public void everyKnownOriginalIdentityMustAgree() {
        byte[] a = page(0), b = a.clone(), view = a.clone();
        var notes = List.of(note(.2f), note(.3f), note(.4f));
        var map = new IdentityHashMap<ScoreNoteEvent, RestFigureOrigins.HeadOrigin>();
        for (int i = 0; i < 3; i++) {
            var one = carrier(notes.get(i), i == 2 ? b : a, view);
            map.put(notes.get(i), RestFigureOrigins.origin(one, notes.get(i), view, W, H));
        }
        for (int shift = 0; shift < 3; shift++) {
            var order = new ArrayList<>(notes);
            Collections.rotate(order, shift);
            boolean[] m = mask();
            var d = bind(view, m, RestFigureOrigins.bind(order, map));
            assertNotNull(d);
            assertFalse(d.registered());
            assertSame(m, d.line());
        }
    }
}
