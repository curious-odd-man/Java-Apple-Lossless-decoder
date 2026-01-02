/*
 ** AlacDecodeUtils.java
 **
 ** Copyright (c) 2011 Peter McQuillan
 **
 ** All Rights Reserved.
 **
 ** Distributed under the BSD Software License (see license.txt)
 **
 */
package com.github.curiousoddman.alacdecoder;

public class AlacDecodeUtils {
    public static int countLeadingZerosExtra(int curbyte, int output) {
        if ((curbyte & 0xf0) == 0) {
            output += 4;
        } else {
            curbyte = curbyte >> 4;
        }

        if ((curbyte & 0x8) != 0) {
            return output;
        }
        if ((curbyte & 0x4) != 0) {
            return output + 1;
        }
        if ((curbyte & 0x2) != 0) {
            return output + 2;
        }
        if ((curbyte & 0x1) != 0) {
            return output + 3;
        }

        /* shouldn't get here: */
        return output + 4;
    }

    public static int countLeadingZeros(int input) {
        int output = 0;

        int curbyte = input >> 24;
        if (curbyte != 0) {
            return countLeadingZerosExtra(curbyte, output);
        }
        output += 8;

        curbyte = input >> 16;
        if ((curbyte & 0xFF) != 0) {
            return countLeadingZerosExtra(curbyte, output);
        }
        output += 8;

        curbyte = input >> 8;
        if ((curbyte & 0xFF) != 0) {
            return countLeadingZerosExtra(curbyte, output);
        }
        output += 8;

        curbyte = input;
        if ((curbyte & 0xFF) != 0) {
            return countLeadingZerosExtra(curbyte, output);
        }
        output += 8;
        return output;
    }

    public static int[] predictorDecompressFirAdapt(int[] bufferOut,
                                                    int outputSize,
                                                    int readSampleSize,
                                                    int[] predictorCoefTable,
                                                    int predictorCoefNum,
                                                    int predictorQuantization) {
        /* first sample always copies */
        if (predictorCoefNum == 0) {
            if (outputSize <= 1) {
                return bufferOut;
            }
            int sizeToCopy = (outputSize - 1) * 4;
            System.arraycopy(bufferOut, 1, bufferOut, 1, sizeToCopy);
            return bufferOut;
        }

        int bitsmove;
        if (predictorCoefNum == 0x1f) { // 11111 - max value of predictorCoefNum
            /* second-best case scenario for fir decompression,
             * error describes a small difference from the previous sample only
             */
            if (outputSize <= 1) {
                return bufferOut;
            }

            for (int i = 0; i < outputSize - 1; i++) {
                int prevValue = bufferOut[i];
                int errorValue = bufferOut[i + 1];

                bitsmove = 32 - readSampleSize;
                bufferOut[i + 1] = prevValue + errorValue << bitsmove >> bitsmove;
            }
            return bufferOut;
        }

        /* read warm-up samples */
        if (predictorCoefNum > 0) {
            for (int i = 0; i < predictorCoefNum; i++) {

                int val = bufferOut[i] + bufferOut[i + 1];

                bitsmove = 32 - readSampleSize;

                val = val << bitsmove >> bitsmove;

                bufferOut[i + 1] = val;
            }
        }

        /* general case */
        if (predictorCoefNum > 0) {
            int buffer_out_idx = 0;
            for (int i = predictorCoefNum + 1; i < outputSize; i++) {
                int sum = 0;
                int error_val = bufferOut[i];

                for (int j = 0; j < predictorCoefNum; j++) {
                    sum += (bufferOut[buffer_out_idx + predictorCoefNum - j] - bufferOut[buffer_out_idx]) * predictorCoefTable[j];
                }

                int outval = (1 << predictorQuantization - 1) + sum;
                outval = outval >> predictorQuantization;
                outval = outval + bufferOut[buffer_out_idx] + error_val;
                bitsmove = 32 - readSampleSize;

                outval = outval << bitsmove >> bitsmove;

                bufferOut[buffer_out_idx + predictorCoefNum + 1] = outval;

                if (error_val > 0) {
                    int predictor_num = predictorCoefNum - 1;

                    while (predictor_num >= 0 && error_val > 0) {
                        int val = bufferOut[buffer_out_idx] - bufferOut[buffer_out_idx + predictorCoefNum - predictor_num];
                        int sign = Integer.compare(val, 0);

                        predictorCoefTable[predictor_num] -= sign;

                        val *= sign; // absolute value

                        error_val -= (val >> predictorQuantization) * (predictorCoefNum - predictor_num);

                        predictor_num--;
                    }
                } else if (error_val < 0) {
                    int predictor_num = predictorCoefNum - 1;

                    while (predictor_num >= 0 && error_val < 0) {
                        int val = bufferOut[buffer_out_idx] - bufferOut[buffer_out_idx + predictorCoefNum - predictor_num];
                        int sign = -Integer.compare(val, 0);

                        predictorCoefTable[predictor_num] -= sign;

                        val *= sign; // neg value

                        error_val -= (val >> predictorQuantization) * (predictorCoefNum - predictor_num);

                        predictor_num--;
                    }
                }

                buffer_out_idx++;
            }
        }
        return bufferOut;
    }

    public static void deinterlace16(int[] bufferA,
                                     int[] bufferB,
                                     int[] bufferOut,
                                     int numChannels,
                                     int numSamples,
                                     int interlacingShift,
                                     int interlacingLeftWeight) {
        if (numSamples <= 0) {
            return;
        }

        /* weighted interlacing */
        if (0 != interlacingLeftWeight) {
            for (int i = 0; i < numSamples; i++) {
                int midRight = bufferA[i];
                int difference = bufferB[i];

                int right = midRight - (difference * interlacingLeftWeight >> interlacingShift);
                int left = right + difference;

                /* output is always little endian */
                bufferOut[i * numChannels] = left;
                bufferOut[i * numChannels + 1] = right;
            }

            return;
        }

        /* otherwise basic interlacing took place */
        for (int i = 0; i < numSamples; i++) {
            int left = bufferA[i];
            int right = bufferB[i];

            /* output is always little endian */
            bufferOut[i * numChannels] = left;
            bufferOut[i * numChannels + 1] = right;
        }
    }

    public static void deinterlace24(int[] bufferA,
                                     int[] bufferB,
                                     int uncompressedBytes,
                                     int[] uncompressedBytesBufferA,
                                     int[] uncompressedBytesBufferB,
                                     int[] bufferOut,
                                     int numChannels,
                                     int numSamples,
                                     int interlacingShift,
                                     int interlacingLeftWeight) {
        if (numSamples <= 0) {
            return;
        }

        /* weighted interlacing */
        if (interlacingLeftWeight != 0) {
            for (int i = 0; i < numSamples; i++) {
                int midRight = bufferA[i];
                int difference = bufferB[i];
                int right = midRight - (difference * interlacingLeftWeight >> interlacingShift);
                int left = right + difference;
                deinterlace24Sample(uncompressedBytes, uncompressedBytesBufferA, uncompressedBytesBufferB, bufferOut, numChannels, i, left, right);
            }
            return;
        }

        /* otherwise basic interlacing took place */
        for (int i = 0; i < numSamples; i++) {
            int left = bufferA[i];
            int right = bufferB[i];
            deinterlace24Sample(uncompressedBytes, uncompressedBytesBufferA, uncompressedBytesBufferB, bufferOut, numChannels, i, left, right);
        }
    }

    private static void deinterlace24Sample(int uncompressedBytes,
                                            int[] uncompressedBytesBufferA,
                                            int[] uncompressedBytesBufferB,
                                            int[] bufferOut,
                                            int numChannels,
                                            int i,
                                            int left,
                                            int right) {
        if (uncompressedBytes != 0) {
            int mask = ~(0xFFFFFFFF << uncompressedBytes * 8);
            left <<= uncompressedBytes * 8;
            right <<= uncompressedBytes * 8;

            left = left | uncompressedBytesBufferA[i] & mask;
            right = right | uncompressedBytesBufferB[i] & mask;
        }

        bufferOut[i * numChannels * 3] = left & 0xFF;
        bufferOut[i * numChannels * 3 + 1] = left >> 8 & 0xFF;
        bufferOut[i * numChannels * 3 + 2] = left >> 16 & 0xFF;

        bufferOut[i * numChannels * 3 + 3] = right & 0xFF;
        bufferOut[i * numChannels * 3 + 4] = right >> 8 & 0xFF;
        bufferOut[i * numChannels * 3 + 5] = right >> 16 & 0xFF;
    }
}

