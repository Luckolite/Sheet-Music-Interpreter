// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Exact common straight strip feasibility; domain widening requires observed staff co-membership. */
final class CoownedParallelBodyIntervals {
    static boolean fits(int[] top,int[] bottom,int[] possibleTop,int[] possibleBottom,int thickness) {
        if(top==null||bottom==null||possibleTop==null||possibleBottom==null||top.length<2
                ||top.length>512||top.length!=bottom.length||top.length!=possibleTop.length
                ||top.length!=possibleBottom.length||thickness<1)return false;
        int n=top.length;long[] lo=new long[n],hi=new long[n];
        for(int i=0;i<n;i++) {
            if(possibleTop[i]<0||possibleTop[i]>top[i]||top[i]>bottom[i]
                    ||bottom[i]>possibleBottom[i])return false;
            lo[i]=Math.max((long)bottom[i]-1,(long)possibleTop[i]+thickness-2);
            hi[i]=Math.min((long)possibleBottom[i]+1,(long)top[i]+thickness);
            if(lo[i]>hi[i])return false;
        }
        long lowN=0,lowD=1,highN=0,highD=1;boolean first=true;
        // Numerators are differences of integer image coordinates, denominators<=511;
        // even full signed-int inputs cross-multiply safely within signed long.
        for(int i=0;i<n;i++)for(int j=i+1;j<n;j++) {
            long a=lo[j]-hi[i],b=hi[j]-lo[i],d=j-i;
            if(first||a*lowD>lowN*d){lowN=a;lowD=d;}
            if(first||b*highD<highN*d){highN=b;highD=d;}
            first=false;
            if(lowN*highD>highN*lowD)return false;
        }
        return !first;
    }
}
