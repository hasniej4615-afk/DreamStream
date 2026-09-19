import os
path = 'app/src/main/java/com/duta/movie/ui/VideoPlayerScreen.kt'

with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix 1: Change setMediaId(videoId) to setMediaId(effectiveProgressId)
old_media_id = """.setUri(url)
                .setMediaId(videoId)
                .setMediaMetadata("""
new_media_id = """.setUri(url)
                .setMediaId(effectiveProgressId)
                .setMediaMetadata("""
content = content.replace(old_media_id, new_media_id)

# Fix 2: Fix the currentPos calculation to prevent seeking the previous video
old_pos = """// If we just started casting, transfer position from ExoPlayer
                val currentPos = if (exoPlayer.isPlaying || exoPlayer.currentPosition > 0) exoPlayer.currentPosition else 0L
                exoPlayer.pause()"""
new_pos = """// Only transfer position if ExoPlayer was actually playing THIS specific URL
                val exoCurrentUri = exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
                val currentPos = if (exoCurrentUri == url && (exoPlayer.isPlaying || exoPlayer.currentPosition > 0)) {
                    exoPlayer.currentPosition
                } else {
                    0L
                }
                exoPlayer.pause()"""
content = content.replace(old_pos, new_pos)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
