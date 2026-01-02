/*
 ** WavWriter.java
 **
 ** Copyright (c) 2011 Peter McQuillan
 **
 ** All Rights Reserved.
 **
 ** Distributed under the BSD Software License (see license.txt)
 **
 */
package com.github.curiousoddman.alacdecoder;

import java.io.IOException;
import java.io.OutputStream;

public class WavWriter {
    public static void writeHeaders(OutputStream os,
                                    int dataSize,
                                    int numChannels,
                                    int sampleRate,
                                    int bytesPerSample,
                                    int bitsPerSample) throws IOException {
        /* write RIFF header */
        os.write(82);
        os.write(73);
        os.write(70);
        os.write(70);    // "RIFF" ascii values

        writeUint32(os, (36 + dataSize));
        os.write(87);
        os.write(65);
        os.write(86);
        os.write(69);   // "WAVE" ascii values

        /* write fmt header */
        os.write(102);
        os.write(109);
        os.write(116);
        os.write(32);  // "fmt " ascii values

        writeUint32(os, 16);
        writeUint16(os, 1); // PCM data
        writeUint16(os, numChannels);
        writeUint32(os, sampleRate);
        writeUint32(os, (sampleRate * numChannels * bytesPerSample)); // byterate
        writeUint16(os, (numChannels * bytesPerSample));
        writeUint16(os, bitsPerSample);

        /* write data header */
        os.write(100);
        os.write(97);
        os.write(116);
        os.write(97);  // "data" ascii values

        writeUint32(os, dataSize);
    }

    static void writeUint32(OutputStream f, int v) throws IOException {
        f.write((byte) (v));
        f.write((byte) (v >>> 8));
        f.write((byte) (v >>> 16));
        f.write((byte) (v >>> 24));
    }

    static void writeUint16(OutputStream f, int v) throws IOException {
        f.write((byte) (v));
        f.write((byte) (v >>> 8));
    }
}

