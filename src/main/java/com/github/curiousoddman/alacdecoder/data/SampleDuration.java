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

package com.github.curiousoddman.alacdecoder.data;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SampleDuration {
    private int sampleDuration;
    private int sampleByteSize;
}
