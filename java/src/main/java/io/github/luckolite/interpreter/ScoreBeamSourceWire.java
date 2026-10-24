// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

/** Guide297 literal printed beams; frame is independent of expressive-event IDs. */
public final class ScoreBeamSourceWire {
    private ScoreBeamSourceWire() {}
    private static final int MAX_BYTES=32*1024*1024, MAX=1_000_000;
    public static void requireLegacyCompatible(ScoreBeamSource s)throws IOException {
        if(!s.groups().isEmpty() || !s.detachedColumns().isEmpty() || s.noteOrigins().stream().anyMatch(Optional::isPresent))
            throw new IOException("Legacy guide cannot preserve source beams");
    }
    public static void write(DataOutput out,ScoreBeamSource s)throws IOException {out.write(encode(s));}
    public static ScoreBeamSource read(DataInput in,int notes)throws IOException {
        int length=in.readInt();if(length<12 || length>MAX_BYTES)throw new IOException("Invalid beam frame length");
        byte[] frame=new byte[length+4];ByteBuffer.wrap(frame).putInt(length);in.readFully(frame,4,length);
        return decode(frame,notes);
    }
    private static int count(DataInputStream in,int max)throws IOException {
        int n=in.readInt();if(n<0 || n>max || (long)n>in.available())throw new IOException("Invalid beam count");return n;
    }
    private static void anchor(DataOutputStream out,ScoreBeamSource.Anchor a)throws IOException {out.writeInt(a.pageIndex());out.writeInt(a.noteIndex());}
    private static ScoreBeamSource.Anchor anchor(DataInputStream in)throws IOException {return new ScoreBeamSource.Anchor(in.readInt(),in.readInt());}
    private static void column(DataOutputStream out,ScoreBeamSource.Column c)throws IOException {
        out.writeInt(c.members().size());for(var a:c.members())anchor(out,a);
    }
    private static ScoreBeamSource.Column column(DataInputStream in)throws IOException {
        int n=count(in,128);List<ScoreBeamSource.Anchor> a=new ArrayList<>();for(int i=0;i<n;i++)a.add(anchor(in));return new ScoreBeamSource.Column(a);
    }
    public static byte[] encode(ScoreBeamSource s)throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(s.groups().size());
        for(var g:s.groups()) {
            ByteBuffer b=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(g.id()));
            byte[] id=new byte[b.remaining()];b.get(id);if(id.length>1024)throw new IOException("Beam ID too long");
            out.writeInt(id.length);out.write(id);out.writeInt(g.columns().size());for(var c:g.columns()){column(out,c);if(bytes.size()>MAX_BYTES)throw new IOException("Beam frame too large");}
            out.writeInt(g.bridges().size());for(int mask:g.bridges())out.writeInt(mask);
            out.writeInt(g.hooks().size());for(var h:g.hooks()){out.writeInt(h.columnIndex());out.writeInt(h.level());out.writeInt(h.direction().ordinal());}
            if(bytes.size()>MAX_BYTES)throw new IOException("Beam frame too large");
        }
        out.writeInt(s.detachedColumns().size());for(var c:s.detachedColumns()){column(out,c);if(bytes.size()>MAX_BYTES)throw new IOException("Beam frame too large");}
        out.writeInt(s.noteOrigins().size());for(var a:s.noteOrigins()){out.writeBoolean(a.isPresent());if(a.isPresent())anchor(out,a.get());}
        if(bytes.size()>MAX_BYTES)throw new IOException("Beam frame too large");
        ByteArrayOutputStream frame=new ByteArrayOutputStream();DataOutputStream header=new DataOutputStream(frame);header.writeInt(bytes.size());bytes.writeTo(header);return frame.toByteArray();
    }
    public static ScoreBeamSource decode(byte[] frame,int notes)throws IOException {
        if(frame==null || frame.length<16 || frame.length>MAX_BYTES+4 || notes<0 || notes>MAX)throw new IOException("Invalid beam frame");
        try {
            DataInputStream in=new DataInputStream(new ByteArrayInputStream(frame));if(in.readInt()!=frame.length-4)throw new IOException("Invalid beam framing");
            int n=count(in,MAX);List<ScoreBeamSource.Group> groups=new ArrayList<>();long columns=0;
            for(int i=0;i<n;i++) {
                int size=count(in,1024);byte[] id=new byte[size];in.readFully(id);
                String name=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(id)).toString();
                int nc=count(in,MAX);columns+=nc;if(columns>MAX)throw new IOException("Too many beam columns");
                List<ScoreBeamSource.Column> cs=new ArrayList<>();for(int j=0;j<nc;j++)cs.add(column(in));
                int nb=count(in,MAX);if(nb!=nc-1)throw new IOException("Wrong beam bridge count");
                List<Integer> bridges=new ArrayList<>();for(int j=0;j<nb;j++)bridges.add(in.readInt());
                int nh=count(in,nc*8);List<ScoreBeamSource.Hook> hooks=new ArrayList<>();
                for(int j=0;j<nh;j++){int c=in.readInt(),level=in.readInt(),d=in.readInt();if(d<0 || d>1)throw new IOException("Invalid beam hook direction");hooks.add(new ScoreBeamSource.Hook(c,level,ScoreBeamSource.Direction.values()[d]));}
                groups.add(new ScoreBeamSource.Group(name,cs,bridges,hooks));
            }
            int nd=count(in,MAX);if(columns+nd>MAX)throw new IOException("Too many beam columns");List<ScoreBeamSource.Column> detached=new ArrayList<>();for(int i=0;i<nd;i++)detached.add(column(in));
            int no=count(in,MAX);if(no!=notes)throw new IOException("Beam note ownership mismatch");List<Optional<ScoreBeamSource.Anchor>> origins=new ArrayList<>();
            for(int i=0;i<no;i++){int b=in.readUnsignedByte();if(b>1)throw new IOException("Invalid beam presence byte");origins.add(b==0?Optional.empty():Optional.of(anchor(in)));}
            if(in.available()!=0)throw new IOException("Trailing beam bytes");return new ScoreBeamSource(groups,detached,origins);
        }catch(IllegalArgumentException e){throw new IOException("Invalid beam source",e);}
    }
}
