// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.assertFalse;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import org.junit.Test;

/** Prefrozen finite-domain and malformed-prior originals; no private score data. */
public final class RegionalStaffConflictDomainOriginalTest {
    private byte[] raster(String name) throws IOException {
        try (var raw = getClass().getResourceAsStream("/regional-staff/" + name + ".gray.gz")) {
            if (raw == null) throw new IOException("Missing original " + name);
            try (var gz = new GZIPInputStream(raw)) {
                byte[] b = gz.readNBytes(512 * 400 + 1);
                if (b.length != 512 * 400 || gz.read() != -1)
                    throw new IOException("Invalid raster length");
                return b;
            }
        }
    }

    private void check(String file, float[] prior) throws IOException {
        int[] rows = {112, 128, 144, 160, 176};
        var seed = new RegionalStaffSeeds.Seed(112, 176, 16, 0);
        assertFalse(
                RegionalStaffConflict.unsupportedAlias(
                        raster(file),
                        512,
                        400,
                        new RegionalStaffSeeds.Evidence(seed, rows, 0),
                        16,
                        (line, x) -> prior[line]));
    }

    @Test
    public void independentFragmentedRightStaffSurvives() throws IOException {
        check("disjoint_fragmented_five", new float[] {80, 96, 112, 128, 144});
    }

    @Test
    public void duplicateSupportedBandsCannotCreateProof() throws IOException {
        check("shared_three_separate_ledger_runs", new float[] {112, 112, 112, 112, 112});
    }

    @Test
    public void overlappingSupportedBandsCannotCreateProof() throws IOException {
        check("shared_three_separate_ledger_runs", new float[] {112, 114, 128, 130, 144});
    }

    @Test
    public void inclusiveBandContactCannotCreateProof() throws IOException {
        check("shared_three_separate_ledger_runs", new float[] {112, 116, 128, 132, 144});
    }

    @Test
    public void extremeFiniteCentresDoNotOverflow() throws IOException {
        check(
                "shared_three_separate_ledger_runs",
                new float[] {-Float.MAX_VALUE, Float.MAX_VALUE, 112, 128, 144});
    }

    @Test
    public void nonfiniteCentreRefusesNewProof() throws IOException {
        check("shared_three_separate_ledger_runs", new float[] {Float.NaN, 96, 112, 128, 144});
    }
}
