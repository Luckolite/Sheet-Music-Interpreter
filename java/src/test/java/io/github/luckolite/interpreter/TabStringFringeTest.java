// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import org.junit.Test;import java.util.*;import static org.junit.Assert.*;
public class TabStringFringeTest {
 @Test public void intermittentDarkFringeDoesNotMergeAdjacentFrets(){
  int w=500,h=300;byte[] gray=new byte[w*h];Arrays.fill(gray,(byte)255);
  for(int y=60;y<=210;y+=30)for(int x=20;x<480;x++){gray[y*w+x]=100;gray[(y+1)*w+x]=(byte)210;}
  for(int x=215;x<380;x++)gray[61*w+x]=120;
  for(int left:new int[]{230,285,340})for(int y=48;y<74;y++)for(int x=left;x<left+20;x++)
   if(x<left+3||x>=left+17||y<51||y>=71)gray[y*w+x]=0;
  var tabs=List.of(new TablatureDecoder.Staff(60,30,-1,List.of(),List.of(20f,480f)));
  var crops=TabFretRaster.crops(gray,w,h,tabs);
  assertEquals(3,crops.size());assertEquals(List.of(230,285,340),crops.stream().map(TabFretRaster.Crop::left).toList());
  assertTrue(crops.stream().allMatch(c->c.string()==0&&c.right()-c.left()==20));
 }

 @Test public void enlargedPaleRulesKeepAllSixStrings(){
  int w=2048,h=500;byte[] gray=new byte[w*h];Arrays.fill(gray,(byte)255);
  for(int n=0;n<6;n++){int top=100+n*50,thickness=n==1?11:5;for(int y=top;y<top+thickness;y++)for(int x=40;x<2000;x++)gray[y*w+x]=(byte)220;}
  for(int x:new int[]{40,1999})for(int y=100;y<355;y++)gray[y*w+x]=0;
  var rows=TablatureDecoder.detect(gray,w,h);assertEquals(1,rows.size());assertEquals(50,rows.get(0).gap(),1);
 }
}
