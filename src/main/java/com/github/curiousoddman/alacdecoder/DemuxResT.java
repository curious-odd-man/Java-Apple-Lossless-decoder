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
}
