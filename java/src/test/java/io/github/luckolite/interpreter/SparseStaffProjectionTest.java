// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural staffs with different ranges and optional white-paper/dark-ink pixels. */
public final class SparseStaffProjectionTest {
    private static final int[][] EXPECTED = {
        {
            1032358025,
            1051372203,
            1035673272,
            1046743613,
            1052211063,
            1059306428,
            1035673272,
            1046743613,
            1059725858,
            1064234735,
            1035673272,
            1046743613,
            1041305873,
            1052420779,
            1052690350,
            1057030799,
            1053259639,
            1059026807,
            1052690350,
            1057030799,
            1059446238,
            1063186159,
            1052690350,
            1057030799,
            1046478848,
            1054867456,
            1059546071,
            1061749391,
            1055706317,
            1060180241,
            1059546071,
            1061749391,
            1060599671,
            1064269687,
            1059546071,
            1061749391
        },
        {
            1032358025,
            1051372203,
            1036045726,
            1047282162,
            1052211063,
            1059306428,
            1035305851,
            1046929840,
            1059725858,
            1064234735,
            1034596174,
            1046559902,
            1041305873,
            1052420779,
            1052764589,
            1057142787,
            1053259639,
            1059026807,
            1052608562,
            1057067918,
            1059446238,
            1063186159,
            1052458825,
            1056989905,
            1046478848,
            1054867456,
            1059561170,
            1061838101,
            1055706317,
            1060180241,
            1059484415,
            1061764490,
            1060599671,
            1064269687,
            1059410805,
            1061687735
        },
        {
            1032358025,
            1051372203,
            1034865450,
            1046603942,
            1052211063,
            1059306428,
            1035393931,
            1046881397,
            1059725858,
            1064234735,
            1035948838,
            1047147525,
            1041305873,
            1052420779,
            1052522368,
            1057002959,
            1053259639,
            1059026807,
            1052634670,
            1057061470,
            1059446238,
            1063186159,
            1052751693,
            1057117622,
            1046478848,
            1054867456,
            1059479539,
            1061738066,
            1055706317,
            1060180241,
            1059534746,
            1061795634,
            1060599671,
            1064269687,
            1059592314,
            1061850841
        },
        {
            1032358025,
            1051372203,
            1030718331,
            1041363474,
            1052211063,
            1059306428,
            1030718331,
            1041363474,
            1059725858,
            1064234735,
            1030718331,
            1041363474,
            1041305873,
            1052420779,
            1047802571,
            1051009535,
            1053259639,
            1059026807,
            1047802571,
            1051009535,
            1059446238,
            1063186159,
            1047802571,
            1051009535,
            1046478848,
            1054867456,
            1054229083,
            1057006970,
            1055706317,
            1060180241,
            1054229083,
            1057006970,
            1060599671,
            1064269687,
            1054229083,
            1057006970,
            1032078404,
            1050673152,
            1058616744,
            1060026870,
            1051512013,
            1058572425,
            1058616744,
            1060026870,
            1058991855,
            1063186159,
            1058616744,
            1060026870,
            1041305873,
            1053119829,
            1061636643,
            1063046769,
            1053958690,
            1059725858,
            1061636643,
            1063046769,
            1060145289,
            1064269687,
            1061636643,
            1063046769
        },
        {
            1032358025,
            1051372203,
            1035673272,
            1046743613,
            1052211063,
            1059306428,
            1035673272,
            1046743613,
            1059725858,
            1064234735,
            1035673272,
            1046743613,
            1041305873,
            1052420779,
            1052690350,
            1057030799,
            1053259639,
            1059026807,
            1052690350,
            1057030799,
            1059446238,
            1063360922,
            1052690350,
            1057030799,
            1046478848,
            1054867456,
            1059546071,
            1061749391,
            1055706317,
            1060180241,
            1059546071,
            1061749391,
            1060599671,
            1064269687,
            1059546071,
            1061749391
        },
        {
            1032358025,
            1051372203,
            1034865450,
            1046603942,
            1052211063,
            1059306428,
            1035393931,
            1046881397,
            1059725858,
            1064234735,
            1035948838,
            1047147525,
            1041305873,
            1052420779,
            1052585493,
            1056998709,
            1053259639,
            1059026807,
            1052678598,
            1057057014,
            1059446238,
            1063360922,
            1052795208,
            1057083228,
            1046478848,
            1054867456,
            1059519857,
            1061723177,
            1055706317,
            1060180241,
            1059546071,
            1061781482,
            1060599671,
            1064269687,
            1059604376,
            1061861932
        }
    };

    @Test
    public void preservesCompleteOrderedGeometryOnSparseHorizontalAndTiltedStaffs() {
        int[] staffs = {3, 3, 3, 5, 3, 3};
        float[] slopes = {0f, -.024f, .018f, 0f, 0f, .018f};
        for (int fixture = 0; fixture < staffs.length; fixture++) {
            int width = 480, height = 100 + staffs[fixture] * 180;
            byte[] labels = page(width, height, staffs[fixture], slopes[fixture]);
            byte[] beforeLabels = labels.clone(), gray = null;
            if (fixture >= 4) {
                gray = new byte[labels.length];
                for (int i = 0; i < gray.length; i++) gray[i] = labels[i] == 0 ? (byte) 255 : 0;
            }
            byte[] beforeGray = gray == null ? null : gray.clone();
            var actual = OmrMeasurePostProcessor.process(labels, gray, width, height);
            assertEquals("fixture=" + fixture, EXPECTED[fixture].length / 4, actual.size());
            int at = 0;
            for (var measure : actual) {
                for (float value :
                        new float[] {
                            measure.left(), measure.right(), measure.top(), measure.bottom()
                        })
                    assertEquals(
                            "fixture=" + fixture + " bound=" + at,
                            EXPECTED[fixture][at++],
                            Float.floatToRawIntBits(value));
            }
            assertArrayEquals(beforeLabels, labels);
            if (gray != null) assertArrayEquals(beforeGray, gray);
        }
    }

    private static byte[] page(int width, int height, int staffs, float slope) {
        byte[] labels = new byte[width * height];
        for (int row = 0; row < staffs; row++) {
            int top = 80 + row * 180, left = 20 + row % 3 * 37, right = width - 20 - row % 2 * 31;
            for (int x = left; x <= right; x++)
                for (int line = 0; line < 5; line++)
                    for (int dy = 0; dy < 2; dy++) {
                        int y = Math.round(top + line * 10 + slope * (x - width / 2f)) + dy;
                        labels[y * width + x] = 4;
                    }
            for (int bar :
                    new int[] {
                        left, left + (right - left) / 3, left + 2 * (right - left) / 3, right
                    })
                for (int y = top; y <= top + 40; y++) {
                    int shifted = Math.round(y + slope * (bar - width / 2f));
                    labels[shifted * width + bar] = 1;
                }
        }
        return labels;
    }
}
