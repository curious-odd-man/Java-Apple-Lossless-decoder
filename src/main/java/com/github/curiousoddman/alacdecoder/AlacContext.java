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
import com.github.curiousoddman.alacdecoder.data.ChunkInfo;
import com.github.curiousoddman.alacdecoder.data.SampleDuration;
import com.github.curiousoddman.alacdecoder.stream.AlacInputStream;
import com.github.curiousoddman.alacdecoder.stream.DataInputStreamWrapper;
import lombok.Data;

import java.io.IOException;

@Data
public class AlacContext implements AutoCloseable {
    private DemuxRes demuxRes = new DemuxRes();
    private AlacFileData alacFileData = new AlacFileData(0, 0);
    private AlacInputStream alacInputStream;
    private int currentSampleBlock = 0;
    private int offset;
    private byte[] readBuffer = new byte[1024 * 80]; // sample big enough to hold any input for a single alac frame

    public int unpackSamples(int[] destBuffer) throws IOException {
        byte[] readBuffer = getReadBuffer();
        DataInputStreamWrapper inputStream = new DataInputStreamWrapper(getAlacInputStream());

        // if current_sample_block is beyond last block then finished

        if (getCurrentSampleBlock() >= getDemuxRes().getSampleByteSize().length) {
            return 0;
        }

        SampleDuration sampleInfo = getDemuxRes().getSampleInfo(getCurrentSampleBlock());
        int sampleByteSize = sampleInfo.getSampleByteSize();

        inputStream.read(sampleByteSize, readBuffer, 0);

        /* now fetch */

        int outputBytes = alacFileData.decodeFrame(readBuffer, destBuffer);

        setCurrentSampleBlock(getCurrentSampleBlock() + 1);
        outputBytes -= getOffset() * getBytesPerSample();
        System.arraycopy(destBuffer, getOffset(), destBuffer, 0, outputBytes);
        setOffset(0);
        return outputBytes;
    }

    /**
     * sets position in pcm samples
     *
     * @param position position in pcm samples to go to
     */

    public void setPosition(long position) throws IOException {
        DemuxRes res = getDemuxRes();

        int currentPosition = 0;
        int currentSample = 0;
        for (int i = 0; i < res.getStsc().length; i++) {
            ChunkInfo chunkInfo = res.getStsc()[i];
            int lastChunk;

            if (i < res.getStsc().length - 1) {
                lastChunk = res.getStsc()[i + 1].getFirstChunk();
            } else {
                lastChunk = res.getStco().length;
            }

            for (int chunk = chunkInfo.getFirstChunk(); chunk <= lastChunk; chunk++) {
                int pos = res.getStco()[chunk - 1];
                int samplesPerChunk = chunkInfo.getSamplesPerChunk();
                while (samplesPerChunk > 0) {
                    SampleDuration sampleDuration = res.getSampleInfo(currentSample);
                    currentPosition += sampleDuration.getSampleDuration();
                    if (position < currentPosition) {
                        getAlacInputStream().seek(pos);
                        setCurrentSampleBlock(currentSample);
                        setOffset((int) (position - (currentPosition - sampleDuration.getSampleDuration()))
                                * getNumChannels());
                        return;
                    }
                    pos += sampleDuration.getSampleByteSize();
                    currentSample++;
                    samplesPerChunk--;
                }
            }
        }
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
        if (alacInputStream != null) {
            try {
                alacInputStream.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}