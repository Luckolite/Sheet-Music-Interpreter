// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original role oracles frozen before a successor: physical frame ownership is not a dynamic floor. */
public class MisassignedRowRehearsalFTest {
    static List<PlayingTechniqueDetector.Staff> twoRows() {
        return List.of(
                new PlayingTechniqueDetector.Staff(0, 56, 14, 0, 1),
                new PlayingTechniqueDetector.Staff(150, 206, 14, 0, 1));
    }

    @Test
    public void earlierDynamicOwnerDoesNotOwnASeparateCompleteRehearsalFrame() throws Exception {
        var scene = new BoxedRehearsalFFixture("boxed-capital");
        var staffs = twoRows();
        boolean proof =
                BoxedRehearsalF.proved(
                        scene.word("F"), staffs.get(0), staffs, scene.gray, 400, 320);
        System.out.println("complete-frame-wrong-dynamic-owner " + proof);
        assertTrue(
                "The complete capital is in a finite frame uniquely above the later printed row",
                proof);
    }

    @Test
    public void competingPhysicalRowAndForeignFrameKeepOriginalAbstention() throws Exception {
        var scene = new BoxedRehearsalFFixture("foreign-box-f");
        var staffs = scene.staffs();
        assertFalse(
                BoxedRehearsalF.proved(
                        scene.word("f"),
                        staffs.get(staffs.size() - 1),
                        staffs,
                        scene.gray,
                        400,
                        320));
        assertEquals(1, scene.detect("f").size());
    }

    @Test
    public void ItalicForteAndItsFaintHookRemainGenuineAcrossTwoRows() throws Exception {
        for (String name : List.of("italic-f", "faint-tip-f", "boxed-italic-f")) {
            var scene = new BoxedRehearsalFFixture(name);
            var staffs = twoRows();
            for (String token : List.of("F", "f"))
                assertFalse(
                        name + " " + token,
                        BoxedRehearsalF.proved(
                                scene.word(token), staffs.get(0), staffs, scene.gray, 400, 320));
        }
    }
}
