// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Follows complete printed five-line groups across a curved page. */
final class StaffPitchTrack {
    private final float[][] points;
    private StaffPitchTrack(List<float[]> points) { this.points=points.toArray(new float[0][]); }

    /** Carry an already established straight staff slope into column rectification. */
    static StaffPitchTrack linear(int width,float bottom,float gap,float slope) {
        return new StaffPitchTrack(List.of(new float[]{0,bottom-slope*width*.5f,gap},
                new float[]{width-1,bottom+slope*(width-1-width*.5f),gap}));
    }

    static StaffPitchTrack detect(byte[] gray,int width,int height,float top,float bottom,float gap) {
        if(gray==null||gap<3)return null;
        if(straightRules(gray,width,height,bottom,gap,true))return null;
        boolean broadlyStraight=straightRules(gray,width,height,bottom,gap);
        int stripWidth=Math.min(width,Math.max(80,Math.round(gap*10)));
        int first=Math.max(0,Math.round(top-gap*6)),last=Math.min(height,Math.round(bottom+gap*6));
        if(last<=first)return null;
        List<float[]> samples=new ArrayList<>();
        // Sample between the broad windows too: beams can obscure one rule
        // across several windows while clear intervening ink still proves a curve.
        for(int strip=0;strip<13;strip++) {
            int left=Math.max(0,Math.min(width-stripWidth,Math.round(width*(.15f+strip*.06f)-stripWidth*.5f)));
            byte[] local=new byte[stripWidth*(last-first)];
            for(int y=first;y<last;y++)System.arraycopy(gray,y*width+left,local,(y-first)*stripWidth,stripWidth);
            RawStaffLineDetector.StaffLines best=null;float distance=Float.MAX_VALUE;
            for(var lines:RawStaffLineDetector.detect(local,stripWidth,last-first)) {
                if(lines.gap()<gap*.8f||lines.gap()>gap*1.25f)continue;
                float d=Math.abs(lines.bottom()+first-bottom);
                // Broad straight-rule evidence anchors the physical staff phase.
                // A locally shifted group must be unambiguous before replacing
                // it; nearby tilted groups can safely keep their existing track.
                if(!completeRules(local,stripWidth,last-first,lines,broadlyStraight&&d>gap*.6f))continue;
                // Incomplete semantic stripes can compress or widen the seed's
                // spacing. A complete raw group may recalibrate that seed only
                // while its outer rule remains close to the same physical staff.
                if((lines.gap()<gap*.88f||lines.gap()>gap*1.12f)&&d>gap*1.5f)continue;
                if(d<distance){distance=d;best=lines;}
            }
            if(best!=null)samples.add(new float[]{left+stripWidth*.5f,best.bottom()+first,best.gap()});
        }
        if(samples.size()<4)return null;
        List<Float> gaps=new ArrayList<>();
        for(float[] sample:samples)gaps.add(sample[2]);
        float typicalGap=median(gaps);
        samples.removeIf(a->Math.abs(a[2]-typicalGap)>typicalGap*.08f);
        if(samples.size()<4)return null;
        List<Float> slopes=new ArrayList<>();
        for(int i=0;i<samples.size();i++)for(int j=i+1;j<samples.size();j++) {
            float[] a=samples.get(i),b=samples.get(j);
            slopes.add((b[1]-a[1])/(b[0]-a[0]));
        }
        float slope=median(slopes);
        if(Math.abs(slope)*width>typicalGap*8)return null;
        List<Float> intercepts=new ArrayList<>();
        for(float[] sample:samples)intercepts.add(sample[1]-slope*sample[0]);
        float intercept=median(intercepts);
        samples.removeIf(a->Math.abs(a[1]-intercept-slope*a[0])>typicalGap*.8f);
        if(samples.size()<4)return null;
        samples.sort(Comparator.comparingDouble(a->a[0]));
        if(samples.get(samples.size()-1)[0]-samples.get(0)[0]<width*.36f)return null;
        float min=Float.MAX_VALUE,max=-Float.MAX_VALUE;
        for(float[] p:samples){min=Math.min(min,p[1]);max=Math.max(max,p[1]);}
        if(max-min<typicalGap*.8f) {
            // Even a sub-line tilt can move the local search onto the adjacent
            // rule near a page edge. Accept it only with dense, broadly spaced
            // five-rule samples agreeing on a smooth trend; sparse ledger ink
            // or alternating offsets must not create a new pitch reference.
            if(max-min<typicalGap*.4f||samples.size()<6
                    ||samples.get(samples.size()-1)[0]-samples.get(0)[0]<width*.6f)return null;
            float variation=0;
            for(int i=1;i<samples.size();i++) {
                float change=Math.abs(samples.get(i)[1]-samples.get(i-1)[1]);
                // Missing windows increase the distance between valid samples;
                // constrain the slope rather than treating that gap as an abrupt jump.
                float distance=samples.get(i)[0]-samples.get(i-1)[0];
                if(change>typicalGap*.35f*distance/(width*.12f))return null;
                variation+=change;
            }
            if(variation>max-min+typicalGap*.25f)return null;
        }
        return new StaffPitchTrack(samples);
    }

    /** Recalibrate a straight semantic seed only when widely separated complete
     * printed groups agree on the same staff. Faded rules need contrast evidence,
     * not a darker global ink threshold. */
    static float[] straightPitch(byte[] labels,byte[] gray,int width,int height,float bottom,float gap) {
        if(labels==null||gray==null||gap<3)return null;
        float[] broad=broadStraightPitch(gray,width,height,bottom,gap);
        if(broad!=null)return broad;
        if(!straightRules(gray,width,height,bottom,gap))return null;
        List<float[]> samples=new ArrayList<>();
        for(int i=0;i<13;i++) {
            float x=width*(.15f+i*.06f);
            float[] rules=localRulesWithSlope(labels,gray,width,height,x,
                    Math.round(x-gap*.6f),Math.round(x+gap*.6f),bottom,gap,0,true);
            if(rules!=null)samples.add(new float[]{x,rules[0],rules[1]});
        }
        if(samples.size()<6||samples.get(samples.size()-1)[0]-samples.get(0)[0]<width*.6f)return null;
        List<Float> bottoms=new ArrayList<>(),gaps=new ArrayList<>();
        for(float[] sample:samples){bottoms.add(sample[1]);gaps.add(sample[2]);}
        float base=median(bottoms),spacing=median(gaps);
        for(float[] sample:samples)
            if(Math.abs(sample[1]-base)>gap*.16f||Math.abs(sample[2]-spacing)>gap*.035f)return null;
        // Do not change staff phase or perturb an already accurate seed.
        if(Math.abs(base-bottom)>gap*.4f
                ||Math.max(Math.abs(base-bottom),Math.abs(spacing-gap)*8)<gap*.5f)return null;
        return new float[]{base,spacing};
    }

    /** Dense beams may block the small probes while each printed rule remains
     * visible across most of the page. Keep the established phase and require
     * all five thin, straight rules before correcting accumulated ledger error. */
    static float[] broadStraightPitch(byte[] gray,int width,int height,float bottom,float gap) {
        return broadStraightPitch(gray,width,height,bottom,gap,true);
    }
    static float[] broadStraightPitch(byte[] gray,int width,int height,float bottom,float gap,boolean correctionOnly) {
        if(gray==null||gap<8)return null;
        float[] rows=new float[5];int flank=Math.max(2,Math.round(gap*.3f));
        int radius=Math.max(2,Math.round(gap*.35f));
        for(int line=0;line<5;line++) {
            int center=Math.round(bottom-line*gap),first=center-radius,last=center+radius;
            if(first<flank||last>=height-flank)return null;
            int[] support=new int[last-first+1];int peak=0,at=0;
            for(int y=first;y<=last;y++) {
                for(int x=0;x<width;x++) {
                    int ink=gray[y*width+x]&255;
                    if(ink<170&&(gray[(y-flank)*width+x]&255)>ink+20
                            &&(gray[(y+flank)*width+x]&255)>ink+20)support[y-first]++;
                }
                if(support[y-first]>peak){peak=support[y-first];at=y-first;}
            }
            if(peak<width*.55f)return null;
            int lo=at,hi=at;
            while(lo>0&&support[lo-1]>=peak*.85f)lo--;
            while(hi+1<support.length&&support[hi+1]>=peak*.85f)hi++;
            if(hi-lo>Math.max(2,gap*.25f))return null;
            double weight=0,total=0;
            for(int i=lo;i<=hi;i++){weight+=support[i];total+=(first+i)*(double)support[i];}
            rows[line]=(float)(total/weight);
        }
        float spacing=(rows[0]-rows[4])/4;
        if(correctionOnly&&Math.abs(spacing-gap)<gap*.035f||Math.abs(spacing-gap)>gap*.08f
                ||Math.abs(rows[0]-bottom)>gap*.3f)return null;
        for(int i=1;i<5;i++)if(Math.abs(rows[i]-(rows[0]-spacing*i))>gap*.12f)return null;
        return new float[]{rows[0],spacing};
    }

    private static boolean straightRules(byte[] gray,int width,int height,float bottom,float gap) {
        return straightRules(gray,width,height,bottom,gap,false);
    }

    private static boolean straightRules(byte[] gray,int width,int height,float bottom,float gap,boolean throughout) {
        int radius=Math.max(2,Math.round(gap*.3f)),flank=Math.max(2,Math.round(gap*.32f));
        // Broad evidence from all five original rules outweighs a few narrow
        // strips where darker beams displace a faded outer rule. Do not require
        // complete semantic labels: curved scans can lose entire labelled rules.
        for(int line=0;line<5;line++) {
            int supported=0,samples=0,row=Math.round(bottom-line*gap);
            int first=Math.round(width*.1f),last=Math.round(width*.94f);
            int[] regionSamples=new int[4],regionSupport=new int[4];
            for(int x=first;x<last;x+=2) {
                int region=Math.min(3,(x-first)*4/Math.max(1,last-first));
                samples++;regionSamples[region]++;
                for(int y=Math.max(flank,row-radius);y<=Math.min(height-1-flank,row+radius);y++) {
                    int ink=gray[y*width+x]&255;
                    if(ink<=205&&(gray[(y-flank)*width+x]&255)>=ink+12
                            &&(gray[(y+flank)*width+x]&255)>=ink+12){supported++;regionSupport[region]++;break;}
                }
            }
            if(samples<24||supported<samples*.8f)return false;
            // Reject a curved track only when the edge regions also support straight rules.
            // Broad scale calibration can still bridge a locally obscured group.
            if(throughout)for(int region=0;region<4;region++)
                if(regionSamples[region]<6||regionSupport[region]<regionSamples[region]*.8f)return false;
        }
        return true;
    }

    private static boolean completeRules(byte[] gray,int width,int height,RawStaffLineDetector.StaffLines lines,boolean requireUnambiguousPhase) {
        int radius=Math.max(1,Math.round(lines.gap()*.2f)),flank=Math.max(2,Math.round(lines.gap()*.32f));
        // Six equally spaced rules do not establish which five belong to the
        // staff. A beam extending the group must not shift the sampled phase.
        for(int outside:requireUnambiguousPhase?new int[]{Math.round(lines.rows()[0]-lines.gap()),Math.round(lines.bottom()+lines.gap())}:new int[0]) {
            int columns=0;
            for(int x=0;x<width;x++)for(int y=Math.max(flank,outside-radius);y<=Math.min(height-1-flank,outside+radius);y++) {
                int ink=gray[y*width+x]&255;
                if(ink<=170&&(gray[(y-flank)*width+x]&255)>=ink+12&&(gray[(y+flank)*width+x]&255)>=ink+12) {
                    columns++;break;
                }
            }
            if(columns>=width*.6f)return false;
        }
        for(int row:lines.rows()) {
            int columns=0;
            for(int x=0;x<width;x++)for(int y=Math.max(flank,row-radius);y<=Math.min(height-1-flank,row+radius);y++) {
                int ink=gray[y*width+x]&255;
                if(ink<=170&&(gray[(y-flank)*width+x]&255)>=ink+12&&(gray[(y+flank)*width+x]&255)>=ink+12) {
                    columns++;break;
                }
            }
            if(columns<width*.6f)return false;
        }
        return true;
    }

    float[] at(float x) {
        int right=1;
        while(right<points.length-1&&x>points[right][0])right++;
        float[] a=points[right-1],b=points[right];
        float fraction=(x-a[0])/(b[0]-a[0]);
        return new float[]{a[1]+fraction*(b[1]-a[1]),a[2]+Math.max(0,Math.min(1,fraction))*(b[2]-a[2])};
    }

    /** A complete local five-line group resolves the one-line ambiguity of a partial match. */
    static boolean needsContrast(byte[] gray,int width,int height,float x,float bottom,float gap) {
        if(gray==null)return false;
        int left=Math.max(0,Math.round(x-gap*4)),right=Math.min(width-1,Math.round(x+gap*4));
        int top=Math.max(0,Math.round(bottom-gap*5)),last=Math.min(height-1,Math.round(bottom+gap));
        int samples=0,shaded=0;
        for(int y=top;y<=last;y+=2)for(int xx=left;xx<=right;xx+=2) {
            samples++;if((gray[y*width+xx]&255)<=205)shaded++;
        }
        return samples>=24&&shaded>=samples*.65f;
    }

    static float[] localRules(byte[] labels,byte[] gray,int width,int height,float x,int headLeft,int headRight,
                              float referenceBottom,float gap) {
        return localRules(labels,gray,width,height,x,headLeft,headRight,referenceBottom,gap,false);
    }

    static float[] localRules(byte[] labels,byte[] gray,int width,int height,float x,int headLeft,int headRight,
                              float referenceBottom,float gap,boolean curved) {
        // Curled paper can turn sharply after the final reliable wide sample.
        // Compare complete rules in a sloped window instead of extrapolating
        // the last wide-strip slope past the printed note.
        // Require page-level curvature evidence before trying inclined rules:
        // beams on a flat staff can otherwise form a competing tilted pattern.
        for(float slope:curved?new float[]{0,.04f,-.04f,.08f,-.08f,.12f,-.12f,.16f,-.16f}:new float[]{0}) {
            float[] found=localRulesWithSlope(labels,gray,width,height,x,headLeft,headRight,referenceBottom,gap,slope);
            if(found!=null)return found;
        }
        return null;
    }

    /** White paper can curl beyond the last broad sample. Require all five rules
     * on both sides of the head before replacing that broad pitch reference. */
    static float[] localPrintedRules(byte[] labels,byte[] gray,int width,int height,float x,int headLeft,int headRight,
                                     float referenceBottom,float gap) {
        for(float slope:new float[]{0,.04f,-.04f,.08f,-.08f,.12f,-.12f,.16f,-.16f}) {
            float[] found=localRulesWithSlope(labels,gray,width,height,x,headLeft,headRight,referenceBottom,gap,slope,true);
            if(found!=null)return found;
        }
        return null;
    }

    /** A pale local group still needs all five contrasted rules on both sides
     * and the same semantic staff support as the ordinary printed-rule path. */
    static float[] localFadedRules(byte[] labels,byte[] gray,int width,int height,float x,int headLeft,int headRight,
                                  float referenceBottom,float gap) {
        for(float slope:new float[]{0,.04f,-.04f,.08f,-.08f,.12f,-.12f,.16f,-.16f}) {
            float[] found=localRulesWithSlope(labels,gray,width,height,x,headLeft,headRight,referenceBottom,gap,slope,true,false,225);
            if(found!=null)return found;
        }
        return null;
    }

    /** A beam may cover one rule beside a head near the printed staff edge.
     * Keep nine independently contrasted side/rule observations and require
     * thick ink plus a staff label at the single covered observation. */
    static float[] localOccludedRules(byte[] labels,byte[] gray,int width,int height,float x,int headLeft,int headRight,
                                     float referenceBottom,float gap) {
        for(float slope:new float[]{0,.04f,-.04f,.08f,-.08f,.12f,-.12f,.16f,-.16f}) {
            float[] found=localRulesWithSlope(labels,gray,width,height,x,headLeft,headRight,referenceBottom,gap,slope,true,true);
            if(found!=null)return found;
        }
        return null;
    }

    private static float[] localRulesWithSlope(byte[] labels,byte[] gray,int width,int height,float x,int headLeft,int headRight,
                              float referenceBottom,float gap,float slope) {
        return localRulesWithSlope(labels,gray,width,height,x,headLeft,headRight,referenceBottom,gap,slope,false);
    }

    private static float[] localRulesWithSlope(byte[] labels,byte[] gray,int width,int height,float x,int headLeft,int headRight,
                              float referenceBottom,float gap,float slope,boolean bilateral) {
        return localRulesWithSlope(labels,gray,width,height,x,headLeft,headRight,referenceBottom,gap,slope,bilateral,false);
    }
    private static float[] localRulesWithSlope(byte[] labels,byte[] gray,int width,int height,float x,int headLeft,int headRight,
                              float referenceBottom,float gap,float slope,boolean bilateral,boolean occluded) {
        return localRulesWithSlope(labels,gray,width,height,x,headLeft,headRight,referenceBottom,gap,slope,bilateral,occluded,205);
    }
    private static float[] localRulesWithSlope(byte[] labels,byte[] gray,int width,int height,float x,int headLeft,int headRight,
                              float referenceBottom,float gap,float slope,boolean bilateral,boolean occluded,int inkThreshold) {
        if(gray==null)return null;
        int radius=Math.max(4,Math.round(gap*(occluded?2.5f:3.5f))),exclusion=Math.max(1,Math.round(gap*.45f));
        int left=Math.max(0,Math.round(x)-radius),right=Math.min(width-1,Math.round(x)+radius);
        int top=Math.max(0,Math.round(referenceBottom-gap*5.5f)),bottom=Math.min(height-1,Math.round(referenceBottom+gap*1.5f));
        if(bottom<top)return null;
        int[] strength=new int[bottom-top+1];int samples=0;
        // At the pale threshold, require a narrower stroke so broad shading
        // cannot masquerade as five evenly spaced printed rules.
        int flank=Math.max(2,Math.round(gap*(inkThreshold>205?.22f:.32f)));
        for(int xx=left;xx<=right;xx++) {
            if(xx>=headLeft-exclusion&&xx<=headRight+exclusion)continue;
            samples++;
            for(int y=Math.max(flank,top);y<=Math.min(height-1-flank,bottom);y++) {
                int row=y+Math.round((xx-x)*slope);
                if(row<flank||row+flank>=height)continue;
                int ink=gray[row*width+xx]&255;
                if(ink<=inkThreshold&&(gray[(row-flank)*width+xx]&255)>=ink+12&&(gray[(row+flank)*width+xx]&255)>=ink+12)
                    strength[y-top]++;
            }
        }
        if(samples<8)return null;
        int minimum=Math.max(8,Math.round(samples*(occluded?.35f:bilateral?.60f:.45f))),band=Math.max(2,Math.round(gap*.25f));
        // Reject beam-only peaks before selecting a five-line group, so a stronger beam
        // cannot hide a valid staff group through overlap suppression.
        for(int row=0;row<strength.length;row++)if(strength[row]>=minimum) {
            int supported=0,center=top+row;
            for(int xx=left;xx<=right;xx++) {
                if(xx>=headLeft-exclusion&&xx<=headRight+exclusion)continue;
                int shifted=center+Math.round((xx-x)*slope);
                for(int yy=Math.max(0,shifted-band);yy<=Math.min(height-1,shifted+band);yy++)
                    if(labels[yy*width+xx]==4){supported++;break;}
            }
            if(supported<Math.max(4,samples*(bilateral?.45f:.2f)))strength[row]=0;
        }
        for(var lines:RawStaffLineDetector.detectFromStrength(strength,minimum,strength.length)) {
            if(lines.gap()<gap*.88f||lines.gap()>gap*1.12f)continue;
            float[] centers=new float[5];
            for(int i=0;i<5;i++) {
                int peak=lines.rows()[i],a=peak,b=peak;
                while(a>0&&peak-a<gap*.2f&&strength[a-1]>=strength[peak]*.85f)a--;
                while(b+1<strength.length&&b-peak<gap*.2f&&strength[b+1]>=strength[peak]*.85f)b++;
                centers[i]=top+(a+b)*.5f;
            }
            List<Float> spacings=new ArrayList<>();
            for(int i=0;i<5;i++)for(int j=i+1;j<5;j++)spacings.add((centers[j]-centers[i])/(j-i));
            float spacing=median(spacings);List<Float> bottoms=new ArrayList<>();
            for(int i=0;i<5;i++)bottoms.add(centers[i]+(4-i)*spacing);
            float base=median(bottoms);int consistent=0;
            for(float value:bottoms)if(Math.abs(value-base)<=gap*.16f)consistent++;
            // A beam and four rules can outscore a faded outer rule. If that
            // omitted rule continues on both sides at the original staff edge,
            // the shifted group is ambiguous and must not replace the reference.
            if(Math.abs(base-referenceBottom)>gap*.6f) {
                float outside=base<referenceBottom?base+spacing:base-5*spacing;
                float expected=base<referenceBottom?referenceBottom:referenceBottom-4*gap;
                if(Math.abs(outside-expected)<gap*.35f&&printedRuleOnBothSides(gray,width,height,x,
                        headLeft,headRight,outside,slope,left,right,exclusion,band,flank,inkThreshold))continue;
            }
            if(occluded&&Math.abs(base-referenceBottom)>gap*.45f)continue;
            if(consistent==5&&Math.abs(base-referenceBottom)<=gap*1.5f
                    &&(!bilateral||supportedOnBothSides(labels,gray,width,height,x,headLeft,headRight,
                            base,spacing,slope,left,right,exclusion,band,flank,occluded,inkThreshold)))return new float[]{base,spacing};
        }
        return null;
    }

    private static boolean printedRuleOnBothSides(byte[] gray,int width,int height,float x,
            int headLeft,int headRight,float row,float slope,int left,int right,int exclusion,int band,int flank,int inkThreshold) {
        for(int side=0;side<2;side++) {
            int first=side==0?left:headRight+exclusion+1,last=side==0?headLeft-exclusion-1:right;
            int samples=0,supported=0;
            for(int xx=first;xx<=last;xx++) {
                if(xx<0||xx>=width)continue;samples++;boolean ink=false;
                int center=Math.round(row+(xx-x)*slope);
                for(int y=Math.max(flank,center-band);y<=Math.min(height-1-flank,center+band);y++) {
                    int value=gray[y*width+xx]&255;
                    if(value<=inkThreshold&&(gray[(y-flank)*width+xx]&255)>=value+12
                            &&(gray[(y+flank)*width+xx]&255)>=value+12){ink=true;break;}
                }
                if(ink)supported++;
            }
            if(samples<8||supported<samples*.6f)return false;
        }
        return true;
    }

    private static boolean supportedOnBothSides(byte[] labels,byte[] gray,int width,int height,
            float x,int headLeft,int headRight,float base,float gap,float slope,
            int left,int right,int exclusion,int band,int flank,boolean occluded,int inkThreshold) {
        int covered=0;
        for(int side=0;side<2;side++)for(int line=0;line<5;line++) {
            int first=side==0?left:headRight+exclusion+1;
            int last=side==0?headLeft-exclusion-1:right;
            int samples=0,inkColumns=0,labelColumns=0,thickColumns=0;
            for(int xx=first;xx<=last;xx++) {
                if(xx<0||xx>=width)continue;
                samples++;boolean ink=false,label=false;
                int center=Math.round(base-line*gap+(xx-x)*slope);
                for(int yy=Math.max(flank,center-band);yy<=Math.min(height-1-flank,center+band);yy++) {
                    int value=gray[yy*width+xx]&255;
                    if(value<=inkThreshold&&(gray[(yy-flank)*width+xx]&255)>=value+12
                            &&(gray[(yy+flank)*width+xx]&255)>=value+12)ink=true;
                    if(labels[yy*width+xx]==4)label=true;
                }
                if(ink)inkColumns++;if(label)labelColumns++;
                int thick=Math.max(2,Math.round(gap*.25f));
                for(int yy=Math.max(thick,center-band);yy<=Math.min(height-1-thick,center+band);yy++) {
                    if((gray[yy*width+xx]&255)<140&&((gray[(yy-thick)*width+xx]&255)<140
                            ||(gray[(yy+thick)*width+xx]&255)<140)){thickColumns++;break;}
                }
            }
            if(samples<8||labelColumns<samples*.35f)return false;
            if(inkColumns<samples*.5f) {
                if(!occluded||thickColumns<samples*.6f||++covered>1)return false;
            }
        }
        return true;
    }

    private static float median(List<Float> values) {
        float[] sorted=new float[values.size()];for(int i=0;i<sorted.length;i++)sorted[i]=values.get(i);
        Arrays.sort(sorted);return sorted[sorted.length/2];
    }
}
