// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original curved digit outlines generated from geometric pen paths. */
public final class TiltedTwoNumeralTest {
    // Frozen rasters of the original geometric pen paths, including their antialiasing.
    // These fixtures contain no source-score pixels or platform graphics dependencies.
    static byte[] fixture(String name) {
        String hex =
                switch (name) {
                    case "UPRIGHT" ->
                            "121a000000000000f5f5f5f5f5000000000000f5f5f5f5f5f5f5f5f5f5f50000000000000000000000f5f5f5f5f50000"
                                    + "000000000000000000000000f5f5f5000000000000f5000000000000000000f5f50000000000f5f5f5f5f5f5f5000000"
                                    + "00f50000000000f5f5f5f5f5f5f5f5f500000000000000f5f5f5f5f5f5f5f5f5f5f50000000000f5f5f5f5f5f5f5f5f5"
                                    + "f5f5f5f5f5000000f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5000000f5f5f5f5f5f5f5f5f5f5f5f5f5f5000000f5f5f5f5f5"
                                    + "f5f5f5f5f5f5f5f5f500000000f5f5f5f5f5f5f5f5f5f5f5f5f50000000000f5f5f5f5f5f5f5f5f5f5f5f50000000000"
                                    + "f5f5f5f5f5f5f5f5f5f5f5f5000000000000f5f5f5f5f5f5f5f5f5f5f5f50000000000f5f5f5f5f5f5f5f5f5f5f5f500"
                                    + "00000000f5f5f5f5f5f5f5f5f5f5f5f50000000000f5f5f5f5f5f5f5f5f5f5f5f50000000000f5f5f5f5f5f5f5f5f5f5"
                                    + "f5f50000000000f5f5f5f5f5f5f5f5f5f5f5f50000000000f5f5f5f5f5f5f5f5f5f5f5f50000000000f5f5f5f5f5f5f5"
                                    + "f5f5f5f5f50000000000f5f5f5f5f5f5f5f5f5f5f5f5f500000000f5f5f5f5f5f5f5f5f5f5f5f5f50000000000000000"
                                    + "00000000000000f5f5f500000000000000000000000000000000f5f5000000000000000000000000000000f5";
                    case "RISING" ->
                            "121a000000000000f5f5f5f5f5000000000000f5f5f5f5f5f5f5f5f5f5f50000000000000000000000f5f5f5f5f50000"
                                    + "000000000000000000000000f5f5f5000000000000f5000000000000000000f5f50000000000f5f5f5f5f5f5f5000000"
                                    + "00f50000000000f5f5f5f5f5f5f5f5f500000000000000f5f5f5f5f5f5f5f5f5f5f50000000000f5f5f5f5f5f5f5f5f5"
                                    + "f5f5f5f5f5000000f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5000000f5f5f5f5f5f5f5f5f5f5f5f5f5f5000000f5f5f5f5f5"
                                    + "f5f5f5f5f5f5f5f5f500000000f5f5f5f5f5f5f5f5f5f5f5f5f50000000000f5f5f5f5f5f5f5f5f5f5f5f50000000000"
                                    + "f5f5f5f5f5f5f5f5f5f5f5f5000000000000f5f5f5f5f5f5f5f5f5f5f5f50000000000f5f5f5f5f5f5f5f5f5f5f5f500"
                                    + "00000000f5f5f5f5f5f5f5f5f5f5f5f50000000000f5f5f5f5f5f5f5f5f5f5f5f50000000000f5f5f5f5f5f5f5f5f5f5"
                                    + "f5f50000000000f5f5f5f5f5f5f5f5f5f5f5f50000000000f5f5f500000000f5f5f5f5f50000000000f5000000000000"
                                    + "00f5f5f5f50000000000000000000000000000f5f5f5f5000000000000000000000000f5f5f5f5f50000000000000000"
                                    + "00f5f5f5f5f5f5f5f5f5000000000000f5f5f5f5f5f5f5f5f5f5f5f5f50000f5f5f5f5f5f5f5f5f5f5f5f5f5";
                    case "RISING_AA" ->
                            "131a000000000000f5f5f5f5f5be69350b00082b58aaf0f5f5f5f5f5f5f5bd2d00000000000000000021b0f5f5f5f5f5"
                                    + "79020000000000000000000000018df5f5f57700000000094f869a89621600000002b6f5b50000000472e7f5f5f5f5f5"
                                    + "ef7c01000030f526000007abf5f5f5f5f5f5f5f5f571000000d100000080f5f5f5f5f5f5f5f5f5f5d2000000a8370037"
                                    + "eff5f5f5f5f5f5f5f5f5f5da000000a3f1d2f1f5f5f5f5f5f5f5f5f5f5f5cb000000c1f5f5f5f5f5f5f5f5f5f5f5f5f5"
                                    + "f59c000000d6f5f5f5f5f5f5f5f5f5f5f5f5f5dd19000025f5f5f5f5f5f5f5f5f5f5f5f5f5e42e0000009bf5f5f5f5f5"
                                    + "f5f5f5f5f5f5f5e02e00000047f4f5f5f5f5f5f5f5f5f5f5f5d4240000002be5f5f5f5f5f5f5f5f5f5f5f5c015000000"
                                    + "2ce2f5f5f5f5f5f5f5f5f5f5f5a60a0000002fe2f5f5f5f5f5f5f5f5f5f5f5870200000047e9f5f5f5f5f5f5f5f5f5f5"
                                    + "f46b0000000066f2f5f5f0d3f1f5f5f5f5f5f25b0000000287f5f5bb64120036f1f5f5f5f35600000008a6ce761f0000"
                                    + "000000d0f5f5f567000000004a300000000000000036f1f5f58700000000000000000000000954abf1f5f5c706000000"
                                    + "0000000000024099e9f5f5f5f5f429000000000000003087dcf5f5f5f5f5f5f5d700000000001f76cef5f5f5f5f5f5f5"
                                    + "f5f5f5f53f001264bbf5f5f5f5f5f5f5f5f5f5f5f5f5";
                    case "TALL" ->
                            "101d000000000000f5f5f5f5f5f2b3855f7b98d9f5f5f5f5f5f5f5f38d1900000000000356e0f5f5f5f5e53b00000000"
                                    + "00000000001ad1f5f5e92c00000010597b612c00000025edf5590000006eeaf5f5f5f59d07000087b800000088f5f5f5"
                                    + "f5f5f5f58100002a4600004bf5f5f5f5f5f5f5f5e4020001060001c9f5f5f5f5f5f5f5f5f51700006d026df5f5f5f5f5"
                                    + "f5f5f5f5f5130000f5f5f5f5f5f5f5f5f5f5f5f5f2010000f5f5f5f5f5f5f5f5f5f5f5f5dd000005f5f5f5f5f5f5f5f5"
                                    + "f5f5f5f589000035f5f5f5f5f5f5f5f5f5f5f5e31400008bf5f5f5f5f5f5f5f5f5f5f54e000018e9f5f5f5f5f5f5f5f5"
                                    + "f5f585000000a0f5f5f5f5f5f5f5f5f5f5aa0300005ff5f5f5f5f5f5f5f5f5f5c30a00002eedf5f5f5f5f5f5f5f5f5d6"
                                    + "17000016d5f5f5f5f5f5f5f5f5f5e42600000bc3f5f5f5f5f5f5f5f5f5ee3a000003adf5d44f036df5f5f5f5f55b0000"
                                    + "008cea7607000005f5f5f5f587000000689e1b000000006af5f5f5bf04000018360000000027b4f5f5f5e92200000000"
                                    + "0000000f8af1f5f5f5f5650000000000000260e2f5f5f5f5f5cf04000000000039c5f5f5f5f5f5f5f54d000000001b9e"
                                    + "f5f5f5f5f5f5f5f5f50c00000776eaf5f5f5f5f5f5f5f5f5f5700d4fd4f5f5f5f5f5f5f5f5f5f5f5";
                    case "COMPACT" ->
                            "0e16000000000000f5f5f5f5d791615c71aaeef5f5f5f5f5e65a04000000000020aaf5f5f5db21000000001000000000"
                                    + "89f5f13000003db9eaf5db890d0004ca93000059f3f5f5f5f5f5b7010062430019ebf5f5f5f5f5f5f532002ab441b4f5"
                                    + "f5f5f5f5f5f5f547001ff5f5f5f5f5f5f5f5f5f5f53a0039f5f5f5f5f5f5f5f5f5f5ed100064f5f5f5f5f5f5f5f5f5f5"
                                    + "7000009ef5f5f5f5f5f5f5f5f59c010034f4f5f5f5f5f5f5f5f5a905000cc8f5f5f5f5f5f5f5f5a8050004b0f5f5f5f5"
                                    + "f5f5f5f5a40500049ff5f5f5f5f5f5f5f5a1040005a3f5f5f5f5f5f5f5f5a9040004a5f5f5f5f5f5f5f5f5bb070004a2"
                                    + "f5f3bb7441b3f5f5d916000393a35b1300000043f5f53e0000090600000000002ab3f59a00000000000006418cd5f5f5"
                                    + "f549000000135ba3e9f5f5f5f5f5f5b84074bbf3f5f5f5f5f5f5f5f5";
                    case "DESCENDING" ->
                            "131d000000000000f5f5f5f5f5be69350b00082b58aaf0f5f5f5f5f5f5f5bd2d00000000000000000021b0f5f5f5f5f5"
                                    + "79020000000000000000000000018df5f5f57700000000094f869a89621600000002b6f5b50000000472e7f5f5f5f5f5"
                                    + "ef7c01000030f526000007abf5f5f5f5f5f5f5f5f571000000d100000080f5f5f5f5f5f5f5f5f5f5d2000000a8370037"
                                    + "eff5f5f5f5f5f5f5f5f5f5da000000a3f1d2f1f5f5f5f5f5f5f5f5f5f5f5cb000000c1f5f5f5f5f5f5f5f5f5f5f5f5f5"
                                    + "f59c000000d6f5f5f5f5f5f5f5f5f5f5f5f5f5dd19000025f5f5f5f5f5f5f5f5f5f5f5f5f5e42e0000009bf5f5f5f5f5"
                                    + "f5f5f5f5f5f5f5e02e00000047f4f5f5f5f5f5f5f5f5f5f5f5d4240000002be5f5f5f5f5f5f5f5f5f5f5f5c015000000"
                                    + "2ce2f5f5f5f5f5f5f5f5f5f5f5a60a0000002fe2f5f5f5f5f5f5f5f5f5f5f5870200000047e9f5f5f5f5f5f5f5f5f5f5"
                                    + "f46b0000000066f2f5f5f5f5f5f5f5f5f5f5f25b0000000287f5f5f5f5f5f5f5f5f5f5f5f35600000008a6f5f5f5f5f5"
                                    + "f5f5f5f5f5f5f5670000000db8f5f5f5f5f5f5f5f5f5f5f5f58700000010c4f5f5f5f5f5f5f5f5f5f5f5f5c70600000a"
                                    + "bdf5f5f5f5f5f5f5f5f5f5f5f5f3290000003498cbf5f5f5f5f5f5f5f5f5f5f5d2000000000000000d4074a9dff5f5f5"
                                    + "f5f5f5f13900000000000000000000001d5087bbf3f5f5f3bb87501d000000000000000000000035f2f5f5f5f5f5f5df"
                                    + "a974400d00000000000000d0f5f5f5f5f5f5f5f5f5f5f5cb98652e030035f2";
                    default -> throw new IllegalArgumentException(name);
                };
        byte[] pixels = new byte[hex.length() / 2];
        for (int i = 0; i < pixels.length; i++)
            pixels[i] =
                    (byte)
                            ((Character.digit(hex.charAt(2 * i), 16) << 4)
                                    | Character.digit(hex.charAt(2 * i + 1), 16));
        return pixels;
    }

    static boolean matches(byte[] packed) throws Exception {
        int w = packed[0] & 255, h = packed[1] & 255;
        byte[] p = java.util.Arrays.copyOfRange(packed, 8, packed.length);
        var method =
                TripletRhythmDetector.class.getDeclaredMethod(
                        "looksLikeThree",
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, p, w, 0, 0, w, h);
    }

    @Test
    public void uprightTwoHasNoLowerBowl() throws Exception {
        assertFalse(matches(fixture("UPRIGHT")));
    }

    @Test
    public void risingFootStillBelongsToTwo() throws Exception {
        assertFalse(matches(fixture("RISING")));
    }

    @Test
    public void antialiasedRisingFootStillBelongsToTwo() throws Exception {
        assertFalse(matches(fixture("RISING_AA")));
    }

    @Test
    public void tallerHandwrittenTwoHasNoLowerBowl() throws Exception {
        assertFalse(matches(fixture("TALL")));
    }

    @Test
    public void compactTwoHasNoLowerBowl() throws Exception {
        assertFalse(matches(fixture("COMPACT")));
    }

    @Test
    public void descendingFootDoesNotInventThree() throws Exception {
        assertFalse(matches(fixture("DESCENDING")));
    }

    @Test
    public void tiltedTwoCannotRegroupThreeOrdinaryEighths() throws Exception {
        byte[] packed = fixture("RISING_AA");
        int w = 420, h = 260, gw = packed[0] & 255, gh = packed[1] & 255;
        byte[] gray = new byte[w * h];
        java.util.Arrays.fill(gray, (byte) 255);
        for (int y = 0; y < gh; y++)
            for (int x = 0; x < gw; x++) gray[(158 + y) * w + 121 + x] = packed[8 + y * gw + x];
        var notes =
                java.util.List.of(
                        new ScoreNoteEvent(0, .25f, 2, 0, 1, .4f, false, 0, 1, 2, 0, 1),
                        new ScoreNoteEvent(0, .3125f, 2, 0, 1, .4f, false, 0, 1, 2, 0, 1),
                        new ScoreNoteEvent(0, .375f, 2, 0, 1, .4f, false, 0, 1, 2, 0, 1));
        assertTrue(
                TripletRhythmDetector.apply(
                                notes,
                                java.util.List.of(new MeasureRegion(0, 1, .2f, .6f)),
                                gray,
                                w,
                                h)
                        .stream()
                        .allMatch(n -> n.tupletDivisor() == 1));
    }

    @Test
    public void rawGlyphIsNotMutated() throws Exception {
        byte[] p = fixture("RISING_AA"), old = p.clone();
        matches(p);
        assertArrayEquals(old, p);
    }
}
