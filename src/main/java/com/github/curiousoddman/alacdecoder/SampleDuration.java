/*
 ** SampleDuration.java
 **
 ** Copyright (c) 2011 Peter McQuillan
 **
 ** All Rights Reserved.
 **
 ** Distributed under the BSD Software License (see license.txt)
 **
 */

package com.github.curiousoddman.alacdecoder;

import lombok.Data;

@Data
public class SampleDuration {
    private int sampleByteSize = 0;
    private int sampleDuration = 0;
}
