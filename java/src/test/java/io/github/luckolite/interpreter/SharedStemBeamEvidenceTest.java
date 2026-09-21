// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original chord stem with a one-pixel difference between the two head traces. */
public class SharedStemBeamEvidenceTest {
    private final byte[] gray=new byte[260*220],labels=new byte[gray.length];
    private void rectangle(int x0,int y0,int x1,int y1,int label) {
        for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++) {
            gray[y*260+x]=0;labels[y*260+x]=(byte)label;
        }
    }
    private Object head(int y)throws Exception {
        int area=0;
        for(int yy=y-8;yy<=y+8;yy++)for(int x=99;x<=121;x++)
            if((x-110)*(x-110)/121d+(yy-y)*(yy-y)/64d<=1) {
                gray[yy*260+x]=0;labels[yy*260+x]=2;area++;
            }
        var c=Class.forName(OmrScoreInterpreter.class.getName()+"$Component")
                .getDeclaredConstructor(int.class,int.class,int.class,int.class,int.class,float.class,float.class);
        c.setAccessible(true);return c.newInstance(area,99,121,y-8,y+8,110f,(float)y);
    }
    private int count(boolean chord,boolean broad)throws Exception {
        Arrays.fill(gray,(byte)255);
        Object upper=head(40),lower=head(80);
        rectangle(100,40,101,148,1);rectangle(99,80,99,148,1);
        rectangle(85,140,150,146,1);
        rectangle(broad?85:92,126,broad?150:94,131,1);
        var staff=Class.forName(OmrScoreInterpreter.class.getName()+"$Staff");
        var ctor=staff.getDeclaredConstructor(float.class,float.class,float.class);ctor.setAccessible(true);
        var component=lower.getClass();
        var method=OmrScoreInterpreter.class.getDeclaredMethod("detectBeamCount",byte[].class,byte[].class,
                int.class,int.class,component,staff,List.class);method.setAccessible(true);
        return (int)method.invoke(null,labels,gray,260,220,lower,ctor.newInstance(80f,144f,16f),
                chord?List.of(upper,lower):List.of(lower));
    }
    @Test public void commonStemResolvesAnIsolatedExtraBeam()throws Exception {assertEquals(1,count(true,false));}
    @Test public void singleNoteRetainsItsExistingBeamReading()throws Exception {assertEquals(2,count(false,false));}
    @Test public void realSecondaryBeamOnBothChordHeadsIsPreserved()throws Exception {assertEquals(2,count(true,true));}
}
