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
package com.github.curiousoddman.alacdecoder;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DemuxResT {
    private final List<SampleInfo> timeToSample = new ArrayList<>();

    private int formatRead;

    private int numChannels;
    private int sampleSize;
    private int sampleRate;
    private int format;

    private int numTimeToSamples;

    private int[] sampleByteSize;

    private int codecDataLen;

    private int[] codecData = new int[1024];

    private int[] stco;
    private ChunkInfo[] stsc;

    private int mdatLen;

    public SampleDuration getSampleInfo(int sampleIndex) {
        if (sampleIndex >= getSampleByteSize().length) {
            throw new IllegalStateException("sample " + sampleIndex + " does not exist ");
        }

        if (getNumTimeToSamples() == 0) {
            throw new IllegalStateException("no time to samples");
        }

        int durationCurIndex = 0;
        int durationIndexAccum = 0;
        while ((getTimeToSample().get(durationCurIndex).getSampleCount() + durationIndexAccum) <= sampleIndex) {
            durationIndexAccum += getTimeToSample().get(durationCurIndex).getSampleCount();
            durationCurIndex++;
            if (durationCurIndex >= getNumTimeToSamples()) {
                throw new IllegalStateException("sample " + sampleIndex + " does not have a duration");
            }
        }

        return new SampleDuration(
                getTimeToSample().get(durationCurIndex).getSampleDuration(),
                getSampleByteSize()[sampleIndex]
        );
    }
}
