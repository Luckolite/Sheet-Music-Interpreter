// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original antialiased leaning stems and returning hooks; no score pixels. */
public class LeaningFlagStemTest {
    private static final int W=260,H=220;
    private final byte[] gray=new byte[W*H],labels=new byte[W*H];
    private boolean down;
    private int y(int row){return down?H-1-row:row;}
    private void pixel(int x,int row,int shade,int label){gray[y(row)*W+x]=(byte)shade;labels[y(row)*W+x]=(byte)label;}
    private void setup(boolean down,boolean hook) {
        this.down=down;Arrays.fill(gray,(byte)255);Arrays.fill(labels,(byte)0);
        for(int row=50;row<=110;row++) {
            int x=row>=72?100:row>=58?101:102;
            pixel(x-1,row,190,1);pixel(x+1,row,190,1);pixel(x,row,0,1);
        }
        if(hook)for(int d=0;d<=34;d++) {
            int x=102+Math.round(18*(float)Math.sin(Math.PI*d/34));
            for(int dx=0;dx<3;dx++)pixel(x+dx,50+d,0,5);
        }
    }
    private int beams()throws Exception {
        var type=Class.forName(OmrScoreInterpreter.class.getName()+"$Component");
        var ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);
        var head=ctor.newInstance(180,down?100:80,down?120:100,y(110)-6,y(110)+6,down?110f:90f,(float)y(110));
        var st=Class.forName(OmrScoreInterpreter.class.getName()+"$Staff");
        var sc=st.getDeclaredConstructor(float.class,float.class,float.class);sc.setAccessible(true);
        var staff=sc.newInstance(30f,94f,16f);
        var m=OmrScoreInterpreter.class.getDeclaredMethod("detectBeamCount",byte[].class,byte[].class,int.class,int.class,type,st);
        m.setAccessible(true);return (int)m.invoke(null,labels,gray,W,H,head,staff);
    }
    @Test public void leaningUpStemRetainsItsEighthFlag()throws Exception {setup(false,true);assertEquals(1,beams());}
    @Test public void leaningDownStemRetainsItsEighthFlag()throws Exception {setup(true,true);assertEquals(1,beams());}
    @Test public void leaningQuarterHasNoInventedFlag()throws Exception {setup(false,false);assertEquals(0,beams());}
    @Test public void downStemQuarterHasNoInventedFlag()throws Exception {setup(true,false);assertEquals(0,beams());}
    @Test public void disconnectedHookDoesNotExtendTheStem()throws Exception {
        setup(false,true);for(int row=69;row<=71;row++)for(int x=98;x<=105;x++)pixel(x,row,255,0);
        assertEquals(0,beams());
    }
    @Test public void analysisDoesNotModifySourceArrays()throws Exception {
        setup(false,true);var g=gray.clone();var l=labels.clone();beams();assertArrayEquals(g,gray);assertArrayEquals(l,labels);
    }
}
