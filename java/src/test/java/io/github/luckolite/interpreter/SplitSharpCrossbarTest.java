// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original split crossbars with independently drawn printed spines. */
public class SplitSharpCrossbarTest {
    private static final int W=100,H=100;
    private final byte[] gray=new byte[W*H];
    public SplitSharpCrossbarTest(){Arrays.fill(gray,(byte)255);}
    private void rect(int l,int r,int t,int b,int value){for(int y=t;y<=b;y++)for(int x=l;x<=r;x++)gray[y*W+x]=(byte)value;}
    private void sharp(boolean spines){
        rect(30,45,40,43,0);rect(30,45,56,59,0);
        if(spines){rect(33,34,30,69,210);rect(41,42,30,69,210);}
    }
    private Object make(String name,Object...args)throws Exception{
        var c=Class.forName(OmrScoreInterpreter.class.getName()+"$"+name).getDeclaredConstructors()[0];c.setAccessible(true);return c.newInstance(args);
    }
    private boolean detect(float headY,boolean paired)throws Exception{
        Object upper=make("AccidentalCandidate",make("Component",64,30,45,40,43,37.5f,41.5f),(byte)5);
        Object lower=make("AccidentalCandidate",make("Component",64,30,45,56,59,37.5f,57.5f),(byte)3);
        Object head=make("Component",100,55,71,Math.round(headY-6),Math.round(headY+6),63f,headY);
        for(var m:OmrScoreInterpreter.class.getDeclaredMethods())if(m.getName().equals("rawSharpFromSeed")){
            m.setAccessible(true);return (boolean)m.invoke(null,gray,W,H,paired?List.of(upper,lower):List.of(upper),head,16f);
        }
        throw new AssertionError("Missing recovery method");
    }
    @Test public void splitBarsRecoverPrintedSharp()throws Exception{sharp(true);assertTrue(detect(50,true));}
    @Test public void twoBarsWithoutSpinesAreNotSharp()throws Exception{sharp(false);assertFalse(detect(50,true));}
    @Test public void aSingleBarCannotLocateSharp()throws Exception{sharp(true);assertFalse(detect(50,false));}
    @Test public void neighboringPitchDoesNotTakeSharp()throws Exception{sharp(true);assertFalse(detect(58,true));}
    @Test public void naturalEndpointsAreNotSharp()throws Exception{
        sharp(false);rect(33,34,30,59,210);rect(41,42,40,69,210);assertFalse(detect(50,true));
    }
    @Test public void clippedSpinesCannotProveSharp()throws Exception{
        sharp(true);rect(33,34,0,29,210);rect(41,42,0,29,210);assertFalse(detect(50,true));
    }
    @Test public void staffRuleDoesNotHideSharp()throws Exception{
        sharp(true);rect(0,99,49,50,180);assertTrue(detect(50,true));
    }
    @Test public void sourcePixelsArePreserved()throws Exception{
        sharp(true);var before=gray.clone();detect(50,true);assertArrayEquals(before,gray);
    }
}
