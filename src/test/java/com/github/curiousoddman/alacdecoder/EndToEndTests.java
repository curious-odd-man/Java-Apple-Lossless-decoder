package com.github.curiousoddman.alacdecoder;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class EndToEndTests {

    @Test
    void convertAlacToWavTest() throws IOException {

        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             AlacContext ac = AlacUtils.alacOpenFileInput(Path.of("src/test/resources/white_noise.m4a").toAbsolutePath().toString());) {
            int numChannels = ac.getNumChannels();
            int totalSamples = ac.getNumSamples();
            int bytesPerSample = ac.getBytesPerSample();
            int sampleRate = ac.getSampleRate();
            int bitsPerSample = ac.getBitsPerSample();
            WavWriter.writeHeaders(byteArrayOutputStream, (totalSamples * bytesPerSample * numChannels), numChannels, sampleRate, bytesPerSample, bitsPerSample);

            // 24kb buffer = 4096 frames = 1 alac sample (we support max 24bps)
            int destBufferSize = 1024 * 24 * 3;
            int[] pdestbuffer = new int[destBufferSize];

            int bytes_unpacked;
            int counter = 0;
            do {
                bytes_unpacked = AlacUtils.AlacUnpackSamples(ac, pdestbuffer);

                if (bytes_unpacked > 0) {
                    byte[] pcmBuffer = format_samples(bytesPerSample, pdestbuffer, bytes_unpacked);
                    byteArrayOutputStream.write(pcmBuffer, 0, bytes_unpacked);
                }

                counter++;
            } while (bytes_unpacked != 0);


            byte[] output = byteArrayOutputStream.toByteArray();
            byte[] expected = Files.readAllBytes(Path.of("src/test/resources/white_noise.wav"));
            //Files.write(Path.of("src/test/resources/white_noise.act.wav"), output);
            assertArrayEquals(expected, output);
        }
    }

    public static byte[] format_samples(int bps, int[] src, int samcnt) {
        int counter = 0;
        int counter2 = 0;
        byte[] dst = new byte[65536];

        switch (bps) {
            case 1:
                while (samcnt > 0) {
                    dst[counter] = (byte) (0x00FF & (src[counter] + 128));
                    counter++;
                    samcnt--;
                }
                break;

            case 2:
                int temp = 0;
                while (samcnt > 0) {
                    temp = src[counter2];
                    dst[counter] = (byte) temp;
                    counter++;
                    dst[counter] = (byte) (temp >>> 8);
                    counter++;
                    counter2++;
                    samcnt = samcnt - 2;
                }
                break;

            case 3:
                while (samcnt > 0) {
                    dst[counter] = (byte) src[counter2];
                    counter++;
                    counter2++;
                    samcnt--;
                }
                break;
        }

        return dst;
    }

}
