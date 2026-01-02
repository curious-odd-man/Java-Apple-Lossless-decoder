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
    private int ibIdx = 0;
    private int inputBufferBitaccumulator = 0; /* used so we can do arbitary
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

    /* supports reading 1 to 16 bits, in big endian format */
    int readbits_16(int bits) {
        byte[] inputBuffer = getInputBuffer();
        int ibIdx = getIbIdx();
        int part1 = inputBuffer[ibIdx] & 0xff;
        int part2 = inputBuffer[ibIdx + 1] & 0xff;
        int part3 = inputBuffer[ibIdx + 2] & 0xff;

        int result = part1 << 16 | part2 << 8 | part3;

        /* shift left by the number of bits we've already read,
         * so that the top 'n' bits of the 24 bits we read will
         * be the return bits */
        result = result << getInputBufferBitaccumulator();

        result = result & 0x00ffffff;

        /* and then only want the top 'n' bits from that, where
         * n is 'bits' */
        result = result >> 24 - bits;

        int new_accumulator = getInputBufferBitaccumulator() + bits;

        /* increase the buffer pointer if we've read over n bytes. */
        setIbIdx(getIbIdx() + (new_accumulator >> 3));

        /* and the remainder goes back into the bit accumulator */
        setInputBufferBitaccumulator(new_accumulator & 7);

        return result;
    }

    /* supports reading 1 to 32 bits, in big endian format */
    public int readbits(int bits) {
        int result = 0;

        if (bits > 16) {
            bits -= 16;

            result = readbits_16(16) << bits;
        }

        result |= readbits_16(bits);

        return result;
    }

    /* reads a single bit */
    public int readbit() {

        int result = getInputBuffer()[getIbIdx()] & 0xff;

        result = result << getInputBufferBitaccumulator();

        result = result >> 7 & 1;

        int new_accumulator = getInputBufferBitaccumulator() + 1;

        setIbIdx(getIbIdx() + new_accumulator / 8);

        setInputBufferBitaccumulator(new_accumulator % 8);

        return result;
    }

    public void unreadbits() {
        int new_accumulator = getInputBufferBitaccumulator() - 1;

        setIbIdx(getIbIdx() + (new_accumulator >> 3));

        setInputBufferBitaccumulator(new_accumulator & 7);
    }
}