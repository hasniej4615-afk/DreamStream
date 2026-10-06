package com.duta.movie.model

/**
 * Built-in catalog of Malaysian Live TV channels.
 * Features official, high-definition 1080p HLS broadcasts without DRM or token expiration.
 */
object LiveTvCatalog {

    val channels: List<Video> = listOf(
        Video(
            id = "live_tv1",
            title = "TV1",
            thumbnailUrl = "https://i.imgur.com/wSyPmiY.png",
            backdropUrl = "https://i.imgur.com/wSyPmiY.png",
            videoUrl = "https://d25tgymtnqzu8s.cloudfront.net/smil:tv1/playlist.m3u8?id=1",
            duration = "LIVE",
            quality = "1080p FHD",
            description = "Saluran televisyen pertama Malaysia oleh RTM. Berita nasional terkini, siaran langsung rasmi dan acara sukan tempatan.",
            servers = listOf(VideoServer("RTM CloudFront (1080p)", "https://d25tgymtnqzu8s.cloudfront.net/smil:tv1/playlist.m3u8?id=1")),
            isSeries = false
        ),
        Video(
            id = "live_tv2",
            title = "TV2",
            thumbnailUrl = "https://i.imgur.com/LGEdeyJ.png",
            backdropUrl = "https://i.imgur.com/LGEdeyJ.png",
            videoUrl = "https://d25tgymtnqzu8s.cloudfront.net/smil:tv2/playlist.m3u8?id=2",
            duration = "LIVE",
            quality = "1080p FHD",
            description = "Saluran hiburan dan keluarga oleh RTM. Drama, filem, konsert dan pelbagai rancangan menarik multibahasa.",
            servers = listOf(VideoServer("RTM CloudFront (1080p)", "https://d25tgymtnqzu8s.cloudfront.net/smil:tv2/playlist.m3u8?id=2")),
            isSeries = false
        ),
        Video(
            id = "live_okey",
            title = "TV Okey",
            thumbnailUrl = "https://i.imgur.com/V2jREQ5.png",
            backdropUrl = "https://i.imgur.com/V2jREQ5.png",
            videoUrl = "https://d25tgymtnqzu8s.cloudfront.net/smil:okey/playlist.m3u8?id=3",
            duration = "LIVE",
            quality = "1080p FHD",
            description = "Saluran gaya hidup, kebudayaan dan kepelbagaian etnik Malaysia terutamanya Sabah dan Sarawak oleh RTM.",
            servers = listOf(VideoServer("RTM CloudFront (1080p)", "https://d25tgymtnqzu8s.cloudfront.net/smil:okey/playlist.m3u8?id=3")),
            isSeries = false
        ),
        Video(
            id = "live_sukan_rtm",
            title = "Sukan RTM",
            thumbnailUrl = "https://i.imgur.com/1pYYSu2.png",
            backdropUrl = "https://i.imgur.com/1pYYSu2.png",
            videoUrl = "https://d25tgymtnqzu8s.cloudfront.net/smil:sukan/playlist.m3u8?id=4",
            duration = "LIVE",
            quality = "1080p FHD",
            description = "Saluran khusus sukan 24 jam menyiarkan acara sukan antarabangsa, aksi kejohanan tempatan dan berita sukan semasa.",
            servers = listOf(VideoServer("RTM CloudFront (1080p)", "https://d25tgymtnqzu8s.cloudfront.net/smil:sukan/playlist.m3u8?id=4")),
            isSeries = false
        ),
        Video(
            id = "live_berita_rtm",
            title = "Berita RTM",
            thumbnailUrl = "https://i.imgur.com/1Xzxsgl.png",
            backdropUrl = "https://i.imgur.com/1Xzxsgl.png",
            videoUrl = "https://d25tgymtnqzu8s.cloudfront.net/smil:berita/playlist.m3u8?id=5",
            duration = "LIVE",
            quality = "1080p FHD",
            description = "Saluran berita 24 jam menyajikan liputan isu semasa, analisis berita dan perkembangan terkini dalam dan luar negara.",
            servers = listOf(VideoServer("RTM CloudFront (1080p)", "https://d25tgymtnqzu8s.cloudfront.net/smil:berita/playlist.m3u8?id=5")),
            isSeries = false
        ),
        Video(
            id = "live_tv6",
            title = "TV6",
            thumbnailUrl = "https://i.imgur.com/bK8UH9D.png",
            backdropUrl = "https://i.imgur.com/bK8UH9D.png",
            videoUrl = "https://d25tgymtnqzu8s.cloudfront.net/smil:tv6/playlist.m3u8?id=6",
            duration = "LIVE",
            quality = "1080p FHD",
            description = "Saluran nostalgia RTM menyiarkan filem klasik, drama retro dan khazanah arkib rancangan hiburan malar segar.",
            servers = listOf(VideoServer("RTM CloudFront (1080p)", "https://d25tgymtnqzu8s.cloudfront.net/smil:tv6/playlist.m3u8?id=6")),
            isSeries = false
        ),
        Video(
            id = "live_parlimen_rakyat",
            title = "Parlimen (Dewan Rakyat)",
            thumbnailUrl = "https://i.imgur.com/1Xzxsgl.png",
            backdropUrl = "https://i.imgur.com/1Xzxsgl.png",
            videoUrl = "https://d25tgymtnqzu8s.cloudfront.net/smil:rakyat/playlist.m3u8?id=7",
            duration = "LIVE",
            quality = "1080p FHD",
            description = "Siaran langsung persidangan dan perbahasan Dewan Rakyat Parlimen Malaysia secara telus.",
            servers = listOf(VideoServer("RTM CloudFront (1080p)", "https://d25tgymtnqzu8s.cloudfront.net/smil:rakyat/playlist.m3u8?id=7")),
            isSeries = false
        ),
        Video(
            id = "live_parlimen_negara",
            title = "Parlimen (Dewan Negara)",
            thumbnailUrl = "https://i.imgur.com/1Xzxsgl.png",
            backdropUrl = "https://i.imgur.com/1Xzxsgl.png",
            videoUrl = "https://d25tgymtnqzu8s.cloudfront.net/smil:negara/playlist.m3u8?id=8",
            duration = "LIVE",
            quality = "1080p FHD",
            description = "Siaran langsung persidangan dan perbahasan Dewan Negara Senat Parlimen Malaysia.",
            servers = listOf(VideoServer("RTM CloudFront (1080p)", "https://d25tgymtnqzu8s.cloudfront.net/smil:negara/playlist.m3u8?id=8")),
            isSeries = false
        ),
        Video(
            id = "live_rtm_asean",
            title = "RTM ASEAN",
            thumbnailUrl = "https://i.imgur.com/skAiUxg.png",
            backdropUrl = "https://i.imgur.com/skAiUxg.png",
            videoUrl = "https://d25tgymtnqzu8s.cloudfront.net/event/smil:event1/chunklist_b2596000_slENG.m3u8",
            duration = "LIVE",
            quality = "1080p FHD",
            description = "Saluran liputan serantau dan hubungan antarabangsa negara-negara komuniti ASEAN oleh RTM.",
            servers = listOf(VideoServer("RTM CloudFront (1080p)", "https://d25tgymtnqzu8s.cloudfront.net/event/smil:event1/chunklist_b2596000_slENG.m3u8")),
            isSeries = false
        )
    )

    fun isLiveVideo(id: String): Boolean {
        return id.startsWith("live_") || channels.any { it.id == id }
    }

    fun getChannelById(id: String): Video? {
        return channels.find { it.id == id }
    }
}
