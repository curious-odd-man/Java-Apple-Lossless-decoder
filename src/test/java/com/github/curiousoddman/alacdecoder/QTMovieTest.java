package com.github.curiousoddman.alacdecoder;


import com.github.curiousoddman.alacdecoder.stream.DataInputStreamWrapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import static com.github.curiousoddman.alacdecoder.DemuxUtils.makeFourCC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class QTMovieTest {
    @Test
    public void testReadChunkFtyp_validM4A() throws IOException {
        // Prepare input data for a valid "ftyp" chunk with "M4A " brand
        byte[] data = getBytes();

        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(data)) {

            // Mock the stream object
            DataInputStreamWrapper streamWrapper = spy(new DataInputStreamWrapper(new DataInputStream(byteStream)));

            // Mock QTMovie to return our streamWrapper
            QTMovie qtmovie = new QTMovie(streamWrapper);
            // Call the method
            qtmovie.readChunkFtyp(data.length);

            assertEquals(data.length - 8, streamWrapper.getCurrentPos());
            verify(streamWrapper).readUint32();
            verify(streamWrapper).skipBytes(4);
        }
    }

    @Test
    public void testReadChunkFtyp_invalidType() throws IOException {
        // Prepare data with an invalid type
        int invalidType = makeFourCC(0, 0, 0, 0);
        ByteArrayInputStream byteStream = new ByteArrayInputStream(new byte[]{
                (byte) ((invalidType >> 24) & 0xFF),
                (byte) ((invalidType >> 16) & 0xFF),
                (byte) ((invalidType >> 8) & 0xFF),
                (byte) (invalidType & 0xFF),
                // Add remaining bytes if necessary
        });

        DataInputStreamWrapper mockStream = mock(DataInputStreamWrapper.class);
        when(mockStream.readUint32()).thenReturn(invalidType);

        QTMovie qtmovie = new QTMovie(mockStream);

        // Call the method
        assertThrows(UnsupportedFormatException.class,
                () -> qtmovie.readChunkFtyp(8));
    }

    private static byte[] getBytes() {
        int typeCode = makeFourCC(77, 52, 65, 32); // "M4A "
        int brand1 = makeFourCC(105, 115, 111, 109); // 'isom' as example brand
        int brand2 = makeFourCC(97, 118, 99, 49);    // 'avc1'

        return new byte[]{
                // type (4 bytes)
                (byte) ((typeCode >> 24) & 0xFF),
                (byte) ((typeCode >> 16) & 0xFF),
                (byte) ((typeCode >> 8) & 0xFF),
                (byte) (typeCode & 0xFF),
                // reserved (4 bytes)
                0, 0, 0, 0,
                // compatible brands (2 brands, 8 bytes)
                (byte) ((brand1 >> 24) & 0xFF),
                (byte) ((brand1 >> 16) & 0xFF),
                (byte) ((brand1 >> 8) & 0xFF),
                (byte) (brand1 & 0xFF),
                (byte) ((brand2 >> 24) & 0xFF),
                (byte) ((brand2 >> 16) & 0xFF),
                (byte) ((brand2 >> 8) & 0xFF),
                (byte) (brand2 & 0xFF)
        };
    }

}