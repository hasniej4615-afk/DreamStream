package com.duta.movie.util

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.TextTrackStyle

/**
 * Custom [MediaItemConverter] for CastPlayer that maps [MediaItem.SubtitleConfiguration]
 * to Google Cast SDK's [MediaTrack] with WebVTT contentType and activates the track
 * in [MediaQueueItem].
 */
@OptIn(UnstableApi::class)
class DutaCastMediaItemConverter(
    private val defaultConverter: DefaultMediaItemConverter = DefaultMediaItemConverter()
) : MediaItemConverter {

    companion object {
        private const val TAG = "DutaCastConverter"
    }

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
        return defaultConverter.toMediaItem(mediaQueueItem)
    }

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val baseQueueItem = defaultConverter.toMediaQueueItem(mediaItem)
        val baseMediaInfo = baseQueueItem.media ?: return baseQueueItem

        val subtitleConfigs = mediaItem.localConfiguration?.subtitleConfigurations
        if (subtitleConfigs.isNullOrEmpty()) {
            return baseQueueItem
        }

        val tracks = ArrayList<MediaTrack>()
        val activeTrackIds = ArrayList<Long>()

        subtitleConfigs.forEachIndexed { index, subConfig ->
            val trackId = (index + 1).toLong()
            val trackUri = subConfig.uri.toString()
            val trackName = subConfig.label ?: "Subtitle"
            val trackLang = subConfig.language ?: "id"

            try {
                val mediaTrack = MediaTrack.Builder(trackId, MediaTrack.TYPE_TEXT)
                    .setName(trackName)
                    .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
                    .setContentId(trackUri)
                    .setContentType("text/vtt")
                    .setLanguage(trackLang)
                    .build()

                tracks.add(mediaTrack)
                activeTrackIds.add(trackId)
                Log.i(TAG, "Added Cast MediaTrack #$trackId: $trackName ($trackLang) -> $trackUri")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to build MediaTrack for $trackUri: ${e.message}", e)
            }
        }

        if (tracks.isEmpty()) {
            return baseQueueItem
        }

        val trackStyle = TextTrackStyle().apply {
            foregroundColor = -0x1 // 0xFFFFFFFF (White)
            backgroundColor = -0x60000000 // 0xA0000000 (Semi-transparent black)
            edgeType = TextTrackStyle.EDGE_TYPE_DROP_SHADOW
            edgeColor = -0x1000000 // 0xFF000000 (Black)
            fontGenericFamily = TextTrackStyle.FONT_FAMILY_SANS_SERIF
            fontScale = 1.0f
        }

        val contentId = if (mediaItem.mediaId.isEmpty() || mediaItem.mediaId == MediaItem.DEFAULT_MEDIA_ID) {
            baseMediaInfo.contentId ?: baseMediaInfo.contentUrl ?: ""
        } else {
            mediaItem.mediaId
        }

        val newMediaInfo = MediaInfo.Builder(contentId)
            .setStreamType(baseMediaInfo.streamType)
            .setContentType(baseMediaInfo.contentType ?: "video/mp4")
            .setContentUrl(baseMediaInfo.contentUrl ?: "")
            .setMetadata(baseMediaInfo.metadata)
            .setStreamDuration(baseMediaInfo.streamDuration)
            .setCustomData(baseMediaInfo.customData)
            .setMediaTracks(tracks)
            .setTextTrackStyle(trackStyle)
            .build()

        return MediaQueueItem.Builder(newMediaInfo)
            .setActiveTrackIds(activeTrackIds.toLongArray())
            .setAutoplay(baseQueueItem.autoplay)
            .setPreloadTime(baseQueueItem.preloadTime)
            .build()
    }
}
