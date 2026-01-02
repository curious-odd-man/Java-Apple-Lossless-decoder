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
package com.github.curiousoddman.alacdecoder.utils;

import com.github.curiousoddman.alacdecoder.data.AlacContext;
import com.github.curiousoddman.alacdecoder.data.AlacFileData;
import com.github.curiousoddman.alacdecoder.data.DemuxRes;
import com.github.curiousoddman.alacdecoder.stream.AlacInputStream;
import com.github.curiousoddman.alacdecoder.stream.DataInputStreamWrapper;
import com.github.curiousoddman.alacdecoder.stream.QTMovie;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Supplier;

public class AlacUtils {
    // 24kb buffer = 4096 frames = 1 alac sample (we support max 24bps)
    public static final int DEST_BUFFER_SIZE = 1024 * 24 * 3;

    public static AlacContext createContext(Supplier<InputStream> streamSupplier) throws IOException {
        DemuxRes demuxRes = new DemuxRes();
        AlacContext ac = new AlacContext();

        AlacInputStream alacInputStream = new AlacInputStream(streamSupplier.get());

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

            alacInputStream = new AlacInputStream(streamSupplier.get());
            ac.setAlacInputStream(alacInputStream);

            QTMovie qtmovie = new QTMovie(new DataInputStreamWrapper(alacInputStream));
            qtmovie.getQtstream().skip(headerQtMovie.getSavedMdatPos());
        }

        /* initialise the sound converter */

        AlacFileData alac = new AlacFileData(demuxRes.getSampleSize(), demuxRes.getNumChannels());

        alac.setInfo(demuxRes.getCodecData());

        ac.setDemuxRes(demuxRes);
        ac.setAlacFileData(alac);

        return ac;
    }
}