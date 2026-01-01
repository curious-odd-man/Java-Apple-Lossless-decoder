/*
 ** MyStream.java
 **
 ** Copyright (c) 2011 Peter McQuillan
 **
 ** All Rights Reserved.
 **
 ** Distributed under the BSD Software License (see license.txt)
 **
 */

package com.github.curiousoddman.alacdecoder;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.io.DataInputStream;

@Getter
@Setter
@RequiredArgsConstructor
public class DataInputStreamWrapper {
    private final DataInputStream stream;

    private final byte[] readBuf = new byte[8];

    private int currentPos = 0;
}