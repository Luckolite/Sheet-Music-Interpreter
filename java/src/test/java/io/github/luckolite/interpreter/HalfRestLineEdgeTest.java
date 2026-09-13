// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
/** Original filled rectangles and finite rule-edge segments; no score pixels. */
public class HalfRestLineEdgeTest {
    private static final int W=800,H=220;
    private final byte[] gray=new byte[W*H];
    private void rect(int l,int t,int r,int b){for(int y=t;y<=b;y++)for(int x=l;x<=r;x++)gray[y*W+x]=0;}
    private void setup(){Arrays.fill(gray,(byte)250);for(int k=0;k<5;k++)rect(20,Math.round(80+k*13.75f),780,Math.round(80+k*13.75f));rect(300,105,450,110);}
    private List<ScoreRestEvent> detect(){return SixteenthRestDetector.detect(gray,W,H,List.of(new MeasureRegion(0,1,.1f,.9f)),List.of(new SixteenthRestDetector.Staff(80,135,13.75f,0,1)),List.of());}
    @Test public void fractionalGapRetainsHalfRestAboveFiniteLineEdge(){setup();rect(100,99,118,107);var rs=detect();assertEquals(rs.toString(),1,rs.size());assertEquals(2,rs.get(0).durationBeats(),0);}
    @Test public void finiteEdgeAloneDoesNotCreateARest(){setup();assertTrue(detect().isEmpty());}
    @Test public void thinMarkStillLacksRectangleHeight(){setup();rect(100,103,118,104);assertTrue(detect().isEmpty());}
    @Test public void rectangleTooFarAboveMiddleRuleIsRejected(){setup();rect(100,97,118,102);assertTrue(detect().isEmpty());}
    @Test public void sourceInkRemainsUnchanged(){setup();rect(100,99,118,107);var before=gray.clone();detect();assertArrayEquals(before,gray);}
}
