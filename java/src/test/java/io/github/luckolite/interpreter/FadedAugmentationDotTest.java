// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
/** Original two-tone dots, staff lines and disconnected marks; no score pixels. */
public class FadedAugmentationDotTest {
    private static final int W=240,H=160;
    private final byte[] gray=new byte[W*H];
    public FadedAugmentationDotTest(){Arrays.fill(gray,(byte)250);}
    private void dot(int cx,int cy) {
        for(int dy=-3;dy<=3;dy++)for(int dx=-3;dx<=3;dx++)if(dx*dx+dy*dy<=10)
            gray[(cy+dy)*W+cx+dx]=(byte)(dx*dx+dy*dy<=4?165:195);
    }
    private int count()throws Exception {
        var type=Class.forName(OmrScoreInterpreter.class.getName()+"$Component");
        var ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);
        var head=ctor.newInstance(180,90,110,73,87,100f,80f);
        var method=OmrScoreInterpreter.class.getDeclaredMethod("countAugmentationDots",List.class,type,float.class,byte[].class,int.class,int.class,boolean.class);method.setAccessible(true);
        return (int)method.invoke(null,List.of(),head,16f,gray,W,H,false);
    }
    @Test public void faintRoundedDotKeepsItsDurationMark()throws Exception {dot(126,80);assertEquals(1,count());}
    @Test public void twoFaintDotsRemainTwo()throws Exception {dot(124,80);dot(136,80);assertEquals(2,count());}
    @Test public void anAboveHeadMarkIsNotAnAugmentationDot()throws Exception {dot(126,65);assertEquals(0,count());}
    @Test public void aUniformPaleSpeckLacksADarkCore()throws Exception {dot(126,80);for(int i=0;i<gray.length;i++)if((gray[i]&255)==165)gray[i]=(byte)195;assertEquals(0,count());}
    @Test public void aSolidSquareIsNotARoundedFaintDot()throws Exception {
        for(int y=77;y<=83;y++)for(int x=123;x<=129;x++)gray[y*W+x]=(byte)195;
        for(int y=79;y<=81;y++)for(int x=125;x<=127;x++)gray[y*W+x]=(byte)165;assertEquals(0,count());
    }
    @Test public void aFaintStaffCrossingDoesNotSupplyAnIsolatedDot()throws Exception {
        dot(126,80);for(int x=20;x<220;x++)gray[80*W+x]=(byte)195;assertEquals(0,count());
    }
    @Test public void aTinyDarkCoreIsInsufficient()throws Exception {
        dot(126,80);for(int i=0;i<gray.length;i++)if((gray[i]&255)==165)gray[i]=(byte)195;
        gray[80*W+126]=(byte)165;assertEquals(0,count());
    }
    @Test public void anOrdinaryDarkDotStillCountsOnce()throws Exception {
        dot(126,80);for(int i=0;i<gray.length;i++)if((gray[i]&255)<205)gray[i]=0;assertEquals(1,count());
    }
    @Test public void sourcePixelsArePreserved()throws Exception {dot(126,80);var before=gray.clone();count();assertArrayEquals(before,gray);}
}
