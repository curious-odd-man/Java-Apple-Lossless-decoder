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

import com.github.curiousoddman.alacdecoder.data.AlacFileData;
import com.github.curiousoddman.alacdecoder.data.Defines;
import com.github.curiousoddman.alacdecoder.data.LeadingZeros;

class AlacDecodeUtils {

    public static void alac_set_info(AlacFileData alac, int[] inputbuffer) {
        int ptrIndex = 0;
        ptrIndex += 4; // size
        ptrIndex += 4; // frma
        ptrIndex += 4; // alac
        ptrIndex += 4; // size
        ptrIndex += 4; // alac

        ptrIndex += 4; // 0 ?

        alac.setinfo_max_samples_per_frame = ((inputbuffer[ptrIndex] << 24) + (inputbuffer[ptrIndex + 1] << 16) + (inputbuffer[ptrIndex + 2] << 8) + inputbuffer[ptrIndex + 3]); // buffer size / 2 ?
        ptrIndex += 4;
        alac.setinfo_7a = inputbuffer[ptrIndex];
        ptrIndex += 1;
        alac.setinfo_sample_size = inputbuffer[ptrIndex];
        ptrIndex += 1;
        alac.setinfo_rice_historymult = (inputbuffer[ptrIndex] & 0xff);
        ptrIndex += 1;
        alac.setinfo_rice_initialhistory = (inputbuffer[ptrIndex] & 0xff);
        ptrIndex += 1;
        alac.setinfo_rice_kmodifier = (inputbuffer[ptrIndex] & 0xff);
        ptrIndex += 1;
        alac.setinfo_7f = inputbuffer[ptrIndex];
        ptrIndex += 1;
        alac.setinfo_80 = (inputbuffer[ptrIndex] << 8) + inputbuffer[ptrIndex + 1];
        ptrIndex += 2;
        alac.setinfo_82 = ((inputbuffer[ptrIndex] << 24) + (inputbuffer[ptrIndex + 1] << 16) + (inputbuffer[ptrIndex + 2] << 8) + inputbuffer[ptrIndex + 3]);
        ptrIndex += 4;
        alac.setinfo_86 = ((inputbuffer[ptrIndex] << 24) + (inputbuffer[ptrIndex + 1] << 16) + (inputbuffer[ptrIndex + 2] << 8) + inputbuffer[ptrIndex + 3]);
        ptrIndex += 4;
        alac.setinfo_8a_rate = ((inputbuffer[ptrIndex] << 24) + (inputbuffer[ptrIndex + 1] << 16) + (inputbuffer[ptrIndex + 2] << 8) + inputbuffer[ptrIndex + 3]);

    }

    /* stream reading */

    /* supports reading 1 to 16 bits, in big endian format */
    static int readbits_16(AlacFileData alac, int bits) {

        int part1 = (alac.input_buffer[alac.ibIdx] & 0xff);
        int part2 = (alac.input_buffer[alac.ibIdx + 1] & 0xff);
        int part3 = (alac.input_buffer[alac.ibIdx + 2] & 0xff);

        int result = ((part1 << 16) | (part2 << 8) | part3);

        /* shift left by the number of bits we've already read,
         * so that the top 'n' bits of the 24 bits we read will
         * be the return bits */
        result = result << alac.input_buffer_bitaccumulator;

        result = result & 0x00ffffff;

        /* and then only want the top 'n' bits from that, where
         * n is 'bits' */
        result = result >> (24 - bits);

        int new_accumulator = (alac.input_buffer_bitaccumulator + bits);

        /* increase the buffer pointer if we've read over n bytes. */
        alac.ibIdx += (new_accumulator >> 3);

        /* and the remainder goes back into the bit accumulator */
        alac.input_buffer_bitaccumulator = (new_accumulator & 7);

        return result;
    }

    /* supports reading 1 to 32 bits, in big endian format */
    static int readbits(AlacFileData alac, int bits) {
        int result = 0;

        if (bits > 16) {
            bits -= 16;

            result = readbits_16(alac, 16) << bits;
        }

        result |= readbits_16(alac, bits);

        return result;
    }

    /* reads a single bit */
    static int readbit(AlacFileData alac) {

        int result = (alac.input_buffer[alac.ibIdx] & 0xff);

        result = result << alac.input_buffer_bitaccumulator;

        result = result >> 7 & 1;

        int new_accumulator = (alac.input_buffer_bitaccumulator + 1);

        alac.ibIdx += new_accumulator / 8;

        alac.input_buffer_bitaccumulator = (new_accumulator % 8);

        return result;
    }

    static void unreadbits(AlacFileData alac) {
        int new_accumulator = (alac.input_buffer_bitaccumulator - 1);

        alac.ibIdx += (new_accumulator >> 3);

        alac.input_buffer_bitaccumulator = (new_accumulator & 7);
        if (alac.input_buffer_bitaccumulator < 0)
            alac.input_buffer_bitaccumulator *= -1;
    }

    static void count_leading_zeros_extra(int curbyte, int output, LeadingZeros lz) {

        if ((curbyte & 0xf0) == 0) {
            output += 4;
        } else
            curbyte = curbyte >> 4;

        if ((curbyte & 0x8) != 0) {
            lz.setOutput(output);
            lz.setCurbyte(curbyte);
            return;
        }
        if ((curbyte & 0x4) != 0) {
            lz.setOutput(output + 1);
            lz.setCurbyte(curbyte);
            return;
        }
        if ((curbyte & 0x2) != 0) {
            lz.setOutput(output + 2);
            lz.setCurbyte(curbyte);
            return;
        }
        if ((curbyte & 0x1) != 0) {
            lz.setOutput(output + 3);
            lz.setCurbyte(curbyte);
            return;
        }

        /* shouldn't get here: */

        lz.setOutput(output + 4);
        lz.setCurbyte(curbyte);

    }

    static int count_leading_zeros(int input, LeadingZeros lz) {
        int output = 0;

        int curbyte = input >> 24;
        if (curbyte != 0) {
            count_leading_zeros_extra(curbyte, output, lz);
            output = lz.getOutput();
            return output;
        }
        output += 8;

        curbyte = input >> 16;
        if ((curbyte & 0xFF) != 0) {
            count_leading_zeros_extra(curbyte, output, lz);
            output = lz.getOutput();

            return output;
        }
        output += 8;

        curbyte = input >> 8;
        if ((curbyte & 0xFF) != 0) {
            count_leading_zeros_extra(curbyte, output, lz);
            output = lz.getOutput();

            return output;
        }
        output += 8;

        curbyte = input;
        if ((curbyte & 0xFF) != 0) {
            count_leading_zeros_extra(curbyte, output, lz);
            output = lz.getOutput();

            return output;
        }
        output += 8;

        return output;
    }

    public static int entropy_decode_value(AlacFileData alac, int readSampleSize, int k, int rice_kmodifier_mask) {
        int x = 0; // decoded value

        // read x, number of 1s before 0 represent the rice value.
        while (x <= Defines.RICE_THRESHOLD && readbit(alac) != 0) {
            x++;
        }

        if (x > Defines.RICE_THRESHOLD) {
            // read the number from the bit stream (raw value)

            int value = readbits(alac, readSampleSize);

            // mask value
            value &= ((0xffffffff) >> (32 - readSampleSize));

            x = value;
        } else {
            if (k != 1) {
                int extraBits = readbits(alac, k);

                x *= (((1 << k) - 1) & rice_kmodifier_mask);

                if (extraBits > 1)
                    x += extraBits - 1;
                else
                    unreadbits(alac);
            }
        }

        return x;
    }

    public static void entropy_rice_decode(AlacFileData alac, int[] outputBuffer, int outputSize, int readSampleSize, int rice_initialhistory, int rice_kmodifier, int rice_historymult, int rice_kmodifier_mask) {
        int history = rice_initialhistory;
        int outputCount = 0;
        int signModifier = 0;

        while (outputCount < outputSize) {

            int k = 31 - rice_kmodifier - count_leading_zeros((history >> 9) + 3, alac.lz);

            if (k < 0)
                k += rice_kmodifier;
            else
                k = rice_kmodifier;

            // note: don't use rice_kmodifier_mask here (set mask to 0xFFFFFFFF)
            int decodedValue = entropy_decode_value(alac, readSampleSize, k, 0xFFFFFFFF);

            decodedValue += signModifier;
            int finalValue = ((decodedValue + 1) / 2); // inc by 1 and shift out sign bit
            if ((decodedValue & 1) != 0) // the sign is stored in the low bit
                finalValue *= -1;

            outputBuffer[outputCount] = finalValue;

            signModifier = 0;

            // update history
            history += (decodedValue * rice_historymult) - ((history * rice_historymult) >> 9);

            if (decodedValue > 0xFFFF)
                history = 0xFFFF;

            // special case, for compressed blocks of 0
            if ((history < 128) && (outputCount + 1 < outputSize)) {

                signModifier = 1;

                k = count_leading_zeros(history, alac.lz) + ((history + 16) / 64) - 24;

                // note: blockSize is always 16bit
                int blockSize = entropy_decode_value(alac, 16, k, rice_kmodifier_mask);

                // got blockSize 0s
                if (blockSize > 0) {
                    int countSize = blockSize;
                    for (int j = 0; j < countSize; j++) {
                        outputBuffer[outputCount + 1 + j] = 0;
                    }
                    outputCount += blockSize;
                }

                if (blockSize > 0xFFFF)
                    signModifier = 0;

                history = 0;
            }

            outputCount++;
        }
    }

    static int[] predictor_decompress_fir_adapt(int[] error_buffer, int output_size, int readsamplesize, int[] predictor_coef_table, int predictor_coef_num, int predictor_quantitization) {
        int buffer_out_idx;
        int bitsmove;

        /* first sample always copies */
        int[] buffer_out = error_buffer;

        if (predictor_coef_num == 0) {
            if (output_size <= 1)
                return (buffer_out);
            int sizeToCopy = (output_size - 1) * 4;
            System.arraycopy(error_buffer, 1, buffer_out, 1, sizeToCopy);
            return (buffer_out);
        }

        if (predictor_coef_num == 0x1f) // 11111 - max value of predictor_coef_num
        {
            /* second-best case scenario for fir decompression,
             * error describes a small difference from the previous sample only
             */
            if (output_size <= 1)
                return (buffer_out);

            for (int i = 0; i < (output_size - 1); i++) {

                int prev_value = buffer_out[i];
                int error_value = error_buffer[i + 1];

                bitsmove = 32 - readsamplesize;
                buffer_out[i + 1] = (((prev_value + error_value) << bitsmove) >> bitsmove);
            }
            return (buffer_out);
        }

        /* read warm-up samples */
        if (predictor_coef_num > 0) {
            for (int i = 0; i < predictor_coef_num; i++) {

                int val = buffer_out[i] + error_buffer[i + 1];

                bitsmove = 32 - readsamplesize;

                val = ((val << bitsmove) >> bitsmove);

                buffer_out[i + 1] = val;
            }
        }

        /* general case */
        if (predictor_coef_num > 0) {
            buffer_out_idx = 0;
            for (int i = predictor_coef_num + 1; i < output_size; i++) {
                int j;
                int sum = 0;
                int outval;
                int error_val = error_buffer[i];

                for (j = 0; j < predictor_coef_num; j++) {
                    sum += (buffer_out[buffer_out_idx + predictor_coef_num - j] - buffer_out[buffer_out_idx]) * predictor_coef_table[j];
                }

                outval = (1 << (predictor_quantitization - 1)) + sum;
                outval = outval >> predictor_quantitization;
                outval = outval + buffer_out[buffer_out_idx] + error_val;
                bitsmove = 32 - readsamplesize;

                outval = ((outval << bitsmove) >> bitsmove);

                buffer_out[buffer_out_idx + predictor_coef_num + 1] = outval;

                if (error_val > 0) {
                    int predictor_num = predictor_coef_num - 1;

                    while (predictor_num >= 0 && error_val > 0) {
                        int val = buffer_out[buffer_out_idx] - buffer_out[buffer_out_idx + predictor_coef_num - predictor_num];
                        int sign = (Integer.compare(val, 0));

                        predictor_coef_table[predictor_num] -= sign;

                        val *= sign; // absolute value

                        error_val -= ((val >> predictor_quantitization) * (predictor_coef_num - predictor_num));

                        predictor_num--;
                    }
                } else if (error_val < 0) {
                    int predictor_num = predictor_coef_num - 1;

                    while (predictor_num >= 0 && error_val < 0) {
                        int val = buffer_out[buffer_out_idx] - buffer_out[buffer_out_idx + predictor_coef_num - predictor_num];
                        int sign = -(Integer.compare(val, 0));

                        predictor_coef_table[predictor_num] -= sign;

                        val *= sign; // neg value

                        error_val -= ((val >> predictor_quantitization) * (predictor_coef_num - predictor_num));

                        predictor_num--;
                    }
                }

                buffer_out_idx++;
            }
        }
        return (buffer_out);
    }


    public static void deinterlace_16(int[] buffer_a, int[] buffer_b, int[] buffer_out, int numchannels, int numsamples, int interlacing_shift, int interlacing_leftweight) {

        if (numsamples <= 0)
            return;

        /* weighted interlacing */
        if (0 != interlacing_leftweight) {
            for (int i = 0; i < numsamples; i++) {

                int midright = buffer_a[i];
                int difference = buffer_b[i];

                int right = (midright - ((difference * interlacing_leftweight) >> interlacing_shift));
                int left = (right + difference);

                /* output is always little endian */

                buffer_out[i * numchannels] = left;
                buffer_out[i * numchannels + 1] = right;
            }

            return;
        }

        /* otherwise basic interlacing took place */
        for (int i = 0; i < numsamples; i++) {

            int left = buffer_a[i];
            int right = buffer_b[i];

            /* output is always little endian */

            buffer_out[i * numchannels] = left;
            buffer_out[i * numchannels + 1] = right;
        }
    }


    public static void deinterlace_24(int[] buffer_a, int[] buffer_b, int uncompressed_bytes, int[] uncompressed_bytes_buffer_a, int[] uncompressed_bytes_buffer_b, int[] buffer_out, int numchannels, int numsamples, int interlacing_shift, int interlacing_leftweight) {
        if (numsamples <= 0)
            return;

        /* weighted interlacing */
        if (interlacing_leftweight != 0) {
            for (int i = 0; i < numsamples; i++) {

                int midright = buffer_a[i];
                int difference = buffer_b[i];

                int right = midright - ((difference * interlacing_leftweight) >> interlacing_shift);
                int left = right + difference;

                extracted_duplicate_code(uncompressed_bytes, uncompressed_bytes_buffer_a, uncompressed_bytes_buffer_b, buffer_out, numchannels, i, left, right);
            }

            return;
        }

        /* otherwise basic interlacing took place */
        for (int i = 0; i < numsamples; i++) {

            int left = buffer_a[i];
            int right = buffer_b[i];

            extracted_duplicate_code(uncompressed_bytes, uncompressed_bytes_buffer_a, uncompressed_bytes_buffer_b, buffer_out, numchannels, i, left, right);

        }

    }

    private static void extracted_duplicate_code(int uncompressed_bytes, int[] uncompressed_bytes_buffer_a, int[] uncompressed_bytes_buffer_b, int[] buffer_out, int numchannels, int i, int left, int right) {
        if (uncompressed_bytes != 0) {
            int mask = ~(0xFFFFFFFF << (uncompressed_bytes * 8));
            left <<= (uncompressed_bytes * 8);
            right <<= (uncompressed_bytes * 8);

            left = left | (uncompressed_bytes_buffer_a[i] & mask);
            right = right | (uncompressed_bytes_buffer_b[i] & mask);
        }

        buffer_out[i * numchannels * 3] = (left & 0xFF);
        buffer_out[i * numchannels * 3 + 1] = ((left >> 8) & 0xFF);
        buffer_out[i * numchannels * 3 + 2] = ((left >> 16) & 0xFF);

        buffer_out[i * numchannels * 3 + 3] = (right & 0xFF);
        buffer_out[i * numchannels * 3 + 4] = ((right >> 8) & 0xFF);
        buffer_out[i * numchannels * 3 + 5] = ((right >> 16) & 0xFF);
    }


    public static int decode_frame(AlacFileData alac, byte[] inbuffer, int[] outbuffer) {
        int outputsamples = alac.setinfo_max_samples_per_frame;

        /* setup the stream */
        alac.input_buffer = inbuffer;
        alac.input_buffer_bitaccumulator = 0;
        alac.ibIdx = 0;


        int channels = readbits(alac, 3);

        int outputsize = outputsamples * alac.bytespersample;

        if (channels == 0) // 1 channel
        {

            int ricemodifier;

            int tempPred;

            /* 2^result = something to do with output waiting.
             * perhaps matters if we read > 1 frame in a pass?
             */
            readbits(alac, 4);

            readbits(alac, 12); // unknown, skip 12 bits

            int hassize = readbits(alac, 1); // the output sample size is stored soon

            int uncompressed_bytes = readbits(alac, 2); // number of bytes in the (compressed) stream that are not compressed

            int isnotcompressed = readbits(alac, 1); // whether the frame is compressed

            if (hassize != 0) {
                /* now read the number of samples,
                 * as a 32bit integer */
                outputsamples = readbits(alac, 32);
                outputsize = outputsamples * alac.bytespersample;
            }

            int readsamplesize = alac.setinfo_sample_size - (uncompressed_bytes * 8);

            if (isnotcompressed == 0) { // so it is compressed
                int[] predictor_coef_table = alac.predictor_coef_table;
                int i;

                /* skip 16 bits, not sure what they are. seem to be used in
                 * two channel case */
                readbits(alac, 8);
                readbits(alac, 8);

                int prediction_type = readbits(alac, 4);
                int prediction_quantitization = readbits(alac, 4);

                ricemodifier = readbits(alac, 3);
                int predictor_coef_num = readbits(alac, 5);

                /* read the predictor table */

                for (i = 0; i < predictor_coef_num; i++) {
                    tempPred = readbits(alac, 16);
                    if (tempPred > 32767) {
                        // the predictor coef table values are only 16 bit signed
                        tempPred = tempPred - 65536;
                    }

                    predictor_coef_table[i] = tempPred;
                }

                if (uncompressed_bytes != 0) {
                    for (i = 0; i < outputsamples; i++) {
                        alac.uncompressed_bytes_buffer_a[i] = readbits(alac, uncompressed_bytes * 8);
                    }
                }


                another_duplicate_code(alac, outputsamples, readsamplesize, ricemodifier, predictor_coef_table, predictor_coef_num, prediction_type, prediction_quantitization);

            } else { // not compressed, easy case
                if (alac.setinfo_sample_size <= 16) {
                    int bitsmove;
                    for (int i = 0; i < outputsamples; i++) {
                        int audiobits = readbits(alac, alac.setinfo_sample_size);
                        bitsmove = 32 - alac.setinfo_sample_size;

                        audiobits = ((audiobits << bitsmove) >> bitsmove);

                        alac.outputsamples_buffer_a[i] = audiobits;
                    }
                } else {
                    int x;
                    int m = 1 << (24 - 1);
                    for (int i = 0; i < outputsamples; i++) {
                        int audiobits;

                        audiobits = readbits(alac, 16);
                        /* special case of sign extension..
                         * as we'll be ORing the low 16bits into this */
                        audiobits = audiobits << (alac.setinfo_sample_size - 16);
                        audiobits = audiobits | readbits(alac, alac.setinfo_sample_size - 16);
                        x = audiobits & ((1 << 24) - 1);
                        audiobits = (x ^ m) - m;    // sign extend 24 bits

                        alac.outputsamples_buffer_a[i] = audiobits;
                    }
                }
                uncompressed_bytes = 0; // always 0 for uncompressed
            }

            switch (alac.setinfo_sample_size) {
                case 16: {

                    for (int i = 0; i < outputsamples; i++) {
                        int sample = alac.outputsamples_buffer_a[i];
                        outbuffer[i * alac.numchannels] = sample;

                        /*
                         ** We have to handle the case where the data is actually mono, but the stsd atom says it has 2 channels
                         ** in this case we create a stereo file where one of the channels is silent. If mono and 1 channel this value
                         ** will be overwritten in the next iteration
                         */

                        outbuffer[(i * alac.numchannels) + 1] = 0;
                    }
                    break;
                }
                case 24: {
                    for (int i = 0; i < outputsamples; i++) {
                        int sample = alac.outputsamples_buffer_a[i];

                        if (uncompressed_bytes != 0) {
                            sample = sample << (uncompressed_bytes * 8);
                            int mask = ~(0xFFFFFFFF << (uncompressed_bytes * 8));
                            sample = sample | (alac.uncompressed_bytes_buffer_a[i] & mask);
                        }

                        outbuffer[i * alac.numchannels * 3] = ((sample) & 0xFF);
                        outbuffer[i * alac.numchannels * 3 + 1] = ((sample >> 8) & 0xFF);
                        outbuffer[i * alac.numchannels * 3 + 2] = ((sample >> 16) & 0xFF);

                        /*
                         ** We have to handle the case where the data is actually mono, but the stsd atom says it has 2 channels
                         ** in this case we create a stereo file where one of the channels is silent. If mono and 1 channel this value
                         ** will be overwritten in the next iteration
                         */

                        outbuffer[i * alac.numchannels * 3 + 3] = 0;
                        outbuffer[i * alac.numchannels * 3 + 4] = 0;
                        outbuffer[i * alac.numchannels * 3 + 5] = 0;

                    }
                    break;
                }
                case 20:
                case 32:
                    System.err.println("FIXME: unimplemented sample size " + alac.setinfo_sample_size);
                default:

            }
        } else if (channels == 1) // 2 channels
        {

            int interlacing_shift;
            int interlacing_leftweight;

            /* 2^result = something to do with output waiting.
             * perhaps matters if we read > 1 frame in a pass?
             */
            readbits(alac, 4);

            readbits(alac, 12); // unknown, skip 12 bits

            int hassize = readbits(alac, 1); // the output sample size is stored soon

            int uncompressed_bytes = readbits(alac, 2); // the number of bytes in the (compressed) stream that are not compressed

            int isnotcompressed = readbits(alac, 1); // whether the frame is compressed

            if (hassize != 0) {
                /* now read the number of samples,
                 * as a 32bit integer */
                outputsamples = readbits(alac, 32);
                outputsize = outputsamples * alac.bytespersample;
            }

            int readsamplesize = alac.setinfo_sample_size - (uncompressed_bytes * 8) + 1;

            if (isnotcompressed == 0) { // compressed
                int[] predictor_coef_table_a = alac.predictor_coef_table_a;

                int[] predictor_coef_table_b = alac.predictor_coef_table_b;

                int tempPred;

                interlacing_shift = readbits(alac, 8);
                interlacing_leftweight = readbits(alac, 8);

                /******** channel 1 ***********/
                int prediction_type_a = readbits(alac, 4);
                int prediction_quantitization_a = readbits(alac, 4);

                int ricemodifier_a = readbits(alac, 3);
                int predictor_coef_num_a = readbits(alac, 5);

                /* read the predictor table */

                for (int i = 0; i < predictor_coef_num_a; i++) {
                    tempPred = readbits(alac, 16);
                    if (tempPred > 32767) {
                        // the predictor coef table values are only 16 bit signed
                        tempPred = tempPred - 65536;
                    }
                    predictor_coef_table_a[i] = tempPred;
                }

                /******** channel 2 *********/
                int prediction_type_b = readbits(alac, 4);
                int prediction_quantitization_b = readbits(alac, 4);

                int ricemodifier_b = readbits(alac, 3);
                int predictor_coef_num_b = readbits(alac, 5);

                /* read the predictor table */

                for (int i = 0; i < predictor_coef_num_b; i++) {
                    tempPred = readbits(alac, 16);
                    if (tempPred > 32767) {
                        // the predictor coef table values are only 16 bit signed
                        tempPred = tempPred - 65536;
                    }
                    predictor_coef_table_b[i] = tempPred;
                }

                /*********************/
                if (uncompressed_bytes != 0) { // see mono case
                    for (int i = 0; i < outputsamples; i++) {
                        alac.uncompressed_bytes_buffer_a[i] = readbits(alac, uncompressed_bytes * 8);
                        alac.uncompressed_bytes_buffer_b[i] = readbits(alac, uncompressed_bytes * 8);
                    }
                }

                /* channel 1 */

                another_duplicate_code(alac, outputsamples, readsamplesize, ricemodifier_a, predictor_coef_table_a, predictor_coef_num_a, prediction_type_a, prediction_quantitization_a);

                /* channel 2 */
                entropy_rice_decode(alac, alac.predicterror_buffer_b, outputsamples, readsamplesize, alac.setinfo_rice_initialhistory, alac.setinfo_rice_kmodifier, ricemodifier_b * (alac.setinfo_rice_historymult / 4), (1 << alac.setinfo_rice_kmodifier) - 1);

                if (prediction_type_b == 0) { // adaptive fir
                    alac.outputsamples_buffer_b = predictor_decompress_fir_adapt(alac.predicterror_buffer_b, outputsamples, readsamplesize, predictor_coef_table_b, predictor_coef_num_b, prediction_quantitization_b);
                } else {
                    System.err.println("FIXME: unhandled predicition type: " + prediction_type_b);
                }
            } else { // not compressed, easy case
                if (alac.setinfo_sample_size <= 16) {
                    int bitsmove;

                    for (int i = 0; i < outputsamples; i++) {

                        int audiobits_a = readbits(alac, alac.setinfo_sample_size);
                        int audiobits_b = readbits(alac, alac.setinfo_sample_size);

                        bitsmove = 32 - alac.setinfo_sample_size;

                        audiobits_a = ((audiobits_a << bitsmove) >> bitsmove);
                        audiobits_b = ((audiobits_b << bitsmove) >> bitsmove);

                        alac.outputsamples_buffer_a[i] = audiobits_a;
                        alac.outputsamples_buffer_b[i] = audiobits_b;
                    }
                } else {
                    int x;
                    int m = 1 << (24 - 1);

                    for (int i = 0; i < outputsamples; i++) {
                        int audiobits_a;
                        int audiobits_b;

                        audiobits_a = readbits(alac, 16);
                        audiobits_a = audiobits_a << (alac.setinfo_sample_size - 16);
                        audiobits_a = audiobits_a | readbits(alac, alac.setinfo_sample_size - 16);
                        x = audiobits_a & ((1 << 24) - 1);
                        audiobits_a = (x ^ m) - m;        // sign extend 24 bits

                        audiobits_b = readbits(alac, 16);
                        audiobits_b = audiobits_b << (alac.setinfo_sample_size - 16);
                        audiobits_b = audiobits_b | readbits(alac, alac.setinfo_sample_size - 16);
                        x = audiobits_b & ((1 << 24) - 1);
                        audiobits_b = (x ^ m) - m;        // sign extend 24 bits

                        alac.outputsamples_buffer_a[i] = audiobits_a;
                        alac.outputsamples_buffer_b[i] = audiobits_b;
                    }
                }
                uncompressed_bytes = 0; // always 0 for uncompressed
                interlacing_shift = 0;
                interlacing_leftweight = 0;
            }

            switch (alac.setinfo_sample_size) {
                case 16: {
                    deinterlace_16(alac.outputsamples_buffer_a, alac.outputsamples_buffer_b, outbuffer, alac.numchannels, outputsamples, interlacing_shift, interlacing_leftweight);
                    break;
                }
                case 24: {
                    deinterlace_24(alac.outputsamples_buffer_a, alac.outputsamples_buffer_b, uncompressed_bytes, alac.uncompressed_bytes_buffer_a, alac.uncompressed_bytes_buffer_b, outbuffer, alac.numchannels, outputsamples, interlacing_shift, interlacing_leftweight);
                    break;
                }
                case 20:
                case 32:
                    System.err.println("FIXME: unimplemented sample size " + alac.setinfo_sample_size);

                default:

            }
        }
        return outputsize;
    }

    private static void another_duplicate_code(AlacFileData alac, int outputsamples, int readsamplesize, int ricemodifier, int[] predictor_coef_table, int predictor_coef_num, int prediction_type, int prediction_quantitization) {
        entropy_rice_decode(alac, alac.predicterror_buffer_a, outputsamples, readsamplesize, alac.setinfo_rice_initialhistory, alac.setinfo_rice_kmodifier, ricemodifier * (alac.setinfo_rice_historymult / 4), (1 << alac.setinfo_rice_kmodifier) - 1);

        if (prediction_type == 0) { // adaptive fir
            alac.outputsamples_buffer_a = predictor_decompress_fir_adapt(alac.predicterror_buffer_a, outputsamples, readsamplesize, predictor_coef_table, predictor_coef_num, prediction_quantitization);
        } else {
            System.err.println("FIXME: unhandled predicition type: " + prediction_type);

            /* i think the only other prediction type (or perhaps this is just a
             * boolean?) runs adaptive fir twice.. like:
             * predictor_decompress_fir_adapt(predictor_error, tempout, ...)
             * predictor_decompress_fir_adapt(predictor_error, outputsamples ...)
             * little strange..
             */
        }
    }

    public static AlacFileData create_alac(int samplesize, int numchannels) {
        AlacFileData newfile = new AlacFileData();

        newfile.samplesize = samplesize;
        newfile.numchannels = numchannels;
        newfile.bytespersample = (samplesize / 8) * numchannels;

        return newfile;
    }
}

