// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
/** Synthetic dense signatures and adjacent non-signature strokes. */
public class DenseSignatureHeaderTest {
 private JoinedSignatureSharpTest page(int count){
  var f=new JoinedSignatureSharpTest();f.row(100,0,0,false);
  int[] ys={132,108,140,116,148,124,156};
  for(int i=0;i<count;i++){
   int x=75+i*18,y=ys[i];
   for(int yy=y-28;yy<=y+7;yy++)for(int xx=x;xx<=x+2;xx++)f.ink(xx,yy,3);
   for(int yy=y-7;yy<=y+7;yy++)for(int xx=x+2;xx<=x+12;xx++){
    double v=Math.pow((xx-x-3)/9d,2)+Math.pow((yy-y)/7d,2);if(v<=1&&v>=.35)f.ink(xx,yy,3);
   }
  }
  f.measures.set(0,new MeasureRegion(265f/640,620f/640,65f/720,199f/720));return f;
 }
 @Test public void distantPlayableEdgeRetainsFiveFlatHeader(){assertEquals(List.of(-5),page(5).keys());}
 @Test public void denseSevenFlatHeaderStillFits(){assertEquals(List.of(-7),page(7).keys());}
 @Test public void aLaterHeaderCanReallyReduceTheKey(){var f=page(5);f.row(310,2,0,false);assertEquals(List.of(-5,2),f.keys());}
 @Test public void damagedClefStillWorksWhenNoCompleteClefSurvives(){
  var f=page(5);for(int y=166;y<=180;y++)for(int x=30;x<=55;x++)f.labels[y*640+x]=0;
  assertEquals(List.of(-5),f.keys());
 }
 @Test public void laterTallMaskClusterDoesNotReplaceTheCompleteClef(){
  var f=page(5);for(int y=85;y<=163;y++)for(int x=230;x<=252;x++)f.ink(x,y,3);
  assertEquals(List.of(-5),f.keys());
 }
 private int spines(boolean separated)throws Exception{
  var f=new JoinedSignatureSharpTest();
  int[] tops={75,51,83,59,91,67};
  for(int i=0;i<tops.length;i++)for(int x=100+i*16+(i==5&&separated?13:0);x<=102+i*16+(i==5&&separated?13:0);x++)for(int y=tops[i];y<tops[i]+34;y++)f.ink(x,y,3);
  var type=Class.forName(OmrScoreInterpreter.class.getName()+"$Staff");var ctor=type.getDeclaredConstructor(float.class,float.class,float.class);ctor.setAccessible(true);
  var m=OmrScoreInterpreter.class.getDeclaredMethod("countFlatSpines",byte[].class,byte[].class,int.class,int.class,float.class,float.class,type,boolean.class);m.setAccessible(true);
  return (int)m.invoke(null,f.labels,f.gray,640,720,90f,220f,ctor.newInstance(80f,144f,16f),false);
 }
 @Test public void widerMeterGapDoesNotAddASixthFlat()throws Exception{assertEquals(5,spines(true));}
 @Test public void regularlySpacedSixthFlatIsRetained()throws Exception{assertEquals(6,spines(false));}
}
