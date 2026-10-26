// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import java.security.*;
import java.nio.charset.StandardCharsets;

/** Original procedural raw controls against the exact same actual old/new package API. */
final class PrintedBeamMemoOriginals {
    static final String GEOMETRY_DIGEST =
            "8d56a8ad2c628b9ac00a3c6a58e3053d03cb47d75db534174b381911b02d4ba1";
    static final String DEFAULT_DEBIT_DIGEST =
            "02bbf5550f69de796481285f34345c6c25567ed757c02ec51fcef161363c1e78";
    static final String CONTEXT_DIGEST =
            "f9559dc805c3af95a53dc77d4f74e69ffb741124273825e02e4c3b717217b55f";

    static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
    }

    static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    static final class PageRefused extends RuntimeException {}

    static final class Page implements PrintedBeamShaftPixels.Charge {
        final long limit;
        long used;
        boolean refused;

        Page(long limit) {
            this.limit = limit;
        }

        public void take(long n) {
            if (refused || n < 0 || n > limit - used) {
                refused = true;
                throw new PageRefused();
            }
            used += n;
        }
    }

    record Input(byte[] raw, int w, int h, float hx, float hy, float gap) {}

    record Answer(String shape, long raw, long page, boolean refused, int shafts) {}

    static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
    }

    static long remaining(PrintedBeamShafts.Budget budget) throws Exception {
        Field f = PrintedBeamShafts.Budget.class.getDeclaredField("remaining");
        f.setAccessible(true);
        return f.getLong(budget);
    }

    static Input draw(float gap, int shade, int style, int direction) {
        int w = (int) Math.ceil(gap * 12) + 31, h = (int) Math.ceil(gap * 28) + 31;
        float hx = style == 4 ? gap * .62f : w * .5f + .25f, hy = h * .5f + .25f;
        byte[] raw = new byte[w * h];
        Arrays.fill(raw, (byte) 255);
        int cy = direction > 0 ? Math.round(hy) : h - 1 - Math.round(hy);
        int edge = Math.round(hx - direction * .6f * gap),
                first = Math.max(1, Math.round(edge - gap * .2f));
        int last = Math.min(w - 2, (int) Math.floor(edge + gap * .2f)),
                start = cy + Math.round(gap * .2f),
                end = cy + Math.round(gap * 4);
        int middle = (first + last) / 2;
        for (int x = first; x <= last; x++) {
            boolean selected =
                    style == 2
                            ? x == first || x == last
                            : x == middle || style == 1 && x == middle + 1;
            if (!selected) continue;
            for (int y = start; y <= end && y < h - 1; y++) {
                if (style == 3 && y >= cy + Math.round(2 * gap) && y < cy + Math.round(2 * gap) + 2)
                    continue;
                if (style == 1
                        && x == middle + 1
                        && y > cy + Math.round(2.1f * gap)
                        && y < cy + Math.round(2.1f * gap) + 3) continue;
                int actual = direction > 0 ? y : h - 1 - y;
                if (actual >= 0 && actual < h) raw[actual * w + x] = (byte) shade;
            }
        }
        return new Input(raw, w, h, hx, hy, gap);
    }

    static Input both() {
        Input down = draw(16, 200, 0, 1), up = draw(16, 200, 0, -1);
        byte[] raw = down.raw.clone();
        for (int i = 0; i < raw.length; i++)
            raw[i] = (byte) Math.min(raw[i] & 255, up.raw[i] & 255);
        return new Input(raw, down.w, down.h, down.hx, down.hy, down.gap);
    }

    static Input mirror(Input a, boolean horizontal, boolean vertical) {
        byte[] raw = new byte[a.raw.length];
        for (int y = 0; y < a.h; y++)
            for (int x = 0; x < a.w; x++)
                raw[(vertical ? a.h - 1 - y : y) * a.w + (horizontal ? a.w - 1 - x : x)] =
                        a.raw[y * a.w + x];
        return new Input(
                raw,
                a.w,
                a.h,
                horizontal ? a.w - 1 - a.hx : a.hx,
                vertical ? a.h - 1 - a.hy : a.hy,
                a.gap);
    }

    static String geometry(List<PrintedBeamShafts.Shaft> shafts) throws Exception {
        StringBuilder result = new StringBuilder();
        for (var s : shafts) {
            result.append(s.left())
                    .append(',')
                    .append(s.right())
                    .append(',')
                    .append(s.startY())
                    .append(',')
                    .append(s.endY())
                    .append(',')
                    .append(s.direction())
                    .append(':');
            for (var c : s.columns()) result.append(c.x()).append('/').append(c.endY()).append(';');
            if (s.pixels() == null) result.append("noPixels");
            else {
                check(s.pixels().matches(s), "Actual saved Scan must match actual returned Shaft");
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                for (int y = Math.min(s.startY(), s.endY());
                        y <= Math.max(s.startY(), s.endY());
                        y++)
                    for (int x = s.left(); x <= s.right(); x++)
                        digest.update((byte) (s.pixels().has(x, y) ? 1 : 0));
                result.append(HexFormat.of().formatHex(digest.digest()))
                        .append("/contrast=")
                        .append(s.pixels().contrast);
            }
            result.append('|');
        }
        return result.toString();
    }

    static List<PrintedBeamShafts.Shaft> call(
            Input a,
            String route,
            PrintedBeamShafts.Budget budget,
            PrintedBeamShaftPixels.Frame frame) {
        return switch (route) {
            case "atNull" -> PrintedBeamShafts.at(a.raw, a.w, a.h, a.hx, a.hy, a.gap, budget);
            case "at" -> PrintedBeamShafts.at(a.raw, a.w, a.h, a.hx, a.hy, a.gap, budget, frame);
            case "strict" ->
                    PrintedBeamShafts.atStrict(a.raw, a.w, a.h, a.hx, a.hy, a.gap, budget, frame);
            default -> PrintedBeamShafts.atAll(a.raw, a.w, a.h, a.hx, a.hy, a.gap, budget, frame);
        };
    }

    static Answer run(Input a, String route, long rawLimit, long pageLimit) throws Exception {
        byte[] before = a.raw.clone();
        var budget = new PrintedBeamShafts.Budget(rawLimit);
        var page = new Page(pageLimit);
        var frame =
                route.equals("atNull")
                        ? null
                        : new PrintedBeamShaftPixels.Frame(
                                new Object(), a.raw, a.w, a.h, 7, page, !route.equals("allFalse"));
        List<PrintedBeamShafts.Shaft> shafts = call(a, route, budget, frame);
        for (var shaft : shafts)
            check(
                    shaft.pixels() == null ? frame == null : shaft.pixels().frame == frame,
                    "Actual returned Scan retains the exact invocation Frame");
        check(Arrays.equals(before, a.raw), "Raw mutated during operation");
        return new Answer(
                geometry(shafts),
                rawLimit - remaining(budget),
                page.used,
                budget.refused(),
                shafts.size());
    }

    record Corpus(
            String geometry,
            String defaultDebits,
            int cases,
            int positives,
            int memoPositives,
            int defaultCases) {}

    static Corpus corpus() throws Exception {
        MessageDigest geometry = MessageDigest.getInstance("SHA-256"),
                debits = MessageDigest.getInstance("SHA-256");
        int cases = 0, positives = 0, memoPositives = 0, defaultCases = 0;
        for (float gap : new float[] {3, 14.25f, 64})
            for (int shade : new int[] {164, 165, 200, 230})
                for (int style = 0; style < 5; style++)
                    for (int dir : new int[] {-1, 1}) {
                        Input a = draw(gap, shade, style, dir);
                        for (String route :
                                new String[] {"atNull", "at", "strict", "allFalse", "allTrue"}) {
                            String key = gap + "/" + shade + "/" + style + "/" + dir + "/" + route;
                            Answer answer = run(a, route, 8_000_000, 100_000_000);
                            check(!answer.refused, "Original corpus must be funded");
                            update(
                                    geometry,
                                    key + "\t" + answer.refused + "\t" + answer.shape + "\n");
                            cases++;
                            if (answer.shafts > 0) {
                                positives++;
                                if (route.equals("allTrue")) memoPositives++;
                            }
                            if (!route.equals("allTrue")) {
                                update(
                                        debits,
                                        key
                                                + "\t"
                                                + answer.refused
                                                + "\t"
                                                + answer.raw
                                                + "\t"
                                                + answer.page
                                                + "\n");
                                defaultCases++;
                            }
                        }
                    }
        for (float gap : new float[] {6, 32})
            for (int shade : new int[] {165, 200})
                for (int style : new int[] {1, 2})
                    for (int flip = 1; flip <= 3; flip++) {
                        Input a =
                                mirror(
                                        draw(gap, shade, style, 1),
                                        (flip & 1) != 0,
                                        (flip & 2) != 0);
                        String key =
                                gap + "/" + shade + "/" + style + "/mirror" + flip + "/allTrue";
                        Answer answer = run(a, "allTrue", 8_000_000, 100_000_000);
                        check(!answer.refused, "Original mirror must be funded");
                        update(geometry, key + "\t" + answer.refused + "\t" + answer.shape + "\n");
                        cases++;
                        if (answer.shafts > 0) {
                            positives++;
                            memoPositives++;
                        }
                    }
        return new Corpus(
                hex(geometry.digest()),
                hex(debits.digest()),
                cases,
                positives,
                memoPositives,
                defaultCases);
    }

    static void resource() throws Exception {
        Input a = both();
        byte[] before = a.raw.clone();
        Answer full = run(a, "allTrue", 8_000_000, 100_000_000);
        check(
                full.shafts == 2,
                "Both actual stem directions must materialize before boundary controls");
        check(
                full.raw * 2 + 1 <= 8_000_000,
                "Two calls must fit actual unchanged maximum raw pool");
        Answer exact = run(a, "allTrue", full.raw, full.page);
        check(exact.shape.equals(full.shape) && !exact.refused, "Exact raw/page allowance");
        Answer low = run(a, "allTrue", full.raw - 1, 100_000_000);
        check(
                low.refused && low.shafts == 0,
                "No first-direction prefix after last raw debit refuses second direction");
        Answer noAnswer = null;
        boolean refused = false;
        try {
            noAnswer = run(a, "allTrue", 8_000_000, full.page - 1);
        } catch (PageRefused expected) {
            refused = true;
        }
        check(
                refused && noAnswer == null,
                "One-under page cannot return a partial first direction");
        check(Arrays.equals(before, a.raw), "Raw immutable after page refusal");
        int rawCuts = 0;
        for (long cap = 0; cap < full.raw; cap += Math.max(1, full.raw / 31)) {
            Answer r = run(a, "allTrue", cap, 100_000_000);
            check(r.refused && r.shafts == 0, "Every actual raw shortfall is all-or-nothing");
            rawCuts++;
        }
        var sharedBudget = new PrintedBeamShafts.Budget(2 * full.raw);
        var sharedPage = new Page(100_000_000);
        var frame =
                new PrintedBeamShaftPixels.Frame(
                        new Object(), a.raw, a.w, a.h, 7, sharedPage, true);
        check(
                geometry(call(a, "allTrue", sharedBudget, frame)).equals(full.shape),
                "Cumulative first result");
        check(remaining(sharedBudget) == full.raw, "Cumulative first actual raw debit");
        check(
                geometry(call(a, "allTrue", sharedBudget, frame)).equals(full.shape),
                "Cumulative second result");
        check(remaining(sharedBudget) == 0, "Cumulative raw pool not reset by new memo");
        long pageBefore = sharedPage.used;
        check(
                call(a, "allTrue", sharedBudget, frame).isEmpty() && sharedBudget.refused(),
                "Third query cannot reset spent raw pool");
        check(sharedPage.used == pageBefore, "Raw refusal precedes memo allocation/page debit");
        var cumulativePage = new Page(64 + 2 * (full.page - 64));
        var ample = new PrintedBeamShafts.Budget(8_000_000);
        var cumulativeFrame =
                new PrintedBeamShaftPixels.Frame(
                        new Object(), a.raw, a.w, a.h, 7, cumulativePage, true);
        check(
                geometry(call(a, "allTrue", ample, cumulativeFrame)).equals(full.shape),
                "Cumulative page first result");
        check(
                geometry(call(a, "allTrue", ample, cumulativeFrame)).equals(full.shape),
                "Cumulative page second result");
        check(cumulativePage.used == cumulativePage.limit, "Exact cumulative page use");
        boolean pageFailed = false;
        try {
            call(a, "allTrue", ample, cumulativeFrame);
        } catch (PageRefused expected) {
            pageFailed = true;
        }
        check(pageFailed && cumulativePage.refused, "Cumulative page pool cannot reset");
        var mismatchPage = new Page(100_000_000);
        var mismatchBudget = new PrintedBeamShafts.Budget(8_000_000);
        var bound =
                new PrintedBeamShaftPixels.Frame(
                        new Object(), a.raw, a.w, a.h, 7, mismatchPage, true);
        for (Input wrong :
                new Input[] {
                    new Input(a.raw.clone(), a.w, a.h, a.hx, a.hy, a.gap),
                    new Input(a.raw, a.h, a.w, a.hx, a.hy, a.gap)
                }) {
            boolean fault = false;
            try {
                call(wrong, "allTrue", mismatchBudget, bound);
            } catch (PrintedBeamShaftPixels.IdentityFault expected) {
                fault = true;
            }
            check(
                    fault && remaining(mismatchBudget) == 8_000_000 && mismatchPage.used == 64,
                    "Foreign raw/dimensions rejected before scan or memo payment");
        }
        var mutationPage = new Page(100_000_000);
        var mutationBudget = new PrintedBeamShafts.Budget(8_000_000);
        var mutationFrame =
                new PrintedBeamShaftPixels.Frame(
                        new Object(), a.raw, a.w, a.h, 7, mutationPage, true);
        check(
                geometry(call(a, "allTrue", mutationBudget, mutationFrame)).equals(full.shape),
                "Fresh invocation first positives");
        long firstUsed = 8_000_000 - remaining(mutationBudget);
        Arrays.fill(a.raw, (byte) 255); // BETWEEN operations only.
        check(
                call(a, "allTrue", mutationBudget, mutationFrame).isEmpty(),
                "Fresh invocation cannot inherit cached true values");
        System.arraycopy(before, 0, a.raw, 0, before.length);
        long startRaw = remaining(mutationBudget), startPage = mutationPage.used;
        check(
                geometry(call(a, "allTrue", mutationBudget, mutationFrame)).equals(full.shape),
                "Restored input cannot inherit cached false values");
        check(
                startRaw - remaining(mutationBudget) == firstUsed
                        && mutationPage.used - startPage == full.page - 64,
                "New invocation repeats its real original work, not stale cache hits");
        Answer freshFrame = run(a, "allTrue", 8_000_000, 100_000_000);
        check(
                freshFrame.equals(full),
                "New frame with same raw does not inherit cache or prior page pool");
        Input ragged = draw(16, 200, 1, 1);
        var raggedPage = new Page(100_000_000);
        var raggedFrame =
                new PrintedBeamShaftPixels.Frame(
                        new Object(), ragged.raw, ragged.w, ragged.h, 7, raggedPage, true);
        var raggedResult =
                call(ragged, "allTrue", new PrintedBeamShafts.Budget(8_000_000), raggedFrame);
        check(
                raggedResult.size() == 1 && raggedResult.get(0).columns().size() == 2,
                "Ragged original must enter materialize with two unequal columns");
        var s = raggedResult.get(0);
        var c0 = s.columns().get(0);
        var c1 = s.columns().get(1);
        check(
                c0.endY() > c1.endY() + 3,
                "Materialization reads cells beyond the short column's initial two-blank stop");
        check(
                s.pixels().has(c0.x(), c0.endY()) && !s.pixels().has(c1.x(), c0.endY()),
                "Unknown rereads do not counterfeit pixels past the saved short cap");
        var contextPage = new Page(100_000_000);
        var contextFrame =
                new PrintedBeamShaftPixels.Frame(
                        new Object(), a.raw, a.w, a.h, 7, contextPage, true);
        MessageDigest contextDigest = MessageDigest.getInstance("SHA-256");
        int context = 0;
        for (Input changed :
                new Input[] {
                    a,
                    new Input(a.raw, a.w, a.h, a.hx + 1, a.hy, a.gap),
                    new Input(a.raw, a.w, a.h, a.hx, a.hy + 1.5f, a.gap),
                    new Input(a.raw, a.w, a.h, a.hx, a.hy, 14.25f),
                    a
                }) {
            var meter = new PrintedBeamShafts.Budget(8_000_000);
            String shape = geometry(call(changed, "allTrue", meter, contextFrame));
            update(
                    contextDigest,
                    "CONTEXT\t" + (context++) + "\t" + meter.refused() + "\t" + shape + "\n");
        }
        check(
                hex(contextDigest.digest()).equals(CONTEXT_DIGEST),
                "Original changed-context geometry digest");
        memoBoundaries(a);
        check(
                Arrays.equals(before, a.raw),
                "Raw immutable across all operations (only explicit between-invocation edits were restored)");
    }

    static void memoBoundaries(Input a) throws Exception {
        int cy = a.h - 1 - Math.round(a.hy), edge = Math.round(a.hx + .6f * a.gap);
        int first = Math.max(1, Math.round(edge - a.gap * .2f)),
                last = Math.min(a.w - 2, (int) Math.floor(edge + a.gap * .2f));
        int start = cy + Math.round(a.gap * .2f),
                limit = Math.min(a.h - 2, start + (int) Math.ceil(a.gap * 12));
        long cells = (long) (last - first + 1) * (limit - start + 1), fee = 128 + 4 * cells;
        Answer low = run(a, "allTrue", 4 * cells - 1, 100_000_000);
        check(
                low.refused && low.raw == 0 && low.page == 64 && low.shafts == 0,
                "Four-slot raw prepay precedes memo and page allocation");
        var page = new Page(64 + fee - 1);
        var budget = new PrintedBeamShafts.Budget(8_000_000);
        var frame = new PrintedBeamShaftPixels.Frame(new Object(), a.raw, a.w, a.h, 7, page, true);
        boolean refused = false;
        try {
            call(a, "allTrue", budget, frame);
        } catch (PageRefused expected) {
            refused = true;
        }
        check(
                refused && page.used == 64 && 8_000_000 - remaining(budget) == 4 * cells,
                "One-under memo page prepay stops before memo/bytearray construction");
    }
}
