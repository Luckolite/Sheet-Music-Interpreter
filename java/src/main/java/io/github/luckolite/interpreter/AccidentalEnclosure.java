// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Raw, closed oval evidence around a narrow semantic accidental. */
final class AccidentalEnclosure {
    static float[] find(byte[] gray,int w,int h,int l,int t,int r,int b,float gap) {
        if(gray==null)return null;
        float cx=(l+r)*.5f,cy=(t+b)*.5f;
        int tolerance=Math.max(1,Math.round(gap*.09f));
        for(float dy=-.35f;dy<=.55f;dy+=.15f)
            for(float rx=1.05f;rx<=1.55f;rx+=.1f)
                for(float ry=1.05f;ry<=1.65f;ry+=.1f) {
                    if(cy+gap*(dy+ry)<b+gap*.3f||cy+gap*(dy-ry)>t+gap*.3f)continue;
                    int hits=0;
                    for(int n=0;n<40;n++) {
                        double angle=n*Math.PI/20;
                        int x=Math.round(cx+gap*rx*(float)Math.cos(angle));
                        int y=Math.round(cy+gap*(dy+ry*(float)Math.sin(angle)));
                        boolean ink=false;
                        for(int yy=Math.max(0,y-tolerance);yy<=Math.min(h-1,y+tolerance);yy++)
                            for(int xx=Math.max(0,x-tolerance);xx<=Math.min(w-1,x+tolerance);xx++)
                                if((gray[yy*w+xx]&255)<175)ink=true;
                        if(ink)hits++;
                    }
                    if(hits>=38)return new float[]{cx,cy+gap*dy,gap*rx,gap*ry,gap};
                }
        return null;
    }
    static boolean contains(float[] ring,int l,int t,int r,int b) {
        float margin=ring[4]*.3f;
        return l>=ring[0]-ring[2]-margin&&r<=ring[0]+ring[2]+margin
                &&t>=ring[1]-ring[3]-margin&&b<=ring[1]+ring[3]+margin;
    }
}
