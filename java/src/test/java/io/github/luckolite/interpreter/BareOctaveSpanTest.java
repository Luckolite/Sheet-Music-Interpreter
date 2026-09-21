// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class BareOctaveSpanTest {
    private static final int W=320,H=300;
    private final byte[] gray=new byte[W*H];
    public BareOctaveSpanTest(){Arrays.fill(gray,(byte)255);}
    private void mark(int y,boolean line) {
        for(int cy:new int[]{y+5,y+14})for(int yy=cy-5;yy<=cy+5;yy++)for(int x=55;x<=65;x++) {
            double r=Math.pow((x-60)/5d,2)+Math.pow((yy-cy)/5d,2);
            if(r<=1.1&&r>=.3)gray[yy*W+x]=0;
        }
        if(line)for(int x=76;x<210;x+=8)for(int xx=x;xx<x+3;xx++)gray[(y+3)*W+xx]=0;
    }
    private ScoreNoteEvent note(int x,int y) {return new ScoreNoteEvent(0,x/(float)W,2,0,1,y/(float)H,false,0,1,2,0,1);}
    private List<ScoreNoteEvent> apply(List<ScoreNoteEvent> notes,List<PlayingTechniqueDetector.Staff> staffs) {
        return OctaveMarkDetector.apply(List.of(),staffs,List.of(new MeasureRegion(0,1,0,1)),notes,gray,W,H);
    }
    @Test public void bareEightAndDottedLineRaiseOnlyCoveredNotes() {
        mark(90,true);var result=apply(List.of(note(40,170),note(100,170),note(180,170),note(240,170)),List.of(new PlayingTechniqueDetector.Staff(150,198,12,0,1)));
        assertEquals(List.of(0,1,1,0),result.stream().map(ScoreNoteEvent::octaveShift).toList());
    }
    @Test public void bareEightBelowStaffLowersTheOctave() {
        mark(220,true);var result=apply(List.of(note(100,170)),List.of(new PlayingTechniqueDetector.Staff(150,198,12,0,1)));
        assertEquals(-1,result.get(0).octaveShift());
    }
    @Test public void anIsolatedNumeralDoesNotTransposeNotes() {
        mark(90,false);var notes=List.of(note(100,170));assertEquals(notes,apply(notes,List.of(new PlayingTechniqueDetector.Staff(150,198,12,0,1))));
    }
    @Test public void anInterSystemMarkDoesNotShiftBothRows() {
        mark(210,true);var result=apply(List.of(note(100,125),note(100,270)),List.of(new PlayingTechniqueDetector.Staff(100,148,12,0,1),new PlayingTechniqueDetector.Staff(250,298,12,0,1)));
        assertEquals(List.of(0,1),result.stream().map(ScoreNoteEvent::octaveShift).toList());
    }
    @Test public void aThinSlurCrossingDoesNotEndTheDottedSpan() {
        mark(90,true);for(int y=88;y<=105;y++)gray[y*W+92]=0;
        var result=apply(List.of(note(180,170)),List.of(new PlayingTechniqueDetector.Staff(150,198,12,0,1)));
        assertEquals(1,result.get(0).octaveShift());
    }
    @Test public void NeighboringTextStrokesAreNotADottedLine() {
        mark(220,false);
        for(int x=76;x<210;x+=12)for(int y=220;y<238;y++) {
            gray[y*W+x]=0;gray[y*W+x+6]=0;
            if(y==223)for(int xx=x;xx<=x+6;xx++)gray[y*W+xx]=0;
        }
        var notes=List.of(note(100,170));
        assertEquals(notes,apply(notes,List.of(new PlayingTechniqueDetector.Staff(150,198,12,0,1))));
    }
    @Test public void sparseAbbreviationPeriodsCannotTranspose() {
        mark(90,false);
        for(int x:new int[]{76,92,108})for(int xx=x;xx<x+3;xx++)gray[93*W+xx]=0;
        var notes=List.of(note(100,170));
        assertEquals(notes,apply(notes,List.of(new PlayingTechniqueDetector.Staff(150,198,12,0,1))));
    }
    private void attachedItalicEight(boolean dotted,boolean bothHoles) {
        for(int y=90;y<=110;y++)for(int x=49;x<=73;x++) {
            double skew=(110-y)*.35;
            double upper=Math.pow((x-59-skew)/5d,2)+Math.pow((y-95)/5d,2);
            double lower=Math.pow((x-59-skew)/5d,2)+Math.pow((y-105)/5d,2);
            if((upper<=1.15&&(!bothHoles||upper>=.3))||(lower<=1.15&&lower>=.3))gray[y*W+x]=0;
        }
        // A ledger joins the left of the numeral to a stem and note.
        for(int x=25;x<=60;x++)gray[101*W+x]=0;
        for(int y=80;y<=140;y++)gray[y*W+26]=0;
        if(dotted)for(int x=80;x<210;x+=8)for(int xx=x;xx<x+3;xx++)gray[93*W+xx]=0;
    }
    @Test public void ledgerAttachedItalicEightKeepsItsOctaveSpan() {
        attachedItalicEight(true,true);
        var result=apply(List.of(note(100,170),note(180,170),note(240,170)),List.of(new PlayingTechniqueDetector.Staff(150,198,12,0,1)));
        assertEquals(List.of(1,1,0),result.stream().map(ScoreNoteEvent::octaveShift).toList());
    }
    @Test public void attachedTwoHolesStillNeedADottedSpan() {
        attachedItalicEight(false,true);var notes=List.of(note(100,170));
        assertEquals(notes,apply(notes,List.of(new PlayingTechniqueDetector.Staff(150,198,12,0,1))));
    }
    @Test public void oneHoleAndADottedLineDoNotCreateAnEight() {
        attachedItalicEight(true,false);var notes=List.of(note(100,170));
        assertEquals(notes,apply(notes,List.of(new PlayingTechniqueDetector.Staff(150,198,12,0,1))));
    }
    @Test public void ambiguousAttachedEightDoesNotLowerThePrecedingSystem() {
        attachedItalicEight(true,true);
        var notes=List.of(note(100,50),note(100,230));
        assertEquals(notes,apply(notes,List.of(new PlayingTechniqueDetector.Staff(20,68,12,0,1),new PlayingTechniqueDetector.Staff(200,248,12,0,1))));
    }
    @Test public void pixelsRemainUnchanged() {mark(90,true);var copy=gray.clone();apply(List.of(note(100,170)),List.of(new PlayingTechniqueDetector.Staff(150,198,12,0,1)));assertArrayEquals(copy,gray);}
}
