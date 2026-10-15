package io.github.luckolite.interpreter;

import java.util.Arrays;

public final class CodaGlyphInkTest {
    @org.junit.Test
    public void originalControls() {
        main(new String[0]);
    }

    static byte[] draw(int n, String kind) {
        byte[] a = new byte[n * n];
        Arrays.fill(a, (byte) 255);
        double c = (n - 1) / 2.0, r = n * .32, t = n * .045;
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++) {
                double dx = x - c, dy = y - c, d = Math.hypot(dx, dy);
                boolean ring = Math.abs(d - r) <= t,
                        vertical = Math.abs(dx) <= t,
                        horizontal = Math.abs(dy) <= t;
                boolean box =
                        (Math.abs(Math.abs(dx) - r) <= t && Math.abs(dy) <= r + t)
                                || (Math.abs(Math.abs(dy) - r) <= t && Math.abs(dx) <= r + t);
                boolean ink =
                        switch (kind) {
                            case "coda" -> ring || vertical || horizontal;
                            case "circle" -> ring;
                            case "plus" -> vertical || horizontal;
                            case "theta" -> ring || horizontal;
                            case "window" -> box || vertical || horizontal;
                            case "broken" ->
                                    (ring || vertical || horizontal)
                                            && !(dx > 0 && dy < 0 && Math.abs(d - r) <= t * 2);
                            default -> false;
                        };
                if (ink) a[y * n + x] = 0;
            }
        return a;
    }

    public static void main(String[] args) {
        int checks = 0;
        for (int n : new int[] {31, 41, 51})
            for (String kind :
                    new String[] {"coda", "circle", "plus", "theta", "window", "broken"}) {
                boolean got = CodaGlyphInk.matches(draw(n, kind), n, n, 0, 0, n - 1, n - 1, n / 3f),
                        want = kind.equals("coda");
                if (got != want) throw new AssertionError(kind + " size=" + n + " got=" + got);
                checks++;
            }
        System.out.println("PASS " + checks + " original synthetic glyph controls");
    }
}
