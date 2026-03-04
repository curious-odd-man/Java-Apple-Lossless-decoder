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
}
