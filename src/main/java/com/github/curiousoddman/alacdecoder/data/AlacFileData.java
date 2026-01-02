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

    private final int[] predictErrorBufferA = new int[BUFFER_SIZE];
    private final int[] predictErrorBufferB = new int[BUFFER_SIZE];
    private final int[] uncompressedBytesBufferA = new int[BUFFER_SIZE];
    private final int[] uncompressedBytesBufferB = new int[BUFFER_SIZE];

    private int maxSamplesPerFrame = 0; // 0x1000 = 4096
    private int sevenA = 0; // 0x00
    private int sampleSizeRaw = 0; // 0x10
    private int riceHistorymult = 0; // 0x28
    private int riceInitialhistory = 0; // 0x0a
    private int riceKmodifier = 0; // 0x0e
    private int sevenF = 0; // 0x02
    private int eight0 = 0; // 0x00ff
    private int eight2 = 0; // 0x000020e7
    private int eight6 = 0; // 0x00069fe4
    private int eigthARate = 0; // 0x0000ac44

    private byte[] inputBuffer;
    public int ibIdx = 0;
    public int inputBufferBitaccumulator = 0; /* used so we can do arbitary
						bit reads */
    private final LeadingZeros lz = new LeadingZeros();

    private int[] outputSamplesBufferA = new int[BUFFER_SIZE];
    private int[] outputSamplesBufferB = new int[BUFFER_SIZE];

    public AlacFileData(int sampleSize, int numChannels) {
        this.sampleSize = sampleSize;
        this.numChannels = numChannels;
        this.bytesPerSample = sampleSize / 8 * numChannels;
    }

    public void setInfo(int[] inputBuffer) {
        int ptrIndex = 0;
        ptrIndex += 4; // size
        ptrIndex += 4; // frma
        ptrIndex += 4; // alac
        ptrIndex += 4; // size
        ptrIndex += 4; // alac
        ptrIndex += 4; // 0 ?

        maxSamplesPerFrame = (inputBuffer[ptrIndex] << 24) + (inputBuffer[ptrIndex + 1] << 16) + (inputBuffer[ptrIndex + 2] << 8) + inputBuffer[ptrIndex + 3]; // buffer size / 2 ?
        ptrIndex += 4;
        sevenA = inputBuffer[ptrIndex];
        ptrIndex += 1;
        sampleSizeRaw = inputBuffer[ptrIndex];
        ptrIndex += 1;
        riceHistorymult = inputBuffer[ptrIndex] & 0xff;
        ptrIndex += 1;
        riceInitialhistory = inputBuffer[ptrIndex] & 0xff;
        ptrIndex += 1;
        riceKmodifier = inputBuffer[ptrIndex] & 0xff;
        ptrIndex += 1;
        sevenF = inputBuffer[ptrIndex];
        ptrIndex += 1;
        eight0 = (inputBuffer[ptrIndex] << 8) + inputBuffer[ptrIndex + 1];
        ptrIndex += 2;
        eight2 = (inputBuffer[ptrIndex] << 24) + (inputBuffer[ptrIndex + 1] << 16) + (inputBuffer[ptrIndex + 2] << 8) + inputBuffer[ptrIndex + 3];
        ptrIndex += 4;
        eight6 = (inputBuffer[ptrIndex] << 24) + (inputBuffer[ptrIndex + 1] << 16) + (inputBuffer[ptrIndex + 2] << 8) + inputBuffer[ptrIndex + 3];
        ptrIndex += 4;
        eigthARate = (inputBuffer[ptrIndex] << 24) + (inputBuffer[ptrIndex + 1] << 16) + (inputBuffer[ptrIndex + 2] << 8) + inputBuffer[ptrIndex + 3];
    }
}