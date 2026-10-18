// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Source-free provenance boundaries; byte equality cannot replace actual raster identity. */
public class RestRuleNullCarrierTest {
    @Test
    public void nullEmptyAndByteEqualOtherCurrentPlaneStayUnqualified() {
        byte[] original = RestRuleOriginalPlaneIdentityTest.page(0),
                view = original.clone(),
                before = view.clone();
        var n = RestRuleOriginalPlaneIdentityTest.note(.3f);
        var carried = RestRuleOriginalPlaneIdentityTest.carrier(n, original, view);
        for (int scene = 0; scene < 3; scene++) {
            byte[] supplied = scene == 2 ? view.clone() : view;
            List<ScoreNoteEvent> notes = scene == 0 ? null : scene == 1 ? List.of() : carried;
            boolean[] mask = RestRuleOriginalPlaneIdentityTest.mask();
            var d = RestRuleOriginalPlaneIdentityTest.bind(supplied, mask, notes);
            assertNotNull(d);
            assertFalse(d.registered());
            assertSame(mask, d.line());
            assertArrayEquals(before, supplied);
        }
        assertArrayEquals(before, view);
        assertArrayEquals(before, original);
    }
}
