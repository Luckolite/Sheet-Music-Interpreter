// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original complete rules whose edge tilt exceeds half a diatonic staff step. */
public class QuarterGapTiltTest {
    private float slope(float tilt,boolean complete)throws Exception {
        int w=2048,h=1000;byte[] gray=new byte[w*h],labels=new byte[w*h];Arrays.fill(gray,(byte)255);
        List<MeasureRegion> regions=new ArrayList<>();
        for(int row=0;row<4;row++) {
            int top=100+row*220;
            regions.add(new MeasureRegion(.04f,.96f,(top-20f)/h,(top+80f)/h));
            for(int x=60;x<w-60;x++)for(int line=0;line<5;line++)for(int dy=0;dy<2;dy++) {
                int y=Math.round(top+line*14+tilt*(x-w*.5f))+dy;
                if(row!=2||complete||line!=4)gray[y*w+x]=0;
                int semantic=y;labels[semantic*w+x]=4;
            }
        }
        var find=OmrScoreInterpreter.class.getDeclaredMethod("findStaffs",byte[].class,byte[].class,int.class,int.class,List.class);
        find.setAccessible(true);
        for(Object s:(List<?>)find.invoke(null,labels,gray,w,h,regions)) {
            var top=s.getClass().getDeclaredField("top");top.setAccessible(true);
            if(top.getFloat(s)<530||top.getFloat(s)>550)continue;
            var value=s.getClass().getDeclaredField("pitchSlope");value.setAccessible(true);return value.getFloat(s);
        }
        throw new AssertionError("Target staff missing");
    }
    @Test public void smallDownhillTiltCalibratesCenteredSeed()throws Exception {assertEquals(.0036f,slope(.0036f,true),.0008f);}
    @Test public void smallUphillTiltCalibratesCenteredSeed()throws Exception {assertEquals(-.0036f,slope(-.0036f,true),.0008f);}
    @Test public void flatStaffKeepsZeroSlope()throws Exception {assertEquals(0,slope(0,true),.0001f);}
    @Test public void incompletePrintedStaffCannotSupplySlope()throws Exception {assertEquals(0,slope(.0036f,false),.0001f);}
}
