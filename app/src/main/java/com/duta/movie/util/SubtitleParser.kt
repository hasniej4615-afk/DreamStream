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
     */
    fun parse(file: File): List<ParsedSubtitleCue> {
        if (!file.exists() || file.length() == 0L) return emptyList()
        val text = try {
            file.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            try {
                file.readText(java.nio.charset.Charset.forName("windows-1252"))
            } catch (_: Exception) {
                return emptyList()
            }
        }
        return parseContent(text)
    }

    /**
     * Parses raw subtitle content string into a sorted list of [ParsedSubtitleCue]s.
     */
    fun parseContent(content: String): List<ParsedSubtitleCue> {
        if (content.isBlank()) return emptyList()
        val normalized = content.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
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
                            if (cleaned.isNotEmpty()) {
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
                    if (startMs != null && endMs != null && endMs > startMs && rawText.isNotBlank()) {
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
        return text
            .replace(Regex("""<[^>]*>"""), "") // Strip HTML tags (<i>, <b>, <font>, etc.)
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("â€¦", "...")
            .replace("ā¦", "...")
            .replace("â€™", "'")
            .replace("â€œ", "\"")
            .replace("â€", "\"")
            .replace("â€”", " - ")
            .replace("â€“", " - ")
            .trim()
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
