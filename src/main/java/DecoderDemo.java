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

import com.github.curiousoddman.alacdecoder.AlacContext;
import com.github.curiousoddman.alacdecoder.AlacUtils;
import com.github.curiousoddman.alacdecoder.WavWriter;

import java.io.FileOutputStream;
import java.io.IOException;

public class DecoderDemo {
    enum WavFormat {
        RAW_PCM,
        NORMAL
    }

    public static void main(String[] args) throws IOException {
        Config config = readCmdArgs(args.length, args);// checks all the parameters passed on command line

        try (FileOutputStream fileOutputStream = new FileOutputStream(config.outputFileName());
             AlacContext ac = AlacUtils.openFile(config.inputFileName())) {

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
                WavWriter.writeHeaders(fileOutputStream, totalSamples * bytesPerSample * numChannels, numChannels, sampleRate, bytesPerSample, bitsPerSample);
            }

            /* will convert the entire buffer */
            writeData(fileOutputStream, ac);
        }
    }

    private record Config(WavFormat wavFormat, String inputFileName, String outputFileName) {
    }

    // Reformat samples from longs in processor's native endian mode to
    // little-endian data with (possibly) less than 3 bytes / sample.
    public static byte[] formatSamples(int bps, int[] src, int samcnt) {
        int counter = 0;
        int counter2 = 0;
        byte[] dst = new byte[65536];

        switch (bps) {
            case 1:
                while (samcnt > 0) {
                    dst[counter] = (byte) (0x00FF & src[counter] + 128);
                    counter++;
                    samcnt--;
                }
                break;

            case 2:
                while (samcnt > 0) {
                    int temp = src[counter2];
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

