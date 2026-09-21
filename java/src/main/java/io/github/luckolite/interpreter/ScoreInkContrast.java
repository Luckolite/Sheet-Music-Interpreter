// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Separates printed ink from a predominantly dark page before segmentation and decoding. */
public final class ScoreInkContrast {
    private ScoreInkContrast() { }

    /** White-paper scans retain their original pixels, including faint staff lines. */
    public static byte[] prepare(byte[] gray, int width, int height) {
        long size=(long)width*height;
        if(width<=0||height<=0||size>20_000_000||gray==null||gray.length!=size)
            throw new IllegalArgumentException("Invalid grayscale page dimensions");
        int dark=0;
        for(byte pixel:gray)if((pixel&255)<200)dark++;
        if(dark<size*.65)return gray;
        int radius=Math.max(3,Math.round(width*15f/2048));
        int span=radius*2+1;
        long[] columns=new long[width],squares=new long[width];
        for(int dy=-radius;dy<=radius;dy++) {
            int row=Math.max(0,Math.min(height-1,dy))*width;
            for(int x=0;x<width;x++) {
                int value=gray[row+x]&255;columns[x]+=value;squares[x]+=(long)value*value;
            }
        }
        byte[] output=new byte[gray.length];
        double area=(double)span*span;
        for(int y=0;y<height;y++) {
            long sum=0,sumSquares=0;
            for(int dx=-radius;dx<=radius;dx++) {
                int x=Math.max(0,Math.min(width-1,dx));sum+=columns[x];sumSquares+=squares[x];
            }
            for(int x=0;x<width;x++) {
                double mean=sum/area;
                double deviation=Math.sqrt(Math.max(0,sumSquares/area-mean*mean));
                double threshold=mean*(1+.3*(deviation/128-1));
                output[y*width+x]=(byte)((gray[y*width+x]&255)<threshold?0:255);
                int remove=Math.max(0,x-radius),add=Math.min(width-1,x+radius+1);
                sum+=columns[add]-columns[remove];sumSquares+=squares[add]-squares[remove];
            }
            int remove=Math.max(0,y-radius)*width,add=Math.min(height-1,y+radius+1)*width;
            for(int x=0;x<width;x++) {
                int before=gray[remove+x]&255,after=gray[add+x]&255;
                columns[x]+=after-before;squares[x]+=(long)after*after-(long)before*before;
            }
        }
        return output;
    }
}
