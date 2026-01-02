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
import com.github.curiousoddman.alacdecoder.data.LeadingZeros;

public class AlacDecodeUtils {
    public static void count_leading_zeros_extra(int curbyte, int output, LeadingZeros lz) {

        if ((curbyte & 0xf0) == 0) {
            output += 4;
        } else {
            curbyte = curbyte >> 4;
        }

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

    public static int count_leading_zeros(int input, LeadingZeros lz) {
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

    static int[] predictor_decompress_fir_adapt(int[] buffer_out, int output_size, int readsamplesize, int[] predictor_coef_table, int predictor_coef_num, int predictor_quantitization) {

        /* first sample always copies */

        if (predictor_coef_num == 0) {
            if (output_size <= 1) {
                return buffer_out;
            }
            int sizeToCopy = (output_size - 1) * 4;
            System.arraycopy(buffer_out, 1, buffer_out, 1, sizeToCopy);
            return buffer_out;
        }

        int bitsmove;
        if (predictor_coef_num == 0x1f) // 11111 - max value of predictor_coef_num
        {
            /* second-best case scenario for fir decompression,
             * error describes a small difference from the previous sample only
             */
            if (output_size <= 1) {
                return buffer_out;
            }

            for (int i = 0; i < output_size - 1; i++) {

                int prev_value = buffer_out[i];
                int error_value = buffer_out[i + 1];

                bitsmove = 32 - readsamplesize;
                buffer_out[i + 1] = prev_value + error_value << bitsmove >> bitsmove;
            }
            return buffer_out;
        }

        /* read warm-up samples */
        if (predictor_coef_num > 0) {
            for (int i = 0; i < predictor_coef_num; i++) {

                int val = buffer_out[i] + buffer_out[i + 1];

                bitsmove = 32 - readsamplesize;

                val = val << bitsmove >> bitsmove;

                buffer_out[i + 1] = val;
            }
        }

        /* general case */
        if (predictor_coef_num > 0) {
            int buffer_out_idx = 0;
            for (int i = predictor_coef_num + 1; i < output_size; i++) {
                int sum = 0;
                int error_val = buffer_out[i];

                for (int j = 0; j < predictor_coef_num; j++) {
                    sum += (buffer_out[buffer_out_idx + predictor_coef_num - j] - buffer_out[buffer_out_idx]) * predictor_coef_table[j];
                }

                int outval = (1 << predictor_quantitization - 1) + sum;
                outval = outval >> predictor_quantitization;
                outval = outval + buffer_out[buffer_out_idx] + error_val;
                bitsmove = 32 - readsamplesize;

                outval = outval << bitsmove >> bitsmove;

                buffer_out[buffer_out_idx + predictor_coef_num + 1] = outval;

                if (error_val > 0) {
                    int predictor_num = predictor_coef_num - 1;

                    while (predictor_num >= 0 && error_val > 0) {
                        int val = buffer_out[buffer_out_idx] - buffer_out[buffer_out_idx + predictor_coef_num - predictor_num];
                        int sign = Integer.compare(val, 0);

                        predictor_coef_table[predictor_num] -= sign;

                        val *= sign; // absolute value

                        error_val -= (val >> predictor_quantitization) * (predictor_coef_num - predictor_num);

                        predictor_num--;
                    }
                } else if (error_val < 0) {
                    int predictor_num = predictor_coef_num - 1;

                    while (predictor_num >= 0 && error_val < 0) {
                        int val = buffer_out[buffer_out_idx] - buffer_out[buffer_out_idx + predictor_coef_num - predictor_num];
                        int sign = -Integer.compare(val, 0);

                        predictor_coef_table[predictor_num] -= sign;

                        val *= sign; // neg value

                        error_val -= (val >> predictor_quantitization) * (predictor_coef_num - predictor_num);

                        predictor_num--;
                    }
                }

                buffer_out_idx++;
            }
        }
        return buffer_out;
    }


    public static void deinterlace_16(int[] buffer_a, int[] buffer_b, int[] buffer_out, int numchannels, int numsamples, int interlacing_shift, int interlacing_leftweight) {

        if (numsamples <= 0) {
            return;
        }

        /* weighted interlacing */
        if (0 != interlacing_leftweight) {
            for (int i = 0; i < numsamples; i++) {

                int midright = buffer_a[i];
                int difference = buffer_b[i];

                int right = midright - (difference * interlacing_leftweight >> interlacing_shift);
                int left = right + difference;

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
        if (numsamples <= 0) {
            return;
        }

        /* weighted interlacing */
        if (interlacing_leftweight != 0) {
            for (int i = 0; i < numsamples; i++) {

                int midright = buffer_a[i];
                int difference = buffer_b[i];

                int right = midright - (difference * interlacing_leftweight >> interlacing_shift);
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
            int mask = ~(0xFFFFFFFF << uncompressed_bytes * 8);
            left <<= uncompressed_bytes * 8;
            right <<= uncompressed_bytes * 8;

            left = left | uncompressed_bytes_buffer_a[i] & mask;
            right = right | uncompressed_bytes_buffer_b[i] & mask;
        }

        buffer_out[i * numchannels * 3] = left & 0xFF;
        buffer_out[i * numchannels * 3 + 1] = left >> 8 & 0xFF;
        buffer_out[i * numchannels * 3 + 2] = left >> 16 & 0xFF;

        buffer_out[i * numchannels * 3 + 3] = right & 0xFF;
        buffer_out[i * numchannels * 3 + 4] = right >> 8 & 0xFF;
        buffer_out[i * numchannels * 3 + 5] = right >> 16 & 0xFF;
    }

    public static int decodeFrame(AlacFileData alac, byte[] inbuffer, int[] outbuffer) {
        int outputsamples = alac.getMaxSamplesPerFrame();

        /* setup the stream */
        alac.setInputBuffer(inbuffer);
        alac.setInputBufferBitAccumulator(0);
        alac.setIbIdx(0);

        int channels = alac.readbits(3);

        int outputsize = outputsamples * alac.getBytesPerSample();

        if (channels == 0) // 1 channel
        {
            /* 2^result = something to do with output waiting.
             * perhaps matters if we read > 1 frame in a pass?
             */
            alac.readbits(4);

            alac.readbits(12); // unknown, skip 12 bits

            int hassize = alac.readbits(1); // the output sample size is stored soon

            int uncompressed_bytes = alac.readbits(2); // number of bytes in the (compressed) stream that are not compressed

            int isnotcompressed = alac.readbits(1); // whether the frame is compressed

            if (hassize != 0) {
                /* now read the number of samples,
                 * as a 32bit integer */
                outputsamples = alac.readbits(32);
                outputsize = outputsamples * alac.getBytesPerSample();
            }

            int readsamplesize = alac.getSampleSizeRaw() - uncompressed_bytes * 8;

            if (isnotcompressed == 0) { // so it is compressed
                int[] predictor_coef_table = alac.getPredictorCoefTable();

                /* skip 16 bits, not sure what they are. seem to be used in
                 * two channel case */
                alac.readbits(8);
                alac.readbits(8);

                int prediction_type = alac.readbits(4);
                int prediction_quantitization = alac.readbits(4);

                int ricemodifier = alac.readbits(3);
                int predictor_coef_num = alac.readbits(5);

                /* read the predictor table */

                int i;
                for (i = 0; i < predictor_coef_num; i++) {
                    int tempPred = alac.readbits(16);
                    if (tempPred > 32767) {
                        // the predictor coef table values are only 16 bit signed
                        tempPred = tempPred - 65536;
                    }

                    predictor_coef_table[i] = tempPred;
                }

                if (uncompressed_bytes != 0) {
                    for (i = 0; i < outputsamples; i++) {
                        alac.getUncompressedBytesBufferA()[i] = alac.readbits(uncompressed_bytes * 8);
                    }
                }


                alac.entropy_rice_decode(alac.getPredictErrorBufferA(), outputsamples, readsamplesize, alac.getRiceInitialhistory(), alac.getRiceKmodifier(), ricemodifier * (alac.getRiceHistorymult() / 4), (1 << alac.getRiceKmodifier()) - 1);

                if (prediction_type == 0) { // adaptive fir
                    alac.setOutputSamplesBufferA(predictor_decompress_fir_adapt(alac.getPredictErrorBufferA(), outputsamples, readsamplesize, predictor_coef_table, predictor_coef_num, prediction_quantitization));
                } else {
                    System.err.println("FIXME: unhandled predicition type: " + prediction_type);

                    /* i think the only other prediction type (or perhaps this is just a
                     * boolean?) runs adaptive fir twice.. like:
                     * predictor_decompress_fir_adapt(predictor_error, tempout, ...)
                     * predictor_decompress_fir_adapt(predictor_error, outputsamples ...)
                     * little strange..
                     */
                }

            } else { // not compressed, easy case
                if (alac.getSampleSizeRaw() <= 16) {
                    for (int i = 0; i < outputsamples; i++) {
                        int audiobits = alac.readbits(alac.getSampleSizeRaw());
                        int bitsmove = 32 - alac.getSampleSizeRaw();

                        audiobits = audiobits << bitsmove >> bitsmove;

                        alac.getOutputSamplesBufferA()[i] = audiobits;
                    }
                } else {
                    int m = 1 << 24 - 1;
                    for (int i = 0; i < outputsamples; i++) {

                        int audiobits = getAudiobits(alac);
                        int x = audiobits & (1 << 24) - 1;
                        audiobits = (x ^ m) - m;    // sign extend 24 bits

                        alac.getOutputSamplesBufferA()[i] = audiobits;
                    }
                }
                uncompressed_bytes = 0; // always 0 for uncompressed
            }

            switch (alac.getSampleSizeRaw()) {
                case 16: {

                    for (int i = 0; i < outputsamples; i++) {
                        int sample = alac.getOutputSamplesBufferA()[i];
                        outbuffer[i * alac.getNumChannels()] = sample;

                        /*
                         ** We have to handle the case where the data is actually mono, but the stsd atom says it has 2 channels
                         ** in this case we create a stereo file where one of the channels is silent. If mono and 1 channel this value
                         ** will be overwritten in the next iteration
                         */

                        outbuffer[i * alac.getNumChannels() + 1] = 0;
                    }
                    break;
                }
                case 24: {
                    for (int i = 0; i < outputsamples; i++) {
                        int sample = alac.getOutputSamplesBufferA()[i];

                        if (uncompressed_bytes != 0) {
                            sample = sample << uncompressed_bytes * 8;
                            int mask = ~(0xFFFFFFFF << uncompressed_bytes * 8);
                            sample = sample | alac.getUncompressedBytesBufferA()[i] & mask;
                        }

                        outbuffer[i * alac.getNumChannels() * 3] = sample & 0xFF;
                        outbuffer[i * alac.getNumChannels() * 3 + 1] = sample >> 8 & 0xFF;
                        outbuffer[i * alac.getNumChannels() * 3 + 2] = sample >> 16 & 0xFF;

                        /*
                         ** We have to handle the case where the data is actually mono, but the stsd atom says it has 2 channels
                         ** in this case we create a stereo file where one of the channels is silent. If mono and 1 channel this value
                         ** will be overwritten in the next iteration
                         */

                        outbuffer[i * alac.getNumChannels() * 3 + 3] = 0;
                        outbuffer[i * alac.getNumChannels() * 3 + 4] = 0;
                        outbuffer[i * alac.getNumChannels() * 3 + 5] = 0;

                    }
                    break;
                }
                case 20:
                case 32:
                    System.err.println("FIXME: unimplemented sample size " + alac.getSampleSizeRaw());
                default:

            }
        } else if (channels == 1) // 2 channels
        {

            /* 2^result = something to do with output waiting.
             * perhaps matters if we read > 1 frame in a pass?
             */
            alac.readbits(4);

            alac.readbits(12); // unknown, skip 12 bits

            int hassize = alac.readbits(1); // the output sample size is stored soon

            int uncompressed_bytes = alac.readbits(2); // the number of bytes in the (compressed) stream that are not compressed

            int isnotcompressed = alac.readbits(1); // whether the frame is compressed

            if (hassize != 0) {
                /* now read the number of samples,
                 * as a 32bit integer */
                outputsamples = alac.readbits(32);
                outputsize = outputsamples * alac.getBytesPerSample();
            }

            int readsamplesize = alac.getSampleSizeRaw() - uncompressed_bytes * 8 + 1;

            int interlacing_leftweight;
            int interlacing_shift;
            if (isnotcompressed == 0) { // compressed
                int[] predictor_coef_table_a = alac.getPredictorCoefTableA();

                int[] predictor_coef_table_b = alac.getPredictorCoefTableB();

                interlacing_shift = alac.readbits(8);
                interlacing_leftweight = alac.readbits(8);

                /* ******* channel 1 ***********/
                int prediction_type_a = alac.readbits(4);
                int prediction_quantitization_a = alac.readbits(4);

                int ricemodifier_a = alac.readbits(3);
                int predictor_coef_num_a = alac.readbits(5);

                /* read the predictor table */

                int tempPred;
                for (int i = 0; i < predictor_coef_num_a; i++) {
                    tempPred = alac.readbits(16);
                    if (tempPred > 32767) {
                        // the predictor coef table values are only 16 bit signed
                        tempPred = tempPred - 65536;
                    }
                    predictor_coef_table_a[i] = tempPred;
                }

                /* ******* channel 2 *********/
                int prediction_type_b = alac.readbits(4);
                int prediction_quantitization_b = alac.readbits(4);

                int ricemodifier_b = alac.readbits(3);
                int predictor_coef_num_b = alac.readbits(5);

                /* read the predictor table */

                for (int i = 0; i < predictor_coef_num_b; i++) {
                    tempPred = alac.readbits(16);
                    if (tempPred > 32767) {
                        // the predictor coef table values are only 16 bit signed
                        tempPred = tempPred - 65536;
                    }
                    predictor_coef_table_b[i] = tempPred;
                }

                /* ********************/
                if (uncompressed_bytes != 0) { // see mono case
                    for (int i = 0; i < outputsamples; i++) {
                        alac.getUncompressedBytesBufferA()[i] = alac.readbits(uncompressed_bytes * 8);
                        alac.getUncompressedBytesBufferB()[i] = alac.readbits(uncompressed_bytes * 8);
                    }
                }

                /* channel 1 */

                alac.entropy_rice_decode(alac.getPredictErrorBufferA(), outputsamples, readsamplesize, alac.getRiceInitialhistory(), alac.getRiceKmodifier(), ricemodifier_a * (alac.getRiceHistorymult() / 4), (1 << alac.getRiceKmodifier()) - 1);

                if (prediction_type_a == 0) { // adaptive fir

                    alac.setOutputSamplesBufferA(predictor_decompress_fir_adapt(alac.getPredictErrorBufferA(), outputsamples, readsamplesize, predictor_coef_table_a, predictor_coef_num_a, prediction_quantitization_a));

                } else { // see mono case
                    System.err.println("FIXME: unhandled predicition type: " + prediction_type_a);
                }

                /* channel 2 */
                alac.entropy_rice_decode(alac.getPredictErrorBufferB(), outputsamples, readsamplesize, alac.getRiceInitialhistory(), alac.getRiceKmodifier(), ricemodifier_b * (alac.getRiceHistorymult() / 4), (1 << alac.getRiceKmodifier()) - 1);

                if (prediction_type_b == 0) { // adaptive fir
                    alac.setOutputSamplesBufferB(predictor_decompress_fir_adapt(alac.getPredictErrorBufferB(), outputsamples, readsamplesize, predictor_coef_table_b, predictor_coef_num_b, prediction_quantitization_b));
                } else {
                    System.err.println("FIXME: unhandled predicition type: " + prediction_type_b);
                }
            } else { // not compressed, easy case
                if (alac.getSampleSizeRaw() <= 16) {

                    for (int i = 0; i < outputsamples; i++) {

                        int audiobits_a = alac.readbits(alac.getSampleSizeRaw());
                        int audiobits_b = alac.readbits(alac.getSampleSizeRaw());

                        int bitsmove = 32 - alac.getSampleSizeRaw();

                        audiobits_a = audiobits_a << bitsmove >> bitsmove;
                        audiobits_b = audiobits_b << bitsmove >> bitsmove;

                        alac.getOutputSamplesBufferA()[i] = audiobits_a;
                        alac.getOutputSamplesBufferB()[i] = audiobits_b;
                    }
                } else {
                    int m = 1 << 24 - 1;

                    for (int i = 0; i < outputsamples; i++) {

                        int audiobits_a = getAudiobits(alac);
                        int x = audiobits_a & (1 << 24) - 1;
                        audiobits_a = (x ^ m) - m;        // sign extend 24 bits

                        int audiobits_b = getAudiobits(alac);
                        x = audiobits_b & (1 << 24) - 1;
                        audiobits_b = (x ^ m) - m;        // sign extend 24 bits

                        alac.getOutputSamplesBufferA()[i] = audiobits_a;
                        alac.getOutputSamplesBufferB()[i] = audiobits_b;
                    }
                }
                uncompressed_bytes = 0; // always 0 for uncompressed
                interlacing_shift = 0;
                interlacing_leftweight = 0;
            }

            switch (alac.getSampleSizeRaw()) {
                case 16: {
                    deinterlace_16(alac.getOutputSamplesBufferA(), alac.getOutputSamplesBufferB(), outbuffer, alac.getNumChannels(), outputsamples, interlacing_shift, interlacing_leftweight);
                    break;
                }
                case 24: {
                    deinterlace_24(alac.getOutputSamplesBufferA(), alac.getOutputSamplesBufferB(), uncompressed_bytes, alac.getUncompressedBytesBufferA(), alac.getUncompressedBytesBufferB(), outbuffer, alac.getNumChannels(), outputsamples, interlacing_shift, interlacing_leftweight);
                    break;
                }
                case 20:
                case 32:
                    System.err.println("FIXME: unimplemented sample size " + alac.getSampleSizeRaw());

                default:

            }
        }
        return outputsize;
    }

    private static int getAudiobits(AlacFileData alac) {
        int audiobits = alac.readbits(16);
        /* special case of sign extension..
         * as we'll be ORing the low 16bits into this */
        audiobits = audiobits << alac.getSampleSizeRaw() - 16;
        audiobits = audiobits | alac.readbits(alac.getSampleSizeRaw() - 16);
        return audiobits;
    }
}

