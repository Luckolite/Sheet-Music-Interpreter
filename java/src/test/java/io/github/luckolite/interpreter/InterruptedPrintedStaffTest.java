// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original interrupted rules and generated texture; no score or photograph pixels. */
public class InterruptedPrintedStaffTest {
    private float bottom(float slope,int mode)throws Exception {
        int w=2048,h=1000;byte[] gray=new byte[w*h],labels=new byte[w*h];Arrays.fill(gray,(byte)255);
        List<MeasureRegion> regions=new ArrayList<>();
        for(int row=0;row<4;row++) {
            int top=100+row*220;
            regions.add(new MeasureRegion(.04f,.96f,(top-20f)/h,(top+90f)/h));
            for(int x=60;x<w-60;x++)for(int line=0;line<5;line++)for(int dy=0;dy<2;dy++) {
                int y=Math.round(top+line*14+slope*(x-w*.5f))+dy;
                labels[(y+(row==2?16:0))*w+x]=4;
                boolean keep=row!=2||(mode<2||mode>=4)&&x%180<155&&(mode!=1||line!=4);
                if(row==2&&mode>=4&&(line==1||mode==5&&line==3)&&x%180>=140)keep=false;
                if(keep)gray[y*w+x]=0;
            }
        }
        if(mode==2) {
            Random random=new Random(70913);
            for(int y=520;y<630;y++)for(int x=60;x<w-60;x++)
                gray[y*w+x]=(byte)(135+random.nextInt(121));
        }
        if(mode==3)for(int x=60;x<w-60;x++)for(int line=0;line<5;line++)for(int dy=-5;dy<=5;dy++)
            gray[(Math.round(540+line*14+slope*(x-w*.5f))+dy)*w+x]=(byte)100;
        var find=OmrScoreInterpreter.class.getDeclaredMethod("findStaffs",byte[].class,byte[].class,int.class,int.class,List.class);
        find.setAccessible(true);
        for(Object s:(List<?>)find.invoke(null,labels,gray,w,h,regions)) {
            var top=s.getClass().getDeclaredField("top");top.setAccessible(true);
            if(top.getFloat(s)<525||top.getFloat(s)>570)continue;
            var value=s.getClass().getDeclaredField("pitchBottom");value.setAccessible(true);return value.getFloat(s);
        }
        throw new AssertionError("Target semantic staff missing");
    }
    @Test public void interruptedDownhillRulesCalibrateShiftedSeed()throws Exception {assertEquals(596.5f,bottom(.0108f,0),1f);}
    @Test public void interruptedUphillRulesCalibrateShiftedSeed()throws Exception {assertEquals(596.5f,bottom(-.0108f,0),1f);}
    @Test public void fourRulesCannotRephaseStaff()throws Exception {assertTrue(bottom(.0108f,1)>601);}
    @Test public void generatedTextureCannotRephaseStaff()throws Exception {assertTrue(bottom(.0108f,2)>601);}
    @Test public void broadShadowBandsCannotRephaseStaff()throws Exception {assertTrue(bottom(.0108f,3)>601);}
    @Test public void fourStrongRulesSupportOnePartlyObscuredRule()throws Exception {assertEquals(596.5f,bottom(.0108f,4),1f);}
    @Test public void twoPartlyObscuredRulesCannotRephaseStaff()throws Exception {
        float detected=bottom(.0108f,5);
        assertTrue("partly obscured bottom="+detected,detected>601);
    }
}
