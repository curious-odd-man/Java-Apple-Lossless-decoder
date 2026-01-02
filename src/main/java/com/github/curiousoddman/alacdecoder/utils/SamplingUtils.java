package com.github.curiousoddman.alacdecoder.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class SamplingUtils {
    public static byte[] formatSamples(int bps, int[] src, int samcnt) {
        int counter = 0;
        int counter2 = 0;
        byte[] dst = new byte[65536];

        switch (bps) {
            case 1:
                while (samcnt > 0) {
                    dst[counter] = (byte) (0x00FF & (src[counter] + 128));
                    counter++;
                    samcnt--;
                }
                break;

            case 2:
                int temp = 0;
                while (samcnt > 0) {
                    temp = src[counter2];
                    dst[counter] = (byte) temp;
                    counter++;
                    dst[counter] = (byte) (temp >>> 8);
                    counter++;
                    counter2++;
                    samcnt = samcnt - 2;
                }
                break;

            case 3:
                while (samcnt > 0) {
                    dst[counter] = (byte) src[counter2];
                    counter++;
                    counter2++;
                    samcnt--;
                }
                break;
        }

        return dst;
    }
}
