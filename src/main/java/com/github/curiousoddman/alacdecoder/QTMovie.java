/*
 ** QTMovieT.java
 **
 ** Copyright (c) 2011 Peter McQuillan
 **
 ** All Rights Reserved.
 **
 ** Distributed under the BSD Software License (see license.txt)
 **
 */

package com.github.curiousoddman.alacdecoder;

import com.github.curiousoddman.alacdecoder.data.ChunkInfo;
import com.github.curiousoddman.alacdecoder.stream.DataInputStreamWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.io.IOException;

import static com.github.curiousoddman.alacdecoder.DemuxUtils.makeFourCC;
import static com.github.curiousoddman.alacdecoder.DemuxUtils.splitFourCC;

@Data
@RequiredArgsConstructor
public class QTMovie {
    private final DataInputStreamWrapper qtstream;
    private DemuxRes res;
    private int savedMdatPos = 0;

    public int read(DemuxRes demuxRes) throws IOException {
        /* construct the stream */
        res = demuxRes;

        // reset demuxResT	TODO

        /* read the chunks */
        int found_mdat = 0;
        int found_moov = 0;
        while (true) {
            int chunk_len;

            try {
                chunk_len = getQtstream().readUint32();
            } catch (Exception e) {
                System.err.println("(top) error reading chunk_len - possibly number too large");
                chunk_len = 1;
            }

//            if (StreamUtils.stream_eof(getQtstream()) != 0) {
//                return 0;
//            }

            if (chunk_len == 1) {
                System.err.println("need 64bit support");
                return 0;
            }
            int chunk_id = getQtstream().readUint32();

            if (chunk_id == makeFourCC(102, 116, 121, 112))    // fourcc equals ftyp
            {
                read_chunk_ftyp(chunk_len);
            } else if (chunk_id == makeFourCC(109, 111, 111, 118))    // fourcc equals moov
            {
                if (read_chunk_moov(chunk_len) == 0) return 0; // failed to read moov, can't do anything
                if (found_mdat != 0) {
                    return set_saved_mdat();
                }
                found_moov = 1;
            }
            /* if we hit mdat before we've found moov, record the position
             * and move on. We can then come back to mdat later.
             * This presumes the stream supports seeking backwards.
             */
            else if (chunk_id == makeFourCC(109, 100, 97, 116))    // fourcc equals mdat
            {
                int not_found_moov = 0;
                if (found_moov == 0) not_found_moov = 1;
                read_chunk_mdat(chunk_len, not_found_moov);
                if (found_moov != 0) {
                    return 1;
                }
                found_mdat = 1;
            }
            /*  these following atoms can be skipped !!!! */
            else if (chunk_id == makeFourCC(102, 114, 101, 101))    // fourcc equals free
            {
                getQtstream().skip(chunk_len - 8); // FIXME not 8
            } else if (chunk_id == makeFourCC(106, 117, 110, 107))     // fourcc equals junk
            {
                getQtstream().skip(chunk_len - 8); // FIXME not 8
            } else {
                System.err.println("(top) unknown chunk id: " + splitFourCC(chunk_id));
                return 0;
            }
        }
    }

    /* chunk handlers */
    void read_chunk_ftyp(int chunk_len) throws IOException {
        int size_remaining = chunk_len - 8; // FIXME: can't hardcode 8, size may be 64bit

        int type = getQtstream().readUint32();
        size_remaining -= 4;

        if (type != makeFourCC(77, 52, 65, 32))        // "M4A " ascii values
        {
            System.err.println("not M4A file");
            return;
        }
        getQtstream().readUint32();
        size_remaining -= 4;

        /* compatible brands */
        while (size_remaining != 0) {
            /* unused */
            /*fourcc_t cbrand =*/
            getQtstream().readUint32();
            size_remaining -= 4;
        }
    }

    void read_chunk_tkhd(int chunk_len) throws IOException {
        /* don't need anything from here atm, skip */
        read_chunk_edts(chunk_len);
    }

    void read_chunk_mdhd(int chunk_len) throws IOException {
        /* don't need anything from here atm, skip */
        read_chunk_edts(chunk_len);
    }

    void read_chunk_edts(int chunk_len) throws IOException {
        /* don't need anything from here atm, skip */
        int size_remaining = chunk_len - 8; // FIXME WRONG

        getQtstream().skip(size_remaining);
    }

    void read_chunk_elst(int chunk_len) throws IOException {
        /* don't need anything from here atm, skip */
        read_chunk_edts(chunk_len);
    }

    /* media handler inside mdia */
    void read_chunk_hdlr(int chunk_len) throws IOException {

        /* version */
        getQtstream().readUint8();
        // FIXME WRONG
        int size_remaining = chunk_len - 8;
        size_remaining -= 1;
        /* flags */
        getQtstream().readUint8();
        getQtstream().readUint8();
        getQtstream().readUint8();
        size_remaining -= 3;

        /* component type */
        int comptype = getQtstream().readUint32();
        int compsubtype = getQtstream().readUint32();
        size_remaining -= 8;

        /* component manufacturer */
        getQtstream().readUint32();
        size_remaining -= 4;

        /* flags */
        getQtstream().readUint32();
        getQtstream().readUint32();
        size_remaining -= 8;

        /* name */
        int strlen = getQtstream().readUint8();

        /*
         ** rewrote this to handle case where we actually read more than required
         ** so here we work out how much we need to read first
         */

        size_remaining -= 1;

        getQtstream().skip(size_remaining);
    }

    int read_chunk_stsd() throws IOException {

        /* version */
        getQtstream().readUint8();
        /* flags */
        getQtstream().readUint8();
        getQtstream().readUint8();
        getQtstream().readUint8();

        int numentries = 0;
        try {
            numentries = (getQtstream().readUint32());
        } catch (Exception e) {
            System.err.println("(read_chunk_stsd) error reading numentries - possibly number too large");
        }


        if (numentries != 1) {
            System.err.println("only expecting one entry in sample description atom!");
            return 0;
        }

        for (int i = 0; i < numentries; i++) {
            /* parse the alac atom contained within the stsd atom */

            int entry_size = (getQtstream().readUint32());
            getRes().setFormat(getQtstream().readUint32());
            int entry_remaining = entry_size;
            entry_remaining -= 8;

            if (getRes().getFormat() != makeFourCC(97, 108, 97, 99))    // "alac" ascii values
            {
                System.err.println("(read_chunk_stsd) error reading description atom - expecting alac, got " + splitFourCC(getRes().getFormat()));
                return 0;
            }

            /* sound info: */

            getQtstream().skip(6); // reserved
            entry_remaining -= 6;

            int version = getQtstream().readUint16();

            if (version != 1) System.err.println("unknown version??");
            entry_remaining -= 2;

            /* revision level */
            getQtstream().readUint16();
            /* vendor */
            getQtstream().readUint32();
            entry_remaining -= 6;

            /* EH?? spec doesn't say theres an extra 16 bits here.. but there is! */
            getQtstream().readUint16();
            entry_remaining -= 2;

            /* skip 4 - this is the top level num of channels and bits per sample */
            getQtstream().skip(4);
            entry_remaining -= 4;

            /* compression id */
            getQtstream().readUint16();
            /* packet size */
            getQtstream().readUint16();
            entry_remaining -= 4;

            /* skip 4 - this is the top level sample rate */
            getQtstream().skip(4);
            entry_remaining -= 4;

            /* remaining is codec data */

            /* 12 = audio format atom, 8 = padding */
            getRes().setCodecDataLen(entry_remaining + 12 + 8);

            if (getRes().getCodecDataLen() > getRes().getCodecData().length) {
                System.err.println("(read_chunk_stsd) unexpected codec data length read from atom " + getRes().getCodecDataLen());
                return 0;
            }

            for (int count = 0; count < getRes().getCodecDataLen(); count++) {
                getRes().getCodecData()[count] = 0;
            }

            /* audio format atom */
            getRes().getCodecData()[0] = 0x0c000000;
            getRes().getCodecData()[1] = makeFourCC(97, 109, 114, 102);        // "amrf" ascii values
            getRes().getCodecData()[2] = makeFourCC(99, 97, 108, 97);        // "cala" ascii values

            getQtstream().read(entry_remaining, getRes().getCodecData(), 12);    // codecdata buffer should be +12
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

            getRes().setSampleSize((getRes().getCodecData()[ptrIndex] & 0xff));

            ptrIndex = 33;    // position of num of channels

            getRes().setNumChannels((getRes().getCodecData()[ptrIndex] & 0xff));

            ptrIndex = 44;        // position of sample rate within codec data buffer

            getRes().setSampleRate((((getRes().getCodecData()[ptrIndex] & 0xff) << 24) | ((getRes().getCodecData()[ptrIndex + 1] & 0xff) << 16) | ((getRes().getCodecData()[ptrIndex + 2] & 0xff) << 8) | (getRes().getCodecData()[ptrIndex + 3] & 0xff)));

            if (entry_remaining != 0)    // was comparing to null
                getQtstream().skip(entry_remaining);

            getRes().setFormatRead(1);
            if (getRes().getFormat() != makeFourCC(97, 108, 97, 99))        // "alac" ascii values
            {
                return 0;
            }
        }

        return 1;
    }

    void read_chunk_stts(int chunk_len) throws IOException {

        /* version */
        getQtstream().readUint8();
        // FIXME WRONG
        int size_remaining = chunk_len - 8;
        size_remaining -= 1;
        /* flags */
        getQtstream().readUint8();
        getQtstream().readUint8();
        getQtstream().readUint8();
        size_remaining -= 3;

        int numentries = 0;
        try {
            numentries = (getQtstream().readUint32());
        } catch (Exception e) {
            System.err.println("(read_chunk_stts) error reading numentries - possibly number too large");
        }

        size_remaining -= 4;

        getRes().setNumTimeToSamples(numentries);

        for (int i = 0; i < numentries; i++) {
            getRes().getTimeToSample().get(i).setSampleCount(getQtstream().readUint32());
            getRes().getTimeToSample().get(i).setSampleDuration(getQtstream().readUint32());
            size_remaining -= 8;
        }

        if (size_remaining != 0) {
            System.err.println("(read_chunk_stts) size remaining?");
            getQtstream().skip(size_remaining);
        }
    }

    void read_chunk_stsz(int chunk_len) throws IOException {

        /* version */
        getQtstream().readUint8();
        // FIXME WRONG
        int size_remaining = chunk_len - 8;
        size_remaining -= 1;
        /* flags */
        getQtstream().readUint8();
        getQtstream().readUint8();
        getQtstream().readUint8();
        size_remaining -= 3;

        /* default sample size */
        int uniform_size = (getQtstream().readUint32());
        int i;
        if (uniform_size != 0) {
            /*
             ** Normally files have intiable sample sizes, this handles the case where
             ** they are all the same size
             */

            int uniform_num = (getQtstream().readUint32());

            getRes().setSampleByteSize(new int[uniform_num]);

            for (i = 0; i < uniform_num; i++) {
                getRes().getSampleByteSize()[i] = uniform_size;
            }
            return;
        }
        size_remaining -= 4;

        int numentries = 0;
        try {
            numentries = (getQtstream().readUint32());
        } catch (Exception e) {
            System.err.println("(read_chunk_stsz) error reading numentries - possibly number too large");
        }

        size_remaining -= 4;

        getRes().setSampleByteSize(new int[numentries]);

        for (i = 0; i < numentries; i++) {
            getRes().getSampleByteSize()[i] = (getQtstream().readUint32());

            size_remaining -= 4;
        }

        if (size_remaining != 0) {
            System.err.println("(read_chunk_stsz) size remaining?");
            getQtstream().skip(size_remaining);
        }
    }

    int read_chunk_stbl(int chunk_len) throws IOException {
        int size_remaining = chunk_len - 8; // FIXME WRONG

        while (size_remaining != 0) {
            int sub_chunk_len;

            try {
                sub_chunk_len = (getQtstream().readUint32());
            } catch (Exception e) {
                System.err.println("(read_chunk_stbl) error reading sub_chunk_len - possibly number too large");
                sub_chunk_len = 0;
            }

            if (sub_chunk_len <= 1 || sub_chunk_len > size_remaining) {
                System.err.println("strange size for chunk inside stbl " + sub_chunk_len + " (remaining: " + size_remaining + ")");
                return 0;
            }

            int sub_chunk_id = getQtstream().readUint32();

            if (sub_chunk_id == makeFourCC(115, 116, 115, 100))    // fourcc equals stsd
            {
                if (read_chunk_stsd() == 0) return 0;
            } else if (sub_chunk_id == makeFourCC(115, 116, 116, 115))    // fourcc equals stts
            {
                read_chunk_stts(sub_chunk_len);
            } else if (sub_chunk_id == makeFourCC(115, 116, 115, 122))    // fourcc equals stsz
            {
                read_chunk_stsz(sub_chunk_len);
            } else if (sub_chunk_id == makeFourCC(115, 116, 115, 99))    // fourcc equals stsc
            {
                read_chunk_stsc();
            } else if (sub_chunk_id == makeFourCC(115, 116, 99, 111))    // fourcc equals stco
            {
                read_chunk_stco();
            } else {
                System.err.println("(stbl) unknown chunk id: " + splitFourCC(sub_chunk_id));
                return 0;
            }

            size_remaining -= sub_chunk_len;
        }

        return 1;
    }

    /*
     * chunk to offset box
     */
    private void read_chunk_stco() throws IOException {
        //skip header and size
        DataInputStreamWrapper stream = getQtstream();
        stream.skip(4);

        int num_entries = stream.readUint32();

        getRes().setStco(new int[num_entries]);
        for (int i = 0; i < num_entries; i++) {
            getRes().getStco()[i] = stream.readUint32();
        }
    }

    /*
     * sample to chunk box
     */
    private void read_chunk_stsc() throws IOException {
        //skip header and size
        DataInputStreamWrapper stream = getQtstream();
        //skip version and other junk
        stream.skip(4);
        int num_entries = stream.readUint32();
        getRes().setStsc(new ChunkInfo[num_entries]);
        for (int i = 0; i < num_entries; i++) {
            ChunkInfo entry = new ChunkInfo();
            entry.setFirstChunk(stream.readUint32());
            entry.setSamplesPerChunk(stream.readUint32());
            entry.setSampleDescIndex(stream.readUint32());
            getRes().getStsc()[i] = entry;
        }
    }

    int read_chunk_minf(int chunk_len) throws IOException {
        int media_info_size;

        /**** SOUND HEADER CHUNK ****/

        try {
            media_info_size = (getQtstream().readUint32());
        } catch (Exception e) {
            System.err.println("(read_chunk_minf) error reading media_info_size - possibly number too large");
            media_info_size = 0;
        }

        if (media_info_size != 16) {
            System.err.println("unexpected size in media info\n");
            return 0;
        }
        if (getQtstream().readUint32() != makeFourCC(115, 109, 104, 100))    // "smhd" ascii values
        {
            System.err.println("not a sound header! can't handle this.");
            return 0;
        }
        /* now skip the rest */
        getQtstream().skip(16 - 8);
        // FIXME WRONG
        int size_remaining = chunk_len - 8;
        size_remaining -= 16;
        /****/

        /**** DINF CHUNK ****/

        int dinf_size;
        try {
            dinf_size = (getQtstream().readUint32());
        } catch (Exception e) {
            System.err.println("(read_chunk_minf) error reading dinf_size - possibly number too large");
            dinf_size = 0;
        }

        if (getQtstream().readUint32() != makeFourCC(100, 105, 110, 102))    // "dinf" ascii values
        {
            System.err.println("expected dinf, didn't get it.");
            return 0;
        }
        /* skip it */
        getQtstream().skip(dinf_size - 8);
        size_remaining -= dinf_size;
        /****/


        /**** SAMPLE TABLE ****/
        int stbl_size;
        try {
            stbl_size = (getQtstream().readUint32());
        } catch (Exception e) {
            System.err.println("(read_chunk_minf) error reading stbl_size - possibly number too large");
            stbl_size = 0;
        }

        if (getQtstream().readUint32() != makeFourCC(115, 116, 98, 108))    // "stbl" ascii values
        {
            System.err.println("expected stbl, didn't get it.");
            return 0;
        }
        if (read_chunk_stbl(stbl_size) == 0) return 0;
        size_remaining -= stbl_size;

        if (size_remaining != 0) {
            System.err.println("(read_chunk_minf) - size remaining?");
            getQtstream().skip(size_remaining);
        }

        return 1;
    }

    int read_chunk_mdia(int chunk_len) throws IOException {
        int size_remaining = chunk_len - 8; // FIXME WRONG

        while (size_remaining != 0) {
            int sub_chunk_len;

            try {
                sub_chunk_len = (getQtstream().readUint32());
            } catch (Exception e) {
                System.err.println("(read_chunk_mdia) error reading sub_chunk_len - possibly number too large");
                sub_chunk_len = 0;
            }

            if (sub_chunk_len <= 1 || sub_chunk_len > size_remaining) {
                System.err.println("strange size for chunk inside mdia\n");
                return 0;
            }

            int sub_chunk_id = getQtstream().readUint32();

            if (sub_chunk_id == makeFourCC(109, 100, 104, 100))    // fourcc equals mdhd
            {
                read_chunk_mdhd(sub_chunk_len);
            } else if (sub_chunk_id == makeFourCC(104, 100, 108, 114))    // fourcc equals hdlr
            {
                read_chunk_hdlr(sub_chunk_len);
            } else if (sub_chunk_id == makeFourCC(109, 105, 110, 102))    // fourcc equals minf
            {
                if (read_chunk_minf(sub_chunk_len) == 0) return 0;
            } else {
                System.err.println("(mdia) unknown chunk id: " + splitFourCC(sub_chunk_id));
                return 0;
            }

            size_remaining -= sub_chunk_len;
        }

        return 1;
    }

    /* 'trak' - a movie track - contains other atoms */
    int read_chunk_trak(int chunk_len) throws IOException {
        int size_remaining = chunk_len - 8; // FIXME WRONG

        while (size_remaining != 0) {
            int sub_chunk_len;

            try {
                sub_chunk_len = (getQtstream().readUint32());
            } catch (Exception e) {
                System.err.println("(read_chunk_trak) error reading sub_chunk_len - possibly number too large");
                sub_chunk_len = 0;
            }

            if (sub_chunk_len <= 1 || sub_chunk_len > size_remaining) {
                System.err.println("strange size for chunk inside trak");
                return 0;
            }

            int sub_chunk_id = getQtstream().readUint32();

            if (sub_chunk_id == makeFourCC(116, 107, 104, 100))    // fourcc equals tkhd
            {
                read_chunk_tkhd(sub_chunk_len);
            } else if (sub_chunk_id == makeFourCC(109, 100, 105, 97))    // fourcc equals mdia
            {
                if (read_chunk_mdia(sub_chunk_len) == 0) return 0;
            } else if (sub_chunk_id == makeFourCC(101, 100, 116, 115))    // fourcc equals edts
            {
                read_chunk_edts(sub_chunk_len);
            } else {
                System.err.println("(trak) unknown chunk id: " + splitFourCC(sub_chunk_id));
                return 0;
            }

            size_remaining -= sub_chunk_len;
        }

        return 1;
    }

    /* 'mvhd' movie header atom */
    void read_chunk_mvhd(int chunk_len) throws IOException {
        /* don't need anything from here atm, skip */
        read_chunk_edts(chunk_len);
    }

    /* 'udta' user data.. contains tag info */
    void read_chunk_udta(int chunk_len) throws IOException {
        /* don't need anything from here atm, skip */
        read_chunk_edts(chunk_len);
    }

    /* 'iods' */
    void read_chunk_iods(int chunk_len) throws IOException {
        /* don't need anything from here atm, skip */
        read_chunk_edts(chunk_len);
    }

    /* 'moov' movie atom - contains other atoms */
    int read_chunk_moov(int chunk_len) throws IOException {
        int size_remaining = chunk_len - 8; // FIXME WRONG

        while (size_remaining != 0) {
            int sub_chunk_len;

            try {
                sub_chunk_len = (getQtstream().readUint32());
            } catch (Exception e) {
                System.err.println("(read_chunk_moov) error reading sub_chunk_len - possibly number too large");
                sub_chunk_len = 0;
            }

            if (sub_chunk_len <= 1 || sub_chunk_len > size_remaining) {
                System.err.println("strange size for chunk inside moov");
                return 0;
            }

            int sub_chunk_id = getQtstream().readUint32();

            if (sub_chunk_id == makeFourCC(109, 118, 104, 100))    // fourcc equals mvhd
            {
                read_chunk_mvhd(sub_chunk_len);
            } else if (sub_chunk_id == makeFourCC(116, 114, 97, 107))    // fourcc equals trak
            {
                if (read_chunk_trak(sub_chunk_len) == 0) return 0;
            } else if (sub_chunk_id == makeFourCC(117, 100, 116, 97))    // fourcc equals udta
            {
                read_chunk_udta(sub_chunk_len);
            } else if (sub_chunk_id == makeFourCC(101, 108, 115, 116))    // fourcc equals elst
            {
                read_chunk_elst(sub_chunk_len);
            } else if (sub_chunk_id == makeFourCC(105, 111, 100, 115))    // fourcc equals iods
            {
                read_chunk_iods(sub_chunk_len);
            } else if (sub_chunk_id == makeFourCC(102, 114, 101, 101))     // fourcc equals free
            {
                getQtstream().skip(sub_chunk_len - 8); // FIXME not 8
            } else {
                System.err.println("(moov) unknown chunk id: " + splitFourCC(sub_chunk_id));
                return 0;
            }

            size_remaining -= sub_chunk_len;
        }

        return 1;
    }

    void read_chunk_mdat(int chunk_len, int skip_mdat) throws IOException {
        int size_remaining = chunk_len - 8; // FIXME WRONG

        if (size_remaining == 0) return;

        getRes().setMdatLen(size_remaining);
        if (skip_mdat != 0) {
            setSavedMdatPos(getQtstream().getCurrentPos());
            getQtstream().skip(size_remaining);
        }
    }

    int set_saved_mdat() {
        // returns as follows
        // 1 - all ok
        // 2 - do not have valid saved mdat pos
        // 3 - have valid saved mdat pos, but cannot seek there - need to close/reopen stream

        if (getSavedMdatPos() == -1) {
            System.err.println("stream contains mdat before moov but is not seekable");
            return 2;
        }

        return 3;
    }
}