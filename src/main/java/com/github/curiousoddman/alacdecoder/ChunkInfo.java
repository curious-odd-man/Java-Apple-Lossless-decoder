package com.github.curiousoddman.alacdecoder;

import lombok.Data;

/**
 * Author: Denis Tulskiy
 * Date: 4/9/11
 */
@Data
public class ChunkInfo {
    private int firstChunk;
    private int samplesPerChunk;
    private int sampleDescIndex;
}
