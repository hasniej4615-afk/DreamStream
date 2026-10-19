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

    @Test
    fun testArrowTvSeriesMatching() {
        runBlocking {
            val entity = InstalledProviderEntity(
                id = "com.duta.provider.moviebox",
                repoId = "official",
                name = "MovieBox",
                displayName = "MovieBox (Global HD Cinema & Series)",
                isEnabled = true
            )
            val provider = MovieboxProvider(entity)
            val searchResults = provider.search("Arrow", 1)
            println("Arrow search results (${searchResults.size}):")
            searchResults.forEach {
                println("  ID=${it.id} | Title='${it.title}' | URL=${it.videoUrl} | Date=${it.date} | isSeries=${it.isSeries}")
            }

            val targetSeason = 1
            val targetEp = 1
            val s1Candidate = searchResults.find { it.title.equals("Arrow S1", ignoreCase = true) || it.title.equals("Arrow", ignoreCase = true) }
            println("Found S1 Candidate: $s1Candidate")
            if (s1Candidate != null) {
                val fetchCand = s1Candidate.copy(videoUrl = "${s1Candidate.videoUrl}&se=$targetSeason&ep=$targetEp", isSeries = true)
                val servers = provider.fetchServers(fetchCand)
                println("Fetched servers for Arrow S1 E1 (${servers.size}):")
                servers.forEach {
                    println("  Server: '${it.name}' -> ${it.url}")
                }
                assert(servers.isNotEmpty()) { "Should have fetched servers for Arrow S1" }
            }
        }
    }

    @Test
    fun testShortTitleMovieRejectsWrongSeries() {
        val targetMovie = Video(
            id = "ain-2026",
            title = "Ain (2026)",
            videoUrl = "https://example.com/ain-2026",
            date = "2026",
            thumbnailUrl = "",
            duration = "100m",
            isSeries = false
        )

        val cleanTargetTitle = VideoExtractor.cleanTitle(targetMovie.title)
        val cleanTargetBase = cleanTargetTitle.replace(Regex("""(?i)\b(?:season|s)[-_ ]?\d+\b"""), "").replace(Regex("""[\(\)\[\]\{\}\-_,:\.'\"\|\\\/–—]"""), " ").trim()
        val targetBaseNoYear = cleanTargetBase.replace(Regex("""\b(19\d{2}|20\d{2})\b"""), "").replace(Regex("""\s+"""), " ").trim()
        val targetTokens = targetBaseNoYear.split(Regex("""\s+""")).filter { it.isNotBlank() }
        val targetYear = Regex("""\b(19\d{2}|20\d{2})\b""").find(targetMovie.title + " " + targetMovie.date)?.groupValues?.get(1)?.toIntOrNull()

        fun isCandidateAccepted(cand: Video): Boolean {
            val isSeriesPlayback = targetMovie.isSeries == true
            val candIsSeries = cand.isSeries == true || cand.isShortTv ||
                    cand.videoUrl.contains("type=2") || cand.videoUrl.contains("type=7") || cand.videoUrl.contains("type=5")
            if (!isSeriesPlayback && candIsSeries) return false
            if (isSeriesPlayback && !candIsSeries && !cand.id.startsWith("mb_") && !cand.videoUrl.startsWith("moviebox://")) return false

            val candYear = Regex("""\b(19\d{2}|20\d{2})\b""").find(cand.title + " " + cand.date + " " + cand.videoUrl)?.groupValues?.get(1)?.toIntOrNull()
            if (targetYear != null && candYear != null && Math.abs(targetYear - candYear) > 1) {
                return false
            }
            if (targetYear != null && candYear == null && targetBaseNoYear.length <= 4) {
                return false
            }

            val cleanCandTitle = VideoExtractor.cleanTitle(cand.title)
            val cleanCandBase = cleanCandTitle.replace(Regex("""(?i)\b(?:season|s)[-_ ]?\d+\b"""), "").replace(Regex("""[\(\)\[\]\{\}\-_,:\.'\"\|\\\/–—]"""), " ").trim()
            val candBaseNoYear = cleanCandBase.replace(Regex("""\b(19\d{2}|20\d{2})\b"""), "").replace(Regex("""\s+"""), " ").trim()

            val isTitleExact = candBaseNoYear.isNotBlank() && targetBaseNoYear.isNotBlank() &&
                    candBaseNoYear.equals(targetBaseNoYear, ignoreCase = true)

            val isTitlePrefix = if (targetTokens.size >= 2 && targetBaseNoYear.length >= 6) {
                cleanCandTitle.startsWith("$targetBaseNoYear ", ignoreCase = true) ||
                cleanCandTitle.startsWith("$targetBaseNoYear:", ignoreCase = true) ||
                cleanCandTitle.startsWith("$targetBaseNoYear -", ignoreCase = true) ||
                cleanTargetTitle.startsWith("$candBaseNoYear ", ignoreCase = true) ||
                cleanTargetTitle.startsWith("$candBaseNoYear:", ignoreCase = true) ||
                cleanTargetTitle.startsWith("$candBaseNoYear -", ignoreCase = true)
            } else false

            val isMovieMatch = !isSeriesPlayback && (
                isTitleExact ||
                isTitlePrefix ||
                VideoExtractor.isCrossProviderMovieMatch(targetMovie, cand)
            )

            return isMovieMatch
        }

        // 1. Unrelated series starting with "Ain" -> MUST BE REJECTED
        val wrongSeries = Video(
            id = "mb_123",
            title = "Ain Se Ishq",
            videoUrl = "moviebox://detail?id=123&type=2",
            date = "2026",
            thumbnailUrl = "",
            duration = "30m",
            isSeries = true
        )
        org.junit.Assert.assertFalse("Should reject TV series when target is a movie", isCandidateAccepted(wrongSeries))

        // 2. Unrelated movie starting with "Ain" (prefix match rejected for short title) -> MUST BE REJECTED
        val wrongPrefixMovie = Video(
            id = "mb_124",
            title = "Ain Se Ishq",
            videoUrl = "moviebox://detail?id=124&type=1",
            date = "2026",
            thumbnailUrl = "",
            duration = "90m",
            isSeries = false
        )
        org.junit.Assert.assertFalse("Should reject prefix match on short title 'Ain'", isCandidateAccepted(wrongPrefixMovie))

        // 3. Old movie named "Ain" with mismatched year (2021) -> MUST BE REJECTED
        val oldMovie = Video(
            id = "mb_125",
            title = "Ain (2021)",
            videoUrl = "moviebox://detail?id=125&type=1",
            date = "2021",
            thumbnailUrl = "",
            duration = "95m",
            isSeries = false
        )
        org.junit.Assert.assertFalse("Should reject movie with mismatched year", isCandidateAccepted(oldMovie))

        // 4. Genuine 2026 movie named "Ain" -> MUST BE ACCEPTED
        val legitimateMovie = Video(
            id = "mb_126",
            title = "Ain",
            videoUrl = "moviebox://detail?id=126&type=1",
            date = "2026",
            thumbnailUrl = "",
            duration = "100m",
            isSeries = false
        )
        org.junit.Assert.assertTrue("Should accept legitimate exact match movie", isCandidateAccepted(legitimateMovie))
    }
}

