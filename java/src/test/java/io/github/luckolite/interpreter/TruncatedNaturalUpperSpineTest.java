// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original natural geometry whose upper-left spine has a different label. */
public class TruncatedNaturalUpperSpineTest {
    private static final int W=420,H=260;
    private final byte[] labels=new byte[W*H],gray=new byte[W*H];
    public TruncatedNaturalUpperSpineTest(){
        Arrays.fill(gray,(byte)255);
        for(int y=100;y<=164;y+=16)box(10,409,y,y,4,180);
        for(int y=118;y<=130;y++)for(int x=272;x<=288;x++)
            if(Math.pow((x-280)/8d,2)+Math.pow((y-124)/6d,2)<=1)box(x,x,y,y,2,0);
        box(272,272,124,172,1,0);
        box(251,252,99,132,3,130);box(260,261,112,146,3,130);
        box(251,261,112,115,3,0);box(251,261,129,132,3,0);
        for(int y=99;y<112;y++)for(int x=251;x<=252;x++)labels[y*W+x]=1;
    }
    private void box(int l,int r,int t,int b,int label,int value){
        for(int y=t;y<=b;y++)for(int x=l;x<=r;x++){labels[y*W+x]=(byte)label;gray[y*W+x]=(byte)value;}
    }
    private void annotation(){box(257,264,80,111,5,0);}
    private int accidental(){
        return OmrScoreInterpreter.analyze(labels,gray,W,H,List.of(new MeasureRegion(0,1,.1f,.9f))).notes().stream()
                .filter(n->Math.abs(n.positionInMeasure()*W-280)<6).findFirst().orElseThrow().writtenAccidental();
    }
    @Test public void adjacentAnnotationDoesNotHideMissingUpperSpine(){annotation();assertEquals(0,accidental());}
    @Test public void rawNaturalWithoutAnnotationStillRecovers(){assertEquals(0,accidental());}
    @Test public void absentPrintedExtensionIsNotInvented(){
        annotation();box(251,252,99,111,0,255);assertEquals(2,accidental());
    }
    @Test public void clippedExtensionDoesNotProveAnEndpoint(){
        annotation();box(251,252,70,111,1,130);assertEquals(2,accidental());
    }
    @Test public void equalLowerEndpointsCannotBecomeNatural(){
        annotation();box(251,252,133,146,3,130);assertNotEquals(0,accidental());
    }
    @Test public void oneConnectorIsInsufficient(){
        annotation();box(253,259,129,132,0,255);assertNotEquals(0,accidental());
    }
    @Test public void sourceArraysAreUnchanged(){
        annotation();var l=labels.clone();var g=gray.clone();accidental();assertArrayEquals(l,labels);assertArrayEquals(g,gray);
    }
}
