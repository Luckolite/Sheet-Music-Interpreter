// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/**
 * Experimental, bounded contour hypothesis: a compact interior-thickness maximum proposes an
 * ellipse; the entire contour and filled interior must be supported by exact observed members,
 * and one independently observed shaft must touch the same body boundary. Ambiguity refuses.
 */
final class InteriorThicknessOwnership {
  private static final int MAX_PIXELS=16_777_216, MAX_LOCAL=262_144;
  private static final long MAX_WORK=70_000_000L;
  private static final double SQRT2=Math.sqrt(2.0);
  private record Seed(double x,double y,double radius) {}
  private record Fit(double cx,double cy,double a,double b,double angle,int left,int top,int right,int bottom,double score) {}
  private static final class Budget {long used;boolean exhausted;boolean add(long n){if(n<0||used>MAX_WORK-n){exhausted=true;return false;}used+=n;return true;}}
  private InteriorThicknessOwnership() {}

  static LobeExtentProposal.Core derive(byte[] labels,byte[] gray,int w,int h,int[] members,float gap) { return derive(labels,gray,w,h,members,gap,null); }
  static LobeExtentProposal.Core derive(byte[] labels,byte[] gray,int w,int h,int[] members,float gap,StringBuilder trace) {
        return derive(labels, gray, w, h, members, gap, trace, Set.of(), new Budget(), null);
    }

    private record ShaftColumn(int x, int top, int bottom) {}

    private record ShaftWitness(Object parent, List<ShaftColumn> columns) {}

    private static final class ShaftContext {
        final Object parent;
        final List<ShaftWitness> established;
        final List<ShaftWitness> observed = new ArrayList<>();

        ShaftContext(Object parent, List<ShaftWitness> established) {
            this.parent = parent;
            this.established = established;
        }

        boolean binds(int x, int top, int bottom) {
            Object owner = null;
            for (var witness : established)
                if (witness.parent() != parent)
                    for (var column : witness.columns())
                        if (column.x() == x && column.top() <= top && column.bottom() >= bottom) {
                            if (owner != null && owner != witness.parent()) return false;
                            owner = witness.parent();
                        }
            return owner != null;
        }
    }

    static Map<Object, LobeExtentProposal.Core> derivePartition(
            byte[] labels,
            byte[] gray,
            int w,
            int h,
            ExactComponentMembers.ProducerPartition partition,
            float gap,
            StringBuilder trace) {
        return deriveCohort(labels, gray, w, h, Collections.singletonList(partition), gap, trace);
    }

    static Map<Object, LobeExtentProposal.Core> deriveCohort(
            byte[] labels,
            byte[] gray,
            int w,
            int h,
            List<ExactComponentMembers.ProducerPartition> input,
            float gap,
            StringBuilder trace) {
        if (labels == null || gray == null || input == null || input.isEmpty() || input.size() > 8)
            return null;
        var source = input.get(0);
        if (source == null
                || !source.authenticates(
                        labels,
                        gray,
                        w,
                        h,
                        new ExactComponentMembers.Budget(2L * labels.length + 4096, 4096)))
            return null;
        var parents = new ArrayList<ExactComponentMembers.ProducerPartition>();
        var identities = new IdentityHashMap<Object, Boolean>();
        for (var partition : input) {
            if (partition == null
                    || !source.sameProducer(partition)
                    || !partition.residual().isEmpty()
                    || partition.leaves().isEmpty()
                    || partition.leaves().size() > 8) return null;
            if (identities.put(partition.parentIdentity(), true) == null) parents.add(partition);
        }
        Budget budget = new Budget();
        var established = new ArrayList<ShaftWitness>();
        var accepted = new IdentityHashMap<Object, LobeExtentProposal.Core>();
        var done = new IdentityHashMap<Object, Boolean>();
        for (int pass = 0; pass < parents.size(); pass++) {
            boolean progress = false;
            for (var partition : parents)
                if (!done.containsKey(partition.parentIdentity())) {
                    if (!budget.add(2L * partition.parentPixels().size())) return null;
                    Set<Integer> parent = new HashSet<>(partition.parentPixels());
                    var candidates = new IdentityHashMap<Object, LobeExtentProposal.Core>();
                    var context =
                            new ShaftContext(partition.parentIdentity(), List.copyOf(established));
                    boolean valid = true;
                    for (var leaf : partition.leaves()) {
                        int[] members =
                                leaf.pixels().stream().mapToInt(Integer::intValue).toArray();
                        var core =
                                derive(
                                        labels, gray, w, h, members, gap, trace, parent, budget,
                                        context);
                        if (core == null) {
                            valid = false;
                            break;
                        }
                        var st = leaf.stats();
                        var head =
                                new SymbolStemHeadRecovery.Head(
                                        st.minX(),
                                        st.minY(),
                                        st.maxX(),
                                        st.maxY(),
                                        st.area(),
                                        st.centerX(),
                                        st.centerY());
                        if (!FilledSparseBeamHead.completeInterior(gray, w, h, head, gap)
                                || WholeHeadPocketEvidence.proved(
                                        gray,
                                        w,
                                        h,
                                        core.left(),
                                        core.top(),
                                        core.right(),
                                        core.bottom(),
                                        gap)
                                || WholeHeadPocketEvidence.hasEnclosedOpening(
                                        gray,
                                        w,
                                        h,
                                        core.left(),
                                        core.top(),
                                        core.right(),
                                        core.bottom(),
                                        core.centerX(),
                                        core.centerY(),
                                        gap)) {
                            valid = false;
                            break;
                        }
                        candidates.put(leaf.identity(), core);
                    }
                    if (budget.exhausted) return null;
                    if (valid) {
                        done.put(partition.parentIdentity(), true);
                        accepted.putAll(candidates);
                        established.addAll(context.observed);
                        progress = true;
                        event(trace, "source-parent-qualified leaves=" + partition.leaves().size());
                    }
                }
            if (done.size() == parents.size()) return Collections.unmodifiableMap(accepted);
            if (!progress) return null;
        }
        return null;
    }

    private static LobeExtentProposal.Core derive(
            byte[] labels,
            byte[] gray,
            int w,
            int h,
            int[] members,
            float gap,
            StringBuilder trace,
            Set<Integer> genuineParentMembers,
            Budget budget,
            ShaftContext context) {
    if(labels==null||gray==null||members==null||w<1||h<1||(long)w*h!=labels.length||gray.length!=labels.length
        ||labels.length>MAX_PIXELS||members.length==0||members.length>1_000_000||!Float.isFinite(gap)||gap<8||gap>64)return refuse(trace,"derive-check-1");
        long upfront = (long) labels.length + (long) members.length * 9L;
        if (!budget.add(upfront)) return refuse(trace, "initial-work-budget");
        event(trace, "precharged-initial=" + upfront);
    int minX=w,minY=h,maxX=-1,maxY=-1;
    for(int p:members){if(p<0||p>=labels.length||(labels[p]&255)!=2)return refuse(trace,"derive-check-2");int x=p%w,y=p/w;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
    int bw=maxX-minX+3,bh=maxY-minY+3;long area=(long)bw*bh;
    if(area>MAX_LOCAL||maxX-minX+1>6*gap||maxY-minY+1>2*gap)return refuse(trace,"derive-check-3");
    long localPrecharge=area*80L;if(!budget.add(localPrecharge))return refuse(trace,"local-work-budget");event(trace,"precharged-local="+localPrecharge+" members="+members.length+" bounds="+minX+","+minY+".."+maxX+","+maxY); int ox=minX-1,oy=minY-1;boolean[] exact=new boolean[(int)area];
    for(int p:members){int local=(p/w-oy)*bw+p%w-ox;if(exact[local])return refuse(trace,"duplicate-member");exact[local]=true;}
        // Check completeness locally and in one pass: no omitted adjacent class-2 member.
        for (int p : members) {
            int x = p % w, y = p / w;
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    int xx = x + dx, yy = y + dy;
                    if (xx >= 0
                            && xx < w
                            && yy >= 0
                            && yy < h
                            && (labels[yy * w + xx] & 255) == 2
                            && !exact[(yy - oy) * bw + xx - ox]
                            && !genuineParentMembers.contains(yy * w + xx))
                        return refuse(trace, "derive-check-4");
                }
        }
    float[] distance=distanceTransform(exact,bw,bh);
    List<Seed> seeds=interiorPeaks(distance,exact,bw,bh,ox,oy,gap);
    event(trace,"seed-count="+seeds.size()+" work="+budget.used);if(seeds.isEmpty())return refuse(trace,"no-interior-seed");
    int paper=paper(gray,w,h,minX,minY,maxX,maxY,Math.round(gap),budget);if(paper<210)return refuse(trace,"derive-check-5");
    List<Fit> plausible=new ArrayList<>();
    for(Seed seed:seeds){Fit f=fit(seed,exact,bw,bh,ox,oy,gray,labels,w,h,paper,gap,budget);if(budget.exhausted)return refuse(trace,"fit-search-work-exhausted");if(f!=null)plausible.add(f);}
    event(trace,"fit-count="+plausible.size()+" work="+budget.used);if(plausible.isEmpty())return refuse(trace,"no-fit-or-fit-work-exhausted");
    plausible.sort(Comparator.comparingDouble(Fit::score).reversed());
    Fit winner=plausible.get(0);
    if(rejectNearbyWholeOwners(labels,gray,w,h,minX,minY,maxX,maxY,exact,bw,bh,ox,oy,gap,budget))return refuse(trace,"nearby-whole-owner-or-budget");
    if(hasForeignTypedInkInside(winner,labels,w,h,budget))return refuse(trace,"foreign-typed-interior-or-budget");
    if(hasMultiSideContourExcess(winner,gray,w,h,members,gap,budget))return refuse(trace,"multi-side-contour-excess-or-budget");
        // Thickness maxima are proposals, not ownership evidence. A secondary peak only competes
        // when it independently yields a complete fitted body; that case is rejected below.
        // Any second disjoint fitted body in the same complete source component makes ownership
        // unknown.
        for (int i = 1; i < plausible.size(); i++) {
            Fit other = plausible.get(i);
            if (Math.hypot(other.cx - winner.cx, other.cy - winner.cy) > gap * .55
                    && boxesOverlapFraction(winner, other) < .45)
                return refuse(trace, "competing-fitted-body");
        }
        long sw = maxX - minX + 1, sh = maxY - minY + 1, sg = (long) Math.ceil(gap);
        long shaftWork =
                (long) members.length + sw * sh + (sw + 6 * sg) * (sh + 4 * sg) + sw * 6 * sg;
        if (!budget.add(shaftWork)) return refuse(trace, "shaft-work-budget");
        boolean contact =
                shaftContact(
                        winner, exact, bw, bh, ox, oy, gray, labels, w, h, members, gap, context,
                        trace);
        event(
                trace,
                "own-shaft-contact="
                        + contact
                        + " shaftWork="
                        + shaftWork
                        + " totalWork="
                        + budget.used
                        + " fitCenter="
                        + winner.cx
                        + ","
                        + winner.cy
                        + " fitAxes="
                        + winner.a
                        + ","
                        + winner.b);
        if (!contact) return refuse(trace, "own-shaft-contact");
    int bodyL=w,bodyT=h,bodyR=-1,bodyB=-1;
    double co=Math.cos(winner.angle),si=Math.sin(winner.angle);
    for(int p:members){int x=p%w,y=p/w;double X=(x-winner.cx)*co+(y-winner.cy)*si,Y=-(x-winner.cx)*si+(y-winner.cy)*co,q=X*X/(winner.a*winner.a)+Y*Y/(winner.b*winner.b);if(q<=1.15&&(gray[p]&255)<=paper-25){bodyL=Math.min(bodyL,x);bodyR=Math.max(bodyR,x);bodyT=Math.min(bodyT,y);bodyB=Math.max(bodyB,y);}}
    if(bodyR<bodyL||bodyB<bodyT)return refuse(trace,"derive-check-6");
    event(trace,"accepted-observed-body="+bodyL+","+bodyT+".."+bodyR+","+bodyB+" totalWork="+budget.used);return new LobeExtentProposal.Core(bodyL,bodyT,bodyR,bodyB,(float)winner.cx,(float)winner.cy);
  }

  private static void event(StringBuilder trace,String value){if(trace!=null)trace.append(value).append("; ");}
  private static LobeExtentProposal.Core refuse(StringBuilder trace,String reason){event(trace,"first-refusal="+reason);return null;}
  private static float[] distanceTransform(boolean[] exact,int w,int h){
    float inf=w+h+2;float[] d=new float[exact.length];for(int i=0;i<d.length;i++)d[i]=exact[i]?inf:0;
    for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){int p=y*w+x;if(!exact[p])continue;float v=d[p];v=Math.min(v,d[p-1]+1);v=Math.min(v,d[p-w]+1);v=Math.min(v,d[p-w-1]+(float)SQRT2);v=Math.min(v,d[p-w+1]+(float)SQRT2);d[p]=v;}
    for(int y=h-2;y>0;y--)for(int x=w-2;x>0;x--){int p=y*w+x;if(!exact[p])continue;float v=d[p];v=Math.min(v,d[p+1]+1);v=Math.min(v,d[p+w]+1);v=Math.min(v,d[p+w+1]+(float)SQRT2);v=Math.min(v,d[p+w-1]+(float)SQRT2);d[p]=v;}
    return d;
  }

  private static List<Seed> interiorPeaks(float[] d,boolean[] exact,int w,int h,int ox,int oy,float gap){
    List<Seed> raw=new ArrayList<>();double min=gap*.22;
    for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){int p=y*w+x;if(!exact[p]||d[p]<min)continue;boolean peak=true;for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)if(d[(y+dy)*w+x+dx]>d[p]+.01)peak=false;if(peak)raw.add(new Seed(ox+x,oy+y,d[p]));}
    raw.sort(Comparator.comparingDouble(Seed::radius).reversed());List<Seed> merged=new ArrayList<>();
    for(Seed s:raw){boolean near=false;for(Seed q:merged)if(Math.hypot(s.x-q.x,s.y-q.y)<gap*.35){near=true;break;}if(!near)merged.add(s);if(merged.size()>16)return List.of();}
    return merged;
  }

  private static Fit fit(Seed seed,boolean[] exact,int localW,int localH,int ox,int oy,byte[] gray,byte[] labels,int w,int h,int paper,float gap,Budget budget){
    double sx=0,sy=0,sxx=0,syy=0,sxy=0;int count=0;double radius=gap*.95,r2=radius*radius;
    for(int ly=1;ly<localH-1;ly++)for(int lx=1;lx<localW-1;lx++){
      if(!budget.add(1))return null;int i=ly*localW+lx;if(!exact[i])continue;int x=ox+lx,y=oy+ly;double dx=x-seed.x,dy=y-seed.y;
      if(dx*dx+dy*dy>r2||(gray[y*w+x]&255)>paper-25)continue;sx+=x;sy+=y;sxx+=x*x;syy+=y*y;sxy+=x*y;count++;
    }
    if(count<gap*gap*.12)return null;double mx=sx/count,my=sy/count,vx=sxx/count-mx*mx,vy=syy/count-my*my,cov=sxy/count-mx*my;
    double theta=.5*Math.atan2(2*cov,vx-vy),disc=Math.sqrt(Math.max(0,(vx-vy)*(vx-vy)+4*cov*cov));
    double major=2*Math.sqrt(Math.max(.01,(vx+vy+disc)/2)),minor=2*Math.sqrt(Math.max(.01,(vx+vy-disc)/2));
    if(major<gap*.55||major>gap*1.4||minor<gap*.35||minor>gap*.9)return null;
    Fit best=null;double bestScore=-1;
    for(int dy=-2;dy<=2;dy++)for(int dx=-2;dx<=2;dx++)for(int da=-2;da<=2;da++)for(int db=-2;db<=2;db++)for(int dt=-2;dt<=2;dt++){
      if(!budget.add(1))return null;double a=major+da*.5,b=minor+db*.5,angle=theta+Math.toRadians(dt*5);if(a/b<1.15||a/b>3.1)continue;double co=Math.cos(angle),si=Math.sin(angle),cx=mx+dx,cy=my+dy;
      double rx=Math.sqrt(a*a*co*co+b*b*si*si),ry=Math.sqrt(a*a*si*si+b*b*co*co);int l=(int)Math.floor(cx-rx),r=(int)Math.ceil(cx+rx),t=(int)Math.floor(cy-ry),bt=(int)Math.ceil(cy+ry);
      if(l<1||r>=w-1||t<1||bt>=h-1||r-l>2.8*gap||bt-t>2*gap)continue;
      int inside=0,occupied=0;for(int y=t;y<=bt;y++)for(int x=l;x<=r;x++){if(!budget.add(1))return null;double X=(x-cx)*co+(y-cy)*si,Y=-(x-cx)*si+(y-cy)*co,q=X*X/(a*a)+Y*Y/(b*b);if(q<=.70){inside++;if(exactAt(exact,localW,localH,ox,oy,x,y)&&(gray[y*w+x]&255)<=paper-25)occupied++;}}
      if(inside<8)continue;double fill=occupied/(double)inside;if(fill<.78)continue;
      int supported=0,samples=72;for(int k=0;k<samples;k++){if(!budget.add(1))return null;double z=2*Math.PI*k/samples,X=a*Math.cos(z),Y=b*Math.sin(z),x0=cx+X*co-Y*si,y0=cy+X*si+Y*co;boolean hit=false;int ix=(int)Math.round(x0),iy=(int)Math.round(y0);for(int yy=iy-1;yy<=iy+1&&!hit;yy++)for(int xx=ix-1;xx<=ix+1;xx++){if(!budget.add(1))return null;if(xx<0||xx>=w||yy<0||yy>=h)continue;if(exactAt(exact,localW,localH,ox,oy,xx,yy)&&(gray[yy*w+xx]&255)<=paper-25){hit=true;break;}}if(hit)supported++;}
      double contour=supported/(double)samples;if(contour<.82)continue;
      int outsideHits=0;for(int k=0;k<samples;k++){if(!budget.add(1))return null;double z=2*Math.PI*k/samples,X=a*1.22*Math.cos(z),Y=b*1.22*Math.sin(z),px=cx+X*co-Y*si,py=cy+X*si+Y*co;int ix=(int)Math.round(px),iy=(int)Math.round(py);boolean hit=false;for(int yy=iy-1;yy<=iy+1&&!hit;yy++)for(int xx=ix-1;xx<=ix+1;xx++){if(!budget.add(1))return null;if(xx<0||xx>=w||yy<0||yy>=h)continue;if(exactAt(exact,localW,localH,ox,oy,xx,yy)){hit=true;break;}}if(hit)outsideHits++;}
      double outside=outsideHits/(double)samples;if(outside>.30)continue;double score=.45*contour+.35*fill+.20*(1-outside);Fit f=new Fit(cx,cy,a,b,angle,l,t,r,bt,score);if(score>bestScore){best=f;bestScore=score;}
    }
    return best;
  }
  static boolean exactAt(boolean[] exact,int localW,int localH,int ox,int oy,int x,int y){int lx=x-ox,ly=y-oy;return lx>=0&&lx<localW&&ly>=0&&ly<localH&&(long)ly*localW+lx<exact.length&&exact[ly*localW+lx];}
  private static boolean hasForeignTypedInkInside(Fit f,byte[] labels,int w,int h,Budget budget){double co=Math.cos(f.angle),si=Math.sin(f.angle);int l=Math.max(0,(int)Math.floor(f.cx-f.a-1)),r=Math.min(w-1,(int)Math.ceil(f.cx+f.a+1)),t=Math.max(0,(int)Math.floor(f.cy-f.a-1)),b=Math.min(h-1,(int)Math.ceil(f.cy+f.a+1));for(int y=t;y<=b;y++)for(int x=l;x<=r;x++){if(!budget.add(1))return true;double X=(x-f.cx)*co+(y-f.cy)*si,Y=-(x-f.cx)*si+(y-f.cy)*co,q=X*X/(f.a*f.a)+Y*Y/(f.b*f.b);int role=labels[y*w+x]&255;if(q<=.70&&(role!=0&&role!=2))return true;}return false;}
  private static boolean hasMultiSideContourExcess(Fit f,byte[] gray,int w,int h,int[] members,float gap,Budget budget){
    int[] sides=new int[4];double co=Math.cos(f.angle),si=Math.sin(f.angle);
    for(int p:members){if(!budget.add(1))return true;int x=p%w,y=p/w;if((gray[p]&255)>170)continue;double X=(x-f.cx)*co+(y-f.cy)*si,Y=-(x-f.cx)*si+(y-f.cy)*co,q=X*X/(f.a*f.a)+Y*Y/(f.b*f.b);if(q<=1.15||q>2.3||Math.abs(X)>f.a*1.35||Math.abs(Y)>f.b*1.35)continue;int side=(Math.abs(X/f.a)>=Math.abs(Y/f.b))?(X<0?0:1):(Y<0?2:3);sides[side]++;}
    int occupiedSides=0;for(int n:sides)if(n>=2)occupiedSides++;return occupiedSides>=3;
  }  private static double boxesOverlapFraction(Fit a,Fit b){int l=Math.max(a.left,b.left),r=Math.min(a.right,b.right),t=Math.max(a.top,b.top),bt=Math.min(a.bottom,b.bottom);if(r<l||bt<t)return 0;double overlap=(r-l+1.0)*(bt-t+1.0);return overlap/Math.min((a.right-a.left+1.0)*(a.bottom-a.top+1.0),(b.right-b.left+1.0)*(b.bottom-b.top+1.0));}

    private static boolean shaftContact(
            Fit f,
            boolean[] exact,
            int localW,
            int localH,
            int ox,
            int oy,
            byte[] gray,
            byte[] labels,
            int w,
            int h,
            int[] members,
            float gap,
            ShaftContext context,
            StringBuilder trace) {
        boolean up =
                shaftContactDirection(
                        f, exact, localW, localH, ox, oy, gray, labels, w, h, members, gap, -1,
                        context, trace);
        boolean down =
                shaftContactDirection(
                        f, exact, localW, localH, ox, oy, gray, labels, w, h, members, gap, 1,
                        context, trace);
        return up != down;
    }

    private static boolean shaftContactDirection(
            Fit f,
            boolean[] exact,
            int localW,
            int localH,
            int ox,
            int oy,
            byte[] gray,
            byte[] labels,
            int w,
            int h,
            int[] members,
            float gap,
            int direction,
            ShaftContext context,
            StringBuilder trace) {
    int minX=w,minY=h,maxX=-1,maxY=-1;for(int p:members){int x=p%w,y=p/w;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
        int clusters = 0,
                clusterStart = -1,
                clusterEnd = -1,
                candidateX = -1,
                candidateY = -1,
                bestRun = -1;
        boolean detached = false;
    double co=Math.cos(f.angle),si=Math.sin(f.angle);
    for(int x=minX;x<=maxX;x++){
            int root = direction < 0 ? h : -1;
            for (int y = minY; y <= maxY; y++) {
                if (exactAt(exact, localW, localH, ox, oy, x, y))
                    root = direction < 0 ? Math.min(root, y) : Math.max(root, y);
            }
            boolean found = direction < 0 ? root != h : root >= 0;
            int first = found ? root + direction : (direction < 0 ? minY : maxY), run = 0;
            for (int distance = 0; distance <= (int) Math.ceil(gap * 2.3); distance++) {
                int y = first + direction * distance;
                if (y < 0 || y >= h) break;
                if ((gray[y * w + x] & 255) < 170) {
                    run++;
                } else break;
            }
      if(run<(int)Math.ceil(gap*2.3)-1)continue;
            if (!found) {
                event(trace, "shaft-dir=" + direction + " detached-column=" + x);
                detached = true;
                continue;
            }
      double X=(x-f.cx)*co+(root-f.cy)*si,Y=-(x-f.cx)*si+(root-f.cy)*co,q=X*X/(f.a*f.a)+Y*Y/(f.b*f.b);
            // A shaft must touch the candidate body at its own observed boundary, not merely share
            // the component.
            if (q < .55 || q > 1.45 || (gray[root * w + x] & 255) >= 205) {
                event(
                        trace,
                        "shaft-dir="
                                + direction
                                + " rejected-column="
                                + x
                                + " root="
                                + root
                                + " q="
                                + q
                                + " rootGray="
                                + (gray[root * w + x] & 255));
                detached = true;
                continue;
            }
            if (clusterEnd < 0 || x > clusterEnd + 1) {
                clusters++;
                clusterStart = x;
                clusterEnd = x;
                candidateX = x;
                candidateY = root;
                bestRun = run;
            } else {
                clusterEnd = x;
                if (run > bestRun) {
                    candidateX = x;
                    candidateY = root;
                    bestRun = run;
                }
            }
    }
        event(
                trace,
                "shaft-dir="
                        + direction
                        + " detached="
                        + detached
                        + " clusters="
                        + clusters
                        + " selected="
                        + candidateX
                        + ":"
                        + candidateY
                        + " run="
                        + bestRun);
    if(detached||clusters!=1||candidateX<0||candidateY<0)return false;
        int top = Math.max(0, minY - (int) Math.ceil(gap * (direction < 0 ? 3.0 : 1.0))),
                bottom = Math.min(h - 1, maxY + (int) Math.ceil(gap * (direction < 0 ? 1.0 : 3.0)));
    for(int x=Math.max(0,minX-(int)Math.ceil(gap*3));x<=Math.min(w-1,maxX+(int)Math.ceil(gap*3));x++){
            int run = 0;
            for (int y = top; y <= bottom + 1; y++) {
                if (y <= bottom && (labels[y * w + x] & 255) == 1 && (gray[y * w + x] & 255) < 205)
                    run++;
                else {
                    if (run >= (int) Math.ceil(gap * 2.3) - 1
                            && Math.abs(x - candidateX) > Math.ceil(gap * 1.5)) {
                        boolean bound = context != null && context.binds(x, y - run, y - 1);
                        event(
                                trace,
                                "external-shaft="
                                        + x
                                        + ":"
                                        + (y - run)
                                        + ".."
                                        + (y - 1)
                                        + " prior-source-parent-bound="
                                        + bound);
                        if (!bound) return false;
                    }
                    run = 0;
                }
            }
        }
        if (context != null) {
            var columns = new ArrayList<ShaftColumn>();
            for (int x = clusterStart; x <= clusterEnd; x++) {
                int root = direction < 0 ? h : -1;
                for (int y = minY; y <= maxY; y++)
                    if (exactAt(exact, localW, localH, ox, oy, x, y))
                        root = direction < 0 ? Math.min(root, y) : Math.max(root, y);
                int first = root + direction, last = root;
                for (int step = 1; step <= (int) Math.ceil(gap * 6); step++) {
                    int y = root + direction * step;
                    if (y < 0 || y >= h || (gray[y * w + x] & 255) >= 170) break;
                    last = y;
                }
                columns.add(new ShaftColumn(x, Math.min(first, last), Math.max(first, last)));
            }
            context.observed.add(new ShaftWitness(context.parent, List.copyOf(columns)));
    }
    return true;
  }  private static boolean rejectNearbyWholeOwners(byte[] labels,byte[] gray,int w,int h,int minX,int minY,int maxX,int maxY,boolean[] exact,int exactW,int exactH,int exactX,int exactY,float gap,Budget budget){
    int l=Math.max(0,minX-(int)Math.ceil(gap*3)),r=Math.min(w-1,maxX+(int)Math.ceil(gap*3)),t=Math.max(0,minY-(int)Math.ceil(gap*3)),b=Math.min(h-1,maxY+(int)Math.ceil(gap*3));long area=(long)(r-l+1)*(b-t+1);if(area>MAX_LOCAL||!budget.add(area*10L))return true;boolean[] seen=new boolean[(int)area];int[] q=new int[(int)area];int rw=r-l+1;
    for(int i=0;i<seen.length;i++){int x=l+i%rw,y=t+i/rw,p=y*w+x;if(seen[i]||exactAt(exact,exactW,exactH,exactX,exactY,x,y)||(labels[p]&255)!=2)continue;int head=0,tail=1;q[0]=i;seen[i]=true;int x0=x,x1=x,y0=y,y1=y;
      while(head<tail){int z=q[head++],zx=l+z%rw,zy=t+z/rw;x0=Math.min(x0,zx);x1=Math.max(x1,zx);y0=Math.min(y0,zy);y1=Math.max(y1,zy);for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){if(dx==0&&dy==0)continue;int nx=zx+dx,ny=zy+dy;if(nx<l||nx>r||ny<t||ny>b)continue;int ni=(ny-t)*rw+nx-l,np=ny*w+nx;if(!seen[ni]&&!exactAt(exact,exactW,exactH,exactX,exactY,nx,ny)&&(labels[np]&255)==2){seen[ni]=true;q[tail++]=ni;}}}
      int gw=x1-x0+1,gh=y1-y0+1;if(gw>=gap*1.35f&&gw<=gap*2.4f&&gh>=gap*.65f&&gh<=gap*1.4f&&!budget.add((long)gw*gh*10L))return true;
      if(WholeHeadPocketEvidence.proved(gray,w,h,x0,y0,x1,y1,gap))return true;
    }return false;
    }

    static int observedPaper(
            byte[] gray, int w, int h, int left, int top, int right, int bottom, float gap) {
        if (gray == null
                || gray.length != (long) w * h
                || left < 0
                || top < 0
                || right >= w
                || bottom >= h
                || left > right
                || top > bottom
                || !Float.isFinite(gap)
                || gap < 8
                || gap > 64) return -1;
        return paper(gray, w, h, left, top, right, bottom, Math.round(gap), new Budget());
    }

    private static int paper(
            byte[] gray,
            int w,
            int h,
            int minX,
            int minY,
            int maxX,
            int maxY,
            int halo,
            Budget budget) {
        int l = Math.max(0, minX - halo),
                r = Math.min(w - 1, maxX + halo),
                t = Math.max(0, minY - halo),
                b = Math.min(h - 1, maxY + halo);
        long scan = (long) (r - l + 1) * (b - t + 1);
        if (!budget.add(scan)) return -1;
        int[] hist = new int[256];
        int n = 0;
        for (int y = t; y <= b; y++)
            for (int x = l; x <= r; x++) {
                if (x >= minX && x <= maxX && y >= minY && y <= maxY) continue;
                hist[gray[y * w + x] & 255]++;
                n++;
            }
        int seen = 0;
        for (int v = 0; v < 256; v++) if ((seen += hist[v]) >= n * .8) return v;
        return 255;
    }
}










