// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter.android;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.util.function.BiConsumer;
import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original text objects, with no score/OCR/model input or application-specific data. */
public final class PdfSourceWordInstrumentedTest {
    private static void withPdf(
            BiConsumer<Canvas, Paint> draw, java.util.function.Consumer<PdfSourceWord> check)
            throws Exception {
        Assume.assumeTrue("PDF text search requires API35", Build.VERSION.SDK_INT >= 35);
        File file =
                File.createTempFile(
                        "original-source-word-",
                        ".pdf",
                        InstrumentationRegistry.getInstrumentation()
                                .getTargetContext()
                                .getCacheDir());
        PdfDocument document = new PdfDocument();
        try {
            var page = document.startPage(new PdfDocument.PageInfo.Builder(400, 300, 1).create());
            page.getCanvas().drawColor(Color.WHITE);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.BLACK);
            paint.setTextSize(28);
            paint.setTypeface(Typeface.MONOSPACE);
            draw.accept(page.getCanvas(), paint);
            document.finishPage(page);
            try (FileOutputStream stream = new FileOutputStream(file)) {
                document.writeTo(stream);
            }
        } finally {
            document.close();
        }
        try (ParcelFileDescriptor descriptor =
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                PdfRenderer renderer = new PdfRenderer(descriptor);
                PdfRenderer.Page page = renderer.openPage(0)) {
            check.accept(new PdfSourceWord(page));
        } finally {
            assertTrue("Disposable PDF cleanup", file.delete() || !file.exists());
        }
    }

    private static boolean isolated(PdfSourceWord source, String token) {
        return source.tokens(token.substring(0, 1), 2).stream()
                .anyMatch(value -> value.text().equals(token));
    }

    private static String trace(PdfSourceWord source) {
        StringBuilder result =
                new StringBuilder(" stream=")
                        .append(source.text().replace("\r", "\\r").replace("\n", "\\n"));
        for (String token : java.util.List.of("Q", "Q.", "Q..", "E", "E.", "E..", ".", "5"))
            for (var match : source.matches(token))
                result.append(' ')
                        .append(token)
                        .append('@')
                        .append(match.getTextStartIndex())
                        .append(match.getBounds());
        return result.toString();
    }

    @Test
    public void sameLogicalWordNeedsIndependentlySeparateGlyphRows() throws Exception {
        withPdf(
                (canvas, paint) -> canvas.drawText("Q5", 100, 140, paint),
                source -> {
                    assertFalse(source.matches("Q").isEmpty());
                    assertFalse("Same-baseline Q5 is not an isolated Q", isolated(source, "Q"));
                });
        withPdf(
                (canvas, paint) -> {
                    canvas.drawText("Q", 100, 140, paint);
                    canvas.drawText("5", 100, 180, paint);
                },
                source -> {
                    assertTrue(
                            "Original logical Q5 is the actual regression precondition",
                            source.text().contains("Q5"));
                    assertTrue("Separate glyph row proves the Q token", isolated(source, "Q"));
                });
    }

    @Test
    public void separateTextObjectsCannotSplitAnOrdinaryPrintedWord() throws Exception {
        withPdf(
                (canvas, paint) -> {
                    canvas.drawText("Q", 100, 140, paint);
                    canvas.drawText("ueen", 100 + paint.measureText("Q"), 140, paint);
                },
                source -> {
                    assertFalse(source.matches("Q").isEmpty());
                    assertFalse(
                            "Separate PDF objects remain one adjoining printed Queen",
                            isolated(source, "Q"));
                });
    }

    @Test
    public void attachedPeriodsRemainPartOfTheLiteralToken() throws Exception {
        for (String expected : java.util.List.of("Q.", "Q..", "E.."))
            withPdf(
                    (canvas, paint) -> {
                        canvas.drawText(expected, 100, 140, paint);
                        canvas.drawText("5", 100, 180, paint);
                    },
                    source -> {
                        assertTrue(
                                "The literal dotted token retains its separate row" + trace(source),
                                isolated(source, expected));
                        assertFalse(
                                "The plain prefix cannot discard its adjacent source period",
                                isolated(source, expected.substring(0, 1)));
                        var token = source.tokens(expected.substring(0, 1), 2).get(0);
                        assertEquals(
                                "Each retained glyph has independent native provenance",
                                expected.length(),
                                token.glyphs().size());
                        for (var glyph : token.glyphs()) {
                            assertTrue(
                                    source.text()
                                            .regionMatches(
                                                    glyph.sourceIndex(),
                                                    glyph.text(),
                                                    0,
                                                    glyph.text().length()));
                            assertTrue(token.bounds().contains(glyph.bounds()));
                        }
                    });
    }

    @Test
    public void logicalConcatenationCannotTurnASeparateDotIntoADurationSuffix() throws Exception {
        withPdf(
                (canvas, paint) -> {
                    canvas.drawText("Q", 100, 140, paint);
                    canvas.drawText(".", 100, 180, paint);
                },
                source -> {
                    assertFalse(
                            "The original Q must remain searchable" + trace(source),
                            source.matches("Q").isEmpty());
                    assertEquals(
                            "The original separate dot must remain searchable" + trace(source),
                            1,
                            source.matches(".").size());
                    assertFalse(
                            "Different glyph rows cannot form a dotted source token",
                            isolated(source, "Q."));
                    assertTrue(
                            "The independently bounded base Q remains valid",
                            isolated(source, "Q"));
                });
    }

    @Test
    public void attachedFirstDotAndSeparateSecondDotRetainOnlyTheProvedPrefix() throws Exception {
        withPdf(
                (canvas, paint) -> {
                    canvas.drawText("Q.", 100, 140, paint);
                    canvas.drawText(".", 100, 180, paint);
                },
                source -> {
                    assertFalse(
                            "Native Q glyph is the regression precondition" + trace(source),
                            source.matches("Q").isEmpty());
                    assertEquals(
                            "Both original dot glyphs must remain searchable" + trace(source),
                            2,
                            source.matches(".").size());
                    assertFalse(
                            "Both dots must be attached to emit the double-dotted token",
                            isolated(source, "Q.."));
                    assertTrue(
                            "The attached first dot remains part of the literal prefix"
                                    + trace(source),
                            isolated(source, "Q."));
                    assertFalse(
                            "The base prefix cannot discard its attached first dot",
                            isolated(source, "Q"));
                });
    }
}
