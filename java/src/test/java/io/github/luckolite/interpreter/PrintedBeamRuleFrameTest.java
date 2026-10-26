// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;

/** Original observed-band geometry; no score/image/model material. */
public final class PrintedBeamRuleFrameTest {
    @org.junit.Test
    public void supportedFloatSeedKeepsItsAcceptanceBoundaryAndWork() throws Exception {
        float gap = 26.920299530029297f, y = 341.7663269042969f;
        int width = 300, height = 500;
        byte[] raw = new byte[width * height];
        Arrays.fill(raw, (byte) 255);
        for (int[] band : new int[][] {{246, 247}, {273, 273}, {300, 300}, {327, 327}, {354, 354}})
            for (int row = band[0]; row <= band[1]; row++)
                Arrays.fill(raw, row * width, (row + 1) * width, (byte) 20);
        org.junit.Assert.assertNotNull(
                PrintedBeamRuleWitness.at(raw, width, height, 128, 354, gap));
        var laneType = Class.forName(PrintedBeamSource.class.getName() + "$Lane");
        var constructor = laneType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        var staff = new PlayingTechniqueDetector.Staff(354 - 4 * gap, 354, gap, 0, 1);
        Object lane =
                constructor.newInstance(0, staff, null, null, new HashMap<>(), new HashMap<>());
        var head =
                new PrintedSlurOwners.Location() {
                    public int owner() {
                        return 0;
                    }

                    public float x() {
                        return 150;
                    }

                    public float y() {
                        return y;
                    }

                    public int staffIndex() {
                        return 0;
                    }

                    public int staffCount() {
                        return 1;
                    }
                };
        var note = new ScoreNoteEvent(0, .5f, 0, 0, 1, y / height, false, 0, 1);
        var method =
                PrintedBeamSource.class.getDeclaredMethod(
                        "bind",
                        PrintedSlurOwners.Location.class,
                        ScoreNoteEvent.class,
                        List.class,
                        byte[].class,
                        int.class,
                        int.class,
                        PrintedBeamSource.Work.class);
        method.setAccessible(true);
        var work = new PrintedBeamSource.Work(1_000_000);
        Object binding = method.invoke(null, head, note, List.of(lane), raw, width, height, work);
        org.junit.Assert.assertNotNull(
                "Supported float seed must retain old boundary acceptance", binding);
        var bottom = binding.getClass().getDeclaredMethod("bottom");
        bottom.setAccessible(true);
        org.junit.Assert.assertEquals(
                Float.floatToRawIntBits(353.88043212890625f),
                Float.floatToRawIntBits((float) bottom.invoke(binding)));
        org.junit.Assert.assertEquals(
                "No fallback fitting charge on the supported path",
                1_000_000 - (long) Math.ceil(gap * 12),
                work.remaining);
    }

    @org.junit.Test
    public void originalBandAndBindingControls() throws Exception {
        main(new String[0]);
    }

    static int checks;

    static void check(boolean ok, String text) {
        checks++;
        if (!ok) throw new AssertionError(text);
    }

    static PrintedBeamRuleWitness.Witness witness(int offset) {
        return new PrintedBeamRuleWitness.Witness(
                List.of(
                        new PrintedBeamRuleWitness.Band(42 + offset, 42 + offset),
                        new PrintedBeamRuleWitness.Band(56 + offset, 57 + offset),
                        new PrintedBeamRuleWitness.Band(71 + offset, 71 + offset),
                        new PrintedBeamRuleWitness.Band(85 + offset, 86 + offset),
                        new PrintedBeamRuleWitness.Band(100 + offset, 100 + offset)),
                100 + offset,
                14.5);
    }

    static void contained(PrintedBeamRuleWitness.Witness w, double bottom, double gap) {
        for (int i = 0; i < 5; i++) {
            var band = w.rules().get(i);
            double y = bottom - (4 - i) * gap;
            check(
                    y >= band.top() - .5 && y <= band.bottom() + .5,
                    "all five observed bands support chosen frame");
        }
    }

    static Object bind(byte[] raw, int step, float y, boolean competing) throws Exception {
        var laneType = Class.forName(PrintedBeamSource.class.getName() + "$Lane");
        var constructor = laneType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        var staff = new PlayingTechniqueDetector.Staff(44, 100, 14, 0, 1);
        Object lane =
                constructor.newInstance(0, staff, null, null, new HashMap<>(), new HashMap<>());
        var lanes =
                competing
                        ? List.of(
                                lane,
                                constructor.newInstance(
                                        1, staff, null, null, new HashMap<>(), new HashMap<>()))
                        : List.of(lane);
        var head =
                new PrintedSlurOwners.Location() {
                    public int owner() {
                        return 0;
                    }

                    public float x() {
                        return 100;
                    }

                    public float y() {
                        return y;
                    }

                    public int staffIndex() {
                        return 0;
                    }

                    public int staffCount() {
                        return 1;
                    }
                };
        var note = new ScoreNoteEvent(0, .5f, step, 0, 1, y / 160, false, 0, 1);
        var method =
                PrintedBeamSource.class.getDeclaredMethod(
                        "bind",
                        PrintedSlurOwners.Location.class,
                        ScoreNoteEvent.class,
                        List.class,
                        byte[].class,
                        int.class,
                        int.class,
                        PrintedBeamSource.Work.class);
        method.setAccessible(true);
        return method.invoke(
                null, head, note, lanes, raw, 200, 160, new PrintedBeamSource.Work(1_000_000));
    }

    public static void main(String[] args) throws Exception {
        boolean old = args.length > 0 && args[0].equals("--expect-old");
        var left = witness(0);
        var right = witness(2);
        var pair = PrintedBeamRuleFrame.fit(left, right, 14);
        check(pair != null, "quantized seed does not erase valid observed bands");
        contained(left, pair.leftBottom(), pair.gap());
        contained(right, pair.rightBottom(), pair.gap());
        check(pair.gap() > 14, "unsupported nominal spacing corrected from observations");
        var retained = PrintedBeamRuleFrame.fit(left, left, 14.5);
        check(retained.gap() == 14.5, "supported nominal spacing stays exact");
        check(PrintedBeamRuleFrame.fit(null, right, 14) == null, "missing witness refuses");
        check(PrintedBeamRuleFrame.fit(left, right, Double.NaN) == null, "nonfinite seed refuses");
        check(PrintedBeamRuleFrame.fit(left, right, 2) == null, "tiny seed refuses");
        var missing = new PrintedBeamRuleWitness.Witness(left.rules().subList(0, 4), 100, 14.5);
        check(
                PrintedBeamRuleFrame.fit(missing, right, 14) == null,
                "four lines cannot establish five-line frame");
        var wide =
                new PrintedBeamRuleWitness.Witness(
                        List.of(
                                new PrintedBeamRuleWitness.Band(20, 20),
                                new PrintedBeamRuleWitness.Band(40, 40),
                                new PrintedBeamRuleWitness.Band(60, 60),
                                new PrintedBeamRuleWitness.Band(80, 80),
                                new PrintedBeamRuleWitness.Band(100, 100)),
                        100,
                        20);
        check(
                PrintedBeamRuleFrame.fit(left, wide, 14) == null,
                "incompatible observed spacing refuses");
        byte[] raw = new byte[200 * 160];
        Arrays.fill(raw, (byte) 255);
        for (var band : left.rules())
            for (int row = band.top(); row <= band.bottom(); row++)
                Arrays.fill(raw, row * 200, (row + 1) * 200, (byte) 20);
        byte[] original = raw.clone();
        check(
                (bind(raw, 0, 100, false) == null) == old,
                "actual fallback bind old-fails/new-passes quantized staff");
        check(bind(raw, 20, 100, false) == null, "wrong note staff step still refuses");
        check(bind(raw, 0, 100, true) == null, "competing physical lanes still refuse");
        check(Arrays.equals(raw, original), "source raw pixels unchanged");
        Arrays.fill(raw, 71 * 200, 72 * 200, (byte) 255);
        check(bind(raw, 0, 100, false) == null, "missing printed middle rule refuses binding");
        System.out.println(
                "PASS "
                        + checks
                        + " band/actual-binding controls; expected="
                        + (old ? "OLD" : "NEW"));
    }
}
