// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
/** Original bar and accidental shapes; no score-derived fixtures. */
public class SignatureBoundarySpacingTest {
 private boolean bars(int separation, boolean rawOnly)throws Exception {
  int w=300,h=200;byte[] l=new byte[w*h],g=new byte[w*h];Arrays.fill(g,(byte)255);
  for(int x:new int[]{140,140+separation})for(int y=45;y<=119;y++){
   g[y*w+x]=0;if(!rawOnly)l[y*w+x]=1;
  }
  var type=Class.forName(OmrScoreInterpreter.class.getName()+"$Staff");var c=type.getDeclaredConstructor(float.class,float.class,float.class);c.setAccessible(true);
  var m=OmrScoreInterpreter.class.getDeclaredMethod("hasDoubleBar",byte[].class,byte[].class,int.class,int.class,float.class,type);m.setAccessible(true);
  return (boolean)m.invoke(null,l,g,w,h,150f,c.newInstance(50f,114f,16f));
 }
 @Test public void closeDoubleBarRemainsValid()throws Exception{assertTrue(bars(7,false));}
 @Test public void fadedSemanticDoubleBarUsesSourceInk()throws Exception{assertTrue(bars(7,true));}
 @Test public void remoteAccidentalShaftCannotCompleteDoubleBar()throws Exception{assertFalse(bars(20,false));}
 @Test public void remoteRawShaftCannotCompleteDoubleBar()throws Exception{assertFalse(bars(20,true));}
 private List<Integer> loneFlat(int x){
  var f=new JoinedSignatureSharpTest();
  for(int y=100;y<=164;y+=16)for(int xx=20;xx<620;xx++)f.ink(xx,y,4);
  for(int xx:new int[]{190,196})for(int y=96;y<=168;y++)f.ink(xx,y,1);
  for(int yy=104;yy<=139;yy++)for(int xx=x;xx<=x+2;xx++)f.ink(xx,yy,3);
  for(int yy=125;yy<=139;yy++)for(int xx=x+2;xx<=x+12;xx++){
   double v=Math.pow((xx-x-3)/9d,2)+Math.pow((yy-132)/7d,2);if(v<=1&&v>=.35)f.ink(xx,yy,3);
  }
  f.measures.add(new MeasureRegion(200f/640,620f/640,65f/720,199f/720));return f.keys();
 }
 @Test public void nearbySingleFlatAfterDoubleBarIsAKey(){assertEquals(List.of(-1),loneFlat(214));}
 @Test public void distantFlatFragmentIsNotAKey(){assertEquals(List.of(),loneFlat(340));}
}
