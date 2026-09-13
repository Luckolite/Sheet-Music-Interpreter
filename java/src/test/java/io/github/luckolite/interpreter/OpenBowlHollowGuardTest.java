// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;
/** Original upright two-bowl numeral geometry, with pale outline and open mouths. */
public class OpenBowlHollowGuardTest {
    private static final int W=220,H=180;
    private final byte[] gray=new byte[W*H];
    private void numeral() {
        Arrays.fill(gray,(byte)250);
        for(int cy:new int[]{94,107})for(int y=cy-6;y<=cy+6;y++)for(int x=95;x<=111;x++) {
            double outer=Math.pow((x-103)/8.0,2)+Math.pow((y-cy)/6.0,2);
            if(outer<=1)gray[y*W+x]=(byte)(Math.pow((x-103)/5.0,2)+Math.pow((y-cy)/3.5,2)<=1?215:160);
        }
        for(int y:new int[]{94,95,107,108})for(int x=95;x<=99;x++)gray[y*W+x]=(byte)250;
        for(int y=98;y<=103;y++)for(int x=103;x<=108;x++)gray[y*W+x]=(byte)160;
    }
    private boolean pocket()throws Exception {
        var type=Class.forName(OmrScoreInterpreter.class.getName()+"$Component");var c=type.getDeclaredConstructors()[0];c.setAccessible(true);
        var head=c.newInstance(300,94,112,88,113,103f,100.5f);
        var m=OmrScoreInterpreter.class.getDeclaredMethod("fadedEnclosedHeadPocket",byte[].class,int.class,type,float.class,int.class,int.class,int.class,int.class);m.setAccessible(true);
        return (boolean)m.invoke(null,gray,W,head,16f,99,107,93,108);
    }
    @Test public void uprightOpenBowlsAreNotAHollowNote()throws Exception {numeral();assertFalse(pocket());}
    @Test public void mirroredOpenBowlsAreAlsoNotAHollowNote()throws Exception {
        numeral();byte[] original=gray.clone();for(int y=0;y<H;y++)for(int x=94;x<=112;x++)gray[y*W+x]=original[y*W+206-x];assertFalse(pocket());
    }
    @Test public void inspectingNumeralsPreservesTheSource()throws Exception {numeral();var before=gray.clone();pocket();assertArrayEquals(before,gray);}
}
