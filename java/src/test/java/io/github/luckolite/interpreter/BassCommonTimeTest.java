// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import org.junit.Test;
import static org.junit.Assert.*;

public class BassCommonTimeTest {
    private HeaderSymbolNormalizationTest.Page page(boolean bass) {
        var p=new HeaderSymbolNormalizationTest.Page(false,!bass);
        if(bass) {
            for(int y=80;y<=132;y++)for(int x=35;x<=58;x++)
                if(x>=43-(y-80)/8&&x<=58-(y-80)/3){p.gray[y*p.w+x]=0;p.labels[y*p.w+x]=3;}
            for(int cy:new int[]{88,104})for(int y=cy-3;y<=cy+3;y++)for(int x=63;x<=69;x++)
                if((x-66)*(x-66)+(y-cy)*(y-cy)<=9){p.gray[y*p.w+x]=0;p.labels[y*p.w+x]=3;}
        }
        return p;
    }
    @Test public void commonTimeAfterBassClefIsNotANote(){var p=page(true);assertEquals(0,p.heads(p.normalized()));}
    @Test public void actualHeadAfterBassClefRemains(){var p=page(true);p.eraseSign();p.note(130,112,10,7,false);assertArrayEquals(p.labels,p.normalized());}
}
