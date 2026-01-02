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
import lombok.extern.slf4j.Slf4j;

import static com.github.curiousoddman.alacdecoder.AlacDecodeUtils.*;

@Data
@Slf4j
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
    private int riceHistoryMult = 0; // 0x28
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
        riceHistoryMult = inputBuffer[ptrIndex] & 0xff;
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

    public int decodeFrame(byte[] inBuffer, int[] outBuffer) {
        int outputSamples = maxSamplesPerFrame;

        /* setup the stream */
        inputBuffer = inBuffer;
        inputBufferBitAccumulator = 0;
        ibIdx = 0;

        int channels = readbits(3);
        int outputSize = outputSamples * bytesPerSample;

        if (channels == 0) {// 1 channel
            /* 2^result = something to do with output waiting.
             * perhaps matters if we read > 1 frame in a pass?
             */
            readbits(4);
            readbits(12); // unknown, skip 12 bits

            int hasSize = readbits(1); // the output sample size is stored soon
            int uncompressedBytes = readbits(2); // number of bytes in the (compressed) stream that are not compressed
            int isNotCompressed = readbits(1); // whether the frame is compressed

            if (hasSize != 0) {
                /* now read the number of samples,
                 * as a 32bit integer */
                outputSamples = readbits(32);
                outputSize = outputSamples * bytesPerSample;
            }

            int readSampleSize = sampleSizeRaw - uncompressedBytes * 8;

            if (isNotCompressed == 0) { // so it is compressed
                /* skip 16 bits, not sure what they are. seem to be used in
                 * two channel case */
                readbits(8);
                readbits(8);

                int predictionType = readbits(4);
                int predictionQuantization = readbits(4);

                int riceModifier = readbits(3);
                int predictorCoefNum = readbits(5);

                /* read the predictor table */

                int i;
                for (i = 0; i < predictorCoefNum; i++) {
                    int tempPred = readbits(16);
                    if (tempPred > 32767) {
                        // the predictor coef table values are only 16 bit signed
                        tempPred = tempPred - 65536;
                    }

                    predictorCoefTable[i] = tempPred;
                }

                if (uncompressedBytes != 0) {
                    for (i = 0; i < outputSamples; i++) {
                        uncompressedBytesBufferA[i] = readbits(uncompressedBytes * 8);
                    }
                }

                entropyRiceDecode(predictErrorBufferA, outputSamples, readSampleSize, riceModifier * (riceHistoryMult / 4));

                if (predictionType == 0) { // adaptive fir
                    setOutputSamplesBufferA(predictorDecompressFirAdapt(predictErrorBufferA, outputSamples, readSampleSize, predictorCoefTable, predictorCoefNum, predictionQuantization));
                } else {
                    log.error("FIXME: unhandled predicition type: {}", predictionType);

                    /* i think the only other prediction type (or perhaps this is just a
                     * boolean?) runs adaptive fir twice.. like:
                     * predictor_decompress_fir_adapt(predictor_error, tempout, ...)
                     * predictor_decompress_fir_adapt(predictor_error, outputSamples ...)
                     * little strange..
                     */
                }

            } else { // not compressed, easy case
                if (sampleSizeRaw <= 16) {
                    for (int i = 0; i < outputSamples; i++) {
                        int audiobits = readbits(sampleSizeRaw);
                        int bitsmove = 32 - sampleSizeRaw;

                        audiobits = audiobits << bitsmove >> bitsmove;

                        outputSamplesBufferA[i] = audiobits;
                    }
                } else {
                    int m = 1 << 24 - 1;
                    for (int i = 0; i < outputSamples; i++) {

                        int audiobits = getAudioBits();
                        int x = audiobits & (1 << 24) - 1;
                        audiobits = (x ^ m) - m;    // sign extend 24 bits

                        outputSamplesBufferA[i] = audiobits;
                    }
                }
                uncompressedBytes = 0; // always 0 for uncompressed
            }

            switch (sampleSizeRaw) {
                case 16: {

                    for (int i = 0; i < outputSamples; i++) {
                        int sample = outputSamplesBufferA[i];
                        outBuffer[i * numChannels] = sample;

                        /*
                         ** We have to handle the case where the data is actually mono, but the stsd atom says it has 2 channels
                         ** in this case we create a stereo file where one of the channels is silent. If mono and 1 channel this value
                         ** will be overwritten in the next iteration
                         */

                        outBuffer[i * numChannels + 1] = 0;
                    }
                    break;
                }
                case 24: {
                    for (int i = 0; i < outputSamples; i++) {
                        int sample = getOutputSamplesBufferA()[i];

                        if (uncompressedBytes != 0) {
                            sample = sample << uncompressedBytes * 8;
                            int mask = ~(0xFFFFFFFF << uncompressedBytes * 8);
                            sample = sample | getUncompressedBytesBufferA()[i] & mask;
                        }

                        outBuffer[i * numChannels * 3] = sample & 0xFF;
                        outBuffer[i * numChannels * 3 + 1] = sample >> 8 & 0xFF;
                        outBuffer[i * numChannels * 3 + 2] = sample >> 16 & 0xFF;

                        /*
                         ** We have to handle the case where the data is actually mono, but the stsd atom says it has 2 channels
                         ** in this case we create a stereo file where one of the channels is silent. If mono and 1 channel this value
                         ** will be overwritten in the next iteration
                         */

                        outBuffer[i * numChannels * 3 + 3] = 0;
                        outBuffer[i * numChannels * 3 + 4] = 0;
                        outBuffer[i * numChannels * 3 + 5] = 0;

                    }
                    break;
                }
                case 20:
                case 32:
                    log.error("FIXME: unimplemented sample size {}", getSampleSizeRaw());
                default:

            }
        } else if (channels == 1) { // 2 channels
            /* 2^result = something to do with output waiting.
             * perhaps matters if we read > 1 frame in a pass?
             */
            readbits(4);
            readbits(12); // unknown, skip 12 bits

            int hasSize = readbits(1); // the output sample size is stored soon
            int uncompressedBytes = readbits(2); // the number of bytes in the (compressed) stream that are not compressed
            int isNotCompressed = readbits(1); // whether the frame is compressed

            if (hasSize != 0) {
                /* now read the number of samples,
                 * as a 32bit integer */
                outputSamples = readbits(32);
                outputSize = outputSamples * bytesPerSample;
            }

            int readSampleSize = sampleSizeRaw - uncompressedBytes * 8 + 1;

            int interlacingLeftWeight;
            int interlacingShift;
            if (isNotCompressed == 0) { // compressed
                interlacingShift = readbits(8);
                interlacingLeftWeight = readbits(8);

                /* ******* channel 1 ***********/
                int predictionTypeA = readbits(4);
                int predictionQuantitizationA = readbits(4);

                int ricemodifierA = readbits(3);
                int predictorCoefNumA = readbits(5);

                /* read the predictor table */

                int tempPred;
                for (int i = 0; i < predictorCoefNumA; i++) {
                    tempPred = readbits(16);
                    if (tempPred > 32767) {
                        // the predictor coef table values are only 16 bit signed
                        tempPred = tempPred - 65536;
                    }
                    predictorCoefTableA[i] = tempPred;
                }

                /* ******* channel 2 *********/
                int predictionTypeB = readbits(4);
                int predictionQuantitizationB = readbits(4);

                int ricemodifierB = readbits(3);
                int predictorCoefNumB = readbits(5);

                /* read the predictor table */

                for (int i = 0; i < predictorCoefNumB; i++) {
                    tempPred = readbits(16);
                    if (tempPred > 32767) {
                        // the predictor coef table values are only 16 bit signed
                        tempPred = tempPred - 65536;
                    }
                    predictorCoefTableB[i] = tempPred;
                }

                /* ********************/
                if (uncompressedBytes != 0) { // see mono case
                    for (int i = 0; i < outputSamples; i++) {
                        uncompressedBytesBufferA[i] = readbits(uncompressedBytes * 8);
                        uncompressedBytesBufferB[i] = readbits(uncompressedBytes * 8);
                    }
                }

                /* channel 1 */

                entropyRiceDecode(predictErrorBufferA, outputSamples, readSampleSize, ricemodifierA * (riceHistoryMult / 4));

                if (predictionTypeA == 0) { // adaptive fir
                    setOutputSamplesBufferA(predictorDecompressFirAdapt(predictErrorBufferA, outputSamples, readSampleSize, predictorCoefTableA, predictorCoefNumA, predictionQuantitizationA));
                } else { // see mono case
                    log.error("FIXME: unhandled predicition type: {}", predictionTypeA);
                }

                /* channel 2 */
                entropyRiceDecode(predictErrorBufferB, outputSamples, readSampleSize, ricemodifierB * (riceHistoryMult / 4));
                if (predictionTypeB == 0) { // adaptive fir
                    setOutputSamplesBufferB(predictorDecompressFirAdapt(predictErrorBufferB, outputSamples, readSampleSize, predictorCoefTableB, predictorCoefNumB, predictionQuantitizationB));
                } else {
                    log.error("FIXME: unhandled predicition type: {}", predictionTypeB);
                }
            } else { // not compressed, easy case
                if (sampleSizeRaw <= 16) {

                    for (int i = 0; i < outputSamples; i++) {

                        int audiobits_a = readbits(sampleSizeRaw);
                        int audiobits_b = readbits(sampleSizeRaw);

                        int bitsmove = 32 - sampleSizeRaw;

                        audiobits_a = audiobits_a << bitsmove >> bitsmove;
                        audiobits_b = audiobits_b << bitsmove >> bitsmove;

                        outputSamplesBufferA[i] = audiobits_a;
                        outputSamplesBufferB[i] = audiobits_b;
                    }
                } else {
                    int m = 1 << 24 - 1;

                    for (int i = 0; i < outputSamples; i++) {

                        int audiobits_a = getAudioBits();
                        int x = audiobits_a & (1 << 24) - 1;
                        audiobits_a = (x ^ m) - m;        // sign extend 24 bits

                        int audiobits_b = getAudioBits();
                        x = audiobits_b & (1 << 24) - 1;
                        audiobits_b = (x ^ m) - m;        // sign extend 24 bits

                        outputSamplesBufferA[i] = audiobits_a;
                        outputSamplesBufferB[i] = audiobits_b;
                    }
                }
                uncompressedBytes = 0; // always 0 for uncompressed
                interlacingShift = 0;
                interlacingLeftWeight = 0;
            }

            switch (getSampleSizeRaw()) {
                case 16: {
                    deinterlace16(outputSamplesBufferA, outputSamplesBufferB, outBuffer, numChannels, outputSamples, interlacingShift, interlacingLeftWeight);
                    break;
                }
                case 24: {
                    deinterlace24(outputSamplesBufferA, outputSamplesBufferB, uncompressedBytes, uncompressedBytesBufferA, uncompressedBytesBufferA, outBuffer, numChannels, outputSamples, interlacingShift, interlacingLeftWeight);
                    break;
                }
                case 20:
                case 32:
                    log.error("FIXME: unimplemented sample size {}", sampleSizeRaw);
                default:
            }
        }
        return outputSize;
    }

    private int getAudioBits() {
        int audioBits = readbits(16);
        /* special case of sign extension..
         * as we'll be ORing the low 16bits into this */
        audioBits = audioBits << sampleSizeRaw - 16;
        audioBits = audioBits | readbits(sampleSizeRaw - 16);
        return audioBits;
    }
}