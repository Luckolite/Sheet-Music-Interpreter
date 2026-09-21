// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Generated beam cores with a narrow scanning fringe. */
public class IsolatedBeamProbeTest {
    private int count(int halfWidth)throws Exception {
        byte[] gray=new byte[300*180],labels=new byte[gray.length];Arrays.fill(gray,(byte)255);
        for(int y=45;y<=50;y++)for(int x=100;x<=200;x++)gray[y*300+x]=0;
        for(int y=57;y<=62;y++)for(int x=150-halfWidth;x<=150+halfWidth;x++)gray[y*300+x]=0;
        var staff=Class.forName(OmrScoreInterpreter.class.getName()+"$Staff");
        var ctor=staff.getDeclaredConstructor(float.class,float.class,float.class);ctor.setAccessible(true);
        var method=OmrScoreInterpreter.class.getDeclaredMethod("supportedBeamBands",byte[].class,byte[].class,
                int.class,int.class,int.class,int.class,int.class,staff,int.class);method.setAccessible(true);
        return (int)method.invoke(null,gray,labels,300,180,150,40,65,ctor.newInstance(80f,144f,16f),150);
    }
    @Test public void isolatedExtraBandDoesNotShortenTheNote()throws Exception {assertEquals(1,count(1));}
    @Test public void broaderSecondaryBeamIsPreserved()throws Exception {assertEquals(2,count(10));}
    @Test public void narrowButCorroboratedSecondaryBeamIsPreserved()throws Exception {assertEquals(2,count(2));}
}
