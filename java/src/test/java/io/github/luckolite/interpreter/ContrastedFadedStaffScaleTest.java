// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original parallel rules with independent staff-scale witnesses. */
public class ContrastedFadedStaffScaleTest {
    static final int W=800,H=1100;
    byte[] page(int lines,int shade,int thickness,int right) {
        byte[] gray=new byte[W*H];Arrays.fill(gray,(byte)255);
        for(int line=0;line<lines;line++)for(int y=600+line*15-thickness/2;y<=600+line*15+thickness/2;y++)
            for(int x=40;x<right;x++)gray[y*W+x]=(byte)shade;
        return gray;
    }
    float[] calibrate(byte[] gray,int witnesses,boolean expanded,boolean established)throws Exception {
        Class<?> type=Class.forName("io.github.luckolite.interpreter.OmrScoreInterpreter$Staff");
        var constructor=type.getDeclaredConstructor(float.class,float.class,float.class);constructor.setAccessible(true);
        List<Object> staffs=new ArrayList<>();
        for(int i=0;i<witnesses;i++)staffs.add(constructor.newInstance(100f+i*160,160f+i*160,15f));
        Object target=expanded?constructor.newInstance(598f,662f,16f):constructor.newInstance(604f,656f,13f);
        var phase=type.getDeclaredField("printedPhase");phase.setAccessible(true);phase.setBoolean(target,established);
        staffs.add(target);
        var method=OmrScoreInterpreter.class.getDeclaredMethod("calibrateContrastedFadedStaffs",byte[].class,int.class,int.class,List.class,float.class);method.setAccessible(true);
        method.invoke(null,gray,W,H,staffs,0f);
        var gap=type.getDeclaredField("pitchGap");gap.setAccessible(true);
        var bottom=type.getDeclaredField("pitchBottom");bottom.setAccessible(true);
        return new float[]{bottom.getFloat(target),gap.getFloat(target)};
    }
    @Test public void completePaleRulesCorrectModeratelyCompressedScale()throws Exception {
        assertArrayEquals(new float[]{660,15},calibrate(page(5,215,1,760),3,false,false),.1f);
    }
    @Test public void completePaleRulesCorrectExpandedScale()throws Exception {
        assertArrayEquals(new float[]{660,15},calibrate(page(5,215,1,760),3,true,false),.1f);
    }
    @Test public void missingOuterRuleCannotCalibrate()throws Exception {
        assertEquals(13,calibrate(page(4,215,1,760),3,false,false)[1],.1f);
    }
    @Test public void sixthRuleMakesPhaseAmbiguous()throws Exception {
        assertEquals(13,calibrate(page(6,215,1,760),3,false,false)[1],.1f);
    }
    @Test public void broadBandsCannotCalibrate()throws Exception {
        assertEquals(13,calibrate(page(5,215,7,760),3,false,false)[1],.1f);
    }
    @Test public void shortParallelStrokesCannotCalibrate()throws Exception {
        assertEquals(13,calibrate(page(5,215,1,300),3,false,false)[1],.1f);
    }
    @Test public void insufficientIndependentSystemsCannotCalibrate()throws Exception {
        assertEquals(13,calibrate(page(5,215,1,760),2,false,false)[1],.1f);
    }
    @Test public void nearlyWhiteRulesCannotCalibrate()throws Exception {
        assertEquals(13,calibrate(page(5,240,1,760),3,false,false)[1],.1f);
    }
    @Test public void establishedPrintedPhaseIsPreserved()throws Exception {
        assertEquals(13,calibrate(page(5,215,1,760),3,false,true)[1],.1f);
    }
    @Test public void inputPixelsArePreserved()throws Exception {
        byte[] gray=page(5,215,1,760),copy=gray.clone();calibrate(gray,3,false,false);assertArrayEquals(copy,gray);
    }
}
