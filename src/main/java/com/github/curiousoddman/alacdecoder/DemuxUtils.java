/*
 ** DemuxUtils.java
 **
 ** Copyright (c) 2011-2014 Peter McQuillan
 **
 ** All Rights Reserved.
 **
 ** Distributed under the BSD Software License (see license.txt)
 **
 */

package com.github.curiousoddman.alacdecoder;


import java.io.IOException;

class DemuxUtils {
    public static int MakeFourCC(int ch0, int ch1, int ch2, int ch3) {
        return (((ch0) << 24) | ((ch1) << 16) | ((ch2) << 8) | ((ch3)));
    }

    public static int MakeFourCC32(int ch0, int ch1, int ch2, int ch3) {
        int tmp = ch0;

        int retval = tmp << 24;

        tmp = ch1;

        retval = retval | (tmp << 16);
        tmp = ch2;

        retval = retval | (tmp << 8);
        tmp = ch3;

        retval = retval | tmp;

        return (retval);
    }

    public static String SplitFourCC(int code) {

        char c1 = (char) ((code >> 24) & 0xFF);
        char c2 = (char) ((code >> 16) & 0xFF);
        char c3 = (char) ((code >> 8) & 0xFF);
        char c4 = (char) (code & 0xFF);
        String retstr = c1 + " " + c2 + " " + c3 + " " + c4;

        return retstr;

    }


    public static int qtmovieRead(QTMovieT qtmovie, DemuxResT demux_res) throws IOException {

        /* construct the stream */
        qtmovie.setRes(demux_res);

        // reset demux_res	TODO

        /* read the chunks */
        int found_mdat = 0;
        int found_moov = 0;
        while (true) {
            int chunk_len;

            try {
                chunk_len = qtmovie.getQtstream().readUint32();
            } catch (Exception e) {
                System.err.println("(top) error reading chunk_len - possibly number too large");
                chunk_len = 1;
            }

//            if (StreamUtils.stream_eof(qtmovie.getQtstream()) != 0) {
//                return 0;
//            }

            if (chunk_len == 1) {
                System.err.println("need 64bit support");
                return 0;
            }
            int chunk_id = qtmovie.getQtstream().readUint32();

            if (chunk_id == MakeFourCC32(102, 116, 121, 112))    // fourcc equals ftyp
            {
                read_chunk_ftyp(qtmovie, chunk_len);
            } else if (chunk_id == MakeFourCC32(109, 111, 111, 118))    // fourcc equals moov
            {
                if (read_chunk_moov(qtmovie, chunk_len) == 0)
                    return 0; // failed to read moov, can't do anything
                if (found_mdat != 0) {
                    return set_saved_mdat(qtmovie);
                }
                found_moov = 1;
            }
            /* if we hit mdat before we've found moov, record the position
             * and move on. We can then come back to mdat later.
             * This presumes the stream supports seeking backwards.
             */
            else if (chunk_id == MakeFourCC32(109, 100, 97, 116))    // fourcc equals mdat
            {
                int not_found_moov = 0;
                if (found_moov == 0)
                    not_found_moov = 1;
                read_chunk_mdat(qtmovie, chunk_len, not_found_moov);
                if (found_moov != 0) {
                    return 1;
                }
                found_mdat = 1;
            }
            /*  these following atoms can be skipped !!!! */
            else if (chunk_id == MakeFourCC32(102, 114, 101, 101))    // fourcc equals free
            {
                qtmovie.getQtstream().skip(chunk_len - 8); // FIXME not 8
            } else if (chunk_id == MakeFourCC32(106, 117, 110, 107))     // fourcc equals junk
            {
                qtmovie.getQtstream().skip(chunk_len - 8); // FIXME not 8
            } else {
                System.err.println("(top) unknown chunk id: " + SplitFourCC(chunk_id));
                return 0;
            }
        }
    }


    /* chunk handlers */
    static void read_chunk_ftyp(QTMovieT qtmovie, int chunk_len) throws IOException {
        int size_remaining = chunk_len - 8; // FIXME: can't hardcode 8, size may be 64bit

        int type = qtmovie.getQtstream().readUint32();
        size_remaining -= 4;

        if (type != MakeFourCC32(77, 52, 65, 32))        // "M4A " ascii values
        {
            System.err.println("not M4A file");
            return;
        }
        qtmovie.getQtstream().readUint32();
        size_remaining -= 4;

        /* compatible brands */
        while (size_remaining != 0) {
            /* unused */
            /*fourcc_t cbrand =*/
            qtmovie.getQtstream().readUint32();
            size_remaining -= 4;
        }
    }

    static void read_chunk_tkhd(QTMovieT qtmovie, int chunk_len) throws IOException {
        /* don't need anything from here atm, skip */
        read_chunk_edts(qtmovie, chunk_len);
    }

    static void read_chunk_mdhd(QTMovieT qtmovie, int chunk_len) throws IOException {
        /* don't need anything from here atm, skip */
        read_chunk_edts(qtmovie, chunk_len);
    }

    static void read_chunk_edts(QTMovieT qtmovie, int chunk_len) throws IOException {
        /* don't need anything from here atm, skip */
        int size_remaining = chunk_len - 8; // FIXME WRONG

        qtmovie.getQtstream().skip(size_remaining);
    }

    static void read_chunk_elst(QTMovieT qtmovie, int chunk_len) throws IOException {
        /* don't need anything from here atm, skip */
        read_chunk_edts(qtmovie, chunk_len);
    }

    /* media handler inside mdia */
    static void read_chunk_hdlr(QTMovieT qtmovie, int chunk_len) throws IOException {

        /* version */
        qtmovie.getQtstream().readUint8();
        // FIXME WRONG
        int size_remaining = chunk_len - 8;
        size_remaining -= 1;
        /* flags */
        qtmovie.getQtstream().readUint8();
        qtmovie.getQtstream().readUint8();
        qtmovie.getQtstream().readUint8();
        size_remaining -= 3;

        /* component type */
        int comptype = qtmovie.getQtstream().readUint32();
        int compsubtype = qtmovie.getQtstream().readUint32();
        size_remaining -= 8;

        /* component manufacturer */
        qtmovie.getQtstream().readUint32();
        size_remaining -= 4;

        /* flags */
        qtmovie.getQtstream().readUint32();
        qtmovie.getQtstream().readUint32();
        size_remaining -= 8;

        /* name */
        int strlen = qtmovie.getQtstream().readUint8();

        /*
         ** rewrote this to handle case where we actually read more than required
         ** so here we work out how much we need to read first
         */

        size_remaining -= 1;

        qtmovie.getQtstream().skip(size_remaining);
    }

    static int read_chunk_stsd(QTMovieT qtmovie) throws IOException {

        /* version */
        qtmovie.getQtstream().readUint8();
        /* flags */
        qtmovie.getQtstream().readUint8();
        qtmovie.getQtstream().readUint8();
        qtmovie.getQtstream().readUint8();

        int numentries = 0;
        try {
            numentries = (qtmovie.getQtstream().readUint32());
        } catch (Exception e) {
            System.err.println("(read_chunk_stsd) error reading numentries - possibly number too large");
        }


        if (numentries != 1) {
            System.err.println("only expecting one entry in sample description atom!");
            return 0;
        }

        for (int i = 0; i < numentries; i++) {
            /* parse the alac atom contained within the stsd atom */

            int entry_size = (qtmovie.getQtstream().readUint32());
            qtmovie.getRes().setFormat(qtmovie.getQtstream().readUint32());
            int entry_remaining = entry_size;
            entry_remaining -= 8;

            if (qtmovie.getRes().getFormat() != MakeFourCC32(97, 108, 97, 99))    // "alac" ascii values
            {
                System.err.println("(read_chunk_stsd) error reading description atom - expecting alac, got " + SplitFourCC(qtmovie.getRes().getFormat()));
                return 0;
            }

            /* sound info: */

            qtmovie.getQtstream().skip(6); // reserved
            entry_remaining -= 6;

            int version = qtmovie.getQtstream().readUint16();

            if (version != 1)
                System.err.println("unknown version??");
            entry_remaining -= 2;

            /* revision level */
            qtmovie.getQtstream().readUint16();
            /* vendor */
            qtmovie.getQtstream().readUint32();
            entry_remaining -= 6;

            /* EH?? spec doesn't say theres an extra 16 bits here.. but there is! */
            qtmovie.getQtstream().readUint16();
            entry_remaining -= 2;

            /* skip 4 - this is the top level num of channels and bits per sample */
            qtmovie.getQtstream().skip(4);
            entry_remaining -= 4;

            /* compression id */
            qtmovie.getQtstream().readUint16();
            /* packet size */
            qtmovie.getQtstream().readUint16();
            entry_remaining -= 4;

            /* skip 4 - this is the top level sample rate */
            qtmovie.getQtstream().skip(4);
            entry_remaining -= 4;

            /* remaining is codec data */

            /* 12 = audio format atom, 8 = padding */
            qtmovie.getRes().setCodecDataLen(entry_remaining + 12 + 8);

            if (qtmovie.getRes().getCodecDataLen() > qtmovie.getRes().getCodecData().length) {
                System.err.println("(read_chunk_stsd) unexpected codec data length read from atom " + qtmovie.getRes().getCodecDataLen());
                return 0;
            }

            for (int count = 0; count < qtmovie.getRes().getCodecDataLen(); count++) {
                qtmovie.getRes().getCodecData()[count] = 0;
            }

            /* audio format atom */
            qtmovie.getRes().getCodecData()[0] = 0x0c000000;
            qtmovie.getRes().getCodecData()[1] = MakeFourCC(97, 109, 114, 102);        // "amrf" ascii values
            qtmovie.getRes().getCodecData()[2] = MakeFourCC(99, 97, 108, 97);        // "cala" ascii values

            qtmovie.getQtstream().read(entry_remaining, qtmovie.getRes().getCodecData(), 12);    // codecdata buffer should be +12
            entry_remaining -= entry_remaining;

            /* We need to read the bits per sample, number of channels and sample rate from the codec data i.e. the alac atom within
             ** the stsd atom the 'alac' atom contains a number of pieces of information which we can skip just now, its processed later
             ** in the alac_set_info() method. This atom contains the following information
             **
             ** samples_per_frame
             ** compatible version
             ** bits per sample
             ** history multiplier
             ** initial history
             ** maximum K
             ** channels
             ** max run
             ** max coded frame size
             ** bitrate
             ** sample rate
             */
            int ptrIndex = 29;    // position of bits per sample

            qtmovie.getRes().setSampleSize((qtmovie.getRes().getCodecData()[ptrIndex] & 0xff));

            ptrIndex = 33;    // position of num of channels

            qtmovie.getRes().setNumChannels((qtmovie.getRes().getCodecData()[ptrIndex] & 0xff));

            ptrIndex = 44;        // position of sample rate within codec data buffer

            qtmovie.getRes().setSampleRate((((qtmovie.getRes().getCodecData()[ptrIndex] & 0xff) << 24) | ((qtmovie.getRes().getCodecData()[ptrIndex + 1] & 0xff) << 16) | ((qtmovie.getRes().getCodecData()[ptrIndex + 2] & 0xff) << 8) | (qtmovie.getRes().getCodecData()[ptrIndex + 3] & 0xff)));

            if (entry_remaining != 0)    // was comparing to null
                qtmovie.getQtstream().skip(entry_remaining);

            qtmovie.getRes().setFormatRead(1);
            if (qtmovie.getRes().getFormat() != MakeFourCC32(97, 108, 97, 99))        // "alac" ascii values
            {
                return 0;
            }
        }

        return 1;
    }

    static void read_chunk_stts(QTMovieT qtmovie, int chunk_len) throws IOException {

        /* version */
        qtmovie.getQtstream().readUint8();
        // FIXME WRONG
        int size_remaining = chunk_len - 8;
        size_remaining -= 1;
        /* flags */
        qtmovie.getQtstream().readUint8();
        qtmovie.getQtstream().readUint8();
        qtmovie.getQtstream().readUint8();
        size_remaining -= 3;

        int numentries = 0;
        try {
            numentries = (qtmovie.getQtstream().readUint32());
        } catch (Exception e) {
            System.err.println("(read_chunk_stts) error reading numentries - possibly number too large");
        }

        size_remaining -= 4;

        qtmovie.getRes().setNumTimeToSamples(numentries);

        for (int i = 0; i < numentries; i++) {
            qtmovie.getRes().getTimeToSample().get(i).setSampleCount(qtmovie.getQtstream().readUint32());
            qtmovie.getRes().getTimeToSample().get(i).setSampleDuration(qtmovie.getQtstream().readUint32());
            size_remaining -= 8;
        }

        if (size_remaining != 0) {
            System.err.println("(read_chunk_stts) size remaining?");
            qtmovie.getQtstream().skip(size_remaining);
        }
    }

    static void read_chunk_stsz(QTMovieT qtmovie, int chunk_len) throws IOException {

        /* version */
        qtmovie.getQtstream().readUint8();
        // FIXME WRONG
        int size_remaining = chunk_len - 8;
        size_remaining -= 1;
        /* flags */
        qtmovie.getQtstream().readUint8();
        qtmovie.getQtstream().readUint8();
        qtmovie.getQtstream().readUint8();
        size_remaining -= 3;

        /* default sample size */
        int uniform_size = (qtmovie.getQtstream().readUint32());
        int i;
        if (uniform_size != 0) {
            /*
             ** Normally files have intiable sample sizes, this handles the case where
             ** they are all the same size
             */

            int uniform_num = (qtmovie.getQtstream().readUint32());

            qtmovie.getRes().setSampleByteSize(new int[uniform_num]);

            for (i = 0; i < uniform_num; i++) {
                qtmovie.getRes().getSampleByteSize()[i] = uniform_size;
            }
            return;
        }
        size_remaining -= 4;

        int numentries = 0;
        try {
            numentries = (qtmovie.getQtstream().readUint32());
        } catch (Exception e) {
            System.err.println("(read_chunk_stsz) error reading numentries - possibly number too large");
        }

        size_remaining -= 4;

        qtmovie.getRes().setSampleByteSize(new int[numentries]);

        for (i = 0; i < numentries; i++) {
            qtmovie.getRes().getSampleByteSize()[i] = (qtmovie.getQtstream().readUint32());

            size_remaining -= 4;
        }

        if (size_remaining != 0) {
            System.err.println("(read_chunk_stsz) size remaining?");
            qtmovie.getQtstream().skip(size_remaining);
        }
    }

    static int read_chunk_stbl(QTMovieT qtmovie, int chunk_len) throws IOException {
        int size_remaining = chunk_len - 8; // FIXME WRONG

        while (size_remaining != 0) {
            int sub_chunk_len;

            try {
                sub_chunk_len = (qtmovie.getQtstream().readUint32());
            } catch (Exception e) {
                System.err.println("(read_chunk_stbl) error reading sub_chunk_len - possibly number too large");
                sub_chunk_len = 0;
            }

            if (sub_chunk_len <= 1 || sub_chunk_len > size_remaining) {
                System.err.println("strange size for chunk inside stbl " + sub_chunk_len + " (remaining: " + size_remaining + ")");
                return 0;
            }

            int sub_chunk_id = qtmovie.getQtstream().readUint32();

            if (sub_chunk_id == MakeFourCC32(115, 116, 115, 100))    // fourcc equals stsd
            {
                if (read_chunk_stsd(qtmovie) == 0)
                    return 0;
            } else if (sub_chunk_id == MakeFourCC32(115, 116, 116, 115))    // fourcc equals stts
            {
                read_chunk_stts(qtmovie, sub_chunk_len);
            } else if (sub_chunk_id == MakeFourCC32(115, 116, 115, 122))    // fourcc equals stsz
            {
                read_chunk_stsz(qtmovie, sub_chunk_len);
            } else if (sub_chunk_id == MakeFourCC32(115, 116, 115, 99))    // fourcc equals stsc
            {
                read_chunk_stsc(qtmovie);
            } else if (sub_chunk_id == MakeFourCC32(115, 116, 99, 111))    // fourcc equals stco
            {
                read_chunk_stco(qtmovie);
            } else {
                System.err.println("(stbl) unknown chunk id: " + SplitFourCC(sub_chunk_id));
                return 0;
            }

            size_remaining -= sub_chunk_len;
        }

        return 1;
    }

    /*
     * chunk to offset box
     */
    private static void read_chunk_stco(QTMovieT qtmovie) throws IOException {
        //skip header and size
        DataInputStreamWrapper stream = qtmovie.getQtstream();
        stream.skip(4);

        int num_entries = stream.readUint32();

        qtmovie.getRes().setStco(new int[num_entries]);
        for (int i = 0; i < num_entries; i++) {
            qtmovie.getRes().getStco()[i] = stream.readUint32();
        }
    }

    /*
     * sample to chunk box
     */
    private static void read_chunk_stsc(QTMovieT qtmovie) throws IOException {
        //skip header and size
        DataInputStreamWrapper stream = qtmovie.getQtstream();
        //skip version and other junk
        stream.skip(4);
        int num_entries = stream.readUint32();
        qtmovie.getRes().setStsc(new ChunkInfo[num_entries]);
        for (int i = 0; i < num_entries; i++) {
            ChunkInfo entry = new ChunkInfo();
            entry.setFirstChunk(stream.readUint32());
            entry.setSamplesPerChunk(stream.readUint32());
            entry.setSampleDescIndex(stream.readUint32());
            qtmovie.getRes().getStsc()[i] = entry;
        }
    }

    static int read_chunk_minf(QTMovieT qtmovie, int chunk_len) throws IOException {
        int media_info_size;

        /**** SOUND HEADER CHUNK ****/

        try {
            media_info_size = (qtmovie.getQtstream().readUint32());
        } catch (Exception e) {
            System.err.println("(read_chunk_minf) error reading media_info_size - possibly number too large");
            media_info_size = 0;
        }

        if (media_info_size != 16) {
            System.err.println("unexpected size in media info\n");
            return 0;
        }
        if (qtmovie.getQtstream().readUint32() != MakeFourCC32(115, 109, 104, 100))    // "smhd" ascii values
        {
            System.err.println("not a sound header! can't handle this.");
            return 0;
        }
        /* now skip the rest */
        qtmovie.getQtstream().skip(16 - 8);
        // FIXME WRONG
        int size_remaining = chunk_len - 8;
        size_remaining -= 16;
        /****/

        /**** DINF CHUNK ****/

        int dinf_size;
        try {
            dinf_size = (qtmovie.getQtstream().readUint32());
        } catch (Exception e) {
            System.err.println("(read_chunk_minf) error reading dinf_size - possibly number too large");
            dinf_size = 0;
        }

        if (qtmovie.getQtstream().readUint32() != MakeFourCC32(100, 105, 110, 102))    // "dinf" ascii values
        {
            System.err.println("expected dinf, didn't get it.");
            return 0;
        }
        /* skip it */
        qtmovie.getQtstream().skip(dinf_size - 8);
        size_remaining -= dinf_size;
        /****/


        /**** SAMPLE TABLE ****/
        int stbl_size;
        try {
            stbl_size = (qtmovie.getQtstream().readUint32());
        } catch (Exception e) {
            System.err.println("(read_chunk_minf) error reading stbl_size - possibly number too large");
            stbl_size = 0;
        }

        if (qtmovie.getQtstream().readUint32() != MakeFourCC32(115, 116, 98, 108))    // "stbl" ascii values
        {
            System.err.println("expected stbl, didn't get it.");
            return 0;
        }
        if (read_chunk_stbl(qtmovie, stbl_size) == 0)
            return 0;
        size_remaining -= stbl_size;

        if (size_remaining != 0) {
            System.err.println("(read_chunk_minf) - size remaining?");
            qtmovie.getQtstream().skip(size_remaining);
        }

        return 1;
    }

    static int read_chunk_mdia(QTMovieT qtmovie, int chunk_len) throws IOException {
        int size_remaining = chunk_len - 8; // FIXME WRONG

        while (size_remaining != 0) {
            int sub_chunk_len;

            try {
                sub_chunk_len = (qtmovie.getQtstream().readUint32());
            } catch (Exception e) {
                System.err.println("(read_chunk_mdia) error reading sub_chunk_len - possibly number too large");
                sub_chunk_len = 0;
            }

            if (sub_chunk_len <= 1 || sub_chunk_len > size_remaining) {
                System.err.println("strange size for chunk inside mdia\n");
                return 0;
            }

            int sub_chunk_id = qtmovie.getQtstream().readUint32();

            if (sub_chunk_id == MakeFourCC32(109, 100, 104, 100))    // fourcc equals mdhd
            {
                read_chunk_mdhd(qtmovie, sub_chunk_len);
            } else if (sub_chunk_id == MakeFourCC32(104, 100, 108, 114))    // fourcc equals hdlr
            {
                read_chunk_hdlr(qtmovie, sub_chunk_len);
            } else if (sub_chunk_id == MakeFourCC32(109, 105, 110, 102))    // fourcc equals minf
            {
                if (read_chunk_minf(qtmovie, sub_chunk_len) == 0)
                    return 0;
            } else {
                System.err.println("(mdia) unknown chunk id: " + SplitFourCC(sub_chunk_id));
                return 0;
            }

            size_remaining -= sub_chunk_len;
        }

        return 1;
    }

    /* 'trak' - a movie track - contains other atoms */
    static int read_chunk_trak(QTMovieT qtmovie, int chunk_len) throws IOException {
        int size_remaining = chunk_len - 8; // FIXME WRONG

        while (size_remaining != 0) {
            int sub_chunk_len;

            try {
                sub_chunk_len = (qtmovie.getQtstream().readUint32());
            } catch (Exception e) {
                System.err.println("(read_chunk_trak) error reading sub_chunk_len - possibly number too large");
                sub_chunk_len = 0;
            }

            if (sub_chunk_len <= 1 || sub_chunk_len > size_remaining) {
                System.err.println("strange size for chunk inside trak");
                return 0;
            }

            int sub_chunk_id = qtmovie.getQtstream().readUint32();

            if (sub_chunk_id == MakeFourCC32(116, 107, 104, 100))    // fourcc equals tkhd
            {
                read_chunk_tkhd(qtmovie, sub_chunk_len);
            } else if (sub_chunk_id == MakeFourCC32(109, 100, 105, 97))    // fourcc equals mdia
            {
                if (read_chunk_mdia(qtmovie, sub_chunk_len) == 0)
                    return 0;
            } else if (sub_chunk_id == MakeFourCC32(101, 100, 116, 115))    // fourcc equals edts
            {
                read_chunk_edts(qtmovie, sub_chunk_len);
            } else {
                System.err.println("(trak) unknown chunk id: " + SplitFourCC(sub_chunk_id));
                return 0;
            }

            size_remaining -= sub_chunk_len;
        }

        return 1;
    }

    /* 'mvhd' movie header atom */
    static void read_chunk_mvhd(QTMovieT qtmovie, int chunk_len) throws IOException {
        /* don't need anything from here atm, skip */
        read_chunk_edts(qtmovie, chunk_len);
    }

    /* 'udta' user data.. contains tag info */
    static void read_chunk_udta(QTMovieT qtmovie, int chunk_len) throws IOException {
        /* don't need anything from here atm, skip */
        read_chunk_edts(qtmovie, chunk_len);
    }

    /* 'iods' */
    static void read_chunk_iods(QTMovieT qtmovie, int chunk_len) throws IOException {
        /* don't need anything from here atm, skip */
        read_chunk_edts(qtmovie, chunk_len);
    }

    /* 'moov' movie atom - contains other atoms */
    static int read_chunk_moov(QTMovieT qtmovie, int chunk_len) throws IOException {
        int size_remaining = chunk_len - 8; // FIXME WRONG

        while (size_remaining != 0) {
            int sub_chunk_len;

            try {
                sub_chunk_len = (qtmovie.getQtstream().readUint32());
            } catch (Exception e) {
                System.err.println("(read_chunk_moov) error reading sub_chunk_len - possibly number too large");
                sub_chunk_len = 0;
            }

            if (sub_chunk_len <= 1 || sub_chunk_len > size_remaining) {
                System.err.println("strange size for chunk inside moov");
                return 0;
            }

            int sub_chunk_id = qtmovie.getQtstream().readUint32();

            if (sub_chunk_id == MakeFourCC32(109, 118, 104, 100))    // fourcc equals mvhd
            {
                read_chunk_mvhd(qtmovie, sub_chunk_len);
            } else if (sub_chunk_id == MakeFourCC32(116, 114, 97, 107))    // fourcc equals trak
            {
                if (read_chunk_trak(qtmovie, sub_chunk_len) == 0)
                    return 0;
            } else if (sub_chunk_id == MakeFourCC32(117, 100, 116, 97))    // fourcc equals udta
            {
                read_chunk_udta(qtmovie, sub_chunk_len);
            } else if (sub_chunk_id == MakeFourCC32(101, 108, 115, 116))    // fourcc equals elst
            {
                read_chunk_elst(qtmovie, sub_chunk_len);
            } else if (sub_chunk_id == MakeFourCC32(105, 111, 100, 115))    // fourcc equals iods
            {
                read_chunk_iods(qtmovie, sub_chunk_len);
            } else if (sub_chunk_id == MakeFourCC32(102, 114, 101, 101))     // fourcc equals free
            {
                qtmovie.getQtstream().skip(sub_chunk_len - 8); // FIXME not 8
            } else {
                System.err.println("(moov) unknown chunk id: " + SplitFourCC(sub_chunk_id));
                return 0;
            }

            size_remaining -= sub_chunk_len;
        }

        return 1;
    }

    static void read_chunk_mdat(QTMovieT qtmovie, int chunk_len, int skip_mdat) throws IOException {
        int size_remaining = chunk_len - 8; // FIXME WRONG

        if (size_remaining == 0)
            return;

        qtmovie.getRes().setMdatLen(size_remaining);
        if (skip_mdat != 0) {
            qtmovie.setSavedMdatPos(qtmovie.getQtstream().getCurrentPos());
            qtmovie.getQtstream().skip(size_remaining);
        }
    }

    static int set_saved_mdat(QTMovieT qtmovie) {
        // returns as follows
        // 1 - all ok
        // 2 - do not have valid saved mdat pos
        // 3 - have valid saved mdat pos, but cannot seek there - need to close/reopen stream

        if (qtmovie.getSavedMdatPos() == -1) {
            System.err.println("stream contains mdat before moov but is not seekable");
            return 2;
        }

        return 3;
    }
}


