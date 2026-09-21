// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class StaffRuleFringeBeamTest {
    private final int w=300,h=200;
    private final byte[] gray=new byte[w*h],labels=new byte[w*h];
    private void rect(int x,int y,int ww,int hh,int label) {
        for(int yy=y;yy<y+hh;yy++)for(int xx=x;xx<x+ww;xx++){gray[yy*w+xx]=0;labels[yy*w+xx]=(byte)label;}
    }
    private int bands()throws Exception {
        var type=Class.forName(OmrScoreInterpreter.class.getName()+"$Staff");
        var ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);var staff=ctor.newInstance(80f,136f,14f);
        var method=OmrScoreInterpreter.class.getDeclaredMethod("thickNonHeadBands",byte[].class,byte[].class,
                int.class,int.class,int.class,int.class,int.class,type);
        method.setAccessible(true);return (int)method.invoke(null,gray,labels,w,h,150,65,98,staff);
    }
    private void prepare() {
        Arrays.fill(gray,(byte)255);for(int i=0;i<5;i++)rect(20,79+i*14,260,3,4);
    }
    @Test public void aShortOnePixelFringeIsNotAnExtraBeam()throws Exception {
        prepare();rect(145,78,11,1,0);assertEquals(0,bands());
    }
    @Test public void aRealBeamBeyondTheStaffThicknessRemains()throws Exception {
        prepare();rect(145,77,40,6,1);assertEquals(1,bands());
    }
    @Test public void aThinBeamAwayFromTheStaffRemains()throws Exception {
        prepare();rect(140,68,60,4,1);assertEquals(1,bands());
    }
    @Test public void blurredWhiteGapBetweenTwoDarkBeamCoresIsRecovered()throws Exception {
        Arrays.fill(gray,(byte)255);
        rect(140,65,60,16,1);
        for(int y=71;y<=74;y++)for(int x=140;x<200;x++)gray[y*w+x]=60;
        assertEquals(2,bands());
    }
    @Test public void oneSolidBroadBeamIsNotSplitWithoutTwoCores()throws Exception {
        Arrays.fill(gray,(byte)255);rect(140,65,60,16,1);assertEquals(1,bands());
    }
    @Test public void twoBlurRowsAtOuterProbeDoNotAddASecondaryBeam()throws Exception {
        prepare();rect(147,78,8,1,0);rect(147,82,8,1,0);
        var type=Class.forName(OmrScoreInterpreter.class.getName()+"$Staff");
        var ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);var staff=ctor.newInstance(80f,146f,16.5f);
        var method=OmrScoreInterpreter.class.getDeclaredMethod("thickNonHeadBands",byte[].class,byte[].class,
                int.class,int.class,int.class,int.class,int.class,type,int.class);
        method.setAccessible(true);assertEquals(0,((Number)method.invoke(null,gray,labels,w,h,150,70,86,staff,144)).intValue());
    }
}
