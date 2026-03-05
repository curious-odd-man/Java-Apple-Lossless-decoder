package com.github.curiousoddman.alacdecoder.utils;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ByteArrayReader {
    private final byte[] inputBuffer;

    private int inputBufferIndex = 0;
    private int inputBufferBitAccumulator = 0; // for arbitrary bit reads

    /* supports reading 1 to 24 bits, in big endian format */
    public int readBits24(int bits) {
        if (bits <= 0 || bits > 24) {
            throw new IllegalArgumentException("bits must be 1–24, got " + bits);
        }
        int result = read3Bytes();

        /* shift left by the number of bits we've already read,
         * so that the top 'n' bits of the 24 bits we read will
         * be the return bits */
        /* and then only want the top 'n' bits from that, where
         * n is 'bits' */
        result = (result << (inputBufferBitAccumulator + 8)) >>> (32 - bits);

        int newAccumulator = inputBufferBitAccumulator + bits;

        /* increase the buffer pointer if we've read over n bytes. */
        inputBufferIndex += newAccumulator >> 3;
        /* and the remainder goes back into the bit accumulator */
        inputBufferBitAccumulator = newAccumulator & 7;
        return result;
    }

    public int readBitsVar(int bits) {
        int result = 0;

        if (bits > 16) {
            bits -= 16;
            result = readBits24(16) << bits;
        }

        result |= readBits24(bits);
        return result;
    }

    /* supports reading 24 to 32 bits, in big endian format */
    public int readBits32(int bits) {
        if (bits <= 24 || bits > 32) {
            throw new IllegalArgumentException("bits must be 25–32, got " + bits);
        }
        return readBitsVar(bits);
    }

    /* reads a single bit */
    public int readBit() {
        int result = inputBuffer[inputBufferIndex] & 0xff;
        result = result << inputBufferBitAccumulator;
        result = result >> 7 & 1;

        int newAccumulator = inputBufferBitAccumulator + 1;
        inputBufferIndex += newAccumulator / 8;
        inputBufferBitAccumulator = newAccumulator % 8;

        return result;
    }

    public void unreadBits() {
        int newAccumulator = inputBufferBitAccumulator - 1;
        inputBufferIndex += newAccumulator >> 3;
        inputBufferBitAccumulator = newAccumulator & 7;
    }

    private int read3Bytes() {
        if (inputBuffer.length < inputBufferIndex + 2) {
            throw new IndexOutOfBoundsException(inputBuffer.length + " < 2 + " + inputBufferIndex);
        }
        return ((inputBuffer[inputBufferIndex] & 0xff) << 16)
                | ((inputBuffer[inputBufferIndex + 1] & 0xff) << 8)
                | (inputBuffer[inputBufferIndex + 2] & 0xff);
    }
}
