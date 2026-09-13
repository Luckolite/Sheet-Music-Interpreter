// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original flat/natural geometry with disconnected ink inside the recovery crop. */
public class DisconnectedNaturalTailTest {
    private static final int W=100,H=100;
    private final byte[] gray=new byte[W*H];
    public DisconnectedNaturalTailTest(){Arrays.fill(gray,(byte)255);}
    private void rect(int x,int y,int w,int h){for(int yy=y;yy<y+h;yy++)for(int xx=x;xx<x+w;xx++)gray[yy*W+xx]=0;}
    private Object make(String name,Object...args)throws Exception {
        var type=Class.forName(OmrScoreInterpreter.class.getName()+"$"+name);
        var c=type.getDeclaredConstructors()[0];c.setAccessible(true);return c.newInstance(args);
    }
    private void glyph(boolean natural) {
        rect(25,18,2,30);rect(35,30,2,natural?29:18);
        rect(25,30,12,4);rect(25,44,12,4);
    }
    private boolean recover()throws Exception {
        Object seed=make("Component",96,25,36,30,47,30.5f,38.5f);
        Object head=make("Component",100,48,60,33,43,54f,38f);
        for(var m:OmrScoreInterpreter.class.getDeclaredMethods())if(m.getName().equals("rawNaturalAtSeed")&&m.getParameterCount()==6) {
            m.setAccessible(true);return (boolean)m.invoke(null,gray,W,H,seed,head,14f);
        }
        throw new AssertionError("Missing recovery method");
    }
    @Test public void disconnectedTailCannotTurnFlatIntoNatural()throws Exception {
        glyph(false);rect(35,51,2,8);assertFalse(recover());
    }
    @Test public void detachedSlurTipCannotSupplyLowerSpine()throws Exception {
        glyph(false);rect(34,52,3,6);assertFalse(recover());
    }
    @Test public void connectedNaturalKeepsBothSpineEnds()throws Exception {glyph(true);assertTrue(recover());}
    @Test public void naturalWithNearbyDisconnectedInkStillRecovers()throws Exception {
        glyph(true);rect(28,61,3,2);assertTrue(recover());
    }
    @Test public void sourcePixelsRemainUnchanged()throws Exception {
        glyph(false);rect(35,51,2,8);byte[] before=gray.clone();recover();assertArrayEquals(before,gray);
    }
}
