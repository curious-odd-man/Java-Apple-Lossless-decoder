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
import com.github.curiousoddman.alacdecoder.data.SampleInfo;
import com.github.curiousoddman.alacdecoder.stream.DataInputStreamWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Arrays;

import static com.github.curiousoddman.alacdecoder.DemuxUtils.makeFourCC;
import static com.github.curiousoddman.alacdecoder.DemuxUtils.splitFourCC;

@Data
@Slf4j
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
        boolean foundMdat = false;
        boolean foundMoov = false;
        while (true) {
            int chunkLen = getChunkLen();
            int chunkId = qtstream.readUint32();
            if (chunkId == makeFourCC(102, 116, 121, 112)) {  // fourcc equals ftyp
                readChunkFtyp(chunkLen);
            } else if (chunkId == makeFourCC(109, 111, 111, 118)) {   // fourcc equals moov
                if (readChunkMoov(chunkLen) == 0) {
                    throw new IllegalStateException("Failed to read moov");
                }
                if (foundMdat) {
                    return setSavedMdat();
                }
                foundMoov = true;
            }
            /* if we hit mdat before we've found moov, record the position
             * and move on. We can then come back to mdat later.
             * This presumes the stream supports seeking backwards.
             */
            else if (chunkId == makeFourCC(109, 100, 97, 116)) {    // fourcc equals mdat
                boolean notFoundMoov = !foundMoov;
                readChunkMdat(chunkLen, notFoundMoov);
                if (foundMoov) {
                    return 1;
                }
                foundMdat = true;
            }
            /*  these following atoms can be skipped !!!! */
            else if (chunkId == makeFourCC(102, 114, 101, 101)) {  // fourcc equals free
                qtstream.skip(chunkLen - 8); // FIXME not 8
            } else if (chunkId == makeFourCC(106, 117, 110, 107)) {// fourcc equals junk
                qtstream.skip(chunkLen - 8); // FIXME not 8
            } else {
                throw new IllegalStateException("(top) unknown chunk id: " + splitFourCC(chunkId));
            }
        }
    }

    private int getChunkLen() {
        int chunkLen;

        try {
            chunkLen = qtstream.readUint32();
        } catch (Exception e) {
            log.error("(top) error reading chunkLen - possibly number too large", e);
            chunkLen = 1;
        }

        if (chunkLen == 1) {
            throw new IllegalStateException("need 64bit support");
        }
        return chunkLen;
    }

    /* chunk handlers */
    void readChunkFtyp(int chunkLen) throws IOException {
        int sizeRemaining = chunkLen - 8; // FIXME: can't hardcode 8, size may be 64bit

        int type = qtstream.readUint32();
        sizeRemaining -= 4;

        if (type != makeFourCC(77, 52, 65, 32)) {     // "M4A " ascii values
            throw new UnsupportedFormatException("Only m4a files are supported");
        }

        /* compatible brands */
        /* unused */
        /*fourcc_t cbrand =*/
        qtstream.skip(sizeRemaining);
    }

    void readChunkTkhd(int chunkLen) throws IOException {
        skipChunkMinus8(chunkLen);
    }

    void readChunkMdhd(int chunkLen) throws IOException {
        skipChunkMinus8(chunkLen);
    }

    void readChunkEdts(int chunkLen) throws IOException {
        skipChunkMinus8(chunkLen);
    }

    void readChunkElst(int chunkLen) throws IOException {
        skipChunkMinus8(chunkLen);
    }

    /* media handler inside mdia */
    void readChunkHandler(int chunkLen) throws IOException {
        skipChunkMinus8(chunkLen);
    }

    void readChunkStsd() throws IOException {
        /* version 1 byte*/
        /* flags  3 bytes */
        qtstream.skip(4);
        if (qtstream.readUint32() != 1) {
            throw new UnsupportedFormatException("only expecting one entry in sample description atom!");
        }

        /* parse the alac atom contained within the stsd atom */
        int entrySize = qtstream.readUint32();
        res.setFormat(qtstream.readUint32());
        int entryRemaining = entrySize;
        entryRemaining -= 8;

        if (res.getFormat() != makeFourCC(97, 108, 97, 99)) {    // "alac" ascii values
            throw new UnsupportedFormatException("(read_chunk_stsd) error reading description atom - expecting alac, got " + splitFourCC(res.getFormat()));
        }

        /* sound info: */
        qtstream.skip(6); // reserved
        entryRemaining -= 6;

        int version = qtstream.readUint16();
        if (version != 1) {
            throw new UnsupportedFormatException("unknown version: " + version);
        }
        entryRemaining -= 2;

        /* revision level 2 bytes*/
        /* vendor 4 bytes*/
        /* EH?? spec doesn't say theres an extra 16 bits here.. but there is! 2 bytes */
        /* skip 4 - this is the top level num of channels and bits per sample 4 bytes*/
        /* compression id  2 bytes*/
        /* packet size 2 bytes*/
        /* skip 4 - this is the top level sample rate */
        int toSkip = 20;
        qtstream.skip(toSkip);
        entryRemaining -= toSkip;

        /* remaining is codec data */
        /* 12 = audio format atom, 8 = padding */
        res.setCodecDataLen(entryRemaining + 12 + 8);

        int[] codecData = new int[res.getCodecDataLen()];
        res.setCodecData(codecData);
        Arrays.fill(codecData, 0);

        /* audio format atom */
        codecData[0] = 0x0c000000;
        codecData[1] = makeFourCC(97, 109, 114, 102);        // "amrf" ascii values
        codecData[2] = makeFourCC(99, 97, 108, 97);        // "cala" ascii values

        qtstream.read(entryRemaining, codecData, 12);    // codecdata buffer should be +12
        entryRemaining -= entryRemaining;

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
        res.setSampleSize((codecData[ptrIndex] & 0xff));

        ptrIndex = 33;    // position of num of channels
        res.setNumChannels((codecData[ptrIndex] & 0xff));

        ptrIndex = 44;        // position of sample rate within codec data buffer
        res.setSampleRate((((codecData[ptrIndex] & 0xff) << 24) | ((codecData[ptrIndex + 1] & 0xff) << 16) | ((codecData[ptrIndex + 2] & 0xff) << 8) | (codecData[ptrIndex + 3] & 0xff)));

        qtstream.skip(entryRemaining);
        if (res.getFormat() != makeFourCC(97, 108, 97, 99)) {        // "alac" ascii values
            throw new UnsupportedFormatException("Expected alac, got something else...");
        }
    }

    void readChunkStts(int chunkLen) throws IOException {
        /* version 1 byte*/
        /* flags  3 bytes */
        qtstream.skip(4);
        // FIXME WRONG
        int sizeRemaining = chunkLen - 8;
        sizeRemaining -= 4;

        int numEntries = qtstream.readUint32();
        sizeRemaining -= 4;

        for (int i = 0; i < numEntries; i++) {
            SampleInfo sampleInfo = new SampleInfo();
            sampleInfo.setSampleCount(qtstream.readUint32());
            sampleInfo.setSampleDuration(qtstream.readUint32());
            res.getTimeToSample().add(sampleInfo);
            sizeRemaining -= 8;
        }

        if (sizeRemaining != 0) {
            log.error("(read_chunk_stts) size remaining is not 0");
            qtstream.skip(sizeRemaining);
        }
    }

    void readChunkStsz(int chunkLen) throws IOException {
        /* version 1 byte*/
        /* flags  3 bytes */
        qtstream.skip(4);
        int sizeRemaining = chunkLen - 8 - 4;

        /* default sample size */
        int uniformSize = qtstream.readUint32();
        if (uniformSize != 0) {
            /*
             ** Normally files have intiable sample sizes, this handles the case where
             ** they are all the same size
             */

            int sampleCount = qtstream.readUint32();
            int[] sampleByteSize = new int[sampleCount];
            Arrays.fill(sampleByteSize, uniformSize);
            res.setSampleByteSize(sampleByteSize);
            return;
        }
        sizeRemaining -= 4;

        int numEntries = qtstream.readUint32();
        sizeRemaining -= 4;

        res.setSampleByteSize(new int[numEntries]);

        for (int i = 0; i < numEntries; i++) {
            res.getSampleByteSize()[i] = qtstream.readUint32();
            sizeRemaining -= 4;
        }

        if (sizeRemaining != 0) {
            log.error("(read_chunk_stsz) size remaining?");
            qtstream.skip(sizeRemaining);
        }
    }

    void readChunkStbl(int chunkLen) throws IOException {
        int sizeRemaining = chunkLen - 8; // FIXME WRONG

        while (sizeRemaining != 0) {
            int subChunkLen = qtstream.readUint32();

            if (subChunkLen <= 1 || subChunkLen > sizeRemaining) {
                throw new UnsupportedFormatException("strange size for chunk inside stbl " + subChunkLen + " (remaining: " + sizeRemaining + ")");
            }

            int subChunkId = qtstream.readUint32();

            if (subChunkId == makeFourCC(115, 116, 115, 100)) {   // fourcc equals stsd
                readChunkStsd();
            } else if (subChunkId == makeFourCC(115, 116, 116, 115)) {  // fourcc equals stts
                readChunkStts(subChunkLen);
            } else if (subChunkId == makeFourCC(115, 116, 115, 122)) { // fourcc equals stsz
                readChunkStsz(subChunkLen);
            } else if (subChunkId == makeFourCC(115, 116, 115, 99)) {  // fourcc equals stsc
                readChunkStsc();
            } else if (subChunkId == makeFourCC(115, 116, 99, 111)) {  // fourcc equals stco
                readChunkStco();
            } else {
                throw new UnsupportedFormatException("(stbl) unknown chunk id: " + splitFourCC(subChunkId));
            }

            sizeRemaining -= subChunkLen;
        }
    }

    /*
     * chunk to offset box
     */
    private void readChunkStco() throws IOException {
        qtstream.skip(4);
        int numEntries = qtstream.readUint32();

        res.setStco(new int[numEntries]);
        for (int i = 0; i < numEntries; i++) {
            res.getStco()[i] = qtstream.readUint32();
        }
    }

    /*
     * sample to chunk box
     */
    private void readChunkStsc() throws IOException {
        //skip header and size
        //skip version and other junk
        qtstream.skip(4);
        int numEntries = qtstream.readUint32();
        ChunkInfo[] chunkInfos = new ChunkInfo[numEntries];
        res.setStsc(chunkInfos);
        for (int i = 0; i < numEntries; i++) {
            ChunkInfo entry = new ChunkInfo();
            entry.setFirstChunk(qtstream.readUint32());
            entry.setSamplesPerChunk(qtstream.readUint32());
            entry.setSampleDescIndex(qtstream.readUint32());
            chunkInfos[i] = entry;
        }
    }

    void readChunkMinf(int chunkLen) throws IOException {
        /* *** SOUND HEADER CHUNK ****/

        int mediaInfoSize = qtstream.readUint32();
        if (mediaInfoSize != 16) {
            throw new UnsupportedFormatException("unexpected size in media info");
        }
        if (qtstream.readUint32() != makeFourCC(115, 109, 104, 100)) {   // "smhd" ascii values
            throw new UnsupportedFormatException("not a sound header! can't handle this.");
        }
        /* now skip the rest */
        qtstream.skip(16 - 8);
        // FIXME WRONG
        int sizeRemaining = chunkLen - 8 - 16;
        /* *** DINF CHUNK ****/

        int dinfSize = qtstream.readUint32();
        if (qtstream.readUint32() != makeFourCC(100, 105, 110, 102)) {    // "dinf" ascii values
            throw new UnsupportedFormatException("expected dinf, didn't get it.");
        }
        /* skip it */
        qtstream.skip(dinfSize - 8);
        sizeRemaining -= dinfSize;
        /* ***/

        /* *** SAMPLE TABLE ****/
        int stblSize = qtstream.readUint32();
        if (qtstream.readUint32() != makeFourCC(115, 116, 98, 108)) { // "stbl" ascii values
            throw new UnsupportedFormatException("expected stbl, didn't get it.");
        }
        readChunkStbl(stblSize);
        sizeRemaining -= stblSize;

        if (sizeRemaining != 0) {
            log.error("(read_chunk_minf) - size remaining?");
            qtstream.skip(sizeRemaining);
        }
    }

    void readChunkMdia(int chunkLen) throws IOException {
        int sizeRemaining = chunkLen - 8; // FIXME WRONG

        while (sizeRemaining != 0) {
            int subChunkLen = qtstream.readUint32();
            if (subChunkLen <= 1 || subChunkLen > sizeRemaining) {
                throw new UnsupportedFormatException("strange size for chunk inside mdia\n");
            }

            int subChunkId = qtstream.readUint32();
            if (subChunkId == makeFourCC(109, 100, 104, 100)) {   // fourcc equals mdhd
                readChunkMdhd(subChunkLen);
            } else if (subChunkId == makeFourCC(104, 100, 108, 114)) {   // fourcc equals hdlr
                readChunkHandler(subChunkLen);
            } else if (subChunkId == makeFourCC(109, 105, 110, 102)) {   // fourcc equals minf
                readChunkMinf(subChunkLen);
            } else {
                throw new UnsupportedFormatException("(mdia) unknown chunk id: " + splitFourCC(subChunkId));
            }

            sizeRemaining -= subChunkLen;
        }
    }

    /* 'trak' - a movie track - contains other atoms */
    int read_chunk_trak(int chunk_len) throws IOException {
        int size_remaining = chunk_len - 8; // FIXME WRONG

        while (size_remaining != 0) {
            int sub_chunk_len;

            try {
                sub_chunk_len = (qtstream.readUint32());
            } catch (Exception e) {
                System.err.println("(read_chunk_trak) error reading sub_chunk_len - possibly number too large");
                sub_chunk_len = 0;
            }

            if (sub_chunk_len <= 1 || sub_chunk_len > size_remaining) {
                System.err.println("strange size for chunk inside trak");
                return 0;
            }

            int sub_chunk_id = qtstream.readUint32();

            if (sub_chunk_id == makeFourCC(116, 107, 104, 100))    // fourcc equals tkhd
            {
                readChunkTkhd(sub_chunk_len);
            } else if (sub_chunk_id == makeFourCC(109, 100, 105, 97))    // fourcc equals mdia
            {
                readChunkMdia(sub_chunk_len);
            } else if (sub_chunk_id == makeFourCC(101, 100, 116, 115))    // fourcc equals edts
            {
                readChunkEdts(sub_chunk_len);
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
        readChunkEdts(chunk_len);
    }

    /* 'udta' user data.. contains tag info */
    void read_chunk_udta(int chunk_len) throws IOException {
        /* don't need anything from here atm, skip */
        readChunkEdts(chunk_len);
    }

    /* 'iods' */
    void read_chunk_iods(int chunk_len) throws IOException {
        /* don't need anything from here atm, skip */
        readChunkEdts(chunk_len);
    }

    /* 'moov' movie atom - contains other atoms */
    int readChunkMoov(int chunk_len) throws IOException {
        int size_remaining = chunk_len - 8; // FIXME WRONG

        while (size_remaining != 0) {
            int sub_chunk_len;

            try {
                sub_chunk_len = (qtstream.readUint32());
            } catch (Exception e) {
                System.err.println("(read_chunk_moov) error reading sub_chunk_len - possibly number too large");
                sub_chunk_len = 0;
            }

            if (sub_chunk_len <= 1 || sub_chunk_len > size_remaining) {
                System.err.println("strange size for chunk inside moov");
                return 0;
            }

            int sub_chunk_id = qtstream.readUint32();

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
                readChunkElst(sub_chunk_len);
            } else if (sub_chunk_id == makeFourCC(105, 111, 100, 115))    // fourcc equals iods
            {
                read_chunk_iods(sub_chunk_len);
            } else if (sub_chunk_id == makeFourCC(102, 114, 101, 101))     // fourcc equals free
            {
                qtstream.skip(sub_chunk_len - 8); // FIXME not 8
            } else {
                System.err.println("(moov) unknown chunk id: " + splitFourCC(sub_chunk_id));
                return 0;
            }

            size_remaining -= sub_chunk_len;
        }

        return 1;
    }

    void readChunkMdat(int chunk_len, boolean skipMdat) throws IOException {
        int size_remaining = chunk_len - 8; // FIXME WRONG

        if (size_remaining == 0) {
            return;
        }

        res.setMdatLen(size_remaining);
        if (skipMdat) {
            setSavedMdatPos(qtstream.getCurrentPos());
            qtstream.skip(size_remaining);
        }
    }

    int setSavedMdat() {
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

    private void skipChunkMinus8(int chunkLen) throws IOException {
        /* don't need anything from here atm, skip */
        qtstream.skip(chunkLen - 8);
    }
}