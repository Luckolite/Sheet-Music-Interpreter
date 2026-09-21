// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original two-flat header with one incomplete semantic bowl. */
public class FragmentedFlatSignatureTest {
    private void flat(JoinedSignatureSharpTest f,int x,int y,boolean partial) {
        for(int yy=y-28;yy<=y+7;yy++)for(int xx=x;xx<=x+2;xx++)f.ink(xx,yy,3);
        for(int yy=y-7;yy<=y+7;yy++)for(int xx=x+2;xx<=x+12;xx++) {
            double r=Math.pow((xx-x-3)/9d,2)+Math.pow((yy-y)/7d,2);
            if(r<=1&&r>=.35) {
                f.gray[yy*JoinedSignatureSharpTest.W+xx]=0;
                if(!partial)f.labels[yy*JoinedSignatureSharpTest.W+xx]=3;
            }
        }
    }
    private JoinedSignatureSharpTest page(boolean bar) {
        var f=new JoinedSignatureSharpTest();f.row(100,0,0,false);
        flat(f,60,132,true);flat(f,80,108,false);
        if(bar)for(int x:new int[]{178,183})for(int y=100;y<=164;y++)f.ink(x,y,1);
        return f;
    }
    @Test public void orderedSpinesRecoverAFragmentedTwoFlatSignature() {
        assertEquals(List.of(-2),page(false).keys());
    }
    @Test public void barNearMeasureBoundaryDoesNotClipTheClefHeader() {
        assertEquals(List.of(-2),page(true).keys());
    }
    @Test public void realKeyReductionIsPreserved() {
        var f=page(false);f.row(310,0,0,false);flat(f,80,342,false);
        assertEquals(List.of(-2,-1),f.keys());
    }
}
