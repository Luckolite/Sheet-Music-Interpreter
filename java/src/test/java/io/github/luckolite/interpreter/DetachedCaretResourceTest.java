// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original capacity, stage, storage, source and contextual-prepay controls. */
public class DetachedCaretResourceTest {
    static final int W = 240, H = 240;

    static byte[] scene() {
        byte[] r = new byte[W * H];
        Arrays.fill(r, (byte) 255);
        for (int x = 95; x <= 119; x++) {
            int y = 100 + Math.abs(x - 107);
            r[y * W + x] = (byte) (x < 107 ? 195 : 75);
            r[(y + 1) * W + x] = (byte) (x < 107 ? 195 : 75);
        }
        return r;
    }

    static DetachedCaretBodies.Body grow(DetachedCaretBodies.Page p) {
        return p.grow(112 * W + 119, 14);
    }

    @Test
    public void exactProducerAndOneUnderRefusal() {
        byte[] r = scene(), labels = new byte[r.length];
        Object frame = new Object();
        var p = new DetachedCaretBodies.Page(r, labels, W, H, frame);
        assertNotNull(grow(p));
        long used = p.stageLimit - p.producer;
        var exact =
                new DetachedCaretBodies.Page(
                        r, labels, W, H, frame, used, Long.MAX_VALUE, Long.MAX_VALUE);
        assertNotNull(grow(exact));
        assertEquals(0, exact.producer);
        assertTrue(exact.finish(r, labels, W, H, frame));
        var under =
                new DetachedCaretBodies.Page(
                        r, labels, W, H, frame, used - 1, Long.MAX_VALUE, Long.MAX_VALUE);
        try {
            grow(under);
            fail();
        } catch (DetachedCaretBodies.Refusal expected) {
        }
        assertFalse(under.finish(r, labels, W, H, frame));
    }

    @Test
    public void exactSharedStorageAndOneUnder() {
        byte[] r = scene(), labels = new byte[r.length];
        Object frame = new Object();
        var p = new DetachedCaretBodies.Page(r, labels, W, H, frame);
        assertNotNull(grow(p));
        long used = 8_000_000 - p.bytes;
        var exact =
                new DetachedCaretBodies.Page(
                        r, labels, W, H, frame, Long.MAX_VALUE, Long.MAX_VALUE, used);
        assertNotNull(grow(exact));
        assertEquals(0, exact.bytes);
        var under =
                new DetachedCaretBodies.Page(
                        r, labels, W, H, frame, Long.MAX_VALUE, Long.MAX_VALUE, used - 1);
        try {
            grow(under);
            fail();
        } catch (DetachedCaretBodies.Refusal expected) {
        }
        assertFalse(under.finish(r, labels, W, H, frame));
    }

    @Test
    public void admissionHasIndependentExactLimitAndSharedFailureLatch() {
        byte[] r = scene(), labels = new byte[r.length];
        Object frame = new Object();
        var exact =
                new DetachedCaretBodies.Page(
                        r, labels, W, H, frame, Long.MAX_VALUE, 17, Long.MAX_VALUE);
        exact.a(17, 0);
        assertTrue(exact.finish(r, labels, W, H, frame));
        var under =
                new DetachedCaretBodies.Page(
                        r, labels, W, H, frame, Long.MAX_VALUE, 16, Long.MAX_VALUE);
        try {
            under.a(17, 0);
            fail();
        } catch (DetachedCaretBodies.Refusal expected) {
        }
        assertTrue(under.failed);
        assertFalse(under.finish(r, labels, W, H, frame));
    }

    @Test
    public void changedSourceFrameOrThreadCannotCommit() throws Exception {
        byte[] r = scene(), labels = new byte[r.length];
        Object frame = new Object();
        var p = new DetachedCaretBodies.Page(r, labels, W, H, frame);
        assertNotNull(grow(p));
        assertFalse(p.finish(r.clone(), labels, W, H, frame));
        p = new DetachedCaretBodies.Page(r, labels, W, H, frame);
        assertFalse(p.finish(r, labels, W, H, new Object()));
        var threaded = new DetachedCaretBodies.Page(r, labels, W, H, frame);
        Thread other =
                new Thread(
                        () -> {
                            try {
                                grow(threaded);
                                fail();
                            } catch (DetachedCaretBodies.Refusal expected) {
                            }
                        });
        other.start();
        other.join();
        assertFalse(threaded.finish(r, labels, W, H, frame));
    }

    @Test
    public void optionalCapacityRefusalDoesNotThrow() {
        byte[] r = new byte[20_005_000], labels = new byte[r.length];
        Arrays.fill(r, (byte) 255);
        Object frame = new Object();
        var p = new DetachedCaretBodies.Page(r, labels, 5000, 4001, frame);
        assertTrue(p.failed);
        assertFalse(p.finish(r, labels, 5000, 4001, frame));
        assertEquals(
                0,
                NoteArticulationDetector.detect(
                        labels,
                        r,
                        5000,
                        4001,
                        List.of(new NoteArticulationDetector.Anchor(100, 100, 14, 0)))[0]);
    }

    @Test
    public void denseLettersRefuseBeforeUnfundedGuardExecution() {
        byte[] r = scene(), labels = new byte[r.length];
        Object frame = new Object();
        var page = new DetachedCaretBodies.Page(r, labels, W, H, frame);
        var target = new DetachedCaretCosts.Ink(95, 100, 119, 114, 52);
        var context = new DetachedCaretCosts.Ink[1200];
        for (int i = 0; i < context.length; i++) {
            int x = 15 + (i % 30) * 3;
            context[i] = new DetachedCaretCosts.Ink(x, 100, x + 8, 112, 30);
        }
        var notes = new DetachedCaretCosts.Note[] {new DetachedCaretCosts.Note(107, 160, 14, 0)};
        try {
            DetachedCaretCosts.admissionGlyph(page, target, context, notes, W, H);
            fail();
        } catch (DetachedCaretBodies.Refusal expected) {
        }
        assertFalse(page.finish(r, labels, W, H, frame));
    }

    @Test
    public void limitsCannotRaiseProductionAllowances() {
        byte[] r = scene(), labels = new byte[r.length];
        var p =
                new DetachedCaretBodies.Page(
                        r,
                        labels,
                        W,
                        H,
                        new Object(),
                        Long.MAX_VALUE,
                        Long.MAX_VALUE,
                        Long.MAX_VALUE);
        assertEquals(2_000_000, p.stageLimit);
        assertEquals(8_000_000 - 320, p.bytes);
    }

    @Test
    public void unchangedAngularDimensionsHaveExactAndNextCellRefusal() {
        for (float gap : new float[] {3, 14, 17.5f, 64})
            for (boolean wide : new boolean[] {true, false})
                for (int extra : new int[] {0, 1}) {
                    int w = (int) Math.floor(gap * 2.1f + 1), h = (int) Math.floor(gap * 1.7f);
                    w = wide ? w + extra : Math.min(w, 5);
                    h = wide ? Math.min(h, 5) : h + extra;
                    byte[] raw = new byte[400 * 400], labels = new byte[raw.length];
                    Arrays.fill(raw, (byte) 255);
                    int left = 200 - w / 2, top = 200 - h / 2;
                    for (int y = top; y < top + h; y++)
                        for (int x = left; x < left + w; x++) raw[y * 400 + x] = 75;
                    var page = new DetachedCaretBodies.Page(raw, labels, 400, 400, new Object());
                    var body = page.grow(200 * 400 + 200, gap);
                    assertEquals(extra == 0, body != null);
                    assertFalse(page.failed);
                    if (body != null) assertEquals(w * h, body.pixels().length);
                }
    }

    @Test
    public void cropBoundaryIsRefusalNeverAClippedLiteralBody() {
        byte[] r = scene();
        for (int x = 0; x < W; x++) r[112 * W + x] = 75;
        var page = new DetachedCaretBodies.Page(r, new byte[r.length], W, H, new Object());
        assertNull(grow(page));
        assertFalse(page.failed);
    }
}
