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

import java.io.FileInputStream;
import java.io.IOException;

public class AlacUtils {

    public static AlacContext AlacOpenFileInput(String inputFileName) throws IOException {
        DemuxResT demuxResT = new DemuxResT();
        AlacContext ac = new AlacContext();

        AlacInputStream alacInputStream = new AlacInputStream(
                new FileInputStream(inputFileName)
        );

        ac.setAlacInputStream(alacInputStream);

        /* if qtmovie_read returns successfully, the stream is up to
         * the movie data, which can be used directly by the decoder */
        int headerRead = DemuxUtils.qtmovieRead(alacInputStream,
                new QTMovieT(new DataInputStreamWrapper(alacInputStream)),
                demuxResT
        );

        if (headerRead == 0) {
            if (demuxResT.getFormatRead() == 0) {
                throw new IOException("Failed to load the QuickTime movie headers.");
            } else {
                throw new IOException("Error while loading the QuickTime movie headers.");
            }
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

            QTMovieT qtmovie = new QTMovieT(new DataInputStreamWrapper(alacInputStream));
            qtmovie.getQtstream().setCurrentPos(0);
            StreamUtils.streamSkip(qtmovie.getQtstream(), qtmovie.getSavedMdatPos());
        }

        /* initialise the sound converter */

        AlacFile alac = AlacDecodeUtils.create_alac(demuxResT.getSampleSize(), demuxResT.getNumChannels());

        AlacDecodeUtils.alac_set_info(alac, demuxResT.getCodecData());

        ac.setDemux_res(demuxResT);
        ac.setAlac(alac);

        return ac;
    }

    public static void AlacCloseFile(AlacContext ac) throws IOException {
        if (null != ac.getAlacInputStream()) {
            ac.getAlacInputStream().close();
        }
    }

    // Heres where we extract the actual music data

    public static int AlacUnpackSamples(AlacContext ac, int[] pDestBuffer) throws IOException {
        SampleDuration sampleinfo = new SampleDuration();
        byte[] read_buffer = ac.getReadBuffer();
        int destBufferSize = 1024 * 24 * 3; // 24kb buffer = 4096 frames = 1 alac sample (we support max 24bps)
        int outputBytes;
        DataInputStreamWrapper inputStream = new DataInputStreamWrapper(ac.getAlacInputStream());

        // if current_sample_block is beyond last block then finished

        if (ac.getCurrentSampleBlock() >= ac.getDemux_res().getSampleByteSize().length) {
            return 0;
        }

        if (get_sample_info(ac.getDemux_res(), ac.getCurrentSampleBlock(), sampleinfo) == 0) {
            // getting sample failed
            return 0;
        }

        int sample_byte_size = sampleinfo.getSampleByteSize();

        StreamUtils.streamRead(inputStream, sample_byte_size, read_buffer, 0);

        /* now fetch */
        outputBytes = destBufferSize;

        outputBytes = AlacDecodeUtils.decode_frame(ac.getAlac(), read_buffer, pDestBuffer, outputBytes);

        ac.setCurrentSampleBlock(ac.getCurrentSampleBlock() + 1);
        outputBytes -= ac.getOffset() * AlacGetBytesPerSample(ac);
        System.arraycopy(pDestBuffer, ac.getOffset(), pDestBuffer, 0, outputBytes);
        ac.setOffset(0);
        return outputBytes;
    }


    // Returns the sample rate of the specified ALAC file

    public static int AlacGetSampleRate(AlacContext ac) {
        if (null != ac && ac.getDemux_res().getSampleRate() != 0) {
            return ac.getDemux_res().getSampleRate();
        } else {
            return (44100);
        }
    }

    public static int AlacGetNumChannels(AlacContext ac) {
        if (null != ac && ac.getDemux_res().getNumChannels() != 0) {
            return ac.getDemux_res().getNumChannels();
        } else {
            return 2;
        }
    }

    public static int AlacGetBitsPerSample(AlacContext ac) {
        if (null != ac && ac.getDemux_res().getSampleSize() != 0) {
            return ac.getDemux_res().getSampleSize();
        } else {
            return 16;
        }
    }


    public static int AlacGetBytesPerSample(AlacContext ac) {
        if (null != ac && ac.getDemux_res().getSampleSize() != 0) {
            return (int) Math.ceil(ac.getDemux_res().getSampleSize() / 8);
        } else {
            return 2;
        }
    }


    // Get total number of samples contained in the Apple Lossless file, or -1 if unknown

    public static int AlacGetNumSamples(AlacContext ac) {
        /* calculate output size */
        int num_samples = 0;
        int thissample_duration;
        int thissample_bytesize = 0;
        SampleDuration sampleinfo = new SampleDuration();
        int i;
        boolean error_found = false;
        int retval = 0;

        for (i = 0; i < ac.getDemux_res().getSampleByteSize().length; i++) {
            thissample_duration = 0;
            thissample_bytesize = 0;

            retval = get_sample_info(ac.getDemux_res(), i, sampleinfo);

            if (retval == 0) {
                return (-1);
            }
            thissample_duration = sampleinfo.getSampleDuration();
            thissample_bytesize = sampleinfo.getSampleByteSize();

            num_samples += thissample_duration;
        }

        return (num_samples);
    }


    static int get_sample_info(DemuxResT demux_res, int samplenum, SampleDuration sampleinfo) {
        int duration_index_accum = 0;
        int duration_cur_index = 0;

        if (samplenum >= demux_res.getSampleByteSize().length) {
            System.err.println("sample " + samplenum + " does not exist ");
            return 0;
        }

        if (demux_res.getNumTimeToSamples() == 0)        // was null
        {
            System.err.println("no time to samples");
            return 0;
        }
        while ((demux_res.getTimeToSample().get(duration_cur_index).getSampleCount() + duration_index_accum) <= samplenum) {
            duration_index_accum += demux_res.getTimeToSample().get(duration_cur_index).getSampleCount();
            duration_cur_index++;
            if (duration_cur_index >= demux_res.getNumTimeToSamples()) {
                System.err.println("sample " + samplenum + " does not have a duration");
                return 0;
            }
        }

        sampleinfo.setSampleDuration(demux_res.getTimeToSample().get(duration_cur_index).getSampleDuration());
        sampleinfo.setSampleByteSize(demux_res.getSampleByteSize()[samplenum]);

        return 1;
    }

    /**
     * sets position in pcm samples
     *
     * @param ac       alac context
     * @param position position in pcm samples to go to
     */

    public static void AlacSetPosition(AlacContext ac, long position) throws IOException {
        DemuxResT res = ac.getDemux_res();

        int current_position = 0;
        int current_sample = 0;
        SampleDuration sampleDuration = new SampleDuration();
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
                    int ret = get_sample_info(res, current_sample, sampleDuration);
                    if (ret == 0) return;
                    current_position += sampleDuration.getSampleDuration();
                    if (position < current_position) {
                        ac.getAlacInputStream().seek(pos);
                        ac.setCurrentSampleBlock(current_sample);
                        ac.setOffset((int) (position - (current_position - sampleDuration.getSampleDuration()))
                                * AlacGetNumChannels(ac));
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