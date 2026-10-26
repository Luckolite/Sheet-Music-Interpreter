// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter.android;

import io.github.luckolite.interpreter.TabTextSource;
import io.github.luckolite.interpreter.TablatureDecoder;

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
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original Canvas text only: exercises the real API35 PDF text/search adapter, without OCR. */
public final class TabPdfWordsInstrumentedTest {
    private static final int WIDTH = 800, HEIGHT = 360;

    private static Paint textPaint() {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTextSize(28);
        paint.setTypeface(Typeface.MONOSPACE);
        return paint;
    }

    private static File pdf(Consumer<Canvas> draw) throws Exception {
        File output =
                File.createTempFile(
                        "original-tab-text-",
                        ".pdf",
                        InstrumentationRegistry.getInstrumentation()
                                .getTargetContext()
                                .getCacheDir());
        PdfDocument document = new PdfDocument();
        try {
            var page =
                    document.startPage(new PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, 1).create());
            page.getCanvas().drawColor(Color.WHITE);
            draw.accept(page.getCanvas());
            document.finishPage(page);
            try (FileOutputStream stream = new FileOutputStream(output)) {
                document.writeTo(stream);
            }
        } catch (Exception | Error failure) {
            output.delete();
            throw failure;
        } finally {
            document.close();
        }
        return output;
    }

    @Test
    public void realSearchRetainsOnlyWholeUppercaseRhythmLabels() throws Exception {
        Assume.assumeTrue("PDF text search requires API35", Build.VERSION.SDK_INT >= 35);
        File input =
                pdf(
                        canvas -> {
                            Paint paint = textPaint();
                            canvas.drawText("Queen Energy Song Whole Half Theme", 20, 50, paint);
                            canvas.drawText("Q E S W H T Q. E..", 20, 140, paint);
                            canvas.drawText("q e s w h t", 20, 230, paint);
                        });
        try (ParcelFileDescriptor descriptor =
                        ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY);
                PdfRenderer renderer = new PdfRenderer(descriptor);
                PdfRenderer.Page page = renderer.openPage(0)) {
            String extracted =
                    page.getTextContents().stream()
                            .map(item -> item.getText())
                            .collect(Collectors.joining("\n"));
            assertTrue(
                    "Generated PDF must expose original text rather than trigger OCR",
                    extracted.contains("Queen"));
            assertTrue(
                    "Raw substring search must include the title as a negative control",
                    page.searchText("Q").size() >= 3);
            var rhythm =
                    TabPdfWords.read(page).stream()
                            .filter(word -> word.text().matches("[QESWHT]\\.{0,2}"))
                            .toList();
            Map<String, Long> counts =
                    rhythm.stream()
                            .collect(
                                    Collectors.groupingBy(
                                            TablatureDecoder.Word::text, Collectors.counting()));
            StringBuilder trace = new StringBuilder();
            var source = new PdfSourceWord(page);
            trace.append(source.text().replace("\r", "\\r").replace("\n", "\\n"));
            for (String token : List.of("Q", "Q.", "E", "E.", "E..", "."))
                for (var match : source.matches(token))
                    trace.append(' ')
                            .append(token)
                            .append('@')
                            .append(match.getTextStartIndex())
                            .append(match.getBounds());
            assertEquals(
                    trace.toString(),
                    Map.of(
                            "Q", 1L, "E", 1L, "S", 1L, "W", 1L, "H", 1L, "T", 1L, "Q.", 1L, "E..",
                            1L),
                    counts);
            for (var word : rhythm) {
                assertTrue(
                        "Title substring or lowercase row leaked: " + word,
                        word.top() * HEIGHT > 100);
                assertTrue(
                        "Title substring or lowercase row leaked: " + word,
                        word.bottom() * HEIGHT < 160);
            }
        } finally {
            assertTrue("Disposable original PDF cleanup", input.delete() || !input.exists());
        }
    }

    @Test
    public void actualPdfSearchWordsSupplyDurationsAfterNativeFretsSucceed() throws Exception {
        Assume.assumeTrue("PDF text search requires API35", Build.VERSION.SDK_INT >= 35);
        File input =
                pdf(
                        canvas -> {
                            Paint paint = textPaint();
                            String[] labels = {"Q", "E", "S", "W"};
                            for (int index = 0; index < labels.length; index++) {
                                int x = 100 + index * 100;
                                canvas.drawText(labels[index], x, 140, paint);
                                canvas.drawText("5", x, 180, paint);
                            }
                        });
        try (ParcelFileDescriptor descriptor =
                        ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY);
                PdfRenderer renderer = new PdfRenderer(descriptor);
                PdfRenderer.Page page = renderer.openPage(0)) {
            var words = TabPdfWords.read(page);
            var frets = words.stream().filter(word -> word.text().equals("5")).toList();
            assertEquals("Native fret extraction is the regression precondition", 4, frets.size());
            float stringY = (frets.get(0).top() + frets.get(0).bottom()) * HEIGHT / 2;
            var row = new TablatureDecoder.Staff(stringY, 20, -1, List.of(), List.of(20f, 780f));
            var decoded = TablatureDecoder.withWords(List.of(row), words, WIDTH, HEIGHT).get(0);
            assertEquals(4, decoded.frets().size());
            StringBuilder diagnostic = new StringBuilder();
            for (var content : page.getTextContents())
                diagnostic
                        .append(" content=")
                        .append(content.getText().replace("\n", "\\n").replace("\r", "\\r"))
                        .append(" bounds=")
                        .append(content.getBounds());
            for (String token : List.of("Q", "E", "S", "W", "5"))
                for (var match : page.searchText(token)) {
                    diagnostic
                            .append(" search=")
                            .append(token)
                            .append('@')
                            .append(match.getTextStartIndex())
                            .append(' ')
                            .append(match.getBounds());
                }
            diagnostic.append(" words=").append(words).append(" decoded=").append(decoded.frets());
            double[] expected = {1, .5, .25, 4};
            for (int index = 0; index < expected.length; index++)
                assertEquals(
                        "Source rhythm column " + index + diagnostic,
                        expected[index],
                        decoded.frets().get(index).duration(),
                        0);
            assertFalse(
                    "Native frets alone must not hide missing rhythm text",
                    TabTextSource.needsRhythmOcr(decoded));
        } finally {
            assertTrue("Disposable original PDF cleanup", input.delete() || !input.exists());
        }
    }

    @Test
    public void separateDotsCannotSuppressTheActualIsolatedRhythmPrefix() throws Exception {
        Assume.assumeTrue("PDF text search requires API35", Build.VERSION.SDK_INT >= 35);
        for (String expected : List.of("Q", "Q.")) {
            File input =
                    pdf(
                            canvas -> {
                                Paint paint = textPaint();
                                canvas.drawText(expected, 100, 140, paint);
                                canvas.drawText(".", 100, 180, paint);
                            });
            try (ParcelFileDescriptor descriptor =
                            ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY);
                    PdfRenderer renderer = new PdfRenderer(descriptor);
                    PdfRenderer.Page page = renderer.openPage(0)) {
                String stream =
                        page.getTextContents().stream()
                                .map(item -> item.getText())
                                .collect(Collectors.joining());
                assertFalse(
                        "Original Q glyph must remain searchable: " + stream,
                        page.searchText("Q").isEmpty());
                assertEquals(
                        "Every original dot must remain searchable: " + stream,
                        expected.length(),
                        page.searchText(".").size());
                var rhythm =
                        TabPdfWords.read(page).stream()
                                .filter(word -> word.text().startsWith("Q"))
                                .map(TablatureDecoder.Word::text)
                                .toList();
                StringBuilder trace =
                        new StringBuilder(stream.replace("\r", "\\r").replace("\n", "\\n"));
                for (String token : List.of("Q", "Q.", "Q..", "."))
                    for (var match : page.searchText(token))
                        trace.append(' ')
                                .append(token)
                                .append('@')
                                .append(match.getTextStartIndex())
                                .append(match.getBounds());
                assertEquals(
                        "Only the physically attached prefix is retained " + trace,
                        List.of(expected),
                        rhythm);
            } finally {
                assertTrue("Disposable original PDF cleanup", input.delete() || !input.exists());
            }
        }
    }
}
