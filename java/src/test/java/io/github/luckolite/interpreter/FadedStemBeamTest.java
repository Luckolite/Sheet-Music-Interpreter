// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;
/** Original faint stems and darker beam bodies, with absent semantic stem labels. */
public class FadedStemBeamTest {
    private static final int W=300,H=240;
    private final byte[] gray=new byte[W*H],labels=new byte[W*H];
    private boolean down;
    private int y(int row){return down?H-1-row:row;}
    private int stemX(){return down?90:110;}
    private void rect(int left,int right,int top,int bottom,int ink) {
        for(int row=top;row<=bottom;row++)for(int x=left;x<=right;x++)gray[y(row)*W+x]=(byte)ink;
    }
    private void setup(boolean down,int beamThickness) {
        this.down=down;Arrays.fill(gray,(byte)250);
        rect(stemX()-1,stemX()+1,90,150,225);
        if(beamThickness>0)rect(stemX(),stemX()+80,90,89+beamThickness,35);
    }
    private int beams()throws Exception {
        var type=Class.forName(OmrScoreInterpreter.class.getName()+"$Component");var ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);
        var head=ctor.newInstance(180,90,110,y(150)-6,y(150)+6,100f,(float)y(150));
        var st=Class.forName(OmrScoreInterpreter.class.getName()+"$Staff");var sc=st.getDeclaredConstructor(float.class,float.class,float.class);sc.setAccessible(true);
        var method=OmrScoreInterpreter.class.getDeclaredMethod("detectBeamCount",byte[].class,byte[].class,int.class,int.class,type,st);method.setAccessible(true);
        return (int)method.invoke(null,labels,gray,W,H,head,sc.newInstance(40f,104f,16f));
    }
    @Test public void faintUpStemReachesItsDarkBeam()throws Exception {setup(false,7);assertEquals(1,beams());}
    @Test public void faintDownStemReachesItsDarkBeam()throws Exception {setup(true,7);assertEquals(1,beams());}
    @Test public void faintQuarterHasNoBeam()throws Exception {setup(false,0);assertEquals(0,beams());}
    @Test public void aThinRuleCannotProveABeam()throws Exception {setup(false,1);assertEquals(0,beams());}
    @Test public void aThinSlurCannotProveABeam()throws Exception {setup(false,2);assertEquals(0,beams());}
    @Test public void aBroadGrayPatchCannotProveAStem()throws Exception {setup(false,7);rect(100,120,105,137,225);assertEquals(0,beams());}
    @Test public void disconnectedBeamCannotBorrowAStem()throws Exception {setup(false,7);rect(108,112,99,105,250);assertEquals(0,beams());}
    @Test public void sparseStemLabelsWithoutInkAreInsufficient()throws Exception {
        setup(false,7);rect(109,111,100,145,250);for(int row=125;row<=130;row++)labels[row*W+110]=1;assertEquals(0,beams());
    }
    @Test public void analysisPreservesSourceArrays()throws Exception {setup(false,7);var g=gray.clone();var l=labels.clone();beams();assertArrayEquals(g,gray);assertArrayEquals(l,labels);}
}
