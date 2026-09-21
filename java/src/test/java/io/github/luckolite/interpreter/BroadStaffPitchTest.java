// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;
public class BroadStaffPitchTest {
    private byte[] staff(int count) {
        byte[] gray=new byte[400*180];Arrays.fill(gray,(byte)255);
        for(int line=0;line<count;line++)for(int x=30;x<370;x++)gray[(140-line*14)*400+x]=0;
        // Beams obscure isolated probes but leave broad evidence of every rule.
        for(int x=100;x<160;x++)for(int y=90;y<=116;y++)gray[y*400+x]=0;
        return gray;
    }
    @Test public void smallSpacingErrorIsCorrectedBeforeItChangesHighLedgerPitch() {
        float[] pitch=StaffPitchTrack.broadStraightPitch(staff(5),400,180,139,13.25f);
        assertNotNull(pitch);assertEquals(140,pitch[0],.01);assertEquals(14,pitch[1],.01);
        assertEquals(17,Math.round((pitch[0]-21)/(pitch[1]*.5f)));
    }
    @Test public void fourRulesCannotRecalibratePitch() {
        assertNull(StaffPitchTrack.broadStraightPitch(staff(4),400,180,139,13.25f));
    }
    @Test public void accurateSpacingIsLeftAlone() {
        assertNull(StaffPitchTrack.broadStraightPitch(staff(5),400,180,140,14));
    }
    @Test public void aWholeRulePhaseShiftIsRejected() {
        assertNull(StaffPitchTrack.broadStraightPitch(staff(5),400,180,126,13.25f));
    }
}
