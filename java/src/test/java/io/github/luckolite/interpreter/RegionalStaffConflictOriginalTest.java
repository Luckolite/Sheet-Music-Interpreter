// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.assertEquals;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import org.junit.Ignore;
import org.junit.Test;

/** Sourcefree gate-boundary originals, not full findStaffs or note-ownership acceptance. */
public final class RegionalStaffConflictOriginalTest {
    private static final int[] PRIOR = {80, 96, 112, 128, 144};
    private static final int[] SHIFTED = {112, 128, 144, 160, 176};
    private static final int[] SEPARATE = {224, 240, 256, 272, 288};

    private void check(String name, int[] rows, float gap, boolean alias) throws IOException {
        byte[] gray;
        try (var raw = getClass().getResourceAsStream("/regional-staff/" + name + ".gray.gz")) {
            if (raw == null) throw new IOException("Missing original raster: " + name);
            try (var gz = new GZIPInputStream(raw)) {
                gray = gz.readNBytes(512 * 400 + 1);
                if (gray.length != 512 * 400 || gz.read() != -1)
                    throw new IOException("Incorrect original dimensions");
            }
        }
        var seed = new RegionalStaffSeeds.Seed(rows[0], rows[4], gap, 0);
        var evidence = new RegionalStaffSeeds.Evidence(seed, rows, 0);
        assertEquals(
                alias,
                RegionalStaffConflict.unsupportedAlias(
                        gray, 512, 400, evidence, 16, (line, x) -> PRIOR[line]));
    }

    @Test
    public void sharedThreeWithSeparateLedgersCannotAddAlias() throws IOException {
        check("shared_three_separate_ledger_runs", SHIFTED, 16, true);
    }

    @Test
    public void independentFaintFiveRetainsOldPath() throws IOException {
        check("genuine_faint_separate_five", SEPARATE, 16, false);
    }

    @Test
    public void independentShortFiveRetainsOldPath() throws IOException {
        check("genuine_short_separate_five", SEPARATE, 16, false);
    }

    @Test
    public void equalPhaseHorizontalDomainsAreNotNewlyVetoed() throws IOException {
        check("disjoint_horizontal_same_phase", PRIOR, 16, false);
    }

    @Test
    public void adjacentOwnFiveRetainsOldPath() throws IOException {
        check("adjacent_independent_staffs", new int[] {160, 176, 192, 208, 224}, 16, false);
    }

    @Test
    public void distinctStraightReferenceRetainsOldPath() throws IOException {
        check("brief_incidental_rule_contact", SEPARATE, 16, false);
    }

    @Test
    public void brokenOuterFragmentsDoNotJointlySupportAlias() throws IOException {
        check("broken_outer_support", SHIFTED, 16, true);
    }

    @Test
    public void separateShortScaleSeventeenRetainsOldPath() throws IOException {
        check("separate_scaled_short_staff", new int[] {222, 239, 256, 273, 290}, 17, false);
    }

    @Test
    public void sourceRowsAreDefensivelyRetained() {
        int[] rows = SHIFTED.clone();
        var e =
                new RegionalStaffSeeds.Evidence(
                        new RegionalStaffSeeds.Seed(112, 176, 16, 0), rows, 0);
        rows[0] = 0;
        int[] copy = e.rows();
        copy[1] = 0;
        assertEquals(112, e.rows()[0]);
        assertEquals(128, e.rows()[1]);
    }

    @Ignore(
            "Held broader ambiguity: this narrow gate does not reject jointly supported seven-line windows")
    @Test
    public void sevenLongRulesDoNotCreateIndependentOwnership() throws IOException {
        check("seven_long_shared_lattice", SHIFTED, 16, true);
    }
}
