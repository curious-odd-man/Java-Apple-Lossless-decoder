package com.github.curiousoddman.alacdecoder.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ByteArrayReaderTest {
    private ByteArrayReader reader;

    // Helper: construct a ByteArrayReader with a given byte array at bit offset 0
    private ByteArrayReader readerFor(byte... bytes) {
        return new ByteArrayReader(bytes);
    }

    // ─────────────────────────────────────────────
    // readBit()
    // ─────────────────────────────────────────────

    @Test
    void readBit_readsHighBitFirst() {
        // 0b10000000 → first bit should be 1, next seven should be 0
        ByteArrayReader r = readerFor((byte) 0b10000000);
        assertEquals(1, r.readBit());
        for (int i = 0; i < 7; i++) assertEquals(0, r.readBit(), "bit " + (i + 2) + " should be 0");
    }

    @Test
    void readBit_allOnes() {
        ByteArrayReader r = readerFor((byte) 0xFF);
        for (int i = 0; i < 8; i++) assertEquals(1, r.readBit(), "bit " + i + " should be 1");
    }

    @Test
    void readBit_allZeros() {
        ByteArrayReader r = readerFor((byte) 0x00);
        for (int i = 0; i < 8; i++) assertEquals(0, r.readBit(), "bit " + i + " should be 0");
    }

    @Test
    void readBit_advancesAcrossByteBoundary() {
        // 0b00000001 0b10000000 → bits: 0,0,0,0,0,0,0,1 | 1,0,0,0,0,0,0,0
        ByteArrayReader r = readerFor((byte) 0x01, (byte) 0x80);
        int[] expected = {0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], r.readBit(), "bit index " + i);
        }
    }

    @Test
    void readBit_alternatingPattern() {
        // 0b10101010
        ByteArrayReader r = readerFor((byte) 0xAA);
        int[] expected = {1, 0, 1, 0, 1, 0, 1, 0};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], r.readBit(), "bit index " + i);
        }
    }

    // ─────────────────────────────────────────────
    // readBits24()
    // ─────────────────────────────────────────────

    @Test
    void readBits24_throwsOnZeroBits() {
        ByteArrayReader r = readerFor((byte) 0x00, (byte) 0x00, (byte) 0x00);
        assertThrows(IllegalArgumentException.class, () -> r.readBits24(0));
    }

    @Test
    void readBits24_throwsOnNegativeBits() {
        ByteArrayReader r = readerFor((byte) 0x00, (byte) 0x00, (byte) 0x00);
        assertThrows(IllegalArgumentException.class, () -> r.readBits24(-1));
    }

    @Test
    void readBits24_throwsOnMoreThan24Bits() {
        ByteArrayReader r = readerFor((byte) 0x00, (byte) 0x00, (byte) 0x00);
        assertThrows(IllegalArgumentException.class, () -> r.readBits24(25));
    }

    @Test
    void readBits24_readSingleBit() {
        // MSB of 0x80 = 1
        ByteArrayReader r = readerFor((byte) 0x80, (byte) 0x00, (byte) 0x00);
        assertEquals(1, r.readBits24(1));
    }

    @Test
    void readBits24_readSingleBitZero() {
        ByteArrayReader r = readerFor((byte) 0x00, (byte) 0x00, (byte) 0x00);
        assertEquals(0, r.readBits24(1));
    }

    @Test
    void readBits24_read8Bits() {
        ByteArrayReader r = readerFor((byte) 0xAB, (byte) 0x00, (byte) 0x00);
        assertEquals(0xAB, r.readBits24(8));
    }

    @Test
    void readBits24_read16Bits() {
        ByteArrayReader r = readerFor((byte) 0xAB, (byte) 0xCD, (byte) 0x00);
        assertEquals(0xABCD, r.readBits24(16));
    }

    @Test
    void readBits24_read24Bits() {
        ByteArrayReader r = readerFor((byte) 0xAB, (byte) 0xCD, (byte) 0xEF);
        assertEquals(0xABCDEF, r.readBits24(24));
    }

    @Test
    void readBits24_read24BitsAllOnes() {
        ByteArrayReader r = readerFor((byte) 0xFF, (byte) 0xFF, (byte) 0xFF);
        assertEquals(0xFFFFFF, r.readBits24(24));
    }

    @Test
    void readBits24_read24BitsAllZeros() {
        ByteArrayReader r = readerFor((byte) 0x00, (byte) 0x00, (byte) 0x00);
        assertEquals(0, r.readBits24(24));
    }

    @Test
    void readBits24_consecutiveReadsAcrossByteAlignedBoundary() {
        // Read 8 bits twice: 0xAB then 0xCD
        ByteArrayReader r = readerFor((byte) 0xAB, (byte) 0xCD, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00);
        assertEquals(0xAB, r.readBits24(8));
        assertEquals(0xCD, r.readBits24(8));
    }

    @Test
    void readBits24_consecutiveUnalignedReads() {
        // Two 4-bit reads from 0xAB → 0xA then 0xB
        ByteArrayReader r = readerFor((byte) 0xAB, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00);
        assertEquals(0xA, r.readBits24(4));
        assertEquals(0xB, r.readBits24(4));
    }

    @Test
    void readBits24_unalignedReadSpanningBytes() {
        // Read 4 bits → 0xA, then read 8 bits spanning the byte boundary
        // Bytes: 0xAB 0xCD → bits: 1010 1011 | 1100 1101
        // First 4 bits: 1010 = 0xA
        // Next 8 bits:  1011 1100 = 0xBC
        ByteArrayReader r = readerFor((byte) 0xAB, (byte) 0xCD, (byte) 0x00, (byte) 0x00, (byte) 0x00);
        assertEquals(0xA, r.readBits24(4));
        assertEquals(0xBC, r.readBits24(8));
    }

    // ─────────────────────────────────────────────
    // readBits32()
    // ─────────────────────────────────────────────

    @Test
    void readBits32_throwsOnZeroBits() {
        ByteArrayReader r = readerFor(new byte[4]);
        assertThrows(IllegalArgumentException.class, () -> r.readBits32(0));
    }

    @Test
    void readBits32_throwsOnNegativeBits() {
        ByteArrayReader r = readerFor(new byte[4]);
        assertThrows(IllegalArgumentException.class, () -> r.readBits32(-1));
    }

    @Test
    void readBits32_throwsOnMoreThan32Bits() {
        ByteArrayReader r = readerFor(new byte[4]);
        assertThrows(IllegalArgumentException.class, () -> r.readBits32(33));
    }

    @Test
    void readBits32_read1Bit() {
        ByteArrayReader r = readerFor((byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x00);
        assertEquals(1, r.readBit());
    }

    @Test
    void readBits32_read8Bits() {
        ByteArrayReader r = readerFor((byte) 0xAB, (byte) 0x00, (byte) 0x00, (byte) 0x00);
        assertEquals(0xAB, r.readBits24(8));
    }

    @Test
    void readBits32_read16Bits() {
        ByteArrayReader r = readerFor((byte) 0xAB, (byte) 0xCD, (byte) 0x00, (byte) 0x00, (byte) 0x00);
        assertEquals(0xABCD, r.readBits32(16));
    }

    @Test
    void readBits32_read24Bits() {
        ByteArrayReader r = readerFor((byte) 0xAB, (byte) 0xCD, (byte) 0xEF, (byte) 0x00, (byte) 0x00);
        assertEquals(0xABCDEF, r.readBits32(24));
    }

    @Test
    void readBits32_read32Bits() {
        // 0x12345678
        ByteArrayReader r = readerFor((byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x78, (byte) 0x00, (byte) 0x00);
        assertEquals(0x12345678, r.readBits32(32));
    }

    @Test
    void readBits32_read32BitsAllOnes() {
        ByteArrayReader r = readerFor((byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x00, (byte) 0x00);
        assertEquals(0xFFFFFFFF, r.readBits32(32));
    }

    @Test
    void readBits32_read32BitsAllZeros() {
        ByteArrayReader r = readerFor(new byte[6]);
        assertEquals(0, r.readBits32(32));
    }

    @Test
    void readBits32_consecutiveReads() {
        // Read 16 bits twice: 0xABCD then 0xEF01
        ByteArrayReader r = readerFor((byte) 0xAB, (byte) 0xCD, (byte) 0xEF, (byte) 0x01,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00);
        assertEquals(0xABCD, r.readBits32(16));
        assertEquals(0xEF01, r.readBits32(16));
    }

    @Test
    void readBits32_unalignedRead17Bits() {
        // Skip 1 bit (the leading 0 of 0x7F), then read 17 bits
        // Bytes: 0x7F 0xFF 0x80 → bits: 0 | 1111.111 1.1111.111 | 1 0000000
        // readBits32(17) splits into readBits24(16) + readBits24(1):
        //   readBits24(16) → bits 1–16 = 0111 1111 1111 1111 = 0x7FFF
        //   readBits24(1)  → bit  17   = 1 (MSB of 0x80)
        //   result = (0x7FFF << 1) | 1 = 0xFFFF = 65535
        ByteArrayReader r = readerFor((byte) 0x7F, (byte) 0xFF, (byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x00);
        r.readBit(); // skip leading 0
        assertEquals(0xFFFF, r.readBits32(17));
    }

    // ─────────────────────────────────────────────
    // Mixed readBit + readBits24/32 interleaving
    // ─────────────────────────────────────────────

    @Test
    void mixedReads_readBitThenReadBits24() {
        // Byte: 0b10101010 | rest zeros
        // readBit()     → 1
        // readBits24(7) → remaining 7 bits of first byte = 0b0101010 = 0x2A
        ByteArrayReader r = readerFor((byte) 0xAA, (byte) 0x00, (byte) 0x00, (byte) 0x00);
        assertEquals(1, r.readBit());
        assertEquals(0x2A, r.readBits24(7));
    }

    @Test
    void mixedReads_readBits24ThenReadBit() {
        // 0xAB = 1010 1011
        // readBits24(7) → top 7 bits = 0b1010101 = 0x55
        // readBit()     → 8th bit of 0xAB = 1
        ByteArrayReader r = readerFor((byte) 0xAB, (byte) 0x00, (byte) 0x00, (byte) 0x00);
        assertEquals(0x55, r.readBits24(7));
        assertEquals(1, r.readBit());
    }
}