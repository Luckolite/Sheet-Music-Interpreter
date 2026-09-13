// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original signature geometry with faint barlines omitted by segmentation. */
public class FadedDoubleBarTest {
    private byte[][] page(int shade, boolean complete) {
        byte[][] a = MixedSharpSlotTest.base(true);
        MixedSharpSlotTest.sharp(a);
        for (int x : new int[]{122,128}) for (int y=95;y<=169;y++) {
            a[0][y*MixedSharpSlotTest.W+x]=0;
            a[1][y*MixedSharpSlotTest.W+x]=(byte)(complete || y<126 ? shade : 255);
        }
        return a;
    }

    @Test public void fadedFullBarsRecoverSingleSharpChange() {
        byte[][] a=page(215,true);
        byte[] normalized=MixedSharpSlotTest.normalize(a);
        var result=OmrScoreInterpreter.analyze(normalized,a[1],MixedSharpSlotTest.W,MixedSharpSlotTest.H,
                List.of(new MeasureRegion(130f/600,560f/600,60f/260,210f/260)));
        assertEquals(List.of(new ScoreKeyChange(0,1)),result.keyChanges());
    }

    @Test public void shortParallelStrokesDoNotBecomeDoubleBar() {
        byte[][] a=page(215,false);assertArrayEquals(a[0],MixedSharpSlotTest.normalize(a));
    }

    @Test public void nearlyWhiteColumnsCannotValidateSignature() {
        byte[][] a=page(245,true);assertArrayEquals(a[0],MixedSharpSlotTest.normalize(a));
    }

    @Test public void oneFullBarAndOnePartialColumnRemainSingleBar() {
        byte[][] a=page(215,false);
        for(int y=95;y<=169;y++)a[1][y*MixedSharpSlotTest.W+122]=(byte)215;
        assertArrayEquals(a[0],MixedSharpSlotTest.normalize(a));
    }

    @Test public void suppliedArraysRemainUnchanged() {
        byte[][] a=page(215,true);byte[] labels=a[0].clone(),gray=a[1].clone();
        MixedSharpSlotTest.normalize(a);assertArrayEquals(labels,a[0]);assertArrayEquals(gray,a[1]);
    }
}
