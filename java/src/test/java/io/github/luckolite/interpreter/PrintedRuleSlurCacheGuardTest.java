// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Impossible recovery geometry must not initialize an optical staff cache. */
public class PrintedRuleSlurCacheGuardTest {
    void rejected(int[] strict, int[] pale) throws Exception {
        var type = OmrScoreInterpreter.class;
        var st = Class.forName(type.getName() + "$Staff");
        var constructor = st.getDeclaredConstructor(float.class, float.class, float.class);
        constructor.setAccessible(true);
        Object staff = constructor.newInstance(40f, 88f, 12f);
        byte[] gray = new byte[200 * 200], labels = new byte[gray.length];
        Arrays.fill(gray, (byte) 255);
        var method =
                type.getDeclaredMethod(
                        "crossesPrintedRuleSlurCap",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        st,
                        int[].class,
                        int[].class,
                        float.class,
                        int.class);
        method.setAccessible(true);
        assertFalse(
                (boolean)
                        method.invoke(null, labels, gray, 200, 200, staff, strict, pale, 12f, 170));
        var checked = st.getDeclaredField("sharedRuleTrackChecked");
        checked.setAccessible(true);
        var frame = st.getDeclaredField("sharedRuleTrack");
        frame.setAccessible(true);
        assertFalse(checked.getBoolean(staff));
        assertNull(frame.get(staff));
    }

    @Test
    public void unchangedEndpointDoesNotSearchForStaffRules() throws Exception {
        rejected(new int[] {100, 80, -1}, new int[] {100, 80, -1});
    }

    @Test
    public void malformedAndOppositeDirectionsDoNotSearch() throws Exception {
        rejected(new int[] {100}, new int[] {100, 70, -1});
        rejected(new int[] {100, 80, -1}, new int[] {100, 70, 1});
    }

    @Test
    public void remoteRecoveryDoesNotSearchForStaffRules() throws Exception {
        rejected(new int[] {100, 80, -1}, new int[] {110, 70, -1});
        rejected(new int[] {100, 80, -1}, new int[] {100, 40, -1});
    }
}
