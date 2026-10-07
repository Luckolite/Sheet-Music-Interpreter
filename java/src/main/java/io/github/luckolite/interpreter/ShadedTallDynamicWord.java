// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** An already read tall f-word still needs each complete italic f in its original raw ink. */
final class ShadedTallDynamicWord {
    private ShadedTallDynamicWord() {}

    static boolean proved(
            PlayingTechniqueDetector.Word word, byte[] gray, int width, int height, float gap) {
        if (word == null
                || gray == null
                || width <= 0
                || height <= 0
                || gray.length != (long) width * height
                || !Float.isFinite(gap)
                || gap < 8
                || word.text() == null
                || !Float.isFinite(word.left())
                || !Float.isFinite(word.right())
                || !Float.isFinite(word.top())
                || !Float.isFinite(word.bottom())) return false;
        String token =
                ScoreDynamicsDetector.glyphLevelText(word.text())
                        .trim()
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("[.,:;]$", "");
        if (!token.matches("f{1,3}|mf")) return false;
        float glyphHeight = (word.bottom() - word.top()) * height;
        if (!Float.isFinite(glyphHeight) || glyphHeight <= gap * 3 || glyphHeight > gap * 3.6f)
            return false;
        int left = (int) Math.floor(word.left() * width),
                right = (int) Math.ceil(word.right() * width) - 1;
        int top = (int) Math.floor(word.top() * height),
                bottom = (int) Math.ceil(word.bottom() * height) - 1;
        if (left < 0
                || right >= width
                || top < 0
                || bottom >= height
                || left > right
                || top > bottom
                || right - left > gap * 8) return false;
        int[] tones = new int[(right - left + 1) * (bottom - top + 1)];
        int size = 0;
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++) tones[size++] = gray[y * width + x] & 255;
        Arrays.sort(tones);
        int paper = tones[(size - 1) * 75 / 100];
        if (paper < 96 || paper >= 185) return false;
        int cap = Math.max(1, Math.round(glyphHeight * .15f)),
                stride = Math.max(1, Math.round(gap * .1f));
        var bodies = new ArrayList<ShadedItalicFCap.Body>();
        for (int side = 0; side < 2; side++)
            for (int y = side == 0 ? top : bottom - cap;
                    y <= (side == 0 ? top + cap : bottom);
                    y += stride)
                for (int x = left; x <= right; x += stride) {
                    if ((gray[y * width + x] & 255) >= paper - 40) continue;
                    boolean alreadyProved = false;
                    for (var old : bodies)
                        if (x >= old.left()
                                && x < old.right()
                                && y >= old.top()
                                && y < old.bottom()) alreadyProved = true;
                    if (alreadyProved) continue;
                    var body = ShadedItalicFCap.wordBody(gray, width, height, gap, x, y, x, y);
                    if (body == null
                            || body.bottom() - body.top() < glyphHeight * .8f
                            || body.left() < left - gap * .2f
                            || body.right() > right + 1 + gap * .2f
                            || body.top() < top - gap * .2f
                            || body.bottom() > bottom + 1 + gap * .2f) continue;
                    boolean duplicate = false;
                    for (var old : bodies)
                        if (Math.min(old.right(), body.right()) - Math.max(old.left(), body.left())
                                >= Math.min(old.right() - old.left(), body.right() - body.left())
                                        * .8f) duplicate = true;
                    if (!duplicate) bodies.add(body);
                }
        int expected = token.equals("mf") ? 1 : token.length();
        return bodies.size() == expected;
    }
}
