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

import java.io.FileOutputStream;
import java.io.IOException;

public class WavWriter {
    public static void writeHeaders(FileOutputStream fos,
                                    int dataSize,
                                    int numChannels,
                                    int sampleRate,
                                    int bytesPerSample,
                                    int bitsPerSample) throws IOException {
        /* write RIFF header */
        fos.write(82);
        fos.write(73);
        fos.write(70);
        fos.write(70);    // "RIFF" ascii values

        writeUint32(fos, (36 + dataSize));
        fos.write(87);
        fos.write(65);
        fos.write(86);
        fos.write(69);   // "WAVE" ascii values

        /* write fmt header */
        fos.write(102);
        fos.write(109);
        fos.write(116);
        fos.write(32);  // "fmt " ascii values

        writeUint32(fos, 16);
        writeUint16(fos, 1); // PCM data
        writeUint16(fos, numChannels);
        writeUint32(fos, sampleRate);
        writeUint32(fos, (sampleRate * numChannels * bytesPerSample)); // byterate
        writeUint16(fos, (numChannels * bytesPerSample));
        writeUint16(fos, bitsPerSample);

        /* write data header */
        fos.write(100);
        fos.write(97);
        fos.write(116);
        fos.write(97);  // "data" ascii values

        writeUint32(fos, dataSize);
    }

    private static void writeUint32(FileOutputStream f, int v) throws IOException {
        f.write((byte) (v));
        f.write((byte) (v >>> 8));
        f.write((byte) (v >>> 16));
        f.write((byte) (v >>> 24));
    }

    private static void writeUint16(FileOutputStream f, int v) throws IOException {
        f.write((byte) (v));
        f.write((byte) (v >>> 8));
    }
}

