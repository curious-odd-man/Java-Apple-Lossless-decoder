/*
 ** DemuxUtils.java
 **
 ** Copyright (c) 2011-2014 Peter McQuillan
 **
 ** All Rights Reserved.
 **
 ** Distributed under the BSD Software License (see license.txt)
 **
 */

package com.github.curiousoddman.alacdecoder;


public class DemuxUtils {
    public static int makeFourCC(int ch0, int ch1, int ch2, int ch3) {
        return (((ch0) << 24) | ((ch1) << 16) | ((ch2) << 8) | ((ch3)));
    }

    public static String splitFourCC(int code) {
        char c1 = (char) ((code >> 24) & 0xFF);
        char c2 = (char) ((code >> 16) & 0xFF);
        char c3 = (char) ((code >> 8) & 0xFF);
        char c4 = (char) (code & 0xFF);

        return c1 + " " + c2 + " " + c3 + " " + c4;
    }
}


