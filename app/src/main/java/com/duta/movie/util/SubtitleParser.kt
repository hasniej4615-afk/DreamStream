package com.duta.movie.util

import androidx.media3.common.text.Cue
import java.io.File

/**
 * Data class representing a parsed subtitle cue with start and end timestamps in milliseconds.
 */
data class ParsedSubtitleCue(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String
) {
    /**
     * Converts this parsed cue into an AndroidX Media3 [Cue] for rendering via SubtitleView.
     */
    fun toMedia3Cue(): Cue {
        return Cue.Builder().setText(text).build()
    }
}

/**
 * High-performance parser for SRT, WebVTT, and ASS subtitle formats.
 * Designed to work without Android framework UI dependencies so it can run cleanly
 * across coroutines, background threads, and JVM unit tests.
 */
object SubtitleParser {

    /**
     * Parses a local subtitle file into a sorted list of [ParsedSubtitleCue]s.
     * Uses universal charset sniffing (UTF-8, UTF-16, Windows-1252) and automatic Mojibake repair.
     */
    fun parse(file: File): List<ParsedSubtitleCue> {
        if (!file.exists() || file.length() == 0L) return emptyList()
        val text = try {
            detectCharsetAndDecode(file.readBytes())
        } catch (_: Exception) {
            return emptyList()
        }
        return parseContent(text)
    }

    /**
     * Detects character encoding (UTF-8 with/without BOM, UTF-16LE, UTF-16BE, Windows-1252/ANSI)
     * and decodes raw byte array into a clean Unicode string with Mojibake repair.
     */
    fun detectCharsetAndDecode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""

        // 1. Check BOM (Byte Order Mark)
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return fixMojibake(String(bytes, 3, bytes.size - 3, Charsets.UTF_8))
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return fixMojibake(String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE))
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return fixMojibake(String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE))
        }

        // 2. Strict UTF-8 validation
        val utf8Decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        try {
            val buffer = java.nio.ByteBuffer.wrap(bytes)
            val charBuffer = utf8Decoder.decode(buffer)
            return fixMojibake(charBuffer.toString())
        } catch (_: java.nio.charset.CharacterCodingException) {
            // Not valid UTF-8
        }

        // 3. Fallback to Windows-1252 / ANSI (predominant for older legacy subtitles)
        return try {
            val win1252 = java.nio.charset.Charset.forName("windows-1252")
            fixMojibake(String(bytes, win1252))
        } catch (_: Exception) {
            fixMojibake(String(bytes, Charsets.ISO_8859_1))
        }
    }

    /**
     * Repairs common multi-byte Mojibake artifacts from mis-decoded subtitles.
     */
    fun fixMojibake(text: String): String {
        return text
            .replace("ï»¿", "")
            .replace("â€™", "'")
            .replace("â€˜", "'")
            .replace("â€œ", "\"")
            .replace("â€\u009d", "\"")
            .replace("â€¦", "...")
            .replace("ā¦", "...")
            .replace("â€”", " — ")
            .replace("â€“", " – ")
            .replace("â€", "\"")
            .replace("Ã©", "é")
            .replace("Ã¨", "è")
            .replace("Ã ", "à")
            .replace("Ã¡", "á")
            .replace("Ã³", "ó")
            .replace("Ã²", "ò")
            .replace("Ã±", "ñ")
            .replace("Ã§", "ç")
            .replace("Ã¼", "ü")
            .replace("Ã¶", "ö")
            .replace("Ã¤", "ä")
            .replace("Ã»", "û")
            .replace("Ã®", "î")
            .replace("Ã´", "ô")
            .replace("Ã¢", "â")
            .replace("Â ", " ")
            .replace("Â", "")
    }

    /**
     * Determines whether a subtitle line is advertising, gambling spam, or unwanted credit.
     */
    fun isAdOrPromoLine(rawLine: String): Boolean {
        val low = rawLine.lowercase().trim()
        if (low.isEmpty()) return false

        // 1. Gambling & Slot Keywords
        val isGambling = low.contains("slot88") || low.contains("slot gacor") ||
                low.contains("gacor") || low.contains("judol") ||
                low.contains("judi online") || low.contains("deposit pulsa") ||
                low.contains("link alternatif") || low.contains("maxwin") ||
                low.contains("pragmatic play") || low.contains("zeus slot") ||
                low.contains("daftar akun") || low.contains("bonus new member") ||
                low.contains("agen bola") || low.contains("togel online")
        if (isGambling) return true

        // 2. URLs, Telegram, WhatsApp & Donation Links
        val isUrlOrContact = low.contains("t.me/") || low.contains("telegram.me") ||
                low.contains("bit.ly/") || low.contains("tinyurl.com") ||
                low.contains("chat.whatsapp.com") || low.contains("wa:") ||
                low.contains("whatsapp:") || low.contains("support us on") ||
                low.contains("patreon.com") || low.contains("donasi:") ||
                low.contains("saweria.co") || low.contains("trakteer.id")
        if (isUrlOrContact) return true

        // 3. Domain mentions if appearing without dialogue
        val hasDomain = low.contains("www.") || low.contains(".com") || low.contains(".net") ||
                low.contains(".org") || low.contains(".xyz") || low.contains(".vip") ||
                low.contains(".top")
        val isSubtitleSource = low.contains("subscene") || low.contains("opensubtitles") ||
                low.contains("subdl") || low.contains("lebah ganteng") ||
                low.contains("pein akatsuki") || low.contains("idfl") ||
                low.contains("dutafilm") || low.contains("lk21") || low.contains("layarkaca21")
        if (hasDomain && isSubtitleSource) return true

        // 4. Standalone translator credits & ripper watermark promo (short line)
        val isCredit = (low.contains("translated by") || low.contains("subbed by") ||
                low.contains("resync") || low.contains("sync by") || low.contains("corrected by") ||
                low.contains("diterjemahkan oleh") || low.contains("dialihbahasakan oleh") ||
                low.contains("alih bahasa") || low.contains("penerjemah:")) &&
                low.length < 80
        if (isCredit) return true

        return false
    }

    /**
     * Parses raw subtitle content string into a sorted list of [ParsedSubtitleCue]s.
     */
    fun parseContent(content: String): List<ParsedSubtitleCue> {
        if (content.isBlank()) return emptyList()
        val normalized = fixMojibake(content.removePrefix("\uFEFF")).replace("\r\n", "\n").replace('\r', '\n')
        return when {
            normalized.contains("[Events]") || normalized.contains("Dialogue:") -> parseAss(normalized)
            normalized.contains("-->") -> parseSrtOrVtt(normalized)
            else -> emptyList()
        }
    }

    private fun parseSrtOrVtt(content: String): List<ParsedSubtitleCue> {
        val lines = content.lines()
        val results = mutableListOf<ParsedSubtitleCue>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.contains("-->")) {
                val arrowParts = line.split("-->")
                if (arrowParts.size == 2) {
                    val startStr = arrowParts[0].trim()
                    // Strip WebVTT cue settings like 'line:80% align:center'
                    val endStr = arrowParts[1].trim().split(Regex("""\s+"""))[0]
                    val startMs = parseTimestamp(startStr)
                    val endMs = parseTimestamp(endStr)

                    if (startMs != null && endMs != null && endMs > startMs) {
                        val textLines = mutableListOf<String>()
                        i++
                        while (i < lines.size) {
                            val nextLine = lines[i]
                            if (nextLine.trim().isEmpty()) {
                                break
                            }
                            if (nextLine.contains("-->")) {
                                i-- // Rewind so outer loop catches next cue
                                break
                            }
                            // Skip standalone sequence numbers (e.g., '1', '2')
                            if (textLines.isEmpty() && nextLine.trim().all { it.isDigit() }) {
                                i++
                                continue
                            }
                            val cleaned = cleanSubtitleText(nextLine.trim())
                            if (cleaned.isNotEmpty() && !isAdOrPromoLine(cleaned)) {
                                textLines.add(cleaned)
                            }
                            i++
                        }
                        val cueText = textLines.joinToString("\n")
                        if (cueText.isNotBlank()) {
                            results.add(ParsedSubtitleCue(startMs, endMs, cueText))
                        }
                    }
                }
            }
            i++
        }
        return results.sortedBy { it.startTimeMs }
    }

    private fun parseAss(content: String): List<ParsedSubtitleCue> {
        val lines = content.lines()
        val results = mutableListOf<ParsedSubtitleCue>()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Dialogue:", ignoreCase = true)) {
                val payload = trimmed.substringAfter(":").trim()
                // In ASS: Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
                val parts = payload.split(",", limit = 10)
                if (parts.size >= 10) {
                    val startMs = parseAssTimestamp(parts[1].trim())
                    val endMs = parseAssTimestamp(parts[2].trim())
                    var rawText = parts[9].trim()
                    // Remove ASS formatting tags like {\an8}, {\b1}, {\pos(...)}, etc.
                    rawText = rawText.replace(Regex("""\{.*?\}"""), "")
                    rawText = rawText.replace(Regex("""(?i)\\n"""), "\n")
                    rawText = cleanSubtitleText(rawText)
                    if (startMs != null && endMs != null && endMs > startMs && rawText.isNotBlank() && !isAdOrPromoLine(rawText)) {
                        results.add(ParsedSubtitleCue(startMs, endMs, rawText))
                    }
                }
            }
        }
        return results.sortedBy { it.startTimeMs }
    }

    /**
     * Parses standard SRT/VTT timestamps:
     * - HH:MM:SS.mmm or HH:MM:SS,mmm
     * - MM:SS.mmm or MM:SS,mmm
     */
    fun parseTimestamp(timeStr: String): Long? {
        val clean = timeStr.trim().replace(',', '.')
        val parts = clean.split(':')
        return try {
            when (parts.size) {
                3 -> {
                    val h = parts[0].toLong()
                    val m = parts[1].toLong()
                    val secParts = parts[2].split('.')
                    val s = secParts[0].toLong()
                    val ms = if (secParts.size > 1) secParts[1].padEnd(3, '0').take(3).toLong() else 0L
                    (h * 3600 + m * 60 + s) * 1000L + ms
                }
                2 -> {
                    val m = parts[0].toLong()
                    val secParts = parts[1].split('.')
                    val s = secParts[0].toLong()
                    val ms = if (secParts.size > 1) secParts[1].padEnd(3, '0').take(3).toLong() else 0L
                    (m * 60 + s) * 1000L + ms
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses ASS/SSA timestamps: H:MM:SS.cs (centiseconds).
     */
    fun parseAssTimestamp(timeStr: String): Long? {
        val parts = timeStr.trim().split(':')
        return try {
            if (parts.size == 3) {
                val h = parts[0].toLong()
                val m = parts[1].toLong()
                val secParts = parts[2].split('.')
                val s = secParts[0].toLong()
                val cs = if (secParts.size > 1) secParts[1].padEnd(2, '0').take(2).toLong() else 0L
                (h * 3600 + m * 60 + s) * 1000L + (cs * 10L)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Sanitizes subtitle text by stripping HTML tags and decoding common entities.
     */
    fun cleanSubtitleText(text: String): String {
        val unHtml = text
            .replace(Regex("""<[^>]*>"""), "") // Strip HTML tags (<i>, <b>, <font>, etc.)
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
        return fixMojibake(unHtml).trim()
    }

    /**
     * Serializes a list of [ParsedSubtitleCue]s into standard WebVTT format,
     * optionally applying a time offset in milliseconds.
     */
    fun toWebVtt(cues: List<ParsedSubtitleCue>, offsetMs: Long = 0L): String {
        val sb = StringBuilder("WEBVTT\n\n")
        var validIndex = 1
        for (cue in cues) {
            val start = (cue.startTimeMs + offsetMs).coerceAtLeast(0L)
            val end = (cue.endTimeMs + offsetMs).coerceAtLeast(start + 100L)
            if (cue.text.isNotBlank()) {
                sb.append(validIndex).append("\n")
                sb.append(formatVttTimestamp(start)).append(" --> ").append(formatVttTimestamp(end)).append("\n")
                sb.append(cue.text).append("\n\n")
                validIndex++
            }
        }
        return sb.toString()
    }

    /**
     * Formats a millisecond duration into a WebVTT timestamp: HH:MM:SS.mmm
     */
    fun formatVttTimestamp(ms: Long): String {
        val h = ms / 3600000L
        val m = (ms % 3600000L) / 60000L
        val s = (ms % 60000L) / 1000L
        val millis = ms % 1000L
        return String.format(java.util.Locale.US, "%02d:%02d:%02d.%03d", h, m, s, millis)
    }
}
