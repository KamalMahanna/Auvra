package com.auvra.app.download

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A robust, lightweight MPEG-4 / M4A metadata and artwork writer.
 * Directly constructs Apple iTunes-compatible metadata (`moov.udta.meta.ilst`)
 * and embeds cover art (`covr` atom) into both progressive MP4 and YouTube DASH
 * Fragmented MP4 (fMP4) audio containers without external C/C++ libraries.
 *
 * Recognized natively by Android MediaStore, Samsung Music, Google Files,
 * VLC, Apple Music, and ffmpeg/ffprobe.
 */
object Mp4MetadataEditor {

    private val FTYP = byteArrayOf('f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())
    private val MOOV = byteArrayOf('m'.code.toByte(), 'o'.code.toByte(), 'o'.code.toByte(), 'v'.code.toByte())
    private val UDTA = byteArrayOf('u'.code.toByte(), 'd'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte())
    private val META = byteArrayOf('m'.code.toByte(), 'e'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte())
    private val HDLR = byteArrayOf('h'.code.toByte(), 'd'.code.toByte(), 'l'.code.toByte(), 'r'.code.toByte())
    private val ILST = byteArrayOf('i'.code.toByte(), 'l'.code.toByte(), 's'.code.toByte(), 't'.code.toByte())
    private val DATA = byteArrayOf('d'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte())

    // iTunes metadata atom names
    private val TITLE_BOX = byteArrayOf(0xa9.toByte(), 'n'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte())
    private val ARTIST_BOX = byteArrayOf(0xa9.toByte(), 'A'.code.toByte(), 'R'.code.toByte(), 'T'.code.toByte())
    private val ALBUM_BOX = byteArrayOf(0xa9.toByte(), 'a'.code.toByte(), 'l'.code.toByte(), 'b'.code.toByte())
    private val COVR_BOX = byteArrayOf('c'.code.toByte(), 'o'.code.toByte(), 'v'.code.toByte(), 'r'.code.toByte())

    private const val TYPE_UTF8 = 1
    private const val TYPE_JPEG = 13
    private const val TYPE_PNG = 14

    /**
     * Determines whether [file] is a valid MP4 container.
     */
    fun isMp4(file: File): Boolean {
        if (!file.exists() || file.length() < 12) return false
        return try {
            FileInputStream(file).use { input ->
                val header = ByteArray(12)
                val read = input.read(header)
                if (read < 12) return@use false
                header[4] == FTYP[0] && header[5] == FTYP[1] &&
                header[6] == FTYP[2] && header[7] == FTYP[3]
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Writes iTunes metadata tags (Title, Artist, Album, Cover Art) into an MP4/M4A file.
     * Rewrites `moov.udta` cleanly and safely.
     */
    fun writeMetadata(
        file: File,
        title: String?,
        artist: String?,
        album: String?,
        artworkBytes: ByteArray?,
    ): Boolean {
        if (!isMp4(file)) return false

        val tempTaggedFile = File(file.parentFile, ".tagging_${System.currentTimeMillis()}_${file.name}")
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val fileLength = raf.length()
                var pos = 0L
                var moovPos = -1L
                var moovSize = -1L

                // Scan root atoms to find moov
                while (pos < fileLength - 8) {
                    raf.seek(pos)
                    val sizeInt = raf.readInt().toLong() and 0xFFFFFFFFL
                    val boxType = ByteArray(4)
                    raf.readFully(boxType)

                    val actualSize = when (sizeInt) {
                        1L -> {
                            // 64-bit size
                            raf.readLong()
                        }
                        0L -> fileLength - pos
                        else -> sizeInt
                    }

                    if (boxType.contentEquals(MOOV)) {
                        moovPos = pos
                        moovSize = actualSize
                        break
                    }
                    if (actualSize <= 0) break
                    pos += actualSize
                }

                if (moovPos == -1L || moovSize <= 8 || moovSize > 50 * 1024 * 1024) {
                    return false
                }

                // Read entire moov atom into memory
                raf.seek(moovPos)
                val moovBytes = ByteArray(moovSize.toInt())
                raf.readFully(moovBytes)

                // Scan moov for existing udta
                var mPos = 8
                var existingUdtaStart = -1
                var existingUdtaLen = 0

                while (mPos < moovBytes.size - 8) {
                    val buf = ByteBuffer.wrap(moovBytes, mPos, 8).order(ByteOrder.BIG_ENDIAN)
                    val sInt = buf.int.toLong() and 0xFFFFFFFFL
                    val bType = ByteArray(4)
                    buf.get(bType)

                    val actualS = if (sInt == 1L && mPos + 16 <= moovBytes.size) {
                        ByteBuffer.wrap(moovBytes, mPos + 8, 8).order(ByteOrder.BIG_ENDIAN).long
                    } else if (sInt == 0L) {
                        (moovBytes.size - mPos).toLong()
                    } else {
                        sInt
                    }

                    if (bType.contentEquals(UDTA)) {
                        existingUdtaStart = mPos
                        existingUdtaLen = actualS.toInt()
                        break
                    }
                    if (actualS <= 0) break
                    mPos += actualS.toInt()
                }

                val newUdta = buildUdtaBox(title, artist, album, artworkBytes)

                val newMoovStream = ByteArrayOutputStream()
                val dataOut = DataOutputStream(newMoovStream)

                // Placeholder for new moov 4-byte size
                dataOut.writeInt(0)
                // Write 'moov' identifier
                dataOut.write(MOOV)

                if (existingUdtaStart != -1) {
                    // Copy moov children before existing udta
                    newMoovStream.write(moovBytes, 8, existingUdtaStart - 8)
                    // Write new udta
                    newMoovStream.write(newUdta)
                    // Copy moov children after existing udta
                    val afterUdtaStart = existingUdtaStart + existingUdtaLen
                    val afterUdtaLen = moovBytes.size - afterUdtaStart
                    if (afterUdtaLen > 0) {
                        newMoovStream.write(moovBytes, afterUdtaStart, afterUdtaLen)
                    }
                } else {
                    // Copy all moov children
                    newMoovStream.write(moovBytes, 8, moovBytes.size - 8)
                    // Append new udta
                    newMoovStream.write(newUdta)
                }

                val finalMoovBytes = newMoovStream.toByteArray()
                // Update 4-byte length in moov header
                ByteBuffer.wrap(finalMoovBytes, 0, 4).order(ByteOrder.BIG_ENDIAN).putInt(finalMoovBytes.size)

                // Write new file
                FileOutputStream(tempTaggedFile).use { out ->
                    val buffer = ByteArray(64 * 1024)

                    // 1. Copy everything before moov
                    if (moovPos > 0) {
                        raf.seek(0)
                        var remaining = moovPos
                        while (remaining > 0) {
                            val toRead = minOf(remaining, buffer.size.toLong()).toInt()
                            val read = raf.read(buffer, 0, toRead)
                            if (read == -1) break
                            out.write(buffer, 0, read)
                            remaining -= read
                        }
                    }

                    // 2. Write updated moov
                    out.write(finalMoovBytes)

                    // 3. Copy everything after moov
                    val afterMoovPos = moovPos + moovSize
                    val remainingAfter = fileLength - afterMoovPos
                    if (remainingAfter > 0) {
                        raf.seek(afterMoovPos)
                        var remaining = remainingAfter
                        while (remaining > 0) {
                            val toRead = minOf(remaining, buffer.size.toLong()).toInt()
                            val read = raf.read(buffer, 0, toRead)
                            if (read == -1) break
                            out.write(buffer, 0, read)
                            remaining -= read
                        }
                    }
                    out.flush()
                }
            }

            // Replace original file with newly tagged file
            if (tempTaggedFile.exists() && tempTaggedFile.length() > 0) {
                if (!tempTaggedFile.renameTo(file)) {
                    tempTaggedFile.copyTo(file, overwrite = true)
                    tempTaggedFile.delete()
                }
                true
            } else {
                tempTaggedFile.delete()
                false
            }
        } catch (e: Exception) {
            if (tempTaggedFile.exists()) tempTaggedFile.delete()
            false
        }
    }

    private fun buildDataBox(boxType: ByteArray, dataType: Int, payload: ByteArray): ByteArray {
        val stream = ByteArrayOutputStream()
        val out = DataOutputStream(stream)

        // data atom: size (4), 'data' (4), type_flags (4), locale (4), payload
        val dataBoxSize = 16 + payload.size
        out.writeInt(dataBoxSize)
        out.write(DATA)
        out.writeInt(dataType)
        out.writeInt(0) // locale/reserved
        out.write(payload)
        val dataBox = stream.toByteArray()

        // outer atom: size (4), boxType (4), dataBox
        val outerStream = ByteArrayOutputStream()
        val outerOut = DataOutputStream(outerStream)
        outerOut.writeInt(8 + dataBox.size)
        outerOut.write(boxType)
        outerOut.write(dataBox)
        return outerStream.toByteArray()
    }

    private fun buildUdtaBox(
        title: String?,
        artist: String?,
        album: String?,
        artworkBytes: ByteArray?,
    ): ByteArray {
        val ilstStream = ByteArrayOutputStream()

        if (!title.isNullOrBlank()) {
            ilstStream.write(buildDataBox(TITLE_BOX, TYPE_UTF8, title.toByteArray(Charsets.UTF_8)))
        }
        if (!artist.isNullOrBlank()) {
            ilstStream.write(buildDataBox(ARTIST_BOX, TYPE_UTF8, artist.toByteArray(Charsets.UTF_8)))
        }
        if (!album.isNullOrBlank()) {
            ilstStream.write(buildDataBox(ALBUM_BOX, TYPE_UTF8, album.toByteArray(Charsets.UTF_8)))
        }
        if (artworkBytes != null && artworkBytes.isNotEmpty()) {
            val isPng = artworkBytes.size >= 8 &&
                artworkBytes[0] == 0x89.toByte() &&
                artworkBytes[1] == 'P'.code.toByte() &&
                artworkBytes[2] == 'N'.code.toByte() &&
                artworkBytes[3] == 'G'.code.toByte()
            val imgType = if (isPng) TYPE_PNG else TYPE_JPEG
            ilstStream.write(buildDataBox(COVR_BOX, imgType, artworkBytes))
        }

        val ilstChildren = ilstStream.toByteArray()
        val ilstBoxStream = ByteArrayOutputStream()
        val ilstOut = DataOutputStream(ilstBoxStream)
        ilstOut.writeInt(8 + ilstChildren.size)
        ilstOut.write(ILST)
        ilstOut.write(ilstChildren)
        val ilstBox = ilstBoxStream.toByteArray()

        // Apple hdlr atom (33 bytes)
        val hdlrStream = ByteArrayOutputStream()
        val hdlrOut = DataOutputStream(hdlrStream)
        hdlrOut.writeInt(33)
        hdlrOut.write(HDLR)
        hdlrOut.writeInt(0) // version & flags
        hdlrOut.writeInt(0) // pre_defined
        hdlrOut.write(byteArrayOf('m'.code.toByte(), 'd'.code.toByte(), 'i'.code.toByte(), 'r'.code.toByte())) // mdir
        hdlrOut.write(byteArrayOf('a'.code.toByte(), 'p'.code.toByte(), 'p'.code.toByte(), 'l'.code.toByte())) // appl
        hdlrOut.writeLong(0L) // reserved
        hdlrOut.writeByte(0) // name null-terminator
        val hdlrBox = hdlrStream.toByteArray()

        // meta atom: size (4), 'meta' (4), version+flags (4), hdlrBox, ilstBox
        val metaChildrenStream = ByteArrayOutputStream()
        metaChildrenStream.write(hdlrBox)
        metaChildrenStream.write(ilstBox)
        val metaChildren = metaChildrenStream.toByteArray()

        val metaBoxStream = ByteArrayOutputStream()
        val metaOut = DataOutputStream(metaBoxStream)
        metaOut.writeInt(12 + metaChildren.size)
        metaOut.write(META)
        metaOut.writeInt(0) // version+flags
        metaOut.write(metaChildren)
        val metaBox = metaBoxStream.toByteArray()

        // udta atom: size (4), 'udta' (4), metaBox
        val udtaBoxStream = ByteArrayOutputStream()
        val udtaOut = DataOutputStream(udtaBoxStream)
        udtaOut.writeInt(8 + metaBox.size)
        udtaOut.write(UDTA)
        udtaOut.write(metaBox)
        return udtaBoxStream.toByteArray()
    }
}

