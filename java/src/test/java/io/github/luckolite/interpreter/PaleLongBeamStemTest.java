// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;
/** Original faded shafts distinguish complete beam evidence from faint quarters. */
public class PaleLongBeamStemTest {
    static final int W=360,H=320;
    final byte[] gray=new byte[W*H],labels=new byte[W*H];
    boolean down;
    int y(int row){return down?H-1-row:row;}
    void rect(int l,int r,int t,int b,int ink){for(int yy=t;yy<=b;yy++)for(int x=l;x<=r;x++)gray[y(yy)*W+x]=(byte)ink;}
    void setup(boolean down,int end,boolean fringe,int bands,boolean partialSemantic){
        this.down=down;Arrays.fill(gray,(byte)250);int x=down?90:110;
        if(fringe)rect(x-3,x+3,end,220,244);
        rect(x,x+1,end,220,fringe?240:232);
        for(int i=0;i<bands;i++)rect(x,x+85,end+i*12,end+i*12+6,35);
        if(partialSemantic)for(int yy=200;yy<=216;yy++)labels[y(yy)*W+x]=1;
    }
    int beams()throws Exception{
        var ct=Class.forName(OmrScoreInterpreter.class.getName()+"$Component");var cc=ct.getDeclaredConstructors()[0];cc.setAccessible(true);
        var head=cc.newInstance(220,90,110,y(220)-7,y(220)+7,100f,(float)y(220));
        var st=Class.forName(OmrScoreInterpreter.class.getName()+"$Staff");var sc=st.getDeclaredConstructor(float.class,float.class,float.class);sc.setAccessible(true);
        var m=OmrScoreInterpreter.class.getDeclaredMethod("detectBeamCount",byte[].class,byte[].class,int.class,int.class,ct,st);m.setAccessible(true);
        return (int)m.invoke(null,labels,gray,W,H,head,sc.newInstance(130f,194f,16f));
    }
    @Test public void longUpShaftNeedsItsTwoBeams()throws Exception{setup(false,110,false,2,false);assertEquals(2,beams());}
    @Test public void longDownShaftNeedsItsTwoBeams()throws Exception{setup(true,110,false,2,false);assertEquals(2,beams());}
    @Test public void veryPaleFringeDoesNotHideTheStemCore()throws Exception{setup(false,150,true,2,false);assertEquals(2,beams());}
    @Test public void partialSemanticStemStillUsesCompleteRawBeamEvidence()throws Exception{setup(false,110,false,2,true);assertEquals(2,beams());}
    @Test public void aLongQuarterKeepsZeroBeams()throws Exception{setup(false,110,false,0,false);assertEquals(0,beams());}
    @Test public void oneDarkBandCannotProveTheLongPaleShaft()throws Exception{setup(false,110,false,1,false);assertEquals(0,beams());}
    @Test public void excessiveReachCannotBorrowAnotherGroup()throws Exception{setup(false,65,false,2,false);assertEquals(0,beams());}
    @Test public void inputMasksRemainUnchanged()throws Exception{setup(false,110,false,2,false);var a=gray.clone();var b=labels.clone();beams();assertArrayEquals(a,gray);assertArrayEquals(b,labels);}
    @Test public void beamEndpointAtLowerEdgeStillCountsFullOuterBand()throws Exception {
        Arrays.fill(gray,(byte)255);
        rect(110,111,82,220,0);
        for(int y=82;y<=220;y++)labels[y*W+110]=1;
        for(int i=0;i<3;i++)rect(96,180,74+i*13,82+i*13,0);
        rect(106,114,74,81,255);
        assertEquals(3,beams());
    }
}
