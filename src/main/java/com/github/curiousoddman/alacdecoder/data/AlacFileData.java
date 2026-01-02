/*
 ** AlacFile.java
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

@Data
public class AlacFileData {
    private static final int BUFFER_SIZE = 16384;

    private final int sampleSize;
    private final int numChannels;
    private final int bytesPerSample;

    private final int[] predictorCoefTable = new int[1024];
    private final int[] predictorCoefTableA = new int[1024];
    private final int[] predictorCoefTableB = new int[1024];

    private final int[] predicterrorBufferA = new int[BUFFER_SIZE];
    private final int[] predicterrorBufferB = new int[BUFFER_SIZE];
    private final int[] uncompressedBytesBufferA = new int[BUFFER_SIZE];
    private final int[] uncompressedBytesBufferB = new int[BUFFER_SIZE];

    public byte[] inputBuffer;
    public int ibIdx = 0;
    public int inputBufferBitaccumulator = 0; /* used so we can do arbitary
						bit reads */
    public final LeadingZeros lz = new LeadingZeros();
    /* stuff from setinfo */
    public int setInfoMaxSamplesPerFrame = 0; // 0x1000 = 4096
    public int setInfo7A = 0; // 0x00
    public int setInfoSampleSize = 0; // 0x10
    public int setInfoRiceHistorymult = 0; // 0x28
    public int setInfoRiceInitialhistory = 0; // 0x0a
    /* max samples per frame? */
    public int setInfoRiceKmodifier = 0; // 0x0e
    public int setInfo7F = 0; // 0x02
    public int setInfo80 = 0; // 0x00ff
    public int setInfo82 = 0; // 0x000020e7
    /* max sample size?? */
    public int setInfo86 = 0; // 0x00069fe4
    /* bit rate (avarge)?? */
    public int setInfo8ARate = 0; // 0x0000ac44

    public int[] outputSamplesBufferA = new int[BUFFER_SIZE];
    /* end setinfo stuff */
    public int[] outputSamplesBufferB = new int[BUFFER_SIZE];


    public AlacFileData(int sampleSize, int numChannels) {
        this.sampleSize = sampleSize;
        this.numChannels = numChannels;
        this.bytesPerSample = sampleSize / 8 * numChannels;
    }
}