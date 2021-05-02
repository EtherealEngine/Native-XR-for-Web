/*  
 * Copyright 2008 CoreMedia AG, Hamburg
 *
 * Licensed under the Apache License, Version 2.0 (the License); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an AS IS BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License. 
 */

package com.xrengine.xr.videocompressor.isoparser;

import com.xrengine.xr.videocompressor.isoparser.boxes.iso14496.part12.MovieBox;
import com.xrengine.xr.videocompressor.isoparser.support.DoNotParseDetail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * The most upper container for ISO Boxes. It is a container box that is a file.
 * Uses IsoBufferWrapper  to access the underlying file.
 */
@DoNotParseDetail
public class IsoFile extends BasicContainer implements Closeable {
    public static volatile long MAX_RECORD_SIZE_OVERRIDE = -1;
    private static Logger LOG = LoggerFactory.getLogger(IsoFile.class);
    private ReadableByteChannel readableByteChannel;


    public IsoFile(String file) throws IOException {
        this(new FileInputStream(file).getChannel(), new PropertyBoxParserImpl());
    }

    public IsoFile(File file) throws IOException {
        this(new FileInputStream(file).getChannel(), new PropertyBoxParserImpl());
    }

    /**
     * @param readableByteChannel the data source
     * @throws IOException in case I/O error
     */
    public IsoFile(ReadableByteChannel readableByteChannel) throws IOException {
        this(readableByteChannel, new PropertyBoxParserImpl());
    }

    public IsoFile(ReadableByteChannel readableByteChannel, BoxParser boxParser) throws IOException {
        this.readableByteChannel = readableByteChannel;
        initContainer(readableByteChannel, -1, boxParser);
    }

    public static byte[] fourCCtoBytes(String fourCC) {
        byte[] result = new byte[4];
        if (fourCC != null) {
            for (int i = 0; i < Math.min(4, fourCC.length()); i++) {
                result[i] = (byte) fourCC.charAt(i);
            }
        }
        return result;
    }

    public static String bytesToFourCC(byte[] type) {
        byte[] result = new byte[]{0, 0, 0, 0};
        if (type != null) {
            System.arraycopy(type, 0, result, 0, Math.min(type.length, 4));
        }
        try {
            return new String(result, "ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            throw new Error("Required character encoding is missing", e);
        }
    }


    public long getSize() {
        return getContainerSize();
    }


    /**
     * Shortcut to get the MovieBox since it is often needed and present in
     * nearly all ISO 14496 files (at least if they are derived from MP4 ).
     *
     * @return the MovieBox or <code>null</code>
     */
    public MovieBox getMovieBox() {
        for (Box box : getBoxes()) {
            if (box instanceof MovieBox) {
                return (MovieBox) box;
            }
        }
        return null;
    }

    public void getBox(WritableByteChannel os) throws IOException {
        writeContainer(os);
    }

    public void close() throws IOException {
        for (Box box : getBoxes()) {
            if (box instanceof Closeable) {
                ((Closeable) box).close();
            }
        }
        this.readableByteChannel.close();

    }

    @Override
    public String toString() {
        return "model(" + readableByteChannel.toString() + ")";
    }
}
