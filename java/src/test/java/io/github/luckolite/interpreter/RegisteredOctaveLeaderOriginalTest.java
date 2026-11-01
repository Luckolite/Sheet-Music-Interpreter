// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.io.*;
import java.util.zip.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** RegisteredTenutoStaffOwnershipTest's proved physicalstaff and originallicensedG0word.
 * Independently authored leadergeometry; neither realscorepixels nor fontfiles are bundled. */
public class RegisteredOctaveLeaderOriginalTest {
    static final int W = 1200, H = 1000, G = 16;
    static final String WORD =
            "H4sIAAAAAAAC/5WVXUhUQRTHj7Wbm4ofpEmuZS26pkQSaWWIfVtZpqYJCQk+ZJQ92AcRGQuFRvkR0kMJQYVpmJpkFlaYlJZJiBSBgWGahhpoJmW5pk5zztw7u9tDe52HPed/5v+7O3Nn5g5jtvY6L8m8cJ67MfJw5QSbRZu6uhxszbdkRjPZuQqJ+TnlbbUWX0wTJjWS7WRf30fiSwSKbJ5F8ViOpVyehAlr9S5/Q3Bac/1iIUcXodmnX3nQC1RzP/D6HoBCrEwWAcRjMrwJllx6eMPEDcJ7iCZ4XA4iCOV5njxTq40AT3n4Hg4bxnhsAPCm+g83Qm9LdDPKJJ4MAOynShGsxpAMgaMYPwHspPpd8VobJBqDciNPZgwUGFtDvY/U578DyKckT6CFEg1AmYGZGUIxtEIchlhYMEWGCoDnlBwVaOAvhXxDshjTOPDEsFX3nv/2AxwQjiOgF+ZzykbY+0d0ZKBwH8E0C2CcsTo4heImQIVwmGCtSCrVTZQyjvIx5bnUdQGgm/0MWkYdxwBaqdoEcELZSnIDruxmrMUHs8Rp6roD8JJluzSRSAfoxTgTDVCrTC5Zsl75FgOtjDLvVoDqRpccIVIVtMAI8FVBu/Tg0HzK1HfNF9YSEPZbfTnwhIf7xgIwydU47UAmDMoOvrB617eK4EsS0zeQ59FyELaohm/7HNB7dgfDDHBFzSdC6HjVs3Dw76AFHj7j5Thevx4buh1SbKJnt5tnaicbdeEmnFMNHTjveLv5Rlo1HdWyOWhe8Zm1L7WxpVrIGiLX4Uka3ibRcA2k1YhOQ5f4PuVIdsg5WkrGs6osUdEW5+gOMn6UOldBHzhHg/+ZmdUk0GbnqCvtHrtCpkB7naP+6Eu3K2SJUz/tHI1FY7RdIcrxvf2nFaPRY0TqEZqB76AGdIxGfFLqRPrTOk37sF7HrbqL4nYaow+c7pbG66bKA+0x1191VFloCCFNmi+53jT7b0ToZets7taha5kRRne9nzkpv0079RcavB4G1gcAAA==";

    static class Page {
        final byte[] g = new byte[W * H], l = new byte[W * H];
        final List<NoteArticulationDetector.Anchor> n = new ArrayList<>();

        Page() {
            Arrays.fill(g, (byte) 255);
        }

        void ink(int x, int y, int label) {
            g[y * W + x] = 0;
            l[y * W + x] = (byte) label;
        }

        void head(int x, int y) {
            for (int dy = -6; dy <= 6; dy++)
                for (int dx = -9; dx <= 9; dx++)
                    if (dx * dx / 81.0 + dy * dy / 36.0 <= 1)
                        ink(x + dx, y + dy, OmrMeasurePostProcessor.NOTEHEAD);
            var track =
                    StaffPitchTrack.fromVerifiedSamples(
                            List.of(new float[] {80, 304, G}, new float[] {950, 304, G}));
            n.add(new NoteArticulationDetector.Anchor(x, y, G, 0, track, 240, 304, 0, 1));
        }

        int[] detect() {
            byte[] gg = g.clone(), ll = l.clone();
            var v = NoteArticulationDetector.detect(l, g, W, H, n);
            assertArrayEquals(gg, g);
            assertArrayEquals(ll, l);
            return v;
        }
    }

    static Page page(boolean word, boolean genuine) throws Exception {
        Page p = new Page();
        for (int k = 0; k < 5; k++)
            for (int x = 80; x <= 950; x++) {
                p.ink(x, 240 + k * G, OmrMeasurePostProcessor.STAFF);
                p.g[(240 + k * G) * W + x] = (byte) 200;
            }
        if (word) {
            byte[] b;
            try (var in =
                    new GZIPInputStream(
                            new ByteArrayInputStream(Base64.getDecoder().decode(WORD)))) {
                b = in.readAllBytes();
            }
            for (int y = 0; y < 34; y++)
                for (int x = 0; x < 59; x++) {
                    p.g[(190 + y) * W + 60 + x] = b[y * 59 + x];
                    if ((b[y * 59 + x] & 255) < 155)
                        p.l[(190 + y) * W + 60 + x] = (byte) OmrMeasurePostProcessor.SYMBOL;
                }
        }
        for (int x = 130; x <= 550; x++)
            if ((x - 130) % 15 < 12) {
                p.ink(x, 224, OmrMeasurePostProcessor.SYMBOL);
                p.ink(x, 225, OmrMeasurePostProcessor.SYMBOL);
            }
        for (int x : new int[] {220, 260, 300, 340}) p.head(x, 264);
        if (genuine) {
            p.head(500, 240);
            for (int x = 490; x <= 510; x++)
                for (int y = 199; y <= 201; y++) p.ink(x, y, OmrMeasurePostProcessor.SYMBOL);
        }
        return p;
    }

    static void premises(Page p, boolean word) {
        assertTrue(12 >= G * .65f);
        assertTrue((264 - 224.5f) / G <= 3f);
        assertTrue(15 < G * 1.15f);
        var input = List.of(new ScoreNoteEvent(0, 300f / W, 5, 0, 1, 264f / H, false, 0, 0, 2, 1));
        var octave =
                OctaveMarkDetector.apply(
                        List.of(),
                        List.of(new PlayingTechniqueDetector.Staff(240, 304, G, 0, 1)),
                        List.of(new MeasureRegion(0, 1, 0, 1)),
                        input,
                        p.g,
                        W,
                        H);
        assertEquals(
                "ordinary complete octaveword/dash admission",
                word ? 1 : 0,
                octave.get(0).octaveShift());
    }

    @Test
    public void acceptedOctaveLeaderHasNoTenutoOwners() throws Exception {
        var p = page(true, false);
        premises(p, true);
        assertArrayEquals(new int[] {0, 0, 0, 0}, p.detect());
    }

    @Test
    public void unknownDashRolePreservesFiniteMarkOwners() throws Exception {
        var p = page(false, false);
        premises(p, false);
        assertArrayEquals(new int[] {8, 8, 8, 8}, p.detect());
    }

    @Test
    public void separateGenuineTenutoIsNeverDashMember() throws Exception {
        var p = page(true, true);
        premises(p, true);
        assertEquals(8, p.detect()[4] & 8);
    }
}
