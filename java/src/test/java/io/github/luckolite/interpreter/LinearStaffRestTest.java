// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
/** Original gently sloping rules, two heads, and a one-bulb eighth rest. */
public class LinearStaffRestTest {
    private static final int W=1400,H=340;
    private static final float GAP=14.25f;
    private final byte[] gray=new byte[W*H],labels=new byte[W*H];
    private float slope;
    private void pixel(int x,int y,int label){int row=Math.round(y+slope*(x-W*.5f));gray[row*W+x]=0;labels[row*W+x]=(byte)label;}
    private void ellipse(int cx,int cy,int rx,int ry,int label){for(int y=cy-ry;y<=cy+ry;y++)for(int x=cx-rx;x<=cx+rx;x++)if(Math.pow((x-cx)/(double)rx,2)+Math.pow((y-cy)/(double)ry,2)<=1)pixel(x,y,label);}
    private void setup(float slope,boolean rest) {
        this.slope=slope;Arrays.fill(gray,(byte)250);
        for(int x=30;x<W-30;x++)for(int line=0;line<5;line++)pixel(x,Math.round(140+line*GAP),4);
        for(int x:new int[]{850,1250}){ellipse(x,147,10,7,2);for(int y=100;y<=147;y++)pixel(x+9,y,1);}
        if(rest) {
            ellipse(1098,159,6,4,0);
            for(int y=156;y<=183;y++){int x=1106-(y-156)*11/27;pixel(x,y,0);pixel(x+1,y,0);}
        }
    }
    private OmrScoreInterpreter.Analysis analyze(){return OmrScoreInterpreter.analyze(labels,gray,W,H,List.of(new MeasureRegion(.02f,.98f,.2f,.8f)));}
    @Test public void gentlePositiveSlopeRetainsTheRest() {setup(.0045f,true);var a=analyze();assertEquals(a.rests().toString(),1,a.rests().size());assertEquals(.5,a.rests().get(0).durationBeats(),0);}
    @Test public void gentleNegativeSlopeRetainsTheRest() {setup(-.0045f,true);var a=analyze();assertEquals(a.rests().toString(),1,a.rests().size());assertEquals(.5,a.rests().get(0).durationBeats(),0);}
    @Test public void noPrintedRestDoesNotCreateSilence() {setup(.0045f,false);assertTrue(analyze().rests().isEmpty());}
    @Test public void sourceArraysArePreserved() {setup(.0045f,true);var g=gray.clone();var l=labels.clone();analyze();assertArrayEquals(g,gray);assertArrayEquals(l,labels);}
    @Test public void aLinearTrackUsesTheSameCenteredSlopeAsPitch() {
        var t=StaffPitchTrack.linear(1400,197,14.25f,.0045f);
        assertArrayEquals(new float[]{197,14.25f},t.at(700),.0001f);
        assertArrayEquals(new float[]{200.1455f,14.25f},t.at(1399),.0001f);
    }
}
