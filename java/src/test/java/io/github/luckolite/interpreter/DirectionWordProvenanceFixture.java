// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;

/** Independent source-free adaptation of frozen practical originals, with explicit API-domain limits. */
public final class DirectionWordProvenanceFixture {
    static int pass;

    static void ok(boolean b, String name) {
        if (!b) throw new AssertionError(name);
        pass++;
        System.out.println("PASS " + name);
    }

    record S(
            byte[] raw,
            DirectionWordProvenance.Scope scope,
            List<PlayingTechniqueDetector.Word> ordinary,
            DirectionWordProvenance.Origin o,
            OcrText full) {}

    static OcrText.Box b(int l, int t, int r, int z) {
        return new OcrText.Box(l, t, r, z);
    }

    static OcrText.Element e(String s, OcrText.Box b) {
        return new OcrText.Element(s, b, List.of());
    }

    static OcrText.Line p(String s, OcrText.Box b, OcrText.Element... e) {
        return new OcrText.Line(s, b, List.of(e));
    }

    static OcrText doc(OcrText.Line... p) {
        return new OcrText(
                "original",
                List.of(new OcrText.Block("original", b(0, 0, 300, 240), List.of(p))),
                OcrText.Grouping.DETECTED_LINES);
    }

    static PlayingTechniqueDetector.Word w(float l, float t, float r, float z) {
        return new PlayingTechniqueDetector.Word("quietly", l / 300f, t / 240f, r / 300f, z / 240f);
    }

    static S scene(int side, int scale) {
        int[][] crops = {{0, 80, 300, 200}, {0, 20, 300, 85}, {90, 0, 260, 240}, {20, 0, 105, 240}};
        int[] c = crops[side];
        var fe = e("quietly", b(80, 70, 120, 94));
        var fp = p("quietly", b(72, 62, 126, 102), fe);
        var se =
                e(
                        "quietly",
                        b(
                                (Math.max(80, c[0]) - c[0]) * scale,
                                (Math.max(70, c[1]) - c[1]) * scale,
                                (Math.min(120, c[2]) - c[0]) * scale,
                                (Math.min(94, c[3]) - c[1]) * scale));
        var sp = p("quietly", se.box(), se);
        var sb = se.box();
        var fresh =
                new PlayingTechniqueDetector.Word(
                        "quietly",
                        c[0] == 0
                                ? sb.left / (float) (scale * 300)
                                : (c[0] + sb.left / (float) scale) / 300,
                        (c[1] + sb.top / (float) scale) / 240,
                        c[0] == 0
                                ? sb.right / (float) (scale * 300)
                                : (c[0] + sb.right / (float) scale) / 300,
                        (c[1] + sb.bottom / (float) scale) / 240);
        var ordinary = List.of(w(68, 58, 130, 106));
        byte[] raw = new byte[300 * 240];
        var scope = new DirectionWordProvenance.Scope(raw, 300, 240, 0, ordinary);
        return new S(
                raw,
                scope,
                ordinary,
                new DirectionWordProvenance.Origin(
                        fresh, doc(sp), sp, se, c[0], c[1], c[2], c[3], scale),
                doc(fp));
    }

    static S full(S s, OcrText f) {
        return new S(s.raw, s.scope, s.ordinary, s.o, f);
    }

    static S ord(S s, List<PlayingTechniqueDetector.Word> w) {
        return new S(
                s.raw, new DirectionWordProvenance.Scope(s.raw, 300, 240, 0, w), w, s.o, s.full);
    }

    static S origin(S s, DirectionWordProvenance.Origin o) {
        return new S(s.raw, s.scope, s.ordinary, o, s.full);
    }

    static DirectionWordProvenance.Origin o(
            S s,
            PlayingTechniqueDetector.Word w,
            OcrText t,
            OcrText.Line p,
            OcrText.Element e,
            int l,
            int top,
            int r,
            int bot,
            int scale) {
        return new DirectionWordProvenance.Origin(w, t, p, e, l, top, r, bot, scale);
    }

    static void check(String n, S s, boolean suppress) {
        var input = new ArrayList<>(s.ordinary);
        input.add(s.o.word());
        int[] before =
                input.stream()
                        .flatMapToInt(
                                a ->
                                        java.util.stream.IntStream.of(
                                                Float.floatToRawIntBits(a.left()),
                                                Float.floatToRawIntBits(a.top()),
                                                Float.floatToRawIntBits(a.right()),
                                                Float.floatToRawIntBits(a.bottom())))
                        .toArray();
        var acq = new DirectionWordProvenance.Acquisition(input, List.of(s.o), s.scope);
        var output = DirectionWordProvenance.reconcile(input, acq, s.full, s.raw, 300, 240);
        boolean good = output.size() == input.size() - (suppress ? 1 : 0);
        for (int i = 0; i < s.ordinary.size(); i++) good &= output.get(i) == s.ordinary.get(i);
        if (!suppress) good &= output == input;
        good &=
                Arrays.equals(
                        before,
                        input.stream()
                                .flatMapToInt(
                                        a ->
                                                java.util.stream.IntStream.of(
                                                        Float.floatToRawIntBits(a.left()),
                                                        Float.floatToRawIntBits(a.top()),
                                                        Float.floatToRawIntBits(a.right()),
                                                        Float.floatToRawIntBits(a.bottom())))
                                .toArray());
        ok(good, n);
    }

    static void contract() {
        for (int side = 0; side < 4; side++)
            for (int scale = 2; scale <= 3; scale++)
                check("frozen_exact_edge" + side + "scale" + scale, scene(side, scale), true);
        var s = scene(0, 2);
        var o = s.o;
        var fp = s.full.blocks().get(0).lines().get(0);
        var fe = fp.elements().get(0);
        var old = s.ordinary.get(0);
        check("frozen_repeated_identity", ord(full(s, doc(fp, fp)), List.of(old, old)), true);
        var oe = e("quietly", b(180, 150, 220, 174));
        var op = p("quietly", b(172, 142, 226, 182), oe);
        var ow = w(168, 138, 230, 186);
        check("frozen_distinct_caption_site", ord(full(s, doc(fp, op)), List.of(old, ow)), true);
        check("frozen_only_distinct_site", ord(full(s, doc(op)), List.of(ow)), false);
        check("frozen_no_full", full(s, null), false);
        check(
                "frozen_two_parent_identities",
                full(s, doc(fp, p(fp.text(), fp.box(), e(fe.text(), fe.box())))),
                false);
        check("frozen_two_original_objects", ord(s, List.of(old, w(68, 58, 130, 106))), false);
        check(
                "frozen_full_prose",
                full(s, doc(p("play quietly through this passage", fp.box(), fe))),
                false);
        var prose = p("the word quietly here", o.parent().box(), o.element());
        check(
                "frozen_strip_prose",
                origin(s, o(s, o.word(), doc(prose), prose, o.element(), 0, 80, 300, 200, 2)),
                false);
        check("frozen_ordinary_incomplete_parent", ord(s, List.of(w(80, 70, 120, 94))), false);
        check(
                "frozen_wrong_projection_word",
                origin(
                        s,
                        o(
                                s,
                                w(70, 81, 130, 94),
                                o.text(),
                                o.parent(),
                                o.element(),
                                0,
                                80,
                                300,
                                200,
                                2)),
                false);
        check(
                "frozen_complete_touching_edge",
                full(s, doc(p("quietly", fp.box(), e("quietly", b(80, 80, 120, 94))))),
                false);
        check(
                "frozen_unbound_actual_strip_element",
                origin(
                        s,
                        o(
                                s,
                                o.word(),
                                o.text(),
                                o.parent(),
                                e(o.element().text(), o.element().box()),
                                0,
                                80,
                                300,
                                200,
                                2)),
                false);
        check(
                "frozen_scope_same_bytes_other_identity",
                new S(
                        s.raw,
                        new DirectionWordProvenance.Scope(s.raw.clone(), 300, 240, 0, s.ordinary),
                        s.ordinary,
                        o,
                        s.full),
                false);
        check(
                "frozen_raster_other_invocation",
                new S(s.raw.clone(), s.scope, s.ordinary, o, s.full),
                false);
        var changed = scene(0, 2);
        changed.raw[0] = 1;
        check("frozen_full_digest_changed", changed, false);
        var stripChanged = scene(0, 2);
        stripChanged.raw[1] = 2;
        check("frozen_strip_digest_changed", stripChanged, false);
        check(
                "frozen_wrong_crop",
                origin(s, o(s, o.word(), o.text(), o.parent(), o.element(), 0, 81, 300, 200, 2)),
                false);
        check(
                "frozen_wrong_scale",
                origin(s, o(s, o.word(), o.text(), o.parent(), o.element(), 0, 80, 300, 200, 3)),
                false);
        check(
                "frozen_nonfinite_scale_API_integer_zero",
                origin(s, o(s, o.word(), o.text(), o.parent(), o.element(), 0, 80, 300, 200, 0)),
                false);
        check(
                "frozen_degenerate_crop",
                origin(s, o(s, o.word(), o.text(), o.parent(), o.element(), 0, 80, 0, 200, 2)),
                false);
        check("frozen_unknown_parent_text", full(s, doc(p("", fp.box(), fe))), false);
        check(
                "frozen_full_parent_outside_actual_page",
                full(s, doc(p("quietly", b(-1, 62, 126, 102), fe))),
                false);
        System.out.println(
                "COVERAGE 30/31 neutral cases adapted; unbound ordinary per-Word Scope not representable: actual caller requires all Scope.ordinary identities retained");
    }

    static void envelope() throws Exception {
        var method =
                DirectionWordProvenance.class.getDeclaredMethod(
                        "envelope", PlayingTechniqueDetector.Word.class, int.class, int.class);
        method.setAccessible(true);
        float[][] bounds = {
            {.201f, .3125f, .629f, .509f},
            {.25f, .25f, .75f, .75f},
            {.201f, .317f, .629f, .509f},
            {Float.NaN, .1f, .4f, .5f},
            {.1f, .1f, .4f, .5f}
        };
        int[][] want = {{40, 62, 126, 102}, {64, 64, 192, 192}, {40, 63, 126, 102}, null, null};
        for (int i = 0; i < 5; i++) {
            if (i == 4) {
                var z = scene(0, 2);
                check(
                        "frozen_zero_dimension_scope",
                        new S(
                                z.raw,
                                new DirectionWordProvenance.Scope(z.raw, 0, 240, 0, z.ordinary),
                                z.ordinary,
                                z.o,
                                z.full),
                        false);
                continue;
            }
            var v = bounds[i];
            int width = i == 1 ? 256 : i == 4 ? 0 : 200, height = i == 1 ? 256 : 200;
            var actual =
                    (OcrText.Box)
                            method.invoke(
                                    null,
                                    new PlayingTechniqueDetector.Word(
                                            "quietly", v[0], v[1], v[2], v[3]),
                                    width,
                                    height);
            ok(
                    want[i] == null
                            ? actual == null
                            : actual != null
                                    && actual.left == want[i][0]
                                    && actual.top == want[i][1]
                                    && actual.right == want[i][2]
                                    && actual.bottom == want[i][3],
                    "frozen_envelope_vector" + i);
        }
        check(
                "frozen_fractional_parent_row_retained",
                ord(scene(0, 2), List.of(w(68, 62.5f, 130, 106))),
                true);
        check(
                "frozen_next_pixel_parent_missing",
                ord(scene(0, 2), List.of(w(68, 63.4f, 130, 106))),
                false);
    }

    static final class Reader implements NativeDirectionOcrCache.Reader {
        int full, crop;
        boolean fail;
        OcrText value;

        Reader(OcrText v) {
            value = v;
        }

        public OcrText full(byte[] r, int w, int h) throws Exception {
            full++;
            if (fail) throw new Exception("synthetic");
            return value;
        }

        public OcrText crop(byte[] r, int w, int h, PaperDynamicWord.Crop c, int scale) {
            crop++;
            return doc();
        }
    }

    static void cache() throws Exception {
        var s = scene(0, 2);
        var reader = new Reader(s.full);
        var cache = new NativeDirectionOcrCache(reader);
        var scope =
                new DirectionWordProvenance.Scope(s.raw, 300, 240, cache.fullEpoch(), s.ordinary);
        for (int i = 0; i < 10; i++)
            ok(
                    cache.availableFull(scope) == null && reader.full == 0 && reader.crop == 0,
                    "cache_nonacquiring_absent" + i);
        var literal = new PlayingTechniqueDetector.Word("mf", .2f, .4f, .3f, .5f);
        cache.directions(
                s.raw, 300, 240, List.of(), List.of(), List.of(), List.of(literal), List.of());
        ok(
                reader.full == 1 && reader.crop == 0 && cache.availableFull(scope) == s.full,
                "cache_already_executed_same_invocation");
        for (int i = 0; i < 10; i++) cache.availableFull(scope);
        ok(reader.full == 1 && reader.crop == 0, "cache_repeated_accessor_no_read");
        var later =
                new DirectionWordProvenance.Scope(s.raw, 300, 240, cache.fullEpoch(), s.ordinary);
        ok(cache.availableFull(later) == null, "cache_scope_after_old_read_abstains");
        var equal = s.raw.clone();
        var other = new DirectionWordProvenance.Scope(equal, 300, 240, 0, s.ordinary);
        cache.rawCrop(equal, 300, 240, new PaperDynamicWord.Crop(0, 1, 100, 50), 2);
        ok(
                cache.availableFull(other) == null
                        && cache.availableFull(scope) == null
                        && reader.full == 1,
                "cache_byte_equal_raster_rebind_abstains");
        var freshReader = new Reader(s.full);
        var freshCache = new NativeDirectionOcrCache(freshReader);
        var before = new DirectionWordProvenance.Scope(s.raw, 300, 240, 0, s.ordinary);
        freshCache.directions(
                s.raw, 300, 240, List.of(), List.of(), List.of(), List.of(literal), List.of());
        s.raw[0] = 3;
        ok(
                freshCache.availableFull(before) == null && freshReader.full == 1,
                "cache_mutated_raster_abstains_no_read");
        var failReader = new Reader(s.full);
        failReader.fail = true;
        var failCache = new NativeDirectionOcrCache(failReader);
        var fscope = new DirectionWordProvenance.Scope(equal, 300, 240, 0, s.ordinary);
        failCache.directions(
                equal, 300, 240, List.of(), List.of(), List.of(), List.of(literal), List.of());
        ok(
                failCache.availableFull(fscope) == null && failReader.full == 1,
                "cache_failed_full_abstains");
    }

    public static void main(String[] args) throws Exception {
        contract();
        envelope();
        cache();
        System.out.println(
                "PASS "
                        + pass
                        + " adapted originals/cache assertions; no actual OCR/model/Guide calls");
    }
}
