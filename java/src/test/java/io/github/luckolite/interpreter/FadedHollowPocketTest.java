// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;
/** Original pale hollow ovals crossed by ledger ink; no source score pixels. */
public class FadedHollowPocketTest {
    private static final int W=220,H=200;
    private final byte[] gray=new byte[W*H],labels=new byte[W*H];
    private void setup(boolean hollow,int outline,int pocket) {
        Arrays.fill(gray,(byte)250);
        for(int y=91;y<=109;y++)for(int x=88;x<=112;x++)
            if(Math.pow((x-100)/12.0,2)+Math.pow((y-100)/9.0,2)<=1) {
                gray[y*W+x]=(byte)outline;labels[y*W+x]=2;
                if(hollow&&Math.pow((x-100)/8.0,2)+Math.pow((y-100)/5.0,2)<=1)gray[y*W+x]=(byte)pocket;
            }
        for(int y=98;y<=102;y++)for(int x=80;x<=120;x++)gray[y*W+x]=(byte)outline;
        for(int y=45;y<=100;y++){gray[y*W+112]=(byte)outline;labels[y*W+112]=1;}
    }
    private float duration()throws Exception {
        var type=Class.forName(OmrScoreInterpreter.class.getName()+"$Component");var c=type.getDeclaredConstructors()[0];c.setAccessible(true);
        var head=c.newInstance(300,88,112,91,109,100f,100f);
        var m=OmrScoreInterpreter.class.getDeclaredMethod("detectUnbeamedDuration",byte[].class,byte[].class,int.class,int.class,type,float.class,int.class);m.setAccessible(true);
        return (float)m.invoke(null,labels,gray,W,H,head,16f,0);
    }
    @Test public void aPaleOutlineAroundLedgerPocketsIsAHalfNote()throws Exception {setup(true,155,215);assertEquals(2f,duration(),0f);}
    @Test public void lighterEnclosedPocketsRetainTheirHalfBase()throws Exception {setup(true,170,210);assertEquals(2f,duration(),0f);}
    @Test public void aFilledPaleHeadRemainsAQuarter()throws Exception {setup(false,170,170);assertEquals(1f,duration(),0f);}
    @Test public void weakInteriorContrastIsNotAnOpenHead()throws Exception {setup(true,175,195);assertEquals(1f,duration(),0f);}
    @Test public void aBrokenOutlineDoesNotEnclosePaper()throws Exception {
        setup(false,155,155);for(int y=94;y<=96;y++)for(int x=100;x<=112;x++)gray[y*W+x]=(byte)215;assertEquals(1f,duration(),0f);
    }
    @Test public void aSingleTinyPocketDoesNotChangeDuration()throws Exception {
        setup(false,155,155);for(int y=94;y<=95;y++)for(int x=99;x<=101;x++)gray[y*W+x]=(byte)215;assertEquals(1f,duration(),0f);
    }
    @Test public void darkHollowHeadsStillRetainTheirDuration()throws Exception {setup(true,60,215);assertEquals(2f,duration(),0f);}
    @Test public void sourceArraysRemainUnchanged()throws Exception {
        setup(true,155,215);var g=gray.clone();var l=labels.clone();duration();assertArrayEquals(g,gray);assertArrayEquals(l,labels);
    }
}
