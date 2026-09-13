// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original two-system note geometry with separate returning tie curves. */
public class SystemBreakTieTest {
    static final int W=800,H=480;
    byte[] labels=new byte[W*H],gray=new byte[W*H];
    SystemBreakTieTest setup(int mode,boolean above) {
        Arrays.fill(gray,(byte)255);
        for(int top:new int[]{90,300})for(int line=0;line<5;line++)for(int x=40;x<780;x++)ink(x,top+line*16,4);
        int currentY=mode==3?388:396;
        head(650,186);head(180,currentY);
        int side=above?-1:1;
        if(mode!=1)arc(662,750,186,side,mode==5);
        if(mode!=2)arc(136,168,currentY,mode==4?-side:side,mode==5);
        return this;
    }
    void ink(int x,int y,int label){labels[y*W+x]=(byte)label;gray[y*W+x]=0;}
    void head(int cx,int cy){
        for(int y:new int[]{cy-16,cy})for(int x=cx-12;x<=cx+12;x++)gray[y*W+x]=0;
        for(int y=cy-5;y<=cy+5;y++)for(int x=cx-7;x<=cx+7;x++)
            if(Math.pow((x-cx)/7d,2)+Math.pow((y-cy)/5d,2)<=1)ink(x,y,2);
        for(int y=cy-45;y<cy;y++)ink(cx+7,y,1);
    }
    void arc(int left,int right,int cy,int side,boolean straight){
        for(int x=left;x<=right;x++){
            float t=(x-left)/(float)(right-left);int y=Math.round(cy+side*(8+(straight?0:6*4*t*(1-t))));
            ink(x,y,5);ink(x,y+1,5);
        }
    }
    List<ScoreNoteEvent> notes(){
        return OmrScoreInterpreter.extract(labels,gray,W,H,List.of(new MeasureRegion(.05f,.975f,70f/H,210f/H),new MeasureRegion(.05f,.975f,280f/H,420f/H)));
    }
    void expected(boolean tied){var notes=notes();assertEquals(2,notes.size());assertEquals(tied,notes.get(1).tiedFromPrevious());}
    @Test public void twoReturningCurvesBelowJoinAcrossSystems(){setup(0,false).expected(true);}
    @Test public void twoReturningCurvesAboveJoinAcrossSystems(){setup(0,true).expected(true);}
    @Test public void incomingCurveAloneCannotJoin(){setup(1,false).expected(false);}
    @Test public void outgoingCurveAloneCannotJoin(){setup(2,false).expected(false);}
    @Test public void differentWrittenPitchesCannotJoin(){setup(3,false).expected(false);}
    @Test public void curvesOnOppositeSidesCannotJoin(){setup(4,false).expected(false);}
    @Test public void straightStrokesCannotJoin(){setup(5,false).expected(false);}
    @Test public void sourceArraysArePreserved(){setup(0,false);var l=labels.clone();var g=gray.clone();notes();assertArrayEquals(l,labels);assertArrayEquals(g,gray);}
}
