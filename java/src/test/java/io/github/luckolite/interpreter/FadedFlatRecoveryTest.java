// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original pale spines and dark bowls, with independent neighboring marks. */
public class FadedFlatRecoveryTest {
    private static final int W=100,H=100;
    private final byte[] gray=new byte[W*H];
    public FadedFlatRecoveryTest(){Arrays.fill(gray,(byte)255);}
    private void ink(int left,int right,int top,int bottom,int value){
        for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++)gray[y*W+x]=(byte)value;
    }
    private void flat(int value){
        ink(30,32,25,64,value);ink(30,41,49,50,0);
        ink(30,41,60,61,0);ink(40,41,49,61,0);
    }
    private Object make(String name,Object...args)throws Exception {
        var c=Class.forName(OmrScoreInterpreter.class.getName()+"$"+name).getDeclaredConstructors()[0];
        c.setAccessible(true);return c.newInstance(args);
    }
    private boolean detect()throws Exception {
        Object seed=make("Component",55,30,41,49,64,34f,56f);
        Object candidate=make("AccidentalCandidate",seed,(byte)3);
        Object head=make("Component",120,53,69,49,61,61f,55f);
        for(var m:OmrScoreInterpreter.class.getDeclaredMethods())if(m.getName().equals("rawFlatFromBowl")){
            m.setAccessible(true);return (boolean)m.invoke(null,gray,W,H,List.of(candidate),head,16f);
        }
        throw new AssertionError("Missing recovery method");
    }
    @Test public void paleSpineWithDarkBowlRecovers()throws Exception {flat(230);assertTrue(detect());}
    @Test public void detachedMarkAtCropEdgeDoesNotHideFlat()throws Exception {
        flat(230);ink(39,41,0,18,0);assertTrue(detect());
    }
    @Test public void darkFlatAlsoIgnoresDetachedMark()throws Exception {
        flat(0);ink(39,41,0,18,0);assertTrue(detect());
    }
    @Test public void paleContinuationCannotProveCompleteFlat()throws Exception {
        flat(230);ink(30,32,65,88,230);assertFalse(detect());
    }
    @Test public void paleStemWithoutBowlIsNotFlat()throws Exception {ink(30,32,25,64,230);assertFalse(detect());}
    @Test public void paleSharpSpinesDoNotBecomeFlat()throws Exception {
        flat(230);ink(40,41,25,64,230);assertFalse(detect());
    }
    @Test public void absentSpineDoesNotBecomeFlat()throws Exception {
        flat(255);assertFalse(detect());
    }
    @Test public void paleNaturalSpinesDoNotBecomeFlat()throws Exception {
        ink(30,32,25,51,230);ink(40,41,38,64,230);
        ink(30,41,38,39,0);ink(30,41,50,51,0);assertFalse(detect());
    }
    @Test public void disconnectedUpperStrokeCannotSupplyFlatSpine()throws Exception {
        flat(230);ink(30,32,40,46,255);assertFalse(detect());
    }
    @Test public void paleStaffRuleDoesNotHideFlat()throws Exception {
        flat(230);ink(0,99,55,56,215);assertTrue(detect());
    }
    @Test public void sourcePixelsArePreserved()throws Exception {
        flat(230);var before=gray.clone();detect();assertArrayEquals(before,gray);
    }
}
