/*
 ** AlacUtils.java
 **
 ** Copyright (c) 2011 Peter McQuillan
 **
 ** All Rights Reserved.
 **
 ** Distributed under the BSD Software License (see license.txt)
 **
 */
package com.github.curiousoddman.alacdecoder;

import com.github.curiousoddman.alacdecoder.data.AlacFileData;
import com.github.curiousoddman.alacdecoder.data.ChunkInfo;
import com.github.curiousoddman.alacdecoder.data.SampleDuration;
import com.github.curiousoddman.alacdecoder.stream.AlacInputStream;
import com.github.curiousoddman.alacdecoder.stream.DataInputStreamWrapper;

import java.io.FileInputStream;
import java.io.IOException;

public class AlacUtils {
    // 24kb buffer = 4096 frames = 1 alac sample (we support max 24bps)
    public static final int DEST_BUFFER_SIZE = 1024 * 24 * 3;

    public static AlacContext alacOpenFileInput(String inputFileName) throws IOException {
        DemuxRes demuxRes = new DemuxRes();
        AlacContext ac = new AlacContext();

        AlacInputStream alacInputStream = new AlacInputStream(
                new FileInputStream(inputFileName)
        );

        ac.setAlacInputStream(alacInputStream);

        /* if qtmovie_read returns successfully, the stream is up to
         * the movie data, which can be used directly by the decoder */
        QTMovie headerQtMovie = new QTMovie(new DataInputStreamWrapper(alacInputStream));
        int headerRead = headerQtMovie.read(demuxRes);

        if (headerRead == 0) {
            throw new IOException("Error while loading the QuickTime movie headers.");
        } else if (headerRead == 3) {
            /*
             ** This section is used when the stream system being used doesn't support seeking
             ** We have kept track within the file where we need to go to, we close the file and
             ** skip bytes to go directly to that point
             */

            ac.getAlacInputStream().close();

            alacInputStream = new AlacInputStream(
                    new FileInputStream(inputFileName)
            );
            ac.setAlacInputStream(alacInputStream);

            QTMovie qtmovie = new QTMovie(new DataInputStreamWrapper(alacInputStream));
            qtmovie.getQtstream().skip(headerQtMovie.getSavedMdatPos());
        }

        /* initialise the sound converter */

        AlacFileData alac = AlacDecodeUtils.create_alac(demuxRes.getSampleSize(), demuxRes.getNumChannels());

        AlacDecodeUtils.alac_set_info(alac, demuxRes.getCodecData());

        ac.setDemuxRes(demuxRes);
        ac.setAlacFileData(alac);

        return ac;
    }

    // Heres where we extract the actual music data

    public static int AlacUnpackSamples(AlacContext ac, int[] destBuffer) throws IOException {
        byte[] read_buffer = ac.getReadBuffer();
        DataInputStreamWrapper inputStream = new DataInputStreamWrapper(ac.getAlacInputStream());

        // if current_sample_block is beyond last block then finished

        if (ac.getCurrentSampleBlock() >= ac.getDemuxRes().getSampleByteSize().length) {
            return 0;
        }

        SampleDuration sampleInfo = ac.getDemuxRes().getSampleInfo(ac.getCurrentSampleBlock());
        int sample_byte_size = sampleInfo.getSampleByteSize();

        inputStream.read(sample_byte_size, read_buffer, 0);

        /* now fetch */

        int outputBytes = AlacDecodeUtils.decode_frame(ac.getAlacFileData(), read_buffer, destBuffer);

        ac.setCurrentSampleBlock(ac.getCurrentSampleBlock() + 1);
        outputBytes -= ac.getOffset() * ac.getBytesPerSample();
        System.arraycopy(destBuffer, ac.getOffset(), destBuffer, 0, outputBytes);
        ac.setOffset(0);
        return outputBytes;
    }

    // Get total number of samples contained in the Apple Lossless file, or -1 if unknown


    /**
     * sets position in pcm samples
     *
     * @param ac       alac context
     * @param position position in pcm samples to go to
     */

    public static void AlacSetPosition(AlacContext ac, long position) throws IOException {
        DemuxRes res = ac.getDemuxRes();

        int current_position = 0;
        int current_sample = 0;
        for (int i = 0; i < res.getStsc().length; i++) {
            ChunkInfo chunkInfo = res.getStsc()[i];
            int last_chunk;

            if (i < res.getStsc().length - 1) {
                last_chunk = res.getStsc()[i + 1].getFirstChunk();
            } else {
                last_chunk = res.getStco().length;
            }

            for (int chunk = chunkInfo.getFirstChunk(); chunk <= last_chunk; chunk++) {
                int pos = res.getStco()[chunk - 1];
                int sample_count = chunkInfo.getSamplesPerChunk();
                while (sample_count > 0) {
                    SampleDuration sampleDuration = res.getSampleInfo(current_sample);
                    current_position += sampleDuration.getSampleDuration();
                    if (position < current_position) {
                        ac.getAlacInputStream().seek(pos);
                        ac.setCurrentSampleBlock(current_sample);
                        ac.setOffset((int) (position - (current_position - sampleDuration.getSampleDuration()))
                                * ac.getNumChannels());
                        return;
                    }
                    pos += sampleDuration.getSampleByteSize();
                    current_sample++;
                    sample_count--;
                }
            }
        }
    }
}