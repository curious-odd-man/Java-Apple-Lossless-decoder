/*
 ** AlacContext.java
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
public class AlacContext {
    private DemuxResT demux_res = new DemuxResT();
    private AlacFile alac = new AlacFile();
    private AlacInputStream alacInputStream;
    private int currentSampleBlock = 0;
    private int offset;
    private byte[] readBuffer = new byte[1024 * 80]; // sample big enough to hold any input for a single alac frame
}