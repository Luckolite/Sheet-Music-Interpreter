// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
/** Original rest bulbs at fractional staff scale and finite staff-rule remnants. */
public class RasterEighthRestTest {
    private static final int W=800,H=240;
    private final byte[] gray=new byte[W*H];
    private void ellipse(int cy,int rx,int ry){for(int y=cy-ry;y<=cy+ry;y++)for(int x=177-rx;x<=177+rx;x++)if(Math.pow((x-177)/(double)rx,2)+Math.pow((y-cy)/(double)ry,2)<=1)gray[y*W+x]=0;}
    private void setup(boolean partialRule,boolean small,boolean tail) {
        Arrays.fill(gray,(byte)255);
        for(int line=0;line<5;line++){int y=Math.round(80+line*14.25f);for(int x=partialRule&&line==3?140:20;x<=(partialRule&&line==3?230:780);x++)gray[y*W+x]=0;}
        ellipse(100,small?4:6,small?3:5);
        if(tail)for(int y=97;y<=123;y++){int x=185-(y-97)*10/26;gray[y*W+x]=0;gray[y*W+x+1]=0;}
    }
    private List<ScoreRestEvent> rests(){return SixteenthRestDetector.detect(gray,W,H,List.of(new MeasureRegion(0,1,.2f,.8f)),List.of(new SixteenthRestDetector.Staff(80,137,14.25f,0,1)),List.of());}
    @Test public void aThreeRowBulbAtFractionalScaleIsAnEighthRest(){setup(false,true,true);var r=rests();assertEquals(r.toString(),1,r.size());assertEquals(.5,r.get(0).durationBeats(),0);}
    @Test public void aFiniteRuleRemnantDoesNotJoinTheRestToTheStaff(){setup(true,false,true);var r=rests();assertEquals(r.toString(),1,r.size());assertEquals(.5,r.get(0).durationBeats(),0);}
    @Test public void aBulbWithoutItsDescendingTailIsNotARest(){setup(false,true,false);assertTrue(rests().isEmpty());}
    @Test public void aRuleWithoutAGlyphDoesNotCreateSilence(){setup(true,false,false);for(int y=94;y<=106;y++)for(int x=170;x<=184;x++)gray[y*W+x]=(byte)255;assertTrue(rests().isEmpty());}
    @Test public void sourcePixelsRemainUnchanged(){setup(true,false,true);var before=gray.clone();rests();assertArrayEquals(before,gray);}
}
