// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class LongCrossStaffStemTest {
    @Test public void crossStaffStemDoesNotSplitTheLowerStaffIntoAnotherBar() {
        int w=480,h=320;byte[] labels=new byte[w*h],gray=new byte[w*h];Arrays.fill(gray,(byte)255);
        for(int top:new int[]{60,210})for(int y=top;y<=top+40;y+=10)for(int x=30;x<=460;x++){labels[y*w+x]=4;gray[y*w+x]=0;}
        for(int x:new int[]{30,310,460})for(int y=60;y<=250;y++){labels[y*w+x]=1;gray[y*w+x]=0;}
        for(int y=80;y<=270;y++){labels[y*w+220]=1;gray[y*w+220]=0;}
        for(int y=76;y<=84;y++)for(int x=220;x<=234;x++)if((x-227)*(x-227)/49d+(y-80)*(y-80)/16d<=1){labels[y*w+x]=2;gray[y*w+x]=0;}
        var measures=OmrMeasurePostProcessor.process(labels,gray,w,h);
        assertEquals(2,measures.size());
    }
    private boolean owner(boolean attached,int cx)throws Exception {
        int w=160,h=380;byte[] labels=new byte[w*h];
        for(int y=60;y<=315;y++)labels[y*w+80]=1;
        if(!attached)for(int y=95;y<=105;y++)labels[y*w+80]=0;
        for(int y=56;y<=64;y++)for(int x=cx-6;x<=cx+6;x++)if((x-cx)*(x-cx)/36d+(y-60)*(y-60)/16d<=1)labels[y*w+x]=2;
        var m=OmrMeasurePostProcessor.class.getDeclaredMethod("distantHeadOnSameStem",byte[].class,int.class,int.class,int.class,int.class,int.class,float.class);m.setAccessible(true);
        return (boolean)m.invoke(null,labels,w,h,80,280,312,8f);
    }
    @Test public void aLongContinuousStemCanReachItsHead(){try{assertTrue(owner(true,84));}catch(Exception e){throw new AssertionError(e);}}
    @Test public void gapToPreviousSystemDoesNotHideRealBar(){try{assertFalse(owner(false,84));}catch(Exception e){throw new AssertionError(e);}}
    @Test public void nearbyUnattachedHeadDoesNotHideRealBar(){try{assertFalse(owner(true,110));}catch(Exception e){throw new AssertionError(e);}}
    @Test public void nearbyHeadBesideRealBarDoesNotCountAsAttached()throws Exception {
        assertFalse(owner(true,90));
    }
}
