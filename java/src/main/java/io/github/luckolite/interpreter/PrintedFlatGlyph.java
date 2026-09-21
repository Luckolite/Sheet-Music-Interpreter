// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Source-ink confirmation of a flat's single spine and lower-right bowl. */
final class PrintedFlatGlyph {
    private PrintedFlatGlyph() { }
    static boolean matches(byte[] gray,int w,int h,int left,int top,int right,int bottom,float gap) {
        if(gray==null||left<0||top<0||right>=w||bottom>=h||bottom-top+1<gap*.8f)return false;
        // A staff-labelled cut can leave only the flat's bowl in the semantic box.
        // Follow its existing left spine upward in the source, never invent pixels.
        int extended=top;
        for(int x=left;x<=left+(right-left)*.4f;x++) {
            if(!dark(gray,w,x,top))continue;
            int blank=0,end=top;
            for(int y=top;y>=Math.max(0,top-Math.round(gap*1.5f));y--) {
                if(dark(gray,w,x,y)){end=y;blank=0;}else if(++blank>1)break;
            }
            extended=Math.min(extended,end);
        }
        top=extended;
        int ww=right-left+1,hh=bottom-top+1;
        if(gray==null||left<0||top<0||right>=w||bottom>=h||hh<gap*1.5f||hh>gap*3.5f
                ||ww<gap*.4f||ww>gap*1.6f)return false;
        int spine=left,best=0;
        for(int x=left;x<=left+ww*.4f;x++) {
            int count=0;for(int y=top;y<=bottom;y++)if(dark(gray,w,x,y))count++;
            if(count>best){best=count;spine=x;}
        }
        if(best<hh*.78f)return false;
        int start=Math.max(spine+Math.round(gap*.3f),left+Math.round(ww*.6f));
        if(start>right)return false;
        int upper=0,bowl=0,tail=0,clear=0;
        for(int y=top;y<=bottom;y++) {
            boolean rule=left-gap>=0&&right+gap<w
                    &&dark(gray,w,Math.round(left-gap),y)&&dark(gray,w,Math.round(right+gap),y);
            if(rule)continue;
            boolean ink=false;for(int x=start;x<=right;x++)if(dark(gray,w,x,y)){ink=true;break;}
            if(ink) {
                if(y<top+hh*.4f)upper++;
                else if(y>top+hh*.9f)tail++;
                else bowl++;
            }
            if(y>top+hh*.5f&&y<top+hh*.8f)
                for(int x=spine+Math.round(gap*.2f);x<start;x++)if(!dark(gray,w,x,y))clear++;
        }
        return upper<=hh*.08f&&tail<=1&&bowl>=hh*.18f&&clear>=3;
    }
    private static boolean dark(byte[] gray,int w,int x,int y){return(gray[y*w+x]&255)<165;}
}
