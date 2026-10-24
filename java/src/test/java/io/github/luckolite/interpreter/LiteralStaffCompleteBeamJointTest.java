// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;

/** Private source-free envelope controls; no OMR, model, rendering or head removal. */
public final class LiteralStaffCompleteBeamJointTest {
    @org.junit.Test public void sourceFreeOriginals() throws Exception { originals(); }
    private static final int W=400,H=240;
    private static byte[] field(int tone,boolean hollow,int shift) {
        byte[] g=new byte[W*H];Arrays.fill(g,(byte)255);
        for(int k=0;k<5;k++)for(int x=10;x<=390;x++)g[(100+16*k+shift)*W+x]=0;
        for(int x=110;x<=166;x++) {
            int bottom=146-Math.round((x-110)*8f/56)+shift;
            for(int y=bottom-9;y<=bottom;y++)g[y*W+x]=0;
        }
        for(int[] s:new int[][]{{110,84,146},{111,84,146},{112,84,146},{166,76,138},{167,76,138},{168,76,138}})
            for(int y=s[1]+shift;y<=s[2]+shift;y++)g[y*W+s[0]]=0;
        for(int[] c:new int[][]{{122,84},{178,76}})for(int y=c[1]-8;y<=c[1]+8;y++)for(int x=c[0]-11;x<=c[0]+11;x++)
            if((x-c[0])*(x-c[0])/121.0+(y-c[1])*(y-c[1])/64.0<=1)g[(y+shift)*W+x]=0;
        if(tone>=0)for(int y=129;y<=145;y++)for(int x=146;x<=166;x++) {
            double d=(x-156)*(x-156)/81.0+(y-137)*(y-137)/49.0;
            if(d<=1&&(!hollow||d>=.5))g[(y+shift)*W+x]=(byte)Math.min(g[(y+shift)*W+x]&255,tone);
        }
        return g;
    }
    private static LiteralStaffCompleteBeamJoint.Result observe(byte[]g,int shift,long budget) {
        return LiteralStaffCompleteBeamJoint.observe(g,W,H,110,166,100+shift,16,157,137+shift,150,133+shift,164,141+shift,-8f/56,new LiteralStaffCompleteBeamJoint.Budget(budget));
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void originals() throws Exception {

        int passed=0;
        for(int shift:new int[]{0,1,-1}) {
            byte[] base=field(-1,false,shift),before=base.clone();var r=observe(base,shift,32_000_000);
            require(r.complete(),"independent literal envelope positive "+shift+":"+r);passed++;
            require(Arrays.equals(base,before),"input immutable");passed++;
            for(int tone:new int[]{0,130,170,200,225,235,245,254})for(boolean hollow:new boolean[]{false,true}) {
                byte[] g=field(tone,hollow,shift),saved=g.clone();var result=observe(g,shift,32_000_000);
                require(!result.complete(),"MIN head retained "+tone+" hollow="+hollow+" shift="+shift);passed++;
                require(Arrays.equals(g,saved),"MIN input immutable");
            }
        }
        require(!observe(field(-1,false,0),0,0).complete(),"zero budget refuses");passed++;
        var exhausted=new LiteralStaffCompleteBeamJoint.Budget(1);
        var first=LiteralStaffCompleteBeamJoint.observe(field(-1,false,0),W,H,110,166,100,16,157,137,150,133,164,141,-8f/56,exhausted);
        var next=LiteralStaffCompleteBeamJoint.observe(field(-1,false,0),W,H,110,166,100,16,157,137,150,133,164,141,-8f/56,exhausted);
        require(!first.complete()&&!next.complete(),"sticky exhaustion");passed++;
        byte[] noBeam=field(-1,false,0);
        for(int x=114;x<=162;x++)for(int y=123;y<=153;y++)if(y!=132&&y!=148)noBeam[y*W+x]=(byte)255;
        require(!observe(noBeam,0,32_000_000).complete(),"missing beam extent");passed++;
        byte[] interrupted=field(-1,false,0);
        for(int y=120;y<=153;y++)if(y!=132&&y!=148)interrupted[y*W+140]=(byte)255;
        require(!observe(interrupted,0,32_000_000).complete(),"missing body column cannot borrow staff");passed++;
        byte[] pinhole=field(-1,false,0);pinhole[137*W+146]=(byte)255;
        require(!observe(pinhole,0,32_000_000).complete(),"internal paper cannot be filled");passed++;
        byte[] bent=field(-1,false,0);
        for(int x=114;x<=162;x++)for(int y=115;y<=153;y++)if(y!=116&&y!=132&&y!=148)bent[y*W+x]=(byte)255;
        for(int x=114;x<=162;x++) {
            double phase=(x-110)/56.0;
            int bottom=146-Math.round((x-110)*8f/56)-(int)Math.round(6*Math.sin(Math.PI*phase));
            for(int y=bottom-9;y<=bottom;y++)bent[y*W+x]=0;
        }
        require(!observe(bent,0,32_000_000).complete(),"constant thickness curved body is not a parallel beam");passed++;
        for(int shift:new int[]{0,1,-1}) {
            byte[] coowned=field(-1,false,shift);
            for(int rail=0;rail<5;rail++)for(int dy=0;dy<3;dy++)for(int x=10;x<=390;x++)coowned[(100+rail*16+shift+dy)*W+x]=0;
            require(observe(coowned,shift,32_000_000).complete(),"three-row opaque staff co-membership "+shift);passed++;
        }
        require(CoownedParallelBodyIntervals.fits(new int[]{10,10},new int[]{19,19},new int[]{10,10},new int[]{19,19},10),"literal interval fit");passed++;
        require(!CoownedParallelBodyIntervals.fits(new int[]{10,10},new int[]{19,19},new int[]{11,10},new int[]{19,19},10),"potential edge cannot exclude observed ink");passed++;
        require(!CoownedParallelBodyIntervals.fits(new int[]{10,10},new int[]{19,19},new int[]{10},new int[]{19,19},10),"interval shape mismatch");passed++;
        System.out.println("PASS "+passed+" source-free cases; all48 MIN filled/hollow/shift variants retained; no note ownership asserted.");
    }
}
