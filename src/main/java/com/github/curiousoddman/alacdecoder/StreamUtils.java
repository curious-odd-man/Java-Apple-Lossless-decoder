/*
 ** StreamUtils.java
 **
 ** Copyright (c) 2011 Peter McQuillan
 **
 ** All Rights Reserved.
 **
 ** Distributed under the BSD Software License (see license.txt)
 **
 */

package com.github.curiousoddman.alacdecoder;

import java.io.IOException;

class StreamUtils {
    public static void streamRead(DataInputStreamWrapper mystream, int size, int[] buf, int startPos) throws IOException {
        byte[] byteBuf = new byte[size];
        int bytesRead = streamRead(mystream, size, byteBuf, 0);
        for (int i = 0; i < bytesRead; i++) {
            buf[startPos + i] = byteBuf[i];
        }
    }

    public static int streamRead(DataInputStreamWrapper mystream, int size, byte[] buf, int startPos) throws IOException {
        int bytesRead = mystream.getStream().read(buf, startPos, size);
        mystream.setCurrentPos(mystream.getCurrentPos() + bytesRead);
        return bytesRead;
    }

    public static int streamReadUint32(DataInputStreamWrapper mystream) throws IOException {
        byte[] bytebuf = mystream.getReadBuf();

        int bytesRead = mystream.getStream().read(bytebuf, 0, 4);
        mystream.setCurrentPos(mystream.getCurrentPos() + bytesRead);
        int tmp = (bytebuf[0] & 0xff);

        int v = tmp << 24;
        tmp = (bytebuf[1] & 0xff);

        v = v | (tmp << 16);
        tmp = (bytebuf[2] & 0xff);

        v = v | (tmp << 8);

        tmp = (bytebuf[3] & 0xff);
        v = v | tmp;

        return v;
    }

    public static int streamReadUint16(DataInputStreamWrapper mystream) throws IOException {
        byte[] bytebuf = mystream.getReadBuf();
        int bytesRead = mystream.getStream().read(bytebuf, 0, 2);
        mystream.setCurrentPos(mystream.getCurrentPos() + bytesRead);
        int tmp = (bytebuf[0] & 0xff);
        int v = tmp << 8;
        tmp = (bytebuf[1] & 0xff);
        v = v | tmp;
        return v;
    }

    public static int streamReadUint8(DataInputStreamWrapper mystream) throws IOException {
        byte[] bytebuf = mystream.getReadBuf();
        mystream.getStream().read(bytebuf, 0, 1);
        int v = (bytebuf[0] & 0xff);
        mystream.setCurrentPos(mystream.getCurrentPos() + 1);
        return v;
    }

    public static void streamSkip(DataInputStreamWrapper mystream, int skip) throws IOException {
        if (skip < 0) {
            throw new IOException("Cannot skip backwards...");
        }

        int bytesRead = mystream.getStream().skipBytes(skip);
        mystream.setCurrentPos(mystream.getCurrentPos() + bytesRead);
    }

    public static int stream_tell(DataInputStreamWrapper mystream) {
        return mystream.getCurrentPos();
    }
}

