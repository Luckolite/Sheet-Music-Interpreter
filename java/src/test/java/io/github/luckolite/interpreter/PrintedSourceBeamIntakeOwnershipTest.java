// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.assertEquals;
import java.nio.file.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Exercises the full raw intake with five independently drawn ownership scenes. */
public final class PrintedSourceBeamIntakeOwnershipTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void retainedHeadAndPhysicalShaftOwnership() throws Exception {
        Path input = PrintedBeamOriginalResources.materialize("intake", temporary);
        Path result = temporary.newFile("original-result.tsv").toPath();
        PrintedBeamIntakeOwnershipOriginals.main(
                new String[] {input.toString(), result.toString()});
        var report = Files.readAllLines(result);
        assertEquals("checks\t42", report.get(0));
        assertEquals(6, report.size());
    }
}
