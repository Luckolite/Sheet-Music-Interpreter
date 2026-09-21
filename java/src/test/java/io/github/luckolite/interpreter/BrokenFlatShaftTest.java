// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
/** Original flat outlines with a missing shaft segment and deliberately incomplete labels. */
public class BrokenFlatShaftTest {
 private void flat(JoinedSignatureSharpTest f,int x,int y,boolean broken,boolean bowl){
  for(int yy=y-28;yy<=y+7;yy++)for(int xx=x;xx<=x+2;xx++)if(!broken||yy<y-18||yy>y-12)f.ink(xx,yy,3);
  if(bowl)for(int yy=y-7;yy<=y+7;yy++)for(int xx=x+2;xx<=x+12;xx++){
   double v=Math.pow((xx-x-3)/9d,2)+Math.pow((yy-y)/7d,2);
   if(v<=1&&v>=.35){f.gray[yy*640+xx]=0;if(!broken)f.labels[yy*640+xx]=3;}
  }
 }
 private JoinedSignatureSharpTest page(boolean broken,boolean bowl){var f=new JoinedSignatureSharpTest();f.row(100,0,0,false);flat(f,65,132,broken,bowl);flat(f,85,108,false,true);return f;}
 @Test public void brokenShaftWithPrintedBowlRemainsAFlat(){assertEquals(List.of(-2),page(true,true).keys());}
 @Test public void intactSignatureIsUnchanged(){assertEquals(List.of(-2),page(false,true).keys());}
 @Test public void brokenShaftWithoutBowlDoesNotAddAFlat(){assertEquals(List.of(-1),page(true,false).keys());}
}
