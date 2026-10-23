package com.vaydelux.bousygram.core.media

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance binary parser for MP4 (ISOBMFF) and Matroska/WebM (EBML) container headers.
 *
 * Sizing tail fetches dynamically prevents demuxer stall / tail-probe deadlock when videos
 * place index metadata (MP4 `moov` atom or MKV `Cues` element) at the end of large files.
 */
object ContainerHeaderParser {

    private const val FOURCC_MOOV = 0x6d6f6f76 // "moov"
    private const val FOURCC_MDAT = 0x6d646174 // "mdat"
    private const val FOURCC_FTYP = 0x66747970 // "ftyp"

    // Matroska EBML IDs
    private const val EBML_ID_HEADER = 0x1A45DFA3L
    private const val EBML_ID_SEGMENT = 0x18538067L
    private const val EBML_ID_SEEK_HEAD = 0x114D9B74L
    private const val EBML_ID_SEEK = 0x4DBBL
    private const val EBML_ID_SEEK_ID = 0x53ABL
    private const val EBML_ID_SEEK_POSITION = 0x53ACL
    private const val EBML_ID_CUES = 0x1C53BB6BL

    data class MkvCuesInfo(
        val offset: Long,
        val estimatedSize: Long
    )

    /**
     * Checks if the MP4 container has a `moov` atom located at the head of the file (faststart).
     * Returns the size in bytes of the `moov` atom if found at the head, or null otherwise.
     */
    fun findFaststartMoovSize(headBytes: ByteArray): Long? {
        if (headBytes.size < 8) return null
        val buffer = ByteBuffer.wrap(headBytes).order(ByteOrder.BIG_ENDIAN)
        var offset = 0

        while (offset + 8 <= headBytes.size) {
            val size32 = buffer.getInt(offset).toLong() and 0xFFFFFFFFL
            val type = buffer.getInt(offset + 4)

            val (atomSize, headerLength) = when (size32) {
                1L -> {
                    if (offset + 16 > headBytes.size) return null
                    Pair(buffer.getLong(offset + 8), 16)
                }
                0L -> Pair((headBytes.size - offset).toLong(), 8)
                else -> Pair(size32, 8)
            }

            if (atomSize < headerLength) break

            if (type == FOURCC_MOOV) {
                return atomSize
            }

            // If we hit mdat before moov, it's not faststart
            if (type == FOURCC_MDAT) {
                return null
            }

            offset += atomSize.toInt().coerceAtLeast(headerLength)
        }
        return null
    }

    /**
     * Scans a buffer representing the tail of an MP4 file to locate the `moov` atom and its full length.
     * Handles standard 32-bit atom lengths and 64-bit extended `largesize`.
     */
    fun findTailMoovSize(tailBytes: ByteArray, fileTotalSize: Long): Long? {
        if (tailBytes.size < 8) return null
        val buffer = ByteBuffer.wrap(tailBytes).order(ByteOrder.BIG_ENDIAN)

        // Scan backwards or forwards for 'moov' fourcc
        for (i in 0..(tailBytes.size - 8)) {
            val type = buffer.getInt(i + 4)
            if (type == FOURCC_MOOV) {
                val size32 = buffer.getInt(i).toLong() and 0xFFFFFFFFL
                val fullSize = when (size32) {
                    1L -> {
                        if (i + 16 <= tailBytes.size) buffer.getLong(i + 8) else null
                    }
                    0L -> fileTotalSize - (fileTotalSize - tailBytes.size + i)
                    else -> size32
                }
                if (fullSize != null && fullSize > 8L && fullSize <= fileTotalSize) {
                    return fullSize
                }
            }
        }
        return null
    }

    /**
     * Scans the EBML header / SeekHead in headBytes of an MKV file to locate the byte offset
     * of the Cues element (index) relative to the start of the file.
     */
    fun parseMkvCuesOffset(headBytes: ByteArray): Long? {
        if (headBytes.size < 12) return null
        var cursor = 0

        // Verify EBML Header
        val (ebmlId, ebmlIdLen) = readEbmlVint(headBytes, cursor, isId = true) ?: return null
        if (ebmlId != EBML_ID_HEADER) return null
        cursor += ebmlIdLen

        val (headerSize, headerSizeLen) = readEbmlVint(headBytes, cursor, isId = false) ?: return null
        cursor += headerSizeLen + headerSize.toInt()
        if (cursor >= headBytes.size) return null

        // Find Segment element
        val (segId, segIdLen) = readEbmlVint(headBytes, cursor, isId = true) ?: return null
        if (segId != EBML_ID_SEGMENT) return null
        cursor += segIdLen

        val (_, segSizeLen) = readEbmlVint(headBytes, cursor, isId = false) ?: return null
        cursor += segSizeLen
        val segmentDataStart = cursor.toLong()

        // Scan inside Segment for SeekHead (0x114D9B74)
        while (cursor + 8 < headBytes.size) {
            val (elementId, idLen) = readEbmlVint(headBytes, cursor, isId = true) ?: break
            cursor += idLen
            val (elementSize, sizeLen) = readEbmlVint(headBytes, cursor, isId = false) ?: break
            cursor += sizeLen

            if (elementId == EBML_ID_SEEK_HEAD) {
                val seekHeadEnd = cursor + elementSize.toInt().coerceAtMost(headBytes.size - cursor)
                return parseSeekHeadForCues(headBytes, cursor, seekHeadEnd, segmentDataStart)
            }
            if (elementId == EBML_ID_CUES) {
                // Cues directly at head
                return (cursor - idLen - sizeLen).toLong()
            }
            cursor += elementSize.toInt().coerceAtLeast(0)
        }

        return null
    }

    private fun parseSeekHeadForCues(
        data: ByteArray,
        start: Int,
        end: Int,
        segmentDataStart: Long
    ): Long? {
        var cursor = start
        while (cursor + 4 < end) {
            val (id, idLen) = readEbmlVint(data, cursor, isId = true) ?: break
            cursor += idLen
            val (size, sizeLen) = readEbmlVint(data, cursor, isId = false) ?: break
            cursor += sizeLen
            val entryEnd = (cursor + size.toInt()).coerceAtMost(end)

            if (id == EBML_ID_SEEK) {
                var seekEntryCursor = cursor
                var foundCuesId = false
                var seekPosition: Long? = null

                while (seekEntryCursor < entryEnd) {
                    val (subId, subIdLen) = readEbmlVint(data, seekEntryCursor, isId = true) ?: break
                    seekEntryCursor += subIdLen
                    val (subSize, subSizeLen) = readEbmlVint(data, seekEntryCursor, isId = false) ?: break
                    seekEntryCursor += subSizeLen

                    if (subId == EBML_ID_SEEK_ID) {
                        val (targetId, _) = readEbmlVint(data, seekEntryCursor, isId = true) ?: Pair(0L, 0)
                        if (targetId == EBML_ID_CUES) {
                            foundCuesId = true
                        }
                    } else if (subId == EBML_ID_SEEK_POSITION) {
                        seekPosition = readUint(data, seekEntryCursor, subSize.toInt())
                    }
                    seekEntryCursor += subSize.toInt()
                }

                if (foundCuesId && seekPosition != null) {
                    return segmentDataStart + seekPosition
                }
            }
            cursor = entryEnd
        }
        return null
    }

    /**
     * Estimates the optimal tail probe window for a video file based on parsed box headers
     * or safe fallback heuristics.
     */
    fun calculateOptimalTailSize(
        sampleTail: ByteArray?,
        fileTotalSize: Long,
        isMkv: Boolean
    ): Long {
        val minTail = 512L * 1024L
        val maxTail = min(32L * 1024L * 1024L, (fileTotalSize / 3L).coerceAtLeast(minTail))

        if (sampleTail != null && sampleTail.isNotEmpty()) {
            if (!isMkv) {
                val moovSize = findTailMoovSize(sampleTail, fileTotalSize)
                if (moovSize != null && moovSize > 0L) {
                    // Add 64KB safety margin for box alignment
                    return (moovSize + 64L * 1024L).coerceIn(minTail, maxTail)
                }
            }
        }

        // Adaptive default based on file size:
        // For files > 2GB, index could easily be 4MB - 8MB.
        return when {
            fileTotalSize > 2L * 1024L * 1024L * 1024L -> 8L * 1024L * 1024L
            fileTotalSize > 500L * 1024L * 1024L -> 4L * 1024L * 1024L
            fileTotalSize > 50L * 1024L * 1024L -> 2L * 1024L * 1024L
            else -> min(minTail, fileTotalSize)
        }
    }

    private fun readEbmlVint(data: ByteArray, offset: Int, isId: Boolean): Pair<Long, Int>? {
        if (offset >= data.size) return null
        val firstByte = data[offset].toInt() and 0xFF
        if (firstByte == 0) return null

        val length = 8 - Integer.numberOfLeadingZeros(firstByte).coerceAtMost(7)
        val numBytes = 9 - length
        if (offset + numBytes > data.size) return null

        var value = if (isId) {
            firstByte.toLong()
        } else {
            (firstByte and ((1 shl (8 - numBytes)) - 1)).toLong()
        }

        for (i in 1 until numBytes) {
            value = (value shl 8) or (data[offset + i].toLong() and 0xFFL)
        }
        return Pair(value, numBytes)
    }

    private fun readUint(data: ByteArray, offset: Int, length: Int): Long {
        var value = 0L
        for (i in 0 until min(length, 8)) {
            if (offset + i < data.size) {
                value = (value shl 8) or (data[offset + i].toLong() and 0xFFL)
            }
        }
        return value
    }
}
