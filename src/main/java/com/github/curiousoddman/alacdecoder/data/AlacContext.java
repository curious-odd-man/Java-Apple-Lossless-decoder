/*
 ** AlacContext.java
 **
 ** Copyright (c) 2011 Peter McQuillan
 **
 ** All Rights Reserved.
 **
 ** Distributed under the BSD Software License (see license.txt)
 **
 */

package com.github.curiousoddman.alacdecoder.data;

import com.github.curiousoddman.alacdecoder.stream.DataInputStreamWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.InputStream;

@Data
@RequiredArgsConstructor
public class AlacContext implements AutoCloseable {
    private DemuxRes demuxRes = new DemuxRes();
    private AlacFileData alacFileData = new AlacFileData(0, 0);
    private DataInputStreamWrapper inputStream;
    private int currentSampleBlock = 0;
    private byte[] readBuffer = new byte[1024 * 80]; // sample big enough to hold any input for a single alac frame

    private final long fileSize;

    public void setInputStream(InputStream inputStream) {
        this.inputStream = new DataInputStreamWrapper(inputStream, fileSize);
    }

    public int unpackSamples(int[] destBuffer) throws IOException {
        if (currentSampleBlock >= demuxRes.getSampleByteSize().length) {
            return 0;
        }

        SampleDuration sampleInfo = demuxRes.getSampleInfo(currentSampleBlock);
        int sampleByteSize = sampleInfo.getSampleByteSize();

        inputStream.read(sampleByteSize, readBuffer, 0);

        /* now fetch */

        int outputSizeBytes = alacFileData.decodeFrame(readBuffer, destBuffer);

        currentSampleBlock++;
        return outputSizeBytes;
    }

    public int getNumSamples() {
        int numSamples = 0;
        for (int i = 0; i < demuxRes.getSampleByteSize().length; i++) {
            SampleDuration sampleInfo = demuxRes.getSampleInfo(i);
            numSamples += sampleInfo.getSampleDuration();
        }

        return numSamples;
    }

    public int getNumChannels() {
        if (this.demuxRes.getNumChannels() != 0) {
            return demuxRes.getNumChannels();
        } else {
            return 2;
        }
    }

    public int getSampleRate() {
        if (this.demuxRes.getSampleRate() != 0) {
            return demuxRes.getSampleRate();
        } else {
            return 44100;
        }
    }

    public int getBitsPerSample() {
        if (this.demuxRes.getSampleSize() != 0) {
            return demuxRes.getSampleSize();
        } else {
            return 16;
        }
    }

    public int getBytesPerSample() {
        if (this.demuxRes.getSampleSize() != 0) {
            return (int) Math.ceil((double) demuxRes.getSampleSize() / 8);
        } else {
            return 2;
        }
    }

    @Override
    public void close() {
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}