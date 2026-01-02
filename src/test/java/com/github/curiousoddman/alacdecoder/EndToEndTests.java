package com.github.curiousoddman.alacdecoder;

import com.github.curiousoddman.alacdecoder.data.WavFormat;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class EndToEndTests {

    @Test
    void convertAlacToWavTest() throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        AlacDecoder
                .decode(WavFormat.RAW_PCM)
                .fromFile(Path.of("src/test/resources/white_noise.m4a"))
                .toStream(byteArrayOutputStream)
                .execute();

        byte[] output = byteArrayOutputStream.toByteArray();
        byte[] expected = Files.readAllBytes(Path.of("src/test/resources/white_noise.wav"));
        //Files.write(Path.of("src/test/resources/white_noise.act.wav"), output);
        assertArrayEquals(expected, output);
    }

    @Test
    void convertAlacToWavMonoTest() throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        AlacDecoder
                .decode(WavFormat.RAW_PCM)
                .fromFile(Path.of("src/test/resources/white_noise_mono.m4a"))
                .toStream(byteArrayOutputStream)
                .execute();

        byte[] output = byteArrayOutputStream.toByteArray();
        byte[] expected = Files.readAllBytes(Path.of("src/test/resources/white_noise_mono.wav"));
        //Files.write(Path.of("src/test/resources/white_noise.act.wav"), output);
        assertArrayEquals(expected, output);
    }
}
