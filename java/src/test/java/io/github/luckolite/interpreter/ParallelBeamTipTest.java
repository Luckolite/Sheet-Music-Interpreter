// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class ParallelBeamTipTest {
    private static final int W=260,H=220,G=16;
    private final byte[] gray=new byte[W*H];
    public ParallelBeamTipTest(){Arrays.fill(gray,(byte)255);}
    private void beams(boolean pair) {
        for(int x=80;x<=170;x++) {
            int cy=Math.round(110+(x-150)*.15f);
            for(int dy=-10;dy<=10;dy++)if(dy<=-3||pair&&dy>=3)gray[(cy+dy)*W+x]=0;
        }
    }
    @Test public void twoSlopingCoresProveADoubleBeam() {beams(true);assertTrue(ParallelBeamTip.matches(gray,W,H,150,110,G));}
    @Test public void oneBeamIsInsufficient() {beams(false);assertFalse(ParallelBeamTip.matches(gray,W,H,150,110,G));}
    @Test public void aThinRuleCannotSubstituteForTheSecondCore() {
        beams(false);for(int x=20;x<240;x++)for(int y=116;y<=118;y++)gray[y*W+x]=0;
        assertFalse(ParallelBeamTip.matches(gray,W,H,150,110,G));
    }
    @Test public void aRoundedHeadBulgeAtTheTipIsPreserved() {
        beams(true);for(int y=94;y<=126;y++)for(int x=131;x<=157;x++)
            if(Math.pow((x-144)/13d,2)+Math.pow((y-110)/16d,2)<=1)gray[y*W+x]=0;
        assertFalse(ParallelBeamTip.matches(gray,W,H,150,110,G));
    }
    @Test public void aStaffCrossingTheOutsideContourDoesNotHideTheBeams() {
        beams(true);for(int x=0;x<W;x++)for(int y=123;y<=125;y++)gray[y*W+x]=0;
        assertTrue(ParallelBeamTip.matches(gray,W,H,150,110,G));
    }
    @Test public void missingRawImageCannotRemoveAHead() {assertFalse(ParallelBeamTip.matches(null,W,H,150,110,G));}
}
