// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent raw staff/signature rasters; actual font matcher, no authored OCR/font answers. */
public class IndependentMeterCopiesTest {
    static final int W = 700, H = 440;
    static final File FONT = new File("java/assets/Bravura.otf");

    record Scene(byte[] gray, List<MeterCopyFontEvidence.Copy> copies) {}

    static Scene scene(String upper, String lower) throws Exception {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int top : new int[] {90, 250})
            for (int k = 0; k < 5; k++)
                for (int x = 20; x < 680; x++) gray[(top + 16 * k) * W + x] = 0;
        var image = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
        var g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        g.setColor(Color.BLACK);
        g.setFont(Font.createFont(Font.TRUETYPE_FONT, FONT).deriveFont(62f));
        g.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        print(g, upper, 90);
        print(g, lower, 250);
        g.dispose();
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                gray[y * W + x] =
                        (byte)
                                Math.min(
                                        gray[y * W + x] & 255,
                                        image.getRaster().getSample(x, y, 0));
        return new Scene(gray, List.of(copy(0, 90, 260), copy(0, 250, 260)));
    }

    static void print(Graphics2D g, String fraction, int top) {
        if (fraction.isBlank()) return;
        var parts = fraction.split("/");
        for (int half = 0; half < 2; half++) {
            String s = String.valueOf((char) (0xe080 + Integer.parseInt(parts[half])));
            var b =
                    g.getFont()
                            .createGlyphVector(g.getFontRenderContext(), s)
                            .getPixelBounds(null, 0, 0);
            g.drawString(
                    s, 260 + (42 - b.width) / 2 - b.x, top + 32 * half + (32 - b.height) / 2 - b.y);
        }
    }

    static MeterCopyFontEvidence.Copy copy(int owner, int top, int x) {
        return new MeterCopyFontEvidence.Copy(
                owner, new MeterChangeDetector.Crop(x, top - 3, x + 42, top + 69, top, 16));
    }

    static Set<ScoreMeterChange> choices() {
        return Set.of(new ScoreMeterChange(0, 1, 4), new ScoreMeterChange(0, 4, 4));
    }

    static ScoreMeterChange resolve(Scene s) throws Exception {
        var matcher = new MeterFontMatcher(FONT);
        byte[] before = s.gray.clone();
        var result =
                MeterCopyFontEvidence.resolve(
                        0,
                        choices(),
                        s.copies,
                        s.gray,
                        W,
                        H,
                        (p, w, h, m, f, g) -> {
                            var r = matcher.read(p, w, h, m, f, g);
                            return new MeterCopyFontEvidence.Reading(
                                    r.text(), r.topScore(), r.bottomScore());
                        });
        assertArrayEquals(before, s.gray);
        return result;
    }

    @Test
    public void independentStrongOneQuarterCopiesResolveOptically() throws Exception {
        assertEquals(new ScoreMeterChange(0, 1, 4), resolve(scene("1/4", "1/4")));
    }

    @Test
    public void genuineFourFourCopiesCannotBeReplacedByOneQuarter() throws Exception {
        assertEquals(new ScoreMeterChange(0, 4, 4), resolve(scene("4/4", "4/4")));
    }

    @Test
    public void differingStrongCopiesRemainUnresolved() throws Exception {
        assertNull(resolve(scene("1/4", "4/4")));
    }

    @Test
    public void oneGoodOneBlankCannotCorroborate() throws Exception {
        assertNull(resolve(scene("1/4", "")));
    }

    @Test
    public void duplicateSamePhysicalCropCannotVoteTwice() throws Exception {
        var s = scene("1/4", "1/4");
        assertNull(resolve(new Scene(s.gray, List.of(s.copies.get(0), s.copies.get(0)))));
    }

    @Test
    public void shiftedSamePhysicalRecropCannotVoteTwice() throws Exception {
        var s = scene("1/4", "1/4");
        assertNull(resolve(new Scene(s.gray, List.of(copy(0, 90, 260), copy(0, 91, 261)))));
    }

    @Test
    public void unrelatedHorizontalSlotCannotCorroborate() throws Exception {
        var s = scene("1/4", "1/4");
        assertNull(resolve(new Scene(s.gray, List.of(copy(0, 90, 260), copy(0, 250, 380)))));
    }

    @Test
    public void differentMeasureOwnerCannotCorroborate() throws Exception {
        var s = scene("1/4", "1/4");
        assertNull(resolve(new Scene(s.gray, List.of(copy(0, 90, 260), copy(1, 250, 260)))));
    }

    @Test
    public void absentStaffRulesCannotCorroborate() throws Exception {
        var s = scene("1/4", "1/4");
        for (int y = 240; y < 330; y++)
            for (int x = 0; x < W; x++) if (x < 250 || x > 312) s.gray[y * W + x] = (byte) 255;
        assertNull(resolve(s));
    }

    @Test
    public void weakFaintLowerSignatureCannotPromoteOneQuarter() throws Exception {
        var s = scene("1/4", "1/4");
        for (int y = 247; y < 319; y++)
            for (int x = 260; x < 302; x++)
                if ((y - 250) % 16 != 0)
                    s.gray[y * W + x] = (byte) Math.max(s.gray[y * W + x] & 255, 200);
        assertNull(resolve(s));
    }

    @Test
    public void thirdUnprovedPhysicalCopyCannotBeIgnored() throws Exception {
        var s = scene("1/4", "1/4");
        assertNull(
                resolve(
                        new Scene(
                                s.gray,
                                List.of(copy(0, 90, 260), copy(0, 250, 260), copy(0, 340, 260)))));
    }

    @Test
    public void unanimousFontCannotInventChoiceNotPresentInConflict() throws Exception {
        assertNull(resolve(scene("2/4", "2/4")));
    }
}
