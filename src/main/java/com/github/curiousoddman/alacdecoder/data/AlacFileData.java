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

import static com.github.curiousoddman.alacdecoder.AlacDecodeUtils.*;

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

    public int decodeFrame(byte[] inBuffer, int[] outBuffer) {
        int outputSamples = maxSamplesPerFrame;

        /* setup the stream */
        inputBuffer = inBuffer;
        inputBufferBitAccumulator = 0;
        ibIdx = 0;

        int channels = readbits(3);
        int outputSize = outputSamples * getBytesPerSample();

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
                outputSize = outputSamples * getBytesPerSample();
            }

            int readSampleSize = getSampleSizeRaw() - uncompressedBytes * 8;

            if (isNotCompressed == 0) { // so it is compressed
                /* skip 16 bits, not sure what they are. seem to be used in
                 * two channel case */
                readbits(8);
                readbits(8);

                int predictionType = readbits(4);
                int predictionQuantitization = readbits(4);

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
                        getUncompressedBytesBufferA()[i] = readbits(uncompressedBytes * 8);
                    }
                }

                entropyRiceDecode(getPredictErrorBufferA(), outputSamples, readSampleSize, riceModifier * (getRiceHistorymult() / 4));

                if (predictionType == 0) { // adaptive fir
                    setOutputSamplesBufferA(predictorDecompressFirAdapt(getPredictErrorBufferA(), outputSamples, readSampleSize, predictorCoefTable, predictorCoefNum, predictionQuantitization));
                } else {
                    System.err.println("FIXME: unhandled predicition type: " + predictionType);

                    /* i think the only other prediction type (or perhaps this is just a
                     * boolean?) runs adaptive fir twice.. like:
                     * predictor_decompress_fir_adapt(predictor_error, tempout, ...)
                     * predictor_decompress_fir_adapt(predictor_error, outputSamples ...)
                     * little strange..
                     */
                }

            } else { // not compressed, easy case
                if (getSampleSizeRaw() <= 16) {
                    for (int i = 0; i < outputSamples; i++) {
                        int audiobits = readbits(getSampleSizeRaw());
                        int bitsmove = 32 - getSampleSizeRaw();

                        audiobits = audiobits << bitsmove >> bitsmove;

                        getOutputSamplesBufferA()[i] = audiobits;
                    }
                } else {
                    int m = 1 << 24 - 1;
                    for (int i = 0; i < outputSamples; i++) {

                        int audiobits = getAudiobits();
                        int x = audiobits & (1 << 24) - 1;
                        audiobits = (x ^ m) - m;    // sign extend 24 bits

                        getOutputSamplesBufferA()[i] = audiobits;
                    }
                }
                uncompressedBytes = 0; // always 0 for uncompressed
            }

            switch (getSampleSizeRaw()) {
                case 16: {

                    for (int i = 0; i < outputSamples; i++) {
                        int sample = getOutputSamplesBufferA()[i];
                        outBuffer[i * getNumChannels()] = sample;

                        /*
                         ** We have to handle the case where the data is actually mono, but the stsd atom says it has 2 channels
                         ** in this case we create a stereo file where one of the channels is silent. If mono and 1 channel this value
                         ** will be overwritten in the next iteration
                         */

                        outBuffer[i * getNumChannels() + 1] = 0;
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

                        outBuffer[i * getNumChannels() * 3] = sample & 0xFF;
                        outBuffer[i * getNumChannels() * 3 + 1] = sample >> 8 & 0xFF;
                        outBuffer[i * getNumChannels() * 3 + 2] = sample >> 16 & 0xFF;

                        /*
                         ** We have to handle the case where the data is actually mono, but the stsd atom says it has 2 channels
                         ** in this case we create a stereo file where one of the channels is silent. If mono and 1 channel this value
                         ** will be overwritten in the next iteration
                         */

                        outBuffer[i * getNumChannels() * 3 + 3] = 0;
                        outBuffer[i * getNumChannels() * 3 + 4] = 0;
                        outBuffer[i * getNumChannels() * 3 + 5] = 0;

                    }
                    break;
                }
                case 20:
                case 32:
                    System.err.println("FIXME: unimplemented sample size " + getSampleSizeRaw());
                default:

            }
        } else if (channels == 1) // 2 channels
        {

            /* 2^result = something to do with output waiting.
             * perhaps matters if we read > 1 frame in a pass?
             */
            readbits(4);

            readbits(12); // unknown, skip 12 bits

            int hassize = readbits(1); // the output sample size is stored soon

            int uncompressed_bytes = readbits(2); // the number of bytes in the (compressed) stream that are not compressed

            int isnotcompressed = readbits(1); // whether the frame is compressed

            if (hassize != 0) {
                /* now read the number of samples,
                 * as a 32bit integer */
                outputSamples = readbits(32);
                outputSize = outputSamples * getBytesPerSample();
            }

            int readsamplesize = getSampleSizeRaw() - uncompressed_bytes * 8 + 1;

            int interlacing_leftweight;
            int interlacing_shift;
            if (isnotcompressed == 0) { // compressed
                int[] predictor_coef_table_a = getPredictorCoefTableA();

                int[] predictor_coef_table_b = getPredictorCoefTableB();

                interlacing_shift = readbits(8);
                interlacing_leftweight = readbits(8);

                /* ******* channel 1 ***********/
                int prediction_type_a = readbits(4);
                int prediction_quantitization_a = readbits(4);

                int ricemodifier_a = readbits(3);
                int predictor_coef_num_a = readbits(5);

                /* read the predictor table */

                int tempPred;
                for (int i = 0; i < predictor_coef_num_a; i++) {
                    tempPred = readbits(16);
                    if (tempPred > 32767) {
                        // the predictor coef table values are only 16 bit signed
                        tempPred = tempPred - 65536;
                    }
                    predictor_coef_table_a[i] = tempPred;
                }

                /* ******* channel 2 *********/
                int prediction_type_b = readbits(4);
                int prediction_quantitization_b = readbits(4);

                int ricemodifier_b = readbits(3);
                int predictor_coef_num_b = readbits(5);

                /* read the predictor table */

                for (int i = 0; i < predictor_coef_num_b; i++) {
                    tempPred = readbits(16);
                    if (tempPred > 32767) {
                        // the predictor coef table values are only 16 bit signed
                        tempPred = tempPred - 65536;
                    }
                    predictor_coef_table_b[i] = tempPred;
                }

                /* ********************/
                if (uncompressed_bytes != 0) { // see mono case
                    for (int i = 0; i < outputSamples; i++) {
                        getUncompressedBytesBufferA()[i] = readbits(uncompressed_bytes * 8);
                        getUncompressedBytesBufferB()[i] = readbits(uncompressed_bytes * 8);
                    }
                }

                /* channel 1 */

                entropyRiceDecode(getPredictErrorBufferA(), outputSamples, readsamplesize, ricemodifier_a * (getRiceHistorymult() / 4));

                if (prediction_type_a == 0) { // adaptive fir

                    setOutputSamplesBufferA(predictorDecompressFirAdapt(getPredictErrorBufferA(), outputSamples, readsamplesize, predictor_coef_table_a, predictor_coef_num_a, prediction_quantitization_a));

                } else { // see mono case
                    System.err.println("FIXME: unhandled predicition type: " + prediction_type_a);
                }

                /* channel 2 */
                entropyRiceDecode(getPredictErrorBufferB(), outputSamples, readsamplesize, ricemodifier_b * (getRiceHistorymult() / 4));

                if (prediction_type_b == 0) { // adaptive fir
                    setOutputSamplesBufferB(predictorDecompressFirAdapt(getPredictErrorBufferB(), outputSamples, readsamplesize, predictor_coef_table_b, predictor_coef_num_b, prediction_quantitization_b));
                } else {
                    System.err.println("FIXME: unhandled predicition type: " + prediction_type_b);
                }
            } else { // not compressed, easy case
                if (getSampleSizeRaw() <= 16) {

                    for (int i = 0; i < outputSamples; i++) {

                        int audiobits_a = readbits(getSampleSizeRaw());
                        int audiobits_b = readbits(getSampleSizeRaw());

                        int bitsmove = 32 - getSampleSizeRaw();

                        audiobits_a = audiobits_a << bitsmove >> bitsmove;
                        audiobits_b = audiobits_b << bitsmove >> bitsmove;

                        getOutputSamplesBufferA()[i] = audiobits_a;
                        getOutputSamplesBufferB()[i] = audiobits_b;
                    }
                } else {
                    int m = 1 << 24 - 1;

                    for (int i = 0; i < outputSamples; i++) {

                        int audiobits_a = getAudiobits();
                        int x = audiobits_a & (1 << 24) - 1;
                        audiobits_a = (x ^ m) - m;        // sign extend 24 bits

                        int audiobits_b = getAudiobits();
                        x = audiobits_b & (1 << 24) - 1;
                        audiobits_b = (x ^ m) - m;        // sign extend 24 bits

                        getOutputSamplesBufferA()[i] = audiobits_a;
                        getOutputSamplesBufferB()[i] = audiobits_b;
                    }
                }
                uncompressed_bytes = 0; // always 0 for uncompressed
                interlacing_shift = 0;
                interlacing_leftweight = 0;
            }

            switch (getSampleSizeRaw()) {
                case 16: {
                    deinterlace16(getOutputSamplesBufferA(), getOutputSamplesBufferB(), outBuffer, getNumChannels(), outputSamples, interlacing_shift, interlacing_leftweight);
                    break;
                }
                case 24: {
                    deinterlace24(getOutputSamplesBufferA(), getOutputSamplesBufferB(), uncompressed_bytes, getUncompressedBytesBufferA(), getUncompressedBytesBufferB(), outBuffer, getNumChannels(), outputSamples, interlacing_shift, interlacing_leftweight);
                    break;
                }
                case 20:
                case 32:
                    System.err.println("FIXME: unimplemented sample size " + getSampleSizeRaw());

                default:

            }
        }
        return outputSize;
    }

    private int getAudiobits() {
        int audiobits = readbits(16);
        /* special case of sign extension..
         * as we'll be ORing the low 16bits into this */
        audiobits = audiobits << getSampleSizeRaw() - 16;
        audiobits = audiobits | readbits(getSampleSizeRaw() - 16);
        return audiobits;
    }
}