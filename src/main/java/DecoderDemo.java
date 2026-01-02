/*
 ** DecoderDemo.java
 **
 ** Copyright (c) 2011 Peter McQuillan
 **
 ** All Rights Reserved.
 **
 ** Distributed under the BSD Software License (see license.txt)
 **
 */

import com.github.curiousoddman.alacdecoder.data.AlacContext;
import com.github.curiousoddman.alacdecoder.data.WavFormat;
import com.github.curiousoddman.alacdecoder.utils.AlacUtils;
import com.github.curiousoddman.alacdecoder.utils.WavUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.github.curiousoddman.alacdecoder.utils.SamplingUtils.formatSamples;

public class DecoderDemo {
    public static void main(String[] args) throws IOException {
        Config config = readCmdArgs(args.length, args);// checks all the parameters passed on command line

        try (FileOutputStream fileOutputStream = new FileOutputStream(config.outputFileName());
             AlacContext ac = AlacUtils.createContext(() -> {
                 try {
                     return Files.newInputStream(Path.of(config.inputFileName()));
                 } catch (IOException e) {
                     throw new RuntimeException(e);
                 }
             })) {

            int numChannels = ac.getNumChannels();
            System.out.println("The Apple Lossless file has " + numChannels + " channels");
            int totalSamples = ac.getNumSamples();
            System.out.println("The Apple Lossless file has " + totalSamples + " samples");
            int bytesPerSample = ac.getBytesPerSample();
            System.out.println("The Apple Lossless file has " + bytesPerSample + " bytes per sample");
            int sampleRate = ac.getSampleRate();
            int bitsPerSample = ac.getBitsPerSample();


            /* write wav output headers */
            if (config.wavFormat() == WavFormat.RAW_PCM) {
                WavUtils.writeHeaders(fileOutputStream, totalSamples * bytesPerSample * numChannels, numChannels, sampleRate, bytesPerSample, bitsPerSample);
            }

            /* will convert the entire buffer */
            writeData(fileOutputStream, ac);
        }
    }

    private record Config(WavFormat wavFormat, String inputFileName, String outputFileName) {
    }

    private static Config readCmdArgs(int argc, String[] argv) {
        if (argc < 2) {
            printUsageAndSystemExit();
        }

        WavFormat wavFormat = WavFormat.NORMAL;
        String inputFileName = "";
        String outputFileName = "";

        int argIdx = 0;
        // loop through command-line arguments
        while (argIdx < argc) {
            if (argv[argIdx].startsWith("-")) {
                if (argv[argIdx].startsWith("-r") || argv[argIdx].startsWith("-R")) {
                    // raw PCM output
                    wavFormat = WavFormat.RAW_PCM;
                }
            } else if (inputFileName.isEmpty()) {
                inputFileName = argv[argIdx];
            } else if (outputFileName.isEmpty()) {
                outputFileName = argv[argIdx];
            } else {
                System.out.println("extra unknown argument: " + argv[argIdx]);
                printUsageAndSystemExit();
            }
            argIdx++;
        }

        if (inputFileName.isEmpty() || outputFileName.isEmpty()) {
            printUsageAndSystemExit();
        }

        return new Config(
                wavFormat,
                inputFileName,
                outputFileName
        );
    }

    private static void writeData(FileOutputStream fos, AlacContext ac) throws IOException {
        int[] destinationBuffer = new int[AlacUtils.DEST_BUFFER_SIZE];
        int bps = ac.getBytesPerSample();

        int bytesUnpacked;
        do {
            bytesUnpacked = ac.unpackSamples(destinationBuffer);

            if (bytesUnpacked > 0) {
                byte[] pcmBuffer = formatSamples(bps, destinationBuffer, bytesUnpacked);
                fos.write(pcmBuffer, 0, bytesUnpacked);
            }

        } while (bytesUnpacked != 0);
    }

    static void printUsageAndSystemExit() {
        System.out.println("Usage: alac [options] inputfile outputfile");
        System.out.println("Decompresses the ALAC file specified");
        System.out.println("Options:");
        System.out.println("  -r                write output as raw PCM data. Default");
        System.out.println("                    is in WAV format.");
        System.out.println();
        System.out.println("This port of the code is (c) Peter McQuillan 2011");
        System.out.println("Original software is (c) 2005 David Hammerton");
        System.exit(1);
    }
}

