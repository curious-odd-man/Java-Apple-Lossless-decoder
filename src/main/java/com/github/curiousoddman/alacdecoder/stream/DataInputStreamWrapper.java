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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Getter
@Setter
@RequiredArgsConstructor
public class DataInputStreamWrapper {
    private final InputStream stream;
    private final long fileSize; // total size of the underlying file/stream
    private final byte[] readBuf = new byte[8];

    private long currentPos = 0;

    public void seek(long absolutePos) throws IOException {
        if (!(stream instanceof FileInputStream fis)) {
            throw new IOException("Seek not supported on this stream type");
        }
        fis.getChannel().position(absolutePos);
        currentPos = absolutePos;
    }

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
        int tmp = readBuf[0] & 0xff;

        int v = tmp << 24;
        tmp = readBuf[1] & 0xff;

        v = v | tmp << 16;
        tmp = readBuf[2] & 0xff;

        v = v | tmp << 8;

        tmp = readBuf[3] & 0xff;
        v = v | tmp;

        return v;
    }

    public int readUint16() throws IOException {
        int bytesRead = stream.read(readBuf, 0, 2);
        currentPos += bytesRead;
        int tmp = readBuf[0] & 0xff;
        int v = tmp << 8;
        tmp = readBuf[1] & 0xff;
        v = v | tmp;
        return v;
    }

    public void skip(long skip) throws IOException {
        if (skip < 0) {
            throw new IOException("Cannot skip backwards...");
        }

        stream.skipNBytes(skip);
        currentPos += skip;
    }

    public long getRemainingBytes() {
        return fileSize - currentPos;
    }

    public void close() throws IOException {
        stream.close();
    }
}