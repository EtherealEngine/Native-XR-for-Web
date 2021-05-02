package com.xr3ngine.xr.videocompressor.isoparser.boxes.dece;

import com.xr3ngine.xr.videocompressor.isoparser.support.AbstractFullBox;
import com.xr3ngine.xr.videocompressor.isoparser.tools.IsoTypeReader;
import com.xr3ngine.xr.videocompressor.isoparser.tools.IsoTypeWriter;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * <h1>4cc = "{@value #TYPE}"</h1>
 * <pre>
 * aligned(8) class TrickPlayBox extends FullBox(‘trik’, version=0, flags=0)
 * {
 *  for (i=0; I &lt; sample_count; i++) {
 *   unsigned int(2) pic_type;
 *   unsigned int(6) dependency_level;
 *  }
 * }
 * </pre>
 */
public class TrickPlayBox extends AbstractFullBox {
    public static final String TYPE = "trik";

    private List<Entry> entries = new ArrayList<Entry>();

    public TrickPlayBox() {
        super(TYPE);
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public void setEntries(List<Entry> entries) {
        this.entries = entries;
    }

    @Override
    protected long getContentSize() {
        return 4 + entries.size();
    }

    @Override
    public void _parseDetails(ByteBuffer content) {
        parseVersionAndFlags(content);
        while (content.remaining() > 0) {
            entries.add(new Entry(IsoTypeReader.readUInt8(content)));
        }
    }

    @Override
    protected void getContent(ByteBuffer byteBuffer) {
        writeVersionAndFlags(byteBuffer);
        for (Entry entry : entries) {
            IsoTypeWriter.writeUInt8(byteBuffer, entry.value);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("TrickPlayBox");
        sb.append("{entries=").append(entries);
        sb.append('}');
        return sb.toString();
    }

    public static class Entry {

        private int value;

        public Entry() {
        }


        public Entry(int value) {
            this.value = value;
        }

        public int getPicType() {
            return (value >> 6) & 0x03;
        }

        public void setPicType(int picType) {
            value = value & (0xff >> 3);
            value = (picType & 0x03) << 6 | value;
        }

        public int getDependencyLevel() {
            return value & 0x3f;
        }

        public void setDependencyLevel(int dependencyLevel) {
            value = (dependencyLevel & 0x3f) | value;
        }


        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("Entry");
            sb.append("{picType=").append(getPicType());
            sb.append(",dependencyLevel=").append(getDependencyLevel());
            sb.append('}');
            return sb.toString();
        }
    }
}
