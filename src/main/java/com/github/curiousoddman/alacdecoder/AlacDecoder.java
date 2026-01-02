package com.github.curiousoddman.alacdecoder;

import com.github.curiousoddman.alacdecoder.data.AlacContext;
import com.github.curiousoddman.alacdecoder.data.WavFormat;
import com.github.curiousoddman.alacdecoder.utils.AlacUtils;
import com.github.curiousoddman.alacdecoder.utils.SamplingUtils;
import com.github.curiousoddman.alacdecoder.utils.WavUtils;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

@UtilityClass
public class AlacDecoder {
    @RequiredArgsConstructor
    public static class AlacDecoderBuilder {
        private final WavFormat wavFormat;

        /**
         * Read input ALAC file
         *
         * @param path path to ALAC file
         * @return AlacDecoderWithInputBuilder
         */
        public AlacDecoderWithInputBuilder fromFile(Path path) {
            return fromStream(
                    () -> {
                        try {
                            return new FileInputStream(path.toAbsolutePath().toString());
                        } catch (FileNotFoundException e) {
                            throw new RuntimeException(e);
                        }
                    }
            );
        }

        /**
         * Read input stream for ALAC file contents
         *
         * @param inputStreamSupplier NOTE, supplier will be invoked multiple times (i.e. stream should be re-created in supplier).
         * @return AlacDecoderWithInputBuilder
         */
        public AlacDecoderWithInputBuilder fromStream(Supplier<InputStream> inputStreamSupplier) {
            return new AlacDecoderWithInputBuilder(inputStreamSupplier, wavFormat);
        }
    }

    @RequiredArgsConstructor
    public static class AlacDecoderWithInputBuilder {
        private final Supplier<InputStream> inputStreamSupplier;
        private final WavFormat wavFormat;

        /**
         * Write result to a file
         *
         * @param path path to resulting file
         * @return AlacDecoderWithInputAndOutputBuilder
         * @throws IOException any IO errors
         */
        public AlacDecoderWithInputAndOutputBuilder toFile(Path path) throws IOException {
            return toStream(Files.newOutputStream(path));
        }

        /**
         * Write output to a stream
         *
         * @param outputStream target stream
         * @return AlacDecoderWithInputAndOutputBuilder
         */
        public AlacDecoderWithInputAndOutputBuilder toStream(OutputStream outputStream) {
            return new AlacDecoderWithInputAndOutputBuilder(inputStreamSupplier, outputStream, wavFormat);
        }
    }

    @RequiredArgsConstructor
    public static class AlacDecoderWithInputAndOutputBuilder {
        private final Supplier<InputStream> inputStreamSupplier;
        private final OutputStream outputStream;
        private final WavFormat wavFormat;

        /**
         * Execute conversion
         *
         * @throws IOException any IO errors
         */
        public void execute() throws IOException {
            try (AlacContext ac = AlacUtils.createContext(inputStreamSupplier);
                 outputStream) {
                int numChannels = ac.getNumChannels();
                int totalSamples = ac.getNumSamples();
                int bytesPerSample = ac.getBytesPerSample();
                int sampleRate = ac.getSampleRate();
                int bitsPerSample = ac.getBitsPerSample();
                if (wavFormat == WavFormat.RAW_PCM) {
                    WavUtils.writeHeaders(outputStream, totalSamples * bytesPerSample * numChannels, numChannels, sampleRate, bytesPerSample, bitsPerSample);
                }

                // 24kb buffer = 4096 frames = 1 alac sample (we support max 24bps)
                int destBufferSize = 1024 * 24 * 3;
                int[] pdestbuffer = new int[destBufferSize];

                int bytes_unpacked;
                do {
                    bytes_unpacked = ac.unpackSamples(pdestbuffer);

                    if (bytes_unpacked > 0) {
                        byte[] pcmBuffer = SamplingUtils.formatSamples(bytesPerSample, pdestbuffer, bytes_unpacked);
                        outputStream.write(pcmBuffer, 0, bytes_unpacked);
                    }
                } while (bytes_unpacked != 0);
            }
        }
    }

    /**
     * Facade for alac decoder.
     *
     * @param wavFormat - PCM or plain
     * @return AlacDecoderBuilder
     */
    public static AlacDecoderBuilder decode(WavFormat wavFormat) {
        return new AlacDecoderBuilder(wavFormat);
    }
}
