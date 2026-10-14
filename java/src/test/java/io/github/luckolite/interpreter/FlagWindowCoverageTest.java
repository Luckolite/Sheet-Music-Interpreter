// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
/** Original procedural thin-root hooks; no score pixels, names or coordinates are fixtures. */
public class FlagWindowCoverageTest {
    private boolean hook(float gap, boolean up, int control) throws Exception {
        int width=180,height=240,stem=80,end=up?50:180,sign=up?1:-1;
        byte[] gray=new byte[width*height],labels=new byte[gray.length];Arrays.fill(gray,(byte)255);
        int root=(int)Math.floor(stem+gap*.25f),turn=(int)Math.floor(gap*1.15f);
        for(int d=0;d<=Math.round(gap*2.3f);d++) {
            int x=d<=turn?root:root+(int)Math.ceil((d-turn)*.8f),y=end+sign*d;
            if(control==1 && d<=turn)continue; // Disconnected outer curve, no proximal root.
            gray[y*width+x]=0;
            if(control==3)labels[y*width+x]=2; // Head ink cannot prove a flag.
        }
        for(int x=stem;x<=root;x++)gray[end*width+x]=0;
        if(control==1)for(int x=stem;x<=root;x++)gray[end*width+x]=(byte)255;
        if(control==2) { // A bow continues past the endpoint on the other side of the shaft.
            for(int dx=Math.max(3,Math.round(gap*.3f));dx<=Math.round(gap*1.2f);dx++) {
                int d=3+(dx-Math.max(3,Math.round(gap*.3f)))/2;
                gray[(end-sign*d)*width+stem-dx]=0;
            }
        }
        if(control==4)for(int d=0;d<=Math.round(gap*2.3f);d++)for(int x=0;x<width;x++) {
            gray[(end+sign*d)*width+x]=0;labels[(end+sign*d)*width+x]=1;
        }
        Class<?> type=Class.forName(OmrScoreInterpreter.class.getName()+"$Component");
        var constructor=type.getDeclaredConstructors()[0];constructor.setAccessible(true);
        float headY=end+sign*gap*4;
        Object head=constructor.newInstance(200,60,80,Math.round(headY-5),Math.round(headY+5),70f,headY);
        var method=OmrScoreInterpreter.class.getDeclaredMethod("hasCurvedFlag",byte[].class,byte[].class,
                int.class,int.class,type,float.class,int.class,int.class,boolean.class);method.setAccessible(true);
        return (boolean)method.invoke(null,labels,gray,width,height,head,gap,stem,end,up);
    }
    @Test public void halfPixelRootCoverageIsStableAcrossGapRounding() throws Exception {
        for(boolean up:new boolean[]{true,false})for(float gap:new float[]{13.75f,14f})
            assertTrue("Root lost at gap="+gap+" upward="+up,hook(gap,up,0));
    }
    @Test public void detachedCurvesCannotSupplyRootCoverage() throws Exception {
        for(boolean up:new boolean[]{true,false})assertFalse(hook(14,up,1));
    }
    @Test public void returningBowsStillCannotBecomeFlags() throws Exception {
        for(boolean up:new boolean[]{true,false})assertFalse(hook(14,up,2));
    }
    @Test public void noteheadInkStillCannotBecomeAFlag() throws Exception {
        for(boolean up:new boolean[]{true,false})assertFalse(hook(14,up,3));
    }
    @Test public void staffRuleBandsStillCannotBecomeFlags() throws Exception {
        for(boolean up:new boolean[]{true,false})assertFalse(hook(14,up,4));
    }
}
