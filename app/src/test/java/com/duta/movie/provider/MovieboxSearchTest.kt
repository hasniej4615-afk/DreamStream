package com.duta.movie.provider

import com.duta.movie.data.local.InstalledProviderEntity
import com.duta.movie.model.Video
import com.duta.movie.model.VideoServer
import com.duta.movie.provider.engine.MovieboxProvider
import com.duta.movie.util.VideoExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MovieboxSearchTest {

    @Test
    fun testFixedMirrorMatching() {
        runBlocking {
            val entity = InstalledProviderEntity(
                id = "com.duta.provider.moviebox",
                repoId = "official",
                name = "MovieBox",
                displayName = "MovieBox (Global HD Cinema & Series)",
                isEnabled = true
            )
            val provider = MovieboxProvider(entity)
            
            // Simulating target video from LK21 / Pencuri:
            val targetVideo = Video(
                id = "spider-man-brand-new-day",
                title = "Spider-Man: Brand New Day (2026)",
                videoUrl = "https://kalbeabidschool.org/spider-man-brand-new-day/",
                date = "28 Jul 2026",
                thumbnailUrl = "",
                duration = "",
                servers = listOf(
                    VideoServer("HgcloudVIP", "https://hgcloud.to/e/test"),
                    VideoServer("VOE", "https://voe.sx/e/test")
                )
            )

            val queries = VideoExtractor.buildAlternativeSearchQueries(targetVideo.title)
            val targetNorm = VideoExtractor.normalizeForDedup(targetVideo)
            val targetNormNoYear = VideoExtractor.normalizeForDedup(targetVideo, includeYear = false)
            val targetYear = VideoExtractor.extractReleaseYear(targetVideo.title, targetVideo.date, targetVideo.videoUrl)

            println("targetVideo: '${targetVideo.title}', norm='$targetNorm', year=$targetYear")

            val candidateVideos = mutableListOf<Video>()
            for (q in queries.take(3)) {
                val found = provider.search(q, 1)
                for (item in found) {
                    if (item.id == targetVideo.id || item.videoUrl == targetVideo.videoUrl) continue
                    val itemNorm = VideoExtractor.normalizeForDedup(item)
                    val itemNormNoYear = VideoExtractor.normalizeForDedup(item, includeYear = false)
                    val itemYear = VideoExtractor.extractReleaseYear(item.title, item.date, item.videoUrl)

                    // Strict year check: if both have years, reject if mismatch > 1
                    if (targetYear > 0 && itemYear > 0 && Math.abs(targetYear - itemYear) > 1) {
                        continue
                    }

                    val isExactMatch = (itemNorm.isNotEmpty() && itemNorm == targetNorm) ||
                                       (itemNormNoYear.isNotEmpty() && itemNormNoYear == targetNormNoYear) ||
                                       item.title.equals(targetVideo.title, ignoreCase = true) ||
                                       item.title.equals(q, ignoreCase = true)
                    
                    val isFuzzyMatch = !isExactMatch && VideoExtractor.cleanTitle(item.title)
                        .replace(":", "").replace("-", " ")
                        .trim().equals(
                            VideoExtractor.cleanTitle(targetVideo.title).replace(":", "").replace("-", " ").trim(),
                            ignoreCase = true
                        )

                    if ((isExactMatch || isFuzzyMatch) && candidateVideos.none { it.id == item.id }) {
                        candidateVideos.add(item)
                    }
                }
                if (candidateVideos.isNotEmpty()) break
            }

            println("Candidate videos found: ${candidateVideos.size}")
            candidateVideos.forEach {
                println("  Cand: '${it.title}', id='${it.id}', url='${it.videoUrl}'")
            }

            // Prioritize MovieBox candidates, then non-CAM
            candidateVideos.sortWith(
                compareByDescending<Video> {
                    if (it.id.startsWith("mb_") || it.videoUrl.startsWith("moviebox://")) 10 else 0
                }.thenBy {
                    val low = it.title.lowercase()
                    if (low.contains("cam") || low.contains("hindi") || low.contains("dub")) 1 else 0
                }
            )

            val results = mutableListOf<VideoServer>()
            for (cand in candidateVideos.take(3)) {
                val servers = provider.fetchServers(cand)
                for (s in servers) {
                    if (VideoExtractor.isServerMatchingMovie(targetVideo.title, s) &&
                        results.none { it.url == s.url || it.name.trim().equals(s.name.trim(), ignoreCase = true) }) {
                        results.add(s)
                    }
                }
                // Do NOT break if we haven't found MovieBox or high-tier direct CDN servers!
                if (results.any { it.name.startsWith("MovieBox", ignoreCase = true) }) {
                    break
                }
            }

            println("Final resolved provider mirrors: count = ${results.size}")
            results.forEach {
                println("  Mirror: '${it.name}' -> ${it.url}")
            }
        }
    }
}
