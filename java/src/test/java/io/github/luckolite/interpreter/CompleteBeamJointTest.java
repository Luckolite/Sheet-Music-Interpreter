// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;

/** Private source-free finite cap, literal shaft and exact member controls. */
public final class CompleteBeamJointTest {
    @org.junit.Test public void sourceFreeOriginals() throws Exception { originals(); }
    private static final int W=400,H=240;
    private record Input(byte[] gray,byte[] labels,int[] members) {}
    private static Input field(int shift) {
        byte[] g=new byte[W*H],labels=new byte[W*H];Arrays.fill(g,(byte)255);
        for(int rail=0;rail<5;rail++)for(int x=10;x<=390;x++)g[(100+16*rail+shift)*W+x]=0;
        for(int x=110;x<=166;x++) {
            int bottom=146-Math.round((x-110)*8f/56)+shift;
            for(int y=bottom-9;y<=bottom;y++)g[y*W+x]=0;
        }
        for(int[] shaft:new int[][]{{110,84,146},{111,84,146},{112,84,146},{166,76,138},{167,76,138},{168,76,138}})
            for(int y=shaft[1]+shift;y<=shaft[2]+shift;y++)g[y*W+shaft[0]]=0;
        for(int[] c:new int[][]{{122,84},{178,76}})for(int y=c[1]-8;y<=c[1]+8;y++)for(int x=c[0]-11;x<=c[0]+11;x++)
            if((x-c[0])*(x-c[0])/121.+(y-c[1])*(y-c[1])/64.<=1)g[(y+shift)*W+x]=0;
        int[] members=new int[12];int n=0;
        for(int y=139+shift;y<=141+shift;y++)for(int x=115;x<=118;x++){int p=y*W+x;members[n++]=p;labels[p]=2;}
        return new Input(g,labels,members);
    }
    private static LiteralStaffCompleteBeamJoint.Result observe(Input in,int shift,long allowance) {
        return LiteralStaffCompleteBeamJoint.observe(in.gray(),W,H,110,166,100+shift,16,116.5f,140+shift,
                115,139+shift,118,141+shift,-8f/56,new LiteralStaffCompleteBeamJoint.Budget(allowance),
                new LiteralStaffCompleteBeamJoint.Ownership(in.labels(),in.members(),146+shift,138+shift,1));
    }
    private static void require(boolean ok,String text){if(!ok)throw new AssertionError(text);}
    static void originals() throws Exception {

        int cases=0;
        for(int shift:new int[]{0,1,-1}) {
            Input in=field(shift);byte[] before=in.gray().clone(),lab=in.labels().clone();int[] ids=in.members().clone();
            var r=observe(in,shift,32_000_000);require(r.complete(),"complete finite joint "+shift+":"+r);cases++;
            require(Arrays.equals(before,in.gray())&&Arrays.equals(lab,in.labels())&&Arrays.equals(ids,in.members()),"all inputs immutable");cases++;
            for(boolean right:new boolean[]{false,true})for(int tone:new int[]{0,130,170,200,225,235,245,254})for(boolean hollow:new boolean[]{false,true}) {
                Input cue=field(shift);int cx=right?166:110,cy=right?141:137;
                for(int y=cy-8;y<=cy+8;y++)for(int x=cx-10;x<=cx+10;x++) {
                    double d=(x-cx)*(x-cx)/81.+(y-cy)*(y-cy)/49.;
                    if(d<=1&&(!hollow||d>=.5))cue.gray()[(y+shift)*W+x]=(byte)Math.min(cue.gray()[(y+shift)*W+x]&255,tone);
                }
                require(!observe(cue,shift,32_000_000).complete(),"genuine MIN cap cue retained right="+right+" tone="+tone+" hollow="+hollow+" shift="+shift);cases++;
            }
        }
        Input missing=field(0);require(!observe(new Input(missing.gray(),missing.labels(),Arrays.copyOf(missing.members(),11)),0,32_000_000).complete(),"omitted current member refused");cases++;
        Input duplicate=field(0);duplicate.members()[1]=duplicate.members()[0];require(!observe(duplicate,0,32_000_000).complete(),"duplicate member refused");cases++;
        Input wrongLabel=field(0);wrongLabel.labels()[wrongLabel.members()[0]]=1;require(!observe(wrongLabel,0,32_000_000).complete(),"stale current label refused");cases++;
        Input escaped=field(0);escaped.labels()[139*W+114]=2;require(!observe(escaped,0,32_000_000).complete(),"connected semantic member outside bounds refused");cases++;
        Input disconnected=field(0);disconnected.labels()[140*W+116]=0;require(!observe(disconnected,0,32_000_000).complete(),"missing semantic raw cell refused");cases++;
        Input shaftBreak=field(0);shaftBreak.gray()[125*W+111]=(byte)255;require(!observe(shaftBreak,0,32_000_000).complete(),"literal shaft paper-row profile break refused");cases++;
        Input outerDot=field(0);outerDot.gray()[140*W+109]=(byte)254;require(!observe(outerDot,0,32_000_000).complete(),"faint finite cap exterior intrusion refused");cases++;
        Input contactBreak=field(0);for(int y=133;y<=148;y++)if(y!=148)contactBreak.gray()[y*W+112]=(byte)255;
        require(!observe(contactBreak,0,32_000_000).complete(),"complete shaft seam required");cases++;
        require(!observe(field(0),0,0).complete(),"zero joint budget refuses");cases++;
        Input foreign=field(0);int[] elsewhere=foreign.members().clone();for(int i=0;i<elsewhere.length;i++)elsewhere[i]+=40;
        require(!observe(new Input(foreign.gray(),foreign.labels(),elsewhere),0,32_000_000).complete(),"foreign members refuse");cases++;
        System.out.println("PASS "+cases+" complete joint cases;96 MIN cap cues retained; current membership immutable; no OMR/removal.");
    }
}
