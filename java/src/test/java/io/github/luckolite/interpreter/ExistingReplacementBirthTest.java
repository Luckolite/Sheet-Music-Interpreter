// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ExistingReplacementBirthTest {
    @Test
    public void replacementOwnCreationIsNeverOverwrittenOrPoisoned() {
        var p = new SelectedMeasureOrigins();
        byte[] l = new byte[32000], g = l.clone();
        p.begin(l, g, 160, 200);
        var prior = new Object();
        var next = new Object();
        p.born(prior, new int[] {60, 72, 84, 96, 108}, 12, 20, 120, 0, null);
        p.born(next, new int[] {61, 73, 85, 97, 109}, 12, 22, 122, 0, null);
        p.replaced(prior, next, 12, 22, 122, 0, null);
        assertFalse("real replacement already owns independent constructor birth", p.incomplete());
        assertArrayEquals(
                new int[] {61, 73, 85, 97, 109}, p.member(next, 12, 22, 122, 0, null).rows);
    }
}
