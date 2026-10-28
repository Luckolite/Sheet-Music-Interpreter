package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original generated core and closed taper; no score pixels. */
public class ClosedDotFringeOriginalTest {
    static final int W = 1000, H = 1000;
    static final float GAP = 14.25f;

    static byte[] image(boolean large) {
        byte[] a = new byte[W * H];
        Arrays.fill(a, (byte) 250);
        for (int y = large ? 228 : 229; y <= (large ? 235 : 234); y++)
            for (int x = large ? 478 : 479; x <= (large ? 485 : 484); x++) {
                double d = (x - 481.5) * (x - 481.5) + (y - 231.5) * (y - 231.5);
                if (d <= (large ? 14.5 : 8.5))
                    a[y * W + x] = (byte) (d <= 2.5 ? 100 : (large ? 175 + d * 2 : 170 + d * 5));
            }
        return a;
    }

    static int detect(byte[] a, float x, float y) {
        return NoteArticulationDetector.detect(
                new byte[a.length],
                a,
                W,
                H,
                List.of(new NoteArticulationDetector.Anchor(x, y, GAP, 0)))[0];
    }

    @Test
    public void twelvePixelCoreNeedsItsLiteralCompleteFringe() {
        assertEquals(4, detect(image(false), 481.5f, 260));
    }

    @Test
    public void largerClosedRadialTaperAlsoQualifies() {
        assertEquals(4, detect(image(true), 481.5f, 260));
    }

    @Test
    public void secondDarkBasinRefuses() {
        byte[] a = image(true);
        a[228 * W + 481] = 100;
        assertEquals(0, detect(a, 481.5f, 260));
    }

    @Test
    public void nonmonotoneOutwardPocketRefuses() {
        byte[] a = image(true);
        a[228 * W + 481] = (byte) 155;
        assertEquals(0, detect(a, 481.5f, 260));
    }

    @Test
    public void unclosedTailRefuses() {
        byte[] a = image(false);
        for (int x = 485; x < 520; x++) a[231 * W + x] = (byte) 200;
        assertEquals(0, detect(a, 481.5f, 260));
    }

    @Test
    public void onePixelAndLineNoiseRefuse() {
        byte[] a = new byte[W * H];
        Arrays.fill(a, (byte) 250);
        a[231 * W + 481] = 100;
        assertEquals(0, detect(a, 481.5f, 260));
        a[231 * W + 480] = a[231 * W + 482] = 100;
        assertEquals(0, detect(a, 481.5f, 260));
    }

    @Test
    public void noFringeCannotInventPixels() {
        byte[] a = image(false);
        for (int i = 0; i < a.length; i++) if ((a[i] & 255) >= 155) a[i] = (byte) 250;
        assertEquals(0, detect(a, 481.5f, 260));
    }

    @Test
    public void sameBodyBesideHeadRemainsAugmentation() {
        assertEquals(0, detect(image(false), 466, 231.5f));
    }

    @Test
    public void punctuationInThreeLetterWordStillRefuses() {
        byte[] a = image(false);
        for (int l : new int[] {442, 454, 466})
            for (int y = 220; y <= 233; y++)
                for (int x = l; x <= l + 7; x++)
                    if (y <= 221 || y >= 232 || x <= l + 1 || x >= l + 6) a[y * W + x] = 30;
        assertEquals(0, detect(a, 481.5f, 260));
    }

    @Test
    public void fermataRoofStillOwnsDot() {
        byte[] a = image(false);
        for (int x = 471; x <= 492; x++) {
            int y = 213 + Math.round((x - 481.5f) * (x - 481.5f) * .075f);
            a[y * W + x] = a[(y + 1) * W + x] = 30;
        }
        assertEquals(0, detect(a, 481.5f, 260));
    }

    @Test
    public void ambiguousTwoHeadsRefuses() {
        byte[] a = image(false);
        assertArrayEquals(
                new int[] {0, 0},
                NoteArticulationDetector.detect(
                        new byte[a.length],
                        a,
                        W,
                        H,
                        List.of(
                                new NoteArticulationDetector.Anchor(479, 260, GAP, 0),
                                new NoteArticulationDetector.Anchor(484, 260, GAP, 0))));
    }

    @Test
    public void inputPlanesRemainExact() {
        byte[] a = image(false), b = a.clone(), labels = new byte[a.length];
        assertEquals(
                4,
                NoteArticulationDetector.detect(
                        labels,
                        a,
                        W,
                        H,
                        List.of(new NoteArticulationDetector.Anchor(481.5f, 260, GAP, 0)))[0]);
        assertArrayEquals(b, a);
        assertArrayEquals(new byte[a.length], labels);
    }

    @Test
    public void clippedPhysicalImageCannotSupplyCompleteFringe() {
        byte[] a = image(false), b = new byte[a.length];
        Arrays.fill(b, (byte) 250);
        for (int y = 228; y <= 235; y++)
            for (int x = 478; x <= 485; x++) if (x - 478 >= 0) b[y * W + x - 478] = a[y * W + x];
        assertEquals(0, detect(b, 3.5f, 260));
    }

    @Test
    public void acceptedSemanticHeadCannotBeBorrowed() {
        byte[] a = image(false), labels = new byte[a.length];
        for (int i = 0; i < a.length; i++)
            if ((a[i] & 255) < 155) labels[i] = OmrMeasurePostProcessor.NOTEHEAD;
        assertArrayEquals(
                new int[] {0, 0},
                NoteArticulationDetector.detect(
                        labels,
                        a,
                        W,
                        H,
                        List.of(
                                new NoteArticulationDetector.Anchor(481.5f, 231.5f, GAP, 0),
                                new NoteArticulationDetector.Anchor(481.5f, 260, GAP, 0))));
    }

    static List<?> acquire(byte[] a, int[] offsets, long cells, long pairs) throws Exception {
        Class<?> g =
                Class.forName("io.github.luckolite.interpreter.NoteArticulationDetector$Glyph");
        var ctor = g.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        List<Object> cores = new ArrayList<>();
        List<NoteArticulationDetector.Anchor> ns = new ArrayList<>();
        for (int offset : offsets) {
            List<Integer> ps = new ArrayList<>();
            for (int y = 230; y <= 233; y++)
                for (int x = 480 + offset; x <= 483 + offset; x++)
                    if ((a[y * W + x] & 255) < 155) ps.add(y * W + x);
            cores.add(
                    ctor.newInstance(
                            480 + offset,
                            230,
                            483 + offset,
                            233,
                            ps.size(),
                            ps.stream().mapToInt(Integer::intValue).toArray()));
            ns.add(new NoteArticulationDetector.Anchor(481.5f + offset, 260, GAP, 0));
        }
        var m =
                NoteArticulationDetector.class.getDeclaredMethod(
                        "closedDotFringes",
                        List.class,
                        Map.class,
                        List.class,
                        byte[].class,
                        int.class,
                        int.class,
                        long.class,
                        long.class);
        m.setAccessible(true);
        return (List<?>) m.invoke(null, cores, new IdentityHashMap<>(), ns, a, W, H, cells, pairs);
    }

    @Test
    public void exactLocalFundingAndOneBelowRefusal() throws Exception {
        byte[] a = image(false);
        assertEquals(1, acquire(a, new int[] {0}, 1412, 2).size());
        assertEquals(0, acquire(a, new int[] {0}, 1411, 2).size());
        assertEquals(0, acquire(a, new int[] {0}, 1412, 1).size());
    }

    @Test
    public void exhaustionDropsAlreadyCertifiedAdditionsAtomically() throws Exception {
        byte[] a = image(false);
        for (int y = 228; y <= 235; y++)
            for (int x = 478; x <= 485; x++) a[y * W + x + 60] = a[y * W + x];
        assertEquals(2, acquire(a, new int[] {0, 60}, 2824, 8).size());
        assertEquals(0, acquire(a, new int[] {0, 60}, 2823, 8).size());
        assertEquals(0, acquire(a, new int[] {0, 60}, 2824, 7).size());
    }

    @Test
    public void ownerOrderDoesNotChangeCertificate() throws Exception {
        byte[] a = image(false);
        for (int y = 228; y <= 235; y++)
            for (int x = 478; x <= 485; x++) a[y * W + x + 60] = a[y * W + x];
        assertEquals(2, acquire(a, new int[] {60, 0}, 2824, 8).size());
    }
}
