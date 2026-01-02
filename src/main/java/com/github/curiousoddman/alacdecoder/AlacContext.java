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

package com.github.curiousoddman.alacdecoder;

import com.github.curiousoddman.alacdecoder.data.AlacFileData;
import com.github.curiousoddman.alacdecoder.data.SampleDuration;
import com.github.curiousoddman.alacdecoder.stream.AlacInputStream;
import lombok.Data;

import java.io.IOException;

@Data
public class AlacContext implements AutoCloseable {
    private DemuxRes demuxRes = new DemuxRes();
    private AlacFileData alacFileData = new AlacFileData();
    private AlacInputStream alacInputStream;
    private int currentSampleBlock = 0;
    private int offset;
    private byte[] readBuffer = new byte[1024 * 80]; // sample big enough to hold any input for a single alac frame

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
            return (int) Math.ceil(demuxRes.getSampleSize() / 8);
        } else {
            return 2;
        }
    }

    @Override
    public void close() {
        if (alacInputStream != null) {
            try {
                alacInputStream.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}