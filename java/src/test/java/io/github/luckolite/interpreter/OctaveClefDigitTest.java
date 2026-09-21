// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;
public class OctaveClefDigitTest {
    @Test public void eightHasTwoCountersAndOrdinaryLoopHasOne() {
        byte[] gray=new byte[9*15];Arrays.fill(gray,(byte)255);
        for(int y=1;y<14;y++)for(int x=1;x<8;x++)
            if(x==1||x==7||y==1||y==7||y==13)gray[y*9+x]=0;
        assertEquals(2,OctaveClefDigit.holes(gray,9,0,0,9,15));
        for(int x=2;x<7;x++)gray[7*9+x]=(byte)255;
        assertEquals(1,OctaveClefDigit.holes(gray,9,0,0,9,15));
    }
    @Test public void octaveClefSurvivesRhythmAndAccidentalCopies() {
        var note=new ScoreNoteEvent(0,.2f,0,0,1,.3f,false).withClef(ScoreNoteEvent.CLEF_TREBLE_OTTAVA);
        assertEquals(37,note.withLeadingRest(.25f).withCompactOpening().clefBottomDiatonic());
        assertEquals(37,note.diatonicPitchIdentity());
    }
    private byte[] touchingDigit(boolean eight) {
        byte[] gray=new byte[80*120];Arrays.fill(gray,(byte)255);
        for(int y=20;y<=36;y++)for(int x=30;x<=38;x++)
            if(x==30||x==38||y==20||y==36||(eight&&y==28))gray[y*80+x]=0;
        for(int y=36;y<=90;y++)gray[y*80+34]=0;
        return gray;
    }
    @Test public void eightTouchingTheClefTipStillRaisesAnOctave() {
        assertTrue(OctaveClefDigit.above(touchingDigit(true),80,120,25,38,45,65,12));
    }
    @Test public void ordinarySingleClefLoopDoesNotRaiseAnOctave() {
        assertFalse(OctaveClefDigit.above(touchingDigit(false),80,120,25,38,45,65,12));
    }
    @Test public void measureNumberEndingInEightDoesNotRaiseAnOctave() {
        byte[] gray=touchingDigit(true);
        for(int y=20;y<=36;y++)gray[y*80+19]=0;
        for(int x=19;x<=25;x++){gray[20*80+x]=0;gray[28*80+x]=0;gray[36*80+x]=0;}
        assertFalse(OctaveClefDigit.above(gray,80,120,25,38,45,65,12));
    }
}
