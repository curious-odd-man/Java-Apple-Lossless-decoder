package com.github.curiousoddman.alacdecoder;

import com.github.curiousoddman.alacdecoder.utils.WavUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class WavUtilsTests {

    // Helper method to get the written bytes as an array
    private byte[] getBytesWritten(ByteArrayOutputStream baos) {
        return baos.toByteArray();
    }

    @Test
    public void testWriteUint32_WritesCorrectBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        WavUtils.writeUint32(baos, 0x12345678);
        byte[] result = getBytesWritten(baos);
        byte[] expected = {(byte) 0x78, (byte) 0x56, (byte) 0x34, (byte) 0x12};
        assertArrayEquals(expected, result, "write_uint32 should write bytes in little endian order");
    }

    @Test
    public void testWriteUint16_WritesCorrectBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        WavUtils.writeUint16(baos, 0x1234);
        byte[] result = getBytesWritten(baos);
        byte[] expected = {(byte) 0x34, (byte) 0x12};
        assertArrayEquals(expected, result, "write_uint16 should write bytes in little endian order");
    }

    @Test
    public void testWavwriterWriteHeaders_WritesCorrectHeaders() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        int datasize = 1000;
        int numchannels = 2;
        int samplerate = 44100;
        int bytespersample = 2;
        int bitspersample = 16;

        // Call the method
        WavUtils.writeHeaders(baos, datasize, numchannels, samplerate, bytespersample, bitspersample);

        byte[] outputBytes = getBytesWritten(baos);

        // Verify "RIFF" header
        assertEquals((byte) 82, outputBytes[0], "First byte should be 'R'");
        assertEquals((byte) 73, outputBytes[1], "Second byte should be 'I'");
        assertEquals((byte) 70, outputBytes[2], "Third byte should be 'F'");
        assertEquals((byte) 70, outputBytes[3], "Fourth byte should be 'F'");

        // Verify "WAVE" header
        // waveStartIndex = 4 + 4; // after RIFF and size
        assertEquals((byte) 87, outputBytes[8], "Wave header start");
        assertEquals((byte) 65, outputBytes[9]);
        assertEquals((byte) 86, outputBytes[10]);
        assertEquals((byte) 69, outputBytes[11]);

        // Verify "fmt " header
        assertEquals((byte) 102, outputBytes[12], "fmt header start");
        assertEquals((byte) 109, outputBytes[13]);
        assertEquals((byte) 116, outputBytes[14]);
        assertEquals((byte) 32, outputBytes[15]);

        // Verify "data" header
        int dataStartIndex = 16 + 20; // after "fmt " block
        assertEquals((byte) 100, outputBytes[dataStartIndex], "data header start");
        assertEquals((byte) 97, outputBytes[dataStartIndex + 1]);
        assertEquals((byte) 116, outputBytes[dataStartIndex + 2]);
        assertEquals((byte) 97, outputBytes[dataStartIndex + 3]);

        // Verify total size written for RIFF chunk
        int riffSizeIndex = 4;
        int writtenSize = ((outputBytes[riffSizeIndex + 3] & 0xFF) << 24)
                | ((outputBytes[riffSizeIndex + 2] & 0xFF) << 16)
                | ((outputBytes[riffSizeIndex + 1] & 0xFF) << 8)
                | (outputBytes[riffSizeIndex] & 0xFF);
        assertEquals(36 + datasize, writtenSize, "RIFF chunk size should be 36 + datasize");

        // Verify datasize at the end
        int datasizeIndex = outputBytes.length - 4;
        int dataSizeWritten = ((outputBytes[datasizeIndex + 3] & 0xFF) << 24)
                | ((outputBytes[datasizeIndex + 2] & 0xFF) << 16)
                | ((outputBytes[datasizeIndex + 1] & 0xFF) << 8)
                | (outputBytes[datasizeIndex] & 0xFF);
        assertEquals(datasize, dataSizeWritten, "Data size should match the input");
    }
}
