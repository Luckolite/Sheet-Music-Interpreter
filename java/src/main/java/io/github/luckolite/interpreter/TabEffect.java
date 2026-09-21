// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Guitar performance data in unused articulation bits; written rhythm is unchanged. */
public final class TabEffect {
    public static final int SLIDE=1,HAMMER=2,PULL=3,BEND=4,BEND_RELEASE=5,DEAD=6,HARMONIC=7;
    public static final int VIBRATO=1<<29,PALM_MUTE=1<<30,ALL=0x7ff80000;
    private TabEffect() { }
    public static int kind(int marks){return (marks>>>19)&15;}
    public static int delta(int marks){return ((marks>>>23)&63)-32;}
    public static int encode(int kind,int semitones) {
        if(kind<1||kind>7||Math.abs(semitones)>24)throw new IllegalArgumentException("Unsupported guitar effect");
        return (kind<<19)|((semitones+32)<<23);
    }
    public static String name(int marks){return switch(kind(marks)) {
        case SLIDE->"slide";case HAMMER->"hammer_on";case PULL->"pull_off";case BEND->"bend";
        case BEND_RELEASE->"bend_release";case DEAD->"dead";case HARMONIC->"harmonic";default->"none";};}
    public static double pitchOffset(int marks,double age,double duration) {
        double t=Math.max(0,Math.min(1,age/Math.max(.001,duration))),offset=0;
        int k=kind(marks);
        if(k==SLIDE)offset=delta(marks)*(1-smooth(Math.min(1,t/.3)));
        if(k==BEND)offset=delta(marks)*smooth(Math.min(1,t/.4));
        if(k==BEND_RELEASE)offset=delta(marks)*(t<.5?smooth(t/.5):1-smooth((t-.5)/.5));
        if((marks&VIBRATO)!=0)offset+=.18*Math.sin(Math.max(0,age)*Math.PI*10)*Math.min(1,t*8);
        return offset;
    }
    private static double smooth(double t){return t*t*(3-2*t);}
    public static double gate(int marks){return kind(marks)==DEAD?.12:(marks&PALM_MUTE)!=0?.45:1;}
    public static double gain(int marks){return switch(kind(marks)){case DEAD->.25;case HAMMER,PULL->.78;default->1;};}
    public static int harmonicOffset(int fret){return switch(fret){case 12->12;case 7,19->19;case 5,24->24;default->-1;};}
}
