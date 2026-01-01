/*
 ** SampleInfo.java
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

@Data
public class SampleInfo {
    private int sampleCount = 0;
    private int sampleDuration = 0;
}