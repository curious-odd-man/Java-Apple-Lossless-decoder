/*
 ** QTMovieT.java
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
public class QTMovieT {
    private DataInputStreamWrapper qtstream;
    private DemuxResT res;
    private int savedMdatPos;

    public QTMovieT(DataInputStreamWrapper qtstream) {
        savedMdatPos = 0;
        this.qtstream = qtstream;
    }
}