/*
 ** DemuxResT.java
 **
 ** Copyright (c) 2011 Peter McQuillan
 **
 ** All Rights Reserved.
 **
 ** Distributed under the BSD Software License (see license.txt)
 **
 */
package com.github.curiousoddman.alacdecoder.data;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DemuxRes {
    private final List<SampleInfo> timeToSample = new ArrayList<>();

    private int numChannels;
    private int sampleSize;
    private int sampleRate;
    private int format;

    private int[] sampleByteSize;

    private int codecDataLen;

    private int[] codecData;

    private int[] stco;
    private ChunkInfo[] stsc;

    private int mdatLen;

    public SampleDuration getSampleInfo(int sampleIndex) {
        if (sampleIndex >= sampleByteSize.length) {
            throw new IllegalStateException("sample " + sampleIndex + " does not exist ");
        }

        if (timeToSample.isEmpty()) {
            throw new IllegalStateException("no time to samples");
        }

        int durationCurIndex = 0;
        int durationIndexAccum = 0;
        while (timeToSample.get(durationCurIndex).getSampleCount() + durationIndexAccum <= sampleIndex) {
            durationIndexAccum += timeToSample.get(durationCurIndex).getSampleCount();
            durationCurIndex++;
            if (durationCurIndex >= timeToSample.size()) {
                throw new IllegalStateException("sample " + sampleIndex + " does not have a duration");
            }
        }

        return new SampleDuration(
                timeToSample.get(durationCurIndex).getSampleDuration(),
                sampleByteSize[sampleIndex]
        );
    }

    public long getSampleFileOffset(int sampleIndex) {
        // stco = array of chunk file offsets
        // stsc = array of ChunkInfo(firstChunk, samplesPerChunk, sampleDescIndex)
        // Walk chunks to find which chunk contains sampleIndex

        int samplesAccum = 0;
        for (int chunkIdx = 0; chunkIdx < stco.length; chunkIdx++) {
            int samplesInChunk = getSamplesPerChunk(chunkIdx); // see below
            if (sampleIndex < samplesAccum + samplesInChunk) {
                // This chunk contains our sample
                // Walk sample sizes within chunk to get byte offset
                int sampleWithinChunk = sampleIndex - samplesAccum;
                long offset = stco[chunkIdx];
                for (int i = 0; i < sampleWithinChunk; i++) {
                    offset += sampleByteSize[samplesAccum + i];
                }
                return offset;
            }
            samplesAccum += samplesInChunk;
        }
        throw new IllegalStateException("Could not find file offset for sample " + sampleIndex);
    }

    private int getSamplesPerChunk(int chunkIdx) {
        // stsc entries: each entry applies from firstChunk-1 until next entry's firstChunk-1
        // stsc is sorted by firstChunk ascending
        int result = stsc[0].getSamplesPerChunk(); // default
        for (ChunkInfo ci : stsc) {
            if (ci.getFirstChunk() - 1 <= chunkIdx) { // stco is 0-based, stsc firstChunk is 1-based
                result = ci.getSamplesPerChunk();
            } else {
                break;
            }
        }
        return result;
    }
}
