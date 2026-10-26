// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter.android;

import io.github.luckolite.interpreter.TabTextSource;
import io.github.luckolite.interpreter.TablatureDecoder;
import android.graphics.pdf.PdfRenderer;
import android.os.Build;
import java.util.*;

/** Android text-layer adapter. Raster geometry still decides which symbols belong to tablature. */
public final class TabPdfWords {
    private TabPdfWords() {}

    public static List<TablatureDecoder.Word> read(PdfRenderer.Page page) {
        if (Build.VERSION.SDK_INT < 35) return List.of();
        try {
            var source = new PdfSourceWord(page);
            var words = new ArrayList<TablatureDecoder.Word>();
            float width = page.getWidth(), height = page.getHeight();
            for (String letter : List.of("Q", "E", "S", "W", "H", "T"))
                if (source.text().contains(letter))
                    for (var token : source.tokens(letter, 2)) {
                        var box = token.bounds();
                        float left = box.left / width,
                                top = box.top / height,
                                right = box.right / width,
                                bottom = box.bottom / height;
                        if (left >= 0
                                && top >= 0
                                && right <= 1
                                && bottom <= 1
                                && left < right
                                && top < bottom)
                            words.add(
                                    new TablatureDecoder.Word(
                                            token.text(), left, top, right, bottom));
                    }
            for (String token : TabTextSource.tokens(source.text())) {
                if (token.matches("[QESWHT]\\.{0,2}")) continue;
                for (var match : source.matches(token)) {
                    for (var box : match.getBounds()) {
                        float left = box.left / width,
                                top = box.top / height,
                                right = box.right / width,
                                bottom = box.bottom / height;
                        if (left >= 0
                                && top >= 0
                                && right <= 1
                                && bottom <= 1
                                && left < right
                                && top < bottom)
                            words.add(new TablatureDecoder.Word(token, left, top, right, bottom));
                    }
                }
            }
            return TabTextSource.disjoint(words);
        } catch (RuntimeException unavailable) {
            android.util.Log.w(
                    "MusicSheetsOMR", "PDF text layer unavailable; using tab OCR", unavailable);
            return List.of();
        }
    }
}
