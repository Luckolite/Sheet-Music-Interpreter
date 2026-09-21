// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
/** An octave-clef 8 has two enclosed counters; an isolated page/fingering number is not near a clef. */
final class OctaveClefDigit {
    static boolean above(byte[] gray,int width,int height,int left,int top,int right,float staffTop,float gap) {
        if(gray==null)return false;
        int x0=Math.max(0,left),x1=Math.min(width-1,right),y0=Math.max(0,Math.round(top-gap*1.8f));
        int y1=Math.min(height-1,Math.round(staffTop-gap*.45f));
        if(y1<=y0)return false;
        int w=x1-x0+1,h=y1-y0+1;boolean[] seen=new boolean[w*h];int[] queue=new int[w*h];
        for(int origin=0;origin<seen.length;origin++) {
            if(seen[origin]||(gray[(y0+origin/w)*width+x0+origin%w]&255)>175)continue;
            int count=1,at=0,minX=w,maxX=0,minY=h,maxY=0;queue[0]=origin;seen[origin]=true;
            while(at<count) {
                int n=queue[at++],x=n%w,y=n/w;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
                for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++) {
                    int xx=x+dx,yy=y+dy;if(xx<0||xx>=w||yy<0||yy>=h)continue;int next=yy*w+xx;
                    if(!seen[next]&&(gray[(y0+yy)*width+x0+xx]&255)<=175){seen[next]=true;queue[count++]=next;}
                }
            }
            // In tightly engraved clefs, the 8 can touch the clef's upper tip.
            // Examine the narrow prefix as well as isolated components. Two closed
            // counters must fit within a digit-sized box above the staff.
            for(int end=minY+Math.max(1,Math.round(gap*.6f));
                    end<=Math.min(maxY,minY+Math.round(gap*2.2f)-1);end++) {
                int a=w,b=-1;
                for(int i=0;i<count;i++)if(queue[i]/w<=end) {
                    a=Math.min(a,queue[i]%w);b=Math.max(b,queue[i]%w);
                }
                int gw=b-a+1,gh=end-minY+1;
                if(gw<gap*.3f||gw>gap*1.1f||gh<gw*1.1f)continue;
                if(holes(gray,width,x0+a,y0+minY,gw,gh)==2
                        && !neighboringDigit(gray,width,height,x0+a,y0+minY,gw,gh,gap))return true;
            }
        }
        return false;
    }
    private static boolean neighboringDigit(byte[] gray,int width,int height,int left,int top,int w,int h,float gap) {
        for(int direction:new int[]{-1,1}) {
            int x0=direction<0?Math.max(0,Math.round(left-gap*1.6f)):left+w+1;
            int x1=direction<0?left-2:Math.min(width-1,Math.round(left+w+gap*1.6f));
            int rows=0,area=0;
            for(int y=top;y<Math.min(height,top+h);y++) {
                boolean ink=false;
                for(int x=x0;x<=x1;x++)if((gray[y*width+x]&255)<175){ink=true;area++;}
                if(ink)rows++;
            }
            if(rows>=Math.min(h,gap*1.5f)*.7f&&area>=gap*gap*.15f)return true;
        }
        return false;
    }
    static int holes(byte[] gray,int stride,int left,int top,int w,int h) {
        boolean[] seen=new boolean[w*h];int[] queue=new int[w*h];int holes=0;
        for(int origin=0;origin<w*h;origin++) {
            if(seen[origin]||(gray[(top+origin/w)*stride+left+origin%w]&255)<=175)continue;
            boolean edge=false;int count=1,at=0;queue[0]=origin;seen[origin]=true;
            while(at<count) {
                int n=queue[at++],x=n%w,y=n/w;edge|=x==0||y==0||x==w-1||y==h-1;
                for(int d:new int[]{-1,1,-w,w}) {
                    int next=n+d;if(next<0||next>=w*h||Math.abs(next%w-x)+Math.abs(next/w-y)!=1)continue;
                    if(!seen[next]&&(gray[(top+next/w)*stride+left+next%w]&255)>175){seen[next]=true;queue[count++]=next;}
                }
            }
            if(!edge&&count>=2)holes++;
        }
        return holes;
    }
}
