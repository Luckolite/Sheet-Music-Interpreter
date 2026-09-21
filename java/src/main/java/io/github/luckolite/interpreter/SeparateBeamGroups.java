// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
/** Evidence of two separate outward-going beams, not merely a faded connection. */
final class SeparateBeamGroups {
 record Tip(int x,int y,int direction){}
 static boolean between(byte[] g,int w,int h,float ax,float ay,float bx,float by,float gap){
  if(g==null||gap<6||bx-ax<gap*1.3f||bx-ax>gap*5)return false;
  for(int direction:new int[]{-1,1}){
   Tip a=tip(g,w,h,ax,ay,gap,direction,-1),b=tip(g,w,h,bx,by,gap,direction,1);
   if(a==null||b==null||b.x-a.x<gap*.7f)continue;
   int clear=0,total=0;
   for(int x=a.x+Math.round(gap*.25f);x<=b.x-Math.round(gap*.25f);x++){
    float t=(x-a.x)/(float)(b.x-a.x);int y=Math.round(a.y+t*(b.y-a.y));
    boolean beam=false;
    for(int dy=-Math.round(gap*.35f);dy<=Math.round(gap*.35f);dy++)if(core(g,w,h,x,y+dy,gap)){beam=true;break;}
    total++;if(!beam)clear++;
   }
   if(total>=gap*.35f&&clear>=total*.85f)return true;
  }return false;
 }
 static boolean connected(byte[] g,int w,int h,float ax,float ay,float bx,float by,float gap,int direction){
  Tip a=tip(g,w,h,ax,ay,gap,direction,1),b=tip(g,w,h,bx,by,gap,direction,-1);
  if(a==null||b==null||b.x-a.x<gap*2)return false;
  int covered=0,total=0;
  for(int x=a.x+2;x<b.x-1;x++){
   float t=(x-a.x)/(float)(b.x-a.x);int y=Math.round(a.y+t*(b.y-a.y));boolean ink=false;
   for(int dy=-Math.round(gap*.2f);dy<=Math.round(gap*.2f);dy++)if(core(g,w,h,x,y+dy,gap)){ink=true;break;}
   total++;if(ink)covered++;
  }
  return total>0&&covered>=total*.94f;
 }
 private static Tip tip(byte[] g,int w,int h,float hx,float hy,float gap,int direction,int outward){
  int best=0;Tip found=null;
  for(int x=Math.round(hx-gap*.65f);x<=Math.round(hx+gap*.65f);x++){
   if(x<0||x>=w)continue;
   int run=0,blanks=0;
   for(int d=0;d<=gap*5;d++){
    int y=Math.round(hy)+direction*d;if(y<0||y>=h)break;
    if((g[y*w+x]&255)<165){run++;blanks=0;}else if(++blanks>1)break;
    if(d<gap*1.8f||run<d*.85f||d<=best)continue;
    if(outwardBeam(g,w,h,x,y,gap,outward)){found=new Tip(x,y,direction);best=d;}
   }
  }return found;
 }
 private static boolean outwardBeam(byte[] g,int w,int h,int x,int y,float gap,int side){
  for(int angle=-10;angle<=10;angle++){
   int count=0,total=0;float slope=angle*.06f;
   for(int d=Math.round(gap*.25f);d<=Math.round(gap*1.35f);d++){
    total++;if(core(g,w,h,x+side*d,Math.round(y+slope*d),gap))count++;
   }
   if(total>0&&count>=total*.94f)return true;
  }return false;
 }
 private static boolean core(byte[] g,int w,int h,int x,int y,float gap){
  int rad=Math.max(1,Math.round(gap*.12f));if(x<0||x>=w||y-rad<0||y+rad>=h)return false;
  for(int dy=-rad;dy<=rad;dy++)if((g[(y+dy)*w+x]&255)>=165)return false;return true;
 }
}
