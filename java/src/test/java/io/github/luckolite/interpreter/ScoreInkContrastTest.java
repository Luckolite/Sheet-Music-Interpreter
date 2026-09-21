// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class ScoreInkContrastTest {
    @Test public void ordinaryPaperKeepsFaintRulesExactly() {
        byte[] gray=new byte[512*120];Arrays.fill(gray,(byte)247);
        for(int x=40;x<470;x++)gray[70*512+x]=(byte)208;
        assertSame(gray,ScoreInkContrast.prepare(gray,512,120));
    }
    @Test public void shadedPaperIsRemovedWhileFiveRulesAndHeadsSurvive() {
        int w=512,h=160;byte[] gray=new byte[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)gray[y*w+x]=(byte)(100+x/16+y/16);
        for(int y=40;y<=80;y+=10)for(int x=20;x<490;x++)gray[y*w+x]=30;
        for(int y=92;y<=98;y++)for(int x=248;x<=254;x++)gray[y*w+x]=5;
        byte[] output=ScoreInkContrast.prepare(gray,w,h);
        for(int y=40;y<=80;y+=10)assertEquals(0,output[y*w+200]&255);
        assertEquals(0,output[95*w+251]&255);
        assertEquals(255,output[120*w+400]&255);
        assertEquals(100,gray[0]&255);
    }
    @Test public void uniformDarkBackdropDoesNotTurnIntoInk() {
        byte[] gray=new byte[64*64];Arrays.fill(gray,(byte)90);
        for(byte value:ScoreInkContrast.prepare(gray,64,64))assertEquals(255,value&255);
    }
    @Test(expected=IllegalArgumentException.class) public void mismatchedDimensionsAreRejected() {
        ScoreInkContrast.prepare(new byte[8],4,4);
    }
}
