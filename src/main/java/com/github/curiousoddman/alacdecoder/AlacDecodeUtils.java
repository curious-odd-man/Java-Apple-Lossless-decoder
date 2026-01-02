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

import com.github.curiousoddman.alacdecoder.data.LeadingZeros;

public class AlacDecodeUtils {
    public static void countLeadingZerosExtra(int curbyte, int output, LeadingZeros lz) {

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

    public static int countLeadingZeros(int input, LeadingZeros lz) {
        int output = 0;

        int curbyte = input >> 24;
        if (curbyte != 0) {
            countLeadingZerosExtra(curbyte, output, lz);
            output = lz.getOutput();
            return output;
        }
        output += 8;

        curbyte = input >> 16;
        if ((curbyte & 0xFF) != 0) {
            countLeadingZerosExtra(curbyte, output, lz);
            output = lz.getOutput();

            return output;
        }
        output += 8;

        curbyte = input >> 8;
        if ((curbyte & 0xFF) != 0) {
            countLeadingZerosExtra(curbyte, output, lz);
            output = lz.getOutput();

            return output;
        }
        output += 8;

        curbyte = input;
        if ((curbyte & 0xFF) != 0) {
            countLeadingZerosExtra(curbyte, output, lz);
            output = lz.getOutput();

            return output;
        }
        output += 8;

        return output;
    }

    public static int[] predictorDecompressFirAdapt(int[] buffer_out, int output_size, int readsamplesize, int[] predictor_coef_table, int predictor_coef_num, int predictor_quantitization) {

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

    public static void deinterlace16(int[] buffer_a, int[] buffer_b, int[] buffer_out, int numchannels, int numsamples, int interlacing_shift, int interlacing_leftweight) {

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

    public static void deinterlace24(int[] buffer_a, int[] buffer_b, int uncompressed_bytes, int[] uncompressed_bytes_buffer_a, int[] uncompressed_bytes_buffer_b, int[] buffer_out, int numchannels, int numsamples, int interlacing_shift, int interlacing_leftweight) {
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

                extractedDuplicateCode(uncompressed_bytes, uncompressed_bytes_buffer_a, uncompressed_bytes_buffer_b, buffer_out, numchannels, i, left, right);
            }

            return;
        }

        /* otherwise basic interlacing took place */
        for (int i = 0; i < numsamples; i++) {

            int left = buffer_a[i];
            int right = buffer_b[i];

            extractedDuplicateCode(uncompressed_bytes, uncompressed_bytes_buffer_a, uncompressed_bytes_buffer_b, buffer_out, numchannels, i, left, right);
        }
    }

    private static void extractedDuplicateCode(int uncompressed_bytes, int[] uncompressed_bytes_buffer_a, int[] uncompressed_bytes_buffer_b, int[] buffer_out, int numchannels, int i, int left, int right) {
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
}

