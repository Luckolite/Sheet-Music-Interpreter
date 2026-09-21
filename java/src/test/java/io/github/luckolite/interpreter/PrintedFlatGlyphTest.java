// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;import org.junit.Test;import static org.junit.Assert.*;
public class PrintedFlatGlyphTest {
    private final int w=160,h=150;private final byte[] gray=new byte[w*h];
    public PrintedFlatGlyphTest(){Arrays.fill(gray,(byte)255);}
    private void rect(int x,int y,int ww,int hh){for(int yy=y;yy<y+hh;yy++)for(int xx=x;xx<x+ww;xx++)gray[yy*w+xx]=0;}
    private void flat(){rect(60,50,3,41);for(int y=69;y<=87;y++)for(int x=61;x<=74;x++){double r=Math.pow((x-63)/11d,2)+Math.pow((y-78)/9d,2);if(r>=.4&&r<=1)gray[y*w+x]=0;}}
    private boolean read(int top){return PrintedFlatGlyph.matches(gray,w,h,60,top,74,90,16);}
    @Test public void sourceFlatSurvivesStaffCrossings(){flat();for(int y:new int[]{54,70,86})rect(20,y,120,2);assertTrue(read(50));}
    @Test public void truncatedSemanticBoxCanRecoverItsPrintedSpine(){flat();assertTrue(read(69));}
    @Test public void naturalKeepsItsLowerRightStem(){rect(60,50,3,32);rect(71,60,3,31);rect(60,60,14,3);rect(60,79,14,3);assertFalse(read(50));}
    @Test public void sharpKeepsBothUpperSpines(){rect(60,50,3,41);rect(71,50,3,41);rect(57,62,21,3);rect(57,77,21,3);assertFalse(read(50));}
    @Test public void simpleStemHasNoBowl(){rect(60,50,3,41);assertFalse(read(50));}
    @Test public void adjacentLocalFlatsAreNotAKeyChange(){
        byte[] labels=new byte[400*240];for(int y=80;y<=120;y+=10)for(int x=20;x<380;x++)labels[y*400+x]=4;
        RejectedKeyBoundaryTest.flat(labels,90,75);RejectedKeyBoundaryTest.flat(labels,105,75);RejectedKeyBoundaryTest.head(labels,118,110);
        var result=OmrScoreInterpreter.analyze(labels,RejectedKeyBoundaryTest.raw(labels),400,240,RejectedKeyBoundaryTest.M);
        assertTrue(result.keyChanges().isEmpty());
    }
}
