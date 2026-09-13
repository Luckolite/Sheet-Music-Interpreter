// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original Bezier lettering and note geometry, independent of score pixels. */
public class ForteHookHeadTest {
    static final int W=420,H=300;
    final byte[] labels=new byte[W*H],gray=new byte[W*H];
    public ForteHookHeadTest(){
        Arrays.fill(gray,(byte)255);
        for(int y=100;y<=164;y+=16)for(int x=10;x<410;x++)pixel(x,y,4);
        note(280,124);for(int y=124;y<174;y++)pixel(272,y,1);
    }
    void pixel(int x,int y,int label){labels[y*W+x]=(byte)label;gray[y*W+x]=0;}
    void dot(double cx,double cy,double radius){
        cy-=20;
        for(int y=(int)(cy-radius-1);y<=cy+radius+1;y++)for(int x=(int)(cx-radius-1);x<=cx+radius+1;x++)
            if(Math.pow(x-cx,2)+Math.pow(y-cy,2)<=radius*radius)pixel(x,y,5);
    }
    void curve(double x0,double y0,double x1,double y1,double x2,double y2,double x3,double y3,double radius){
        for(int i=0;i<=120;i++){double t=i/120d,u=1-t;dot(u*u*u*x0+3*u*u*t*x1+3*u*t*t*x2+t*t*t*x3,u*u*u*y0+3*u*u*t*y1+3*u*t*t*y2+t*t*t*y3,radius);}
    }
    void letter(boolean bar,boolean lower){
        curve(210,207,217,202,203,199,200,214,2.3);
        curve(200,214,197,223,195,234,191,243,2.3);
        if(lower)curve(191,243,189,247,183,246,182,243,2.3);
        if(bar)for(int x=193;x<=205;x++)dot(x,216,1.6);
        for(int y=179;y<=189;y++)for(int x=201;x<=216;x++)if(labels[y*W+x]==5)labels[y*W+x]=2;
    }
    void note(int cx,int cy){
        for(int y=cy-5;y<=cy+5;y++)for(int x=cx-7;x<=cx+7;x++)
            if(Math.pow((x-cx)/7d,2)+Math.pow((y-cy)/5d,2)<=1)pixel(x,y,2);
    }
    boolean hook(){return OmrScoreInterpreter.analyze(labels,gray,W,H,List.of(new MeasureRegion(0,1,.1f,.98f))).notes().stream().anyMatch(n->n.pageY()*H>170&&n.positionInMeasure()*W<240);}
    @Test public void printedForteHookIsNotPlayed(){letter(true,true);assertFalse(hook());}
    @Test public void missingCrossStrokeDoesNotProveForte(){letter(false,true);assertTrue(hook());}
    @Test public void missingLowerHookDoesNotProveForte(){letter(true,false);assertTrue(hook());}
    @Test public void ordinaryBelowStaffNoteSurvives(){note(208,184);for(int y=184;y<230;y++)pixel(201,y,1);assertTrue(hook());}
    @Test public void symbolLabelledVerticalStemStillKeepsNote(){note(208,184);for(int y=184;y<230;y++)pixel(201,y,5);assertTrue(hook());}
    @Test public void sourceArraysStayUnchanged(){letter(true,true);var l=labels.clone();var g=gray.clone();hook();assertArrayEquals(l,labels);assertArrayEquals(g,gray);}
}
