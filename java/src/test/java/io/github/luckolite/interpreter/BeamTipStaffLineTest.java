// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original single-beam geometry with an optional rule and a real head bulge. */
public class BeamTipStaffLineTest {
    private byte[] page(boolean rule,boolean head) {
        byte[] ink=new byte[300*180];Arrays.fill(ink,(byte)255);
        for(int x=100;x<=150;x++) {
            int cy=Math.round(80-(x-100)*.15f);
            for(int y=cy-5;y<=cy+5;y++)ink[y*300+x]=0;
        }
        if(rule)for(int x=0;x<300;x++)ink[82*300+x]=0;
        if(head)for(int y=66;y<=94;y++)for(int x=90;x<=122;x++)
            if((x-106)*(x-106)/256d+(y-80)*(y-80)/196d<=1)ink[y*300+x]=0;
        return ink;
    }
    @Test public void thickerSingleBeamStillHasAStraightTip() {
        assertTrue(OmrScoreInterpreter.narrowBeamTip(page(false,false),300,180,100,80,16));
    }
    @Test public void horizontalStaffRuleDoesNotMakeABeamTipANote() {
        assertTrue(OmrScoreInterpreter.narrowBeamTip(page(true,false),300,180,100,80,16));
    }
    @Test public void roundedHeadBulgeRemainsANote() {
        assertFalse(OmrScoreInterpreter.narrowBeamTip(page(true,true),300,180,100,80,16));
    }
}
