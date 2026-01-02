package com.github.curiousoddman.alacdecoder.data;

/**
 * // 1 - all ok
 * // 2 - do not have valid saved mdat pos
 * // 3 - have valid saved mdat pos, but cannot seek there - need to close/reopen stream
 */
public enum MdatStatus {
    ALL_OK,     // 1
    NO_VALID_POS,   // 2
    CANNOT_SEEK     // 3
}
