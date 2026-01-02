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

package com.github.curiousoddman.alacdecoder.stream;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.io.DataInputStream;
import java.io.IOException;

@Getter
@Setter
@RequiredArgsConstructor
public class DataInputStreamWrapper {
    private final DataInputStream stream;
    private final byte[] readBuf = new byte[8];

    private int currentPos = 0;

    public void read(int size, int[] buf, int startPos) throws IOException {
        byte[] byteBuf = new byte[size];
        int bytesRead = read(size, byteBuf, 0);
        for (int i = 0; i < bytesRead; i++) {
            buf[startPos + i] = byteBuf[i];
        }
    }

    public int read(int size, byte[] buf, int startPos) throws IOException {
        int bytesRead = stream.read(buf, startPos, size);
        currentPos += bytesRead;
        return bytesRead;
    }

    public int readUint32() throws IOException {
        int bytesRead = stream.read(readBuf, 0, 4);
        currentPos += bytesRead;
        int tmp = (readBuf[0] & 0xff);

        int v = tmp << 24;
        tmp = (readBuf[1] & 0xff);

        v = v | (tmp << 16);
        tmp = (readBuf[2] & 0xff);

        v = v | (tmp << 8);

        tmp = (readBuf[3] & 0xff);
        v = v | tmp;

        return v;
    }

    public int readUint16() throws IOException {
        int bytesRead = stream.read(readBuf, 0, 2);
        currentPos += bytesRead;
        int tmp = (readBuf[0] & 0xff);
        int v = tmp << 8;
        tmp = (readBuf[1] & 0xff);
        v = v | tmp;
        return v;
    }

    public void skip(int skip) throws IOException {
        if (skip < 0) {
            throw new IOException("Cannot skip backwards...");
        }

        currentPos += stream.skipBytes(skip);
    }
}