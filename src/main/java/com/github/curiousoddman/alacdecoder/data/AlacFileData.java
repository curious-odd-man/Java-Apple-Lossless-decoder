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

import static com.github.curiousoddman.alacdecoder.AlacDecodeUtils.countLeadingZeros;

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
    private int inputBufferBitAccumulator = 0; /* used so we can do arbitary
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
    int readbits16(int bits) {
        int part1 = inputBuffer[ibIdx] & 0xff;
        int part2 = inputBuffer[ibIdx + 1] & 0xff;
        int part3 = inputBuffer[ibIdx + 2] & 0xff;

        int result = part1 << 16 | part2 << 8 | part3;

        /* shift left by the number of bits we've already read,
         * so that the top 'n' bits of the 24 bits we read will
         * be the return bits */
        result = result << inputBufferBitAccumulator;

        result = result & 0x00ffffff;

        /* and then only want the top 'n' bits from that, where
         * n is 'bits' */
        result = result >> 24 - bits;

        int newAccumulator = inputBufferBitAccumulator + bits;

        /* increase the buffer pointer if we've read over n bytes. */
        ibIdx += newAccumulator >> 3;

        /* and the remainder goes back into the bit accumulator */
        inputBufferBitAccumulator = newAccumulator & 7;

        return result;
    }

    /* supports reading 1 to 32 bits, in big endian format */
    public int readbits(int bits) {
        int result = 0;

        if (bits > 16) {
            bits -= 16;
            result = readbits16(16) << bits;
        }

        result |= readbits16(bits);

        return result;
    }

    /* reads a single bit */
    public int readbit() {
        int result = getInputBuffer()[getIbIdx()] & 0xff;
        result = result << inputBufferBitAccumulator;
        result = result >> 7 & 1;

        int newAccumulator = inputBufferBitAccumulator + 1;
        ibIdx += newAccumulator / 8;
        inputBufferBitAccumulator = newAccumulator % 8;

        return result;
    }

    public void unreadbits() {
        int newAccumulator = inputBufferBitAccumulator - 1;
        ibIdx += newAccumulator >> 3;
        inputBufferBitAccumulator = newAccumulator & 7;
    }

    public int entropyDecodeValue(int readSampleSize, int k, int riceKmodifierMask) {
        int decodedValue = 0;

        // read x, number of 1s before 0 represent the rice value.
        while (decodedValue <= Defines.RICE_THRESHOLD && readbit() != 0) {
            decodedValue++;
        }

        if (decodedValue > Defines.RICE_THRESHOLD) {
            // read the number from the bit stream (raw value)
            int value = readbits(readSampleSize);
            // mask value
            value &= 0xffffffff >> 32 - readSampleSize;
            decodedValue = value;
        } else {
            if (k != 1) {
                int extraBits = readbits(k);

                decodedValue *= (1 << k) - 1 & riceKmodifierMask;

                if (extraBits > 1) {
                    decodedValue += extraBits - 1;
                } else {
                    unreadbits();
                }
            }
        }

        return decodedValue;
    }

    public void entropyRiceDecode(int[] outputBuffer, int outputSize, int readSampleSize, int riceHistorymult) {
        int riceKmodifierInt = riceKmodifier;
        int riceKmodifierMask = (1 << riceKmodifier) - 1;
        int history = riceInitialhistory;
        int outputCount = 0;
        int signModifier = 0;

        while (outputCount < outputSize) {
            int k = 31 - riceKmodifierInt - countLeadingZeros((history >> 9) + 3, getLz());

            if (k < 0) {
                k += riceKmodifierInt;
            } else {
                k = riceKmodifierInt;
            }

            // note: don't use riceKmodifierMask here (set mask to 0xFFFFFFFF)
            int decodedValue = entropyDecodeValue(readSampleSize, k, 0xFFFFFFFF);
            decodedValue += signModifier;
            int finalValue = (decodedValue + 1) / 2; // inc by 1 and shift out sign bit
            if ((decodedValue & 1) != 0) {// the sign is stored in the low bit
                finalValue *= -1;
            }

            outputBuffer[outputCount] = finalValue;
            signModifier = 0;

            // update history
            history += decodedValue * riceHistorymult - (history * riceHistorymult >> 9);

            if (decodedValue > 0xFFFF) {
                history = 0xFFFF;
            }

            // special case, for compressed blocks of 0
            if (history < 128 && outputCount + 1 < outputSize) {
                signModifier = 1;

                k = countLeadingZeros(history, getLz()) + (history + 16) / 64 - 24;

                // note: blockSize is always 16bit
                int blockSize = entropyDecodeValue(16, k, riceKmodifierMask);
                // got blockSize 0s
                if (blockSize > 0) {
                    for (int j = 0; j < blockSize; j++) {
                        outputBuffer[outputCount + 1 + j] = 0;
                    }
                    outputCount += blockSize;
                }

                if (blockSize > 0xFFFF) {
                    signModifier = 0;
                }
                history = 0;
            }

            outputCount++;
        }
    }
}