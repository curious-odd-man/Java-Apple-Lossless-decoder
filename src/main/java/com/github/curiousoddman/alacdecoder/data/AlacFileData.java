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

import com.github.curiousoddman.alacdecoder.utils.ByteArrayReader;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

import static com.github.curiousoddman.alacdecoder.utils.AlacDecodeUtils.*;

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
    private ByteArrayReader byteArrayReader;

    private int[] outputSamplesBufferA = new int[BUFFER_SIZE];
    private int[] outputSamplesBufferB = new int[BUFFER_SIZE];

    public AlacFileData(int sampleSize, int numChannels) {
        this.sampleSize = sampleSize;
        this.numChannels = numChannels;
        this.bytesPerSample = sampleSize / 8 * numChannels;
    }

    public void setInfo(int[] buffer) {
        int ptrIndex = 0;
        ptrIndex += 4; // size
        ptrIndex += 4; // frma
        ptrIndex += 4; // alac
        ptrIndex += 4; // size
        ptrIndex += 4; // alac
        ptrIndex += 4; // 0 ?

        maxSamplesPerFrame = (buffer[ptrIndex] << 24) + (buffer[ptrIndex + 1] << 16) + (buffer[ptrIndex + 2] << 8) + buffer[ptrIndex + 3]; // buffer size / 2 ?
        ptrIndex += 4;
        sevenA = buffer[ptrIndex];
        ptrIndex += 1;
        sampleSizeRaw = buffer[ptrIndex];
        ptrIndex += 1;
        riceHistoryMult = buffer[ptrIndex] & 0xff;
        ptrIndex += 1;
        riceInitialhistory = buffer[ptrIndex] & 0xff;
        ptrIndex += 1;
        riceKmodifier = buffer[ptrIndex] & 0xff;
        ptrIndex += 1;
        sevenF = buffer[ptrIndex];
        ptrIndex += 1;
        eight0 = (buffer[ptrIndex] << 8) + buffer[ptrIndex + 1];
        ptrIndex += 2;
        eight2 = (buffer[ptrIndex] << 24) + (buffer[ptrIndex + 1] << 16) + (buffer[ptrIndex + 2] << 8) + buffer[ptrIndex + 3];
        ptrIndex += 4;
        eight6 = (buffer[ptrIndex] << 24) + (buffer[ptrIndex + 1] << 16) + (buffer[ptrIndex + 2] << 8) + buffer[ptrIndex + 3];
        ptrIndex += 4;
        eigthARate = (buffer[ptrIndex] << 24) + (buffer[ptrIndex + 1] << 16) + (buffer[ptrIndex + 2] << 8) + buffer[ptrIndex + 3];
    }

    public int entropyDecodeValue(int readSampleSize, int k, int riceKmodifierMask) {
        int decodedValue = 0;

        // read x, number of 1s before 0 represent the rice value.
        while (decodedValue <= Defines.RICE_THRESHOLD && byteArrayReader.readBit() != 0) {
            decodedValue++;
        }

        if (decodedValue > Defines.RICE_THRESHOLD) {
            // read the number from the bit stream (raw value)
            int value = byteArrayReader.readBitsVar(readSampleSize);
            // mask value
            value &= 0xffffffff >> 32 - readSampleSize;
            decodedValue = value;
        } else {
            if (k != 1) {
                int extraBits = byteArrayReader.readBitsVar(k);

                decodedValue *= (1 << k) - 1 & riceKmodifierMask;

                if (extraBits > 1) {
                    decodedValue += extraBits - 1;
                } else {
                    byteArrayReader.unreadBits();
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
            int k = 31 - riceKmodifierInt - countLeadingZeros((history >> 9) + 3);

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

                k = countLeadingZeros(history) + (history + 16) / 64 - 24;

                // note: blockSize is always 16bit
                int blockSize = entropyDecodeValue(16, k, riceKmodifierMask);
                // got blockSize 0s
                if (blockSize > 0) {
                    int fillCount = Math.min(blockSize, outputSize - outputCount - 1);
                    Arrays.fill(outputBuffer, outputCount + 1, outputCount + 1 + fillCount, 0);
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

    private FrameHeader decodeFrameHeader(int outputSamples, int outputSizeBytes) {
        byteArrayReader.readBits24(4);
        byteArrayReader.readBits24(12); // unknown, skip 12 bits

        int hasSize = byteArrayReader.readBit(); // the output sample size is stored soon
        int uncompressedBytes = byteArrayReader.readBits24(2); // number of bytes in the (compressed) stream that are not compressed
        int isNotCompressed = byteArrayReader.readBit(); // whether the frame is compressed

        if (hasSize != 0) {
            /* now read the number of samples,
             * as a 32bit integer */
            outputSamples = byteArrayReader.readBits32(32);
            outputSizeBytes = outputSamples * bytesPerSample;
        }

        int readSampleSize = sampleSizeRaw - uncompressedBytes * 8;
        return new FrameHeader(
                hasSize,
                uncompressedBytes,
                isNotCompressed,
                outputSamples,
                outputSizeBytes,
                readSampleSize
        );
    }

    private record FrameHeader(int hasSize,
                               int uncompressedBytes,
                               int isNotCompressed,
                               int outputSamples,
                               int outputSizeBytes,
                               int readSampleSize) {

    }

    public int decodeFrame(byte[] inBuffer, int[] outBuffer) {
        /* setup the stream */
        byteArrayReader = new ByteArrayReader(inBuffer);
        int outputSizeBytes;

        int channels = byteArrayReader.readBits24(3);

        if (channels == 0) {// 1 channel
            FrameHeader frameHeader = decodeFrameHeader(maxSamplesPerFrame, maxSamplesPerFrame * bytesPerSample);
            int outputSamples = frameHeader.outputSamples;
            int uncompressedBytes = frameHeader.uncompressedBytes;
            outputSizeBytes = frameHeader.outputSizeBytes;

            if (frameHeader.isNotCompressed == 0) { // so it is compressed
                /* skip 16 bits, not sure what they are. seem to be used in
                 * two channel case */
                byteArrayReader.readBits24(8);
                byteArrayReader.readBits24(8);

                int predictionType = byteArrayReader.readBits24(4);
                int predictionQuantization = byteArrayReader.readBits24(4);

                int riceModifier = byteArrayReader.readBits24(3);
                int predictorCoefNum = byteArrayReader.readBits24(5);

                /* read the predictor table */

                int i;
                for (i = 0; i < predictorCoefNum; i++) {
                    int tempPred = byteArrayReader.readBits24(16);
                    if (tempPred > 32767) {
                        // the predictor coef table values are only 16 bit signed
                        tempPred = tempPred - 65536;
                    }

                    predictorCoefTable[i] = tempPred;
                }

                if (uncompressedBytes != 0) {
                    for (i = 0; i < outputSamples; i++) {
                        uncompressedBytesBufferA[i] = byteArrayReader.readBitsVar(uncompressedBytes * 8);
                    }
                }

                processPredictErrorBufferA(outputSamples, frameHeader.readSampleSize, predictionType, predictionQuantization, riceModifier, predictorCoefNum, predictorCoefTable);
            } else { // not compressed, easy case
                if (sampleSizeRaw <= 16) {
                    for (int i = 0; i < outputSamples; i++) {
                        int audiobits = byteArrayReader.readBitsVar(sampleSizeRaw);
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
                        int sample = outputSamplesBufferA[i];

                        if (uncompressedBytes != 0) {
                            sample = sample << uncompressedBytes * 8;
                            int mask = ~(0xFFFFFFFF << uncompressedBytes * 8);
                            sample = sample | uncompressedBytesBufferA[i] & mask;
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
                    log.error("FIXME: unimplemented sample size {}", sampleSizeRaw);
                default:

            }
        } else if (channels == 1) { // 2 channels
            FrameHeader frameHeader = decodeFrameHeader(maxSamplesPerFrame, maxSamplesPerFrame * bytesPerSample);
            int outputSamples = frameHeader.outputSamples;
            outputSizeBytes = frameHeader.outputSizeBytes;
            int uncompressedBytes = frameHeader.uncompressedBytes;

            int readSampleSize = sampleSizeRaw - uncompressedBytes * 8 + 1;

            int interlacingLeftWeight;
            int interlacingShift;
            if (frameHeader.isNotCompressed == 0) { // compressed
                interlacingShift = byteArrayReader.readBits24(8);
                interlacingLeftWeight = byteArrayReader.readBits24(8);

                /* ******* channel 1 ***********/
                int predictionTypeA = byteArrayReader.readBits24(4);
                int predictionQuantitizationA = byteArrayReader.readBits24(4);

                int ricemodifierA = byteArrayReader.readBits24(3);
                int predictorCoefNumA = byteArrayReader.readBits24(5);

                /* read the predictor table */

                int tempPred;
                for (int i = 0; i < predictorCoefNumA; i++) {
                    tempPred = byteArrayReader.readBits24(16);
                    if (tempPred > 32767) {
                        // the predictor coef table values are only 16 bit signed
                        tempPred = tempPred - 65536;
                    }
                    predictorCoefTableA[i] = tempPred;
                }

                /* ******* channel 2 *********/
                int predictionTypeB = byteArrayReader.readBits24(4);
                int predictionQuantitizationB = byteArrayReader.readBits24(4);

                int ricemodifierB = byteArrayReader.readBits24(3);
                int predictorCoefNumB = byteArrayReader.readBits24(5);

                /* read the predictor table */

                for (int i = 0; i < predictorCoefNumB; i++) {
                    tempPred = byteArrayReader.readBits24(16);
                    if (tempPred > 32767) {
                        // the predictor coef table values are only 16 bit signed
                        tempPred = tempPred - 65536;
                    }
                    predictorCoefTableB[i] = tempPred;
                }

                /* ********************/
                if (uncompressedBytes != 0) { // see mono case
                    for (int i = 0; i < outputSamples; i++) {
                        uncompressedBytesBufferA[i] = byteArrayReader.readBitsVar(uncompressedBytes * 8);
                        uncompressedBytesBufferB[i] = byteArrayReader.readBitsVar(uncompressedBytes * 8);
                    }
                }

                /* channel 1 */

                processPredictErrorBufferA(outputSamples, readSampleSize, predictionTypeA, predictionQuantitizationA, ricemodifierA, predictorCoefNumA, predictorCoefTableA);

                /* channel 2 */
                entropyRiceDecode(predictErrorBufferB, outputSamples, readSampleSize, ricemodifierB * (riceHistoryMult / 4));
                if (predictionTypeB == 0) { // adaptive fir
                    setOutputSamplesBufferB(predictorDecompressFirAdapt(predictErrorBufferB, outputSamples, readSampleSize, predictorCoefTableB, predictorCoefNumB, predictionQuantitizationB));
                } else if (predictionTypeB == 14 || predictionTypeB == 15 || predictionTypeB == 31) { // double-pass adaptive fir
                    log.debug("Using double-pass FIR for prediction type {}", predictionTypeB);
                    int[] tempBuffer = predictorDecompressFirAdapt(predictErrorBufferB, outputSamples, readSampleSize, predictorCoefTableB, predictorCoefNumB, predictionQuantitizationB);
                    setOutputSamplesBufferB(predictorDecompressFirAdapt(tempBuffer, outputSamples, readSampleSize, predictorCoefTableB, predictorCoefNumB, predictionQuantitizationB));
                } else {
                    log.error("FIXME: unhandled prediction type: {}", predictionTypeB);
                }
            } else { // not compressed, easy case
                if (sampleSizeRaw <= 16) {

                    for (int i = 0; i < outputSamples; i++) {

                        int audiobits_a = byteArrayReader.readBitsVar(sampleSizeRaw);
                        int audiobits_b = byteArrayReader.readBitsVar(sampleSizeRaw);

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

            switch (sampleSizeRaw) {
                case 16: {
                    deinterlace16(outputSamplesBufferA, outputSamplesBufferB, outBuffer, numChannels, outputSamples, interlacingShift, interlacingLeftWeight);
                    break;
                }
                case 24: {
                    deinterlace24(outputSamplesBufferA, outputSamplesBufferB, uncompressedBytes, uncompressedBytesBufferA, uncompressedBytesBufferB, outBuffer, numChannels, outputSamples, interlacingShift, interlacingLeftWeight);
                    break;
                }
                case 20:
                case 32:
                    log.error("FIXME: unimplemented sample size {}", sampleSizeRaw);
                default:
            }
        } else {
            throw new IllegalStateException("Unexpected count of channels");
        }
        return outputSizeBytes;
    }

    private void processPredictErrorBufferA(int outputSamples, int readSampleSize, int predictionTypeA, int predictionQuantitizationA, int ricemodifierA, int predictorCoefNumA, int[] predictorCoefTableA) {
        entropyRiceDecode(predictErrorBufferA, outputSamples, readSampleSize, ricemodifierA * (riceHistoryMult / 4));

        if (predictionTypeA == 0) { // adaptive fir
            setOutputSamplesBufferA(predictorDecompressFirAdapt(predictErrorBufferA, outputSamples, readSampleSize, predictorCoefTableA, predictorCoefNumA, predictionQuantitizationA));
        } else if (predictionTypeA == 14 || predictionTypeA == 15 || predictionTypeA == 31) { // double-pass adaptive fir
            log.debug("Using double-pass FIR for prediction type {}", predictionTypeA);
            int[] tempBuffer = predictorDecompressFirAdapt(predictErrorBufferA, outputSamples, readSampleSize, predictorCoefTableA, predictorCoefNumA, predictionQuantitizationA);
            setOutputSamplesBufferA(predictorDecompressFirAdapt(tempBuffer, outputSamples, readSampleSize, predictorCoefTableA, predictorCoefNumA, predictionQuantitizationA));
        } else {
            log.error("FIXME: unhandled prediction type: {}", predictionTypeA);
        }
    }

    private int getAudioBits() {
        int audioBits = byteArrayReader.readBits24(16);
        /* special case of sign extension..
         * as we'll be ORing the low 16bits into this */
        audioBits = audioBits << sampleSizeRaw - 16;
        audioBits = audioBits | byteArrayReader.readBitsVar(sampleSizeRaw - 16);
        return audioBits;
    }
}