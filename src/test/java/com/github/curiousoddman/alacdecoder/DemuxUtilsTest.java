package com.github.curiousoddman.alacdecoder;

import org.junit.jupiter.api.Test;

import static com.github.curiousoddman.alacdecoder.utils.DemuxUtils.makeFourCC;
import static com.github.curiousoddman.alacdecoder.utils.DemuxUtils.splitFourCC;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DemuxUtilsTest {

    @Test
    public void testMakeFourCC() {
        // Test with ASCII characters 'A', 'B', 'C', 'D'
        int code = makeFourCC('A', 'B', 'C', 'D');
        // ASCII codes for 'A', 'B', 'C', 'D'
        int expected = (('A') << 24) | (('B') << 16) | (('C') << 8) | 'D';
        assertEquals(0x41424344, code);
    }

    @Test
    public void testMakeFourCCWithDifferentChars() {
        int code = makeFourCC(0x41, 0x42, 0x43, 0x44); // ASCII codes for 'A', 'B', 'C', 'D'
        assertEquals(0x41424344, code);
    }

    @Test
    public void testMakeFourCC32() {
        int code = makeFourCC('W', 'X', 'Y', 'Z');
        int expected = (('W') << 24) | (('X') << 16) | (('Y') << 8) | 'Z';
        assertEquals(expected, code);
    }

    @Test
    public void testMakeFourCC32WithDifferentChars() {
        int code = makeFourCC(0x57, 0x58, 0x59, 0x5A); // 'W', 'X', 'Y', 'Z'
        assertEquals((0x57 << 24) | (0x58 << 16) | (0x59 << 8) | 0x5A, code);
    }

    @Test
    public void testSplitFourCC() {
        int code = makeFourCC('A', 'B', 'C', 'D');
        String result = splitFourCC(code);
        assertEquals("A B C D", result);
    }

    @Test
    public void testSplitFourCCWithDifferentChars() {
        int code = makeFourCC('W', 'X', 'Y', 'Z');
        String result = splitFourCC(code);
        assertEquals("W X Y Z", result);
    }

    @Test
    public void testSplitFourCCWithBoundaryValues() {
        int code = makeFourCC('A', 'B', 'C', 'D');
        assertEquals("A B C D", splitFourCC(code));
    }
}