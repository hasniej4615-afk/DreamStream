package com.duta.movie.model

import java.util.Calendar
import java.util.TimeZone

/**
 * Electronic Program Guide (EPG) Engine for Malaysian Live TV.
 * Provides deterministic 24/7 broadcast schedules synced with Malaysia Time (GMT+8 / Asia/Kuala_Lumpur).
 * Zero external API dependency ensures 100% offline-ready reliability with no network failure risk.
 */
object LiveTvEpg {

    data class EpgProgram(
        val title: String,
        val startTime: String,
        val endTime: String,
        val description: String = ""
    ) {
        val startMinute: Int by lazy { timeToMinute(startTime) }
        val endMinute: Int by lazy { if (endTime == "24:00" || endTime == "00:00") 1440 else timeToMinute(endTime) }
    }

    data class ChannelEpgInfo(
        val channelId: String,
        val currentProgram: EpgProgram,
        val nextProgram: EpgProgram?,
        val progress: Float
    )

    private fun timeToMinute(timeStr: String): Int {
        val parts = timeStr.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return (h * 60 + m).coerceIn(0, 1440)
    }

    private val scheduleMap: Map<String, List<EpgProgram>> = mapOf(
        "live_tv1" to listOf(
            EpgProgram("Dokumentari & Muzik Nostalgia", "00:00", "06:00", "Koleksi dokumentari khazanah warisan dan muzik klasik RTM."),
            EpgProgram("Ayat Suci Al-Quran & Subuh", "06:00", "07:00", "Bacaan ayat suci Al-Quran sempena waktu Subuh."),
            EpgProgram("Berita Pagi RTM", "07:00", "08:00", "Liputan berita nasional dan perkembangan terkini awal pagi."),
            EpgProgram("Selamat Pagi Malaysia (SPM Live)", "08:00", "10:00", "Rancangan bual bicara pagi terulung negara membincangkan isu semasa."),
            EpgProgram("Panorama & Dokumentari Perdana", "10:00", "12:00", "Eksplorasi alam semulajadi, inovasi dan sejarah negara."),
            EpgProgram("Berita Tengah Hari", "12:00", "13:00", "Rangkuman berita mutakhir dalam dan luar negara."),
            EpgProgram("Bicara Naratif", "13:00", "14:00", "Wacana intelektual bersama tokoh dan penganalisis nasional."),
            EpgProgram("Teater Perdana / Filem Klasik", "14:00", "17:00", "Tayangan karya agung seni layar perak Melayu klasik."),
            EpgProgram("Berita Wilayah & Ringkasan", "17:00", "18:00", "Laporan berita perkembangan sosioekonomi negeri-negeri."),
            EpgProgram("Forum Perdana Hal Ehwal Islam", "18:00", "19:00", "Perbincangan ilmiah hal ehwal Islam bersama panel agamawan jemputan."),
            EpgProgram("Pesona Seni & Warisan", "19:00", "20:00", "Menghayati keindahan seni tari, muzik dan kraf Malaysia."),
            EpgProgram("Berita Perdana (Live Utama)", "20:00", "21:00", "Buletin berita perdana televisyen nasional RTM secara langsung."),
            EpgProgram("Drama Eksklusif RTM", "21:00", "22:30", "Koleksi siri drama tempatan berkualiti tinggi."),
            EpgProgram("Dunia Kita / Laporan Khas", "22:30", "23:30", "Analisis mendalam peristiwa hangat serantau dan global."),
            EpgProgram("Berita Dunia & Sukan RTM", "23:30", "24:00", "Sorotan berita antarabangsa dan aksi arena sukan.")
        ),

        "live_tv2" to listOf(
            EpgProgram("Midnight Movie / Relaxing Tunes", "00:00", "06:00", "Tayangan hiburan larut malam dan santapan muzik antarabangsa."),
            EpgProgram("Muzik Santai & Dokumentari", "06:00", "07:30", "Pilihan irama melodi dan paparan gaya hidup sihat."),
            EpgProgram("Berita Tamil & Mandarin", "07:30", "08:30", "Buletin berita bahasa Tamil dan bahasa Mandarin terkini."),
            EpgProgram("Fresh Brew / What Say You", "08:30", "10:00", "Program bual bicara interaktif isu masyarakat pelbagai bangsa."),
            EpgProgram("Golden Classics / C-Drama", "10:00", "12:00", "Siri drama klasik Asia dan drama Mandarin popular."),
            EpgProgram("Midday News & Action Movie", "12:00", "13:30", "Berita tengah hari dan aksi sinema antarabangsa."),
            EpgProgram("Tamil Movie Blockbuster", "13:30", "15:30", "Filem Kollywood dan tayangan hiburan bahasa Tamil."),
            EpgProgram("Kartun & Animasi Kanak-Kanak", "15:30", "17:00", "Slot animasi ceria dan pendidikan untuk seisi keluarga."),
            EpgProgram("K-Drama Express", "17:00", "18:30", "Siri drama Korea terhebat dengan sari kata Bahasa Melayu."),
            EpgProgram("Chinese News & Tamil News Live", "18:30", "19:30", "Liputan berita bahasa Cina dan Tamil waktu perdana."),
            EpgProgram("Bollywood Movie Magic", "19:30", "20:30", "Koleksi filem muzikal Hindi terhebat."),
            EpgProgram("Movie On 2 (Hollywood Blockbuster)", "20:30", "22:30", "Filem hebat pawagam Hollywood tanpa henti."),
            EpgProgram("Late Night English News & Action", "22:30", "24:00", "Berita bahasa Inggeris malam dan drama aksi.")
        ),

        "live_tv3" to listOf(
            EpgProgram("Tayangan Mega / Filem Larut Malam", "00:00", "01:30", "Tayangan sinema blokbuster antarabangsa dan Asia."),
            EpgProgram("Siaran Ulang Tayang & Dokumentari", "01:30", "06:00", "Koleksi siri drama terbaik dan dokumentari Media Prima."),
            EpgProgram("Al-Quran & Tazkirah Pagi", "06:00", "06:30", "Pengisian rohani dan bacaan Al-Quran menyambut fajar."),
            EpgProgram("Malaysia Hari Ini (MHI Live)", "06:30", "09:30", "Bual bicara pagi nombor satu negara membawakan inspirasi dan info terkini."),
            EpgProgram("Tanyalah Ustaz", "09:30", "10:30", "Bimbingan kemusykilan agama dan jawapan terus daripada para asatizah."),
            EpgProgram("Wanita Hari Ini (WHI Live)", "10:30", "12:00", "Program interaktif membincangkan gaya hidup, kekeluargaan dan wanita moden."),
            EpgProgram("CJ WOW Shop", "12:00", "12:30", "Pameran produk keperluan rumah dan promosi istimewa."),
            EpgProgram("Buletin 1:30", "12:30", "13:30", "Berita tengah hari pantas dan liputan peristiwa penting semasa."),
            EpgProgram("Slot Lestary (Drama Terhangat)", "13:30", "14:30", "Siri drama romantik dan kekeluargaan pilihan penonton."),
            EpgProgram("Melodi / Hiburan Sensasi", "14:30", "16:30", "Sorotan dunia seni hiburan tempatan dan kisah selebriti terhangat."),
            EpgProgram("I Can See Your Voice Malaysia / Muzik-Muzik", "16:30", "17:30", "Rancangan permainan muzik dan carta lagu popular tanah air."),
            EpgProgram("Buletin Petang", "17:30", "18:00", "Rangkuman ringkas berita utama menjelang senja."),
            EpgProgram("Slot Sakinah / Drama Pilihan", "18:00", "19:00", "Drama sarat pengajaran moral dan nilai kasih sayang."),
            EpgProgram("Slot Akasia (Drama Perdana)", "19:00", "20:00", "Siri drama perdana waktu puncak yang menjadi bualan rakyat Malaysia."),
            EpgProgram("Buletin Utama (Live HD)", "20:00", "21:00", "Liputan berita terulung nombor satu negara oleh Media Prima secara langsung."),
            EpgProgram("Slot Samarinda (Drama Mega)", "21:00", "22:00", "Drama konflik dan emosi premium dibintangi pelakon tersohor."),
            EpgProgram("Majalah 3 / Helo Doktor / 999", "22:00", "23:00", "Penyiasatan eksklusif, kesihatan dan operasi kepolisan berprofil tinggi."),
            EpgProgram("Nightline (English News)", "23:00", "24:00", "Malaysia's premier late-night English news broadcast.")
        ),

        "live_tv_alhijrah" to listOf(
            EpgProgram("Al-Quran & Zikir Terapi Rohani", "00:00", "05:30", "Alunan merdu bacaan suci Al-Quran dan zikir ketenangan jiwa."),
            EpgProgram("Cinta Ilmu & Kuliah Subuh", "05:30", "07:00", "Pengajian kitab fiqh dan bimbingan ibadah selepas Subuh."),
            EpgProgram("Assalammualaikum (Live Studio)", "07:00", "09:30", "Rancangan bual bicara pagi Islamik membincangkan isu ummah dan gaya hidup."),
            EpgProgram("Madrasah Al-Hikmah & Tadabbur", "09:30", "11:30", "Kupasan mukjizat ayat suci Al-Quran oleh para ulama."),
            EpgProgram("Hijrah Prihatin & Dapur Ibunda", "11:30", "13:00", "Inspirasi kebajikan komuniti dan resipi masakan halal toyyiban."),
            EpgProgram("Berita Alhijrah 1:00", "13:00", "14:00", "Buletin berita mutakhir dunia Islam dan perkembangan nasional."),
            EpgProgram("Dokumentari Tamadun Islam", "14:00", "16:30", "Menyusuri kegemilangan tamadun dan sumbangan tokoh cendekiawan Islam."),
            EpgProgram("Tazkirah Asar & My Quran Time", "16:30", "18:00", "Bimbingan bacaan bertajwid dan penghayatan makna kalamullah."),
            EpgProgram("Sirah Nabawiyyah", "18:00", "19:00", "Kisah perjalanan hidup dan perjuangan junjungan mulia Nabi Muhammad SAW."),
            EpgProgram("Kuliah Maghrib Live", "19:00", "20:00", "Siaran langsung ceramah Maghrib bersama pendakwah terkemuka."),
            EpgProgram("Berita Alhijrah 8:00", "20:00", "21:00", "Laporan komprehensif isu semasa ummah dalam dan luar negara."),
            EpgProgram("Bicara Mawaddah / Ejen Halal", "21:00", "22:30", "Harmoni rumahtangga Islami dan panduan industri halal global."),
            EpgProgram("Hijrah Global / Forum Remaja", "22:30", "24:00", "Wacana pemikiran generasi muda berteraskan syiar Islam.")
        ),

        "live_bernamatv" to listOf(
            EpgProgram("Bernama 24-Hour Rolling News", "00:00", "07:00", "Siaran berita berterusan agensi berita kebangsaan Malaysia."),
            EpgProgram("Bernama Today (Live Morning)", "07:00", "08:30", "Bual bicara berita pagi membincangkan dasar awam dan ekonomi."),
            EpgProgram("Jendela Fikir / Analisis Pasaran", "08:30", "10:00", "Sorotan pasaran saham Bursa Malaysia dan petunjuk kewangan."),
            EpgProgram("Suara Kita / Bisnes Malaysia", "10:00", "12:00", "Wawancara penggerak industri keusahawanan dan perdagangan."),
            EpgProgram("The Nation (Current Affairs)", "12:00", "13:30", "In-depth national affairs and public policy discussions."),
            EpgProgram("Bernama News Channel Live", "13:30", "15:00", "Buletin langsung laporan kementerian dan peristiwa negara."),
            EpgProgram("Ruang Bicara Liputan Khas", "15:00", "17:00", "Kupasan telus isu kebajikan rakyat dan tadbir urus."),
            EpgProgram("Dunia Bernama / Sukan Semasa", "17:00", "18:30", "Perkembangan hal ehwal serantau ASEAN dan dunia sukan."),
            EpgProgram("Berita Perdana Bernama", "18:30", "20:00", "Buletin utama laporan tepat, sahih dan berintegriti."),
            EpgProgram("Ruang Bicara Utama (Live Diskusi)", "20:00", "21:30", "Program bual bicara perdana bersama menteri dan pakar penganalisis."),
            EpgProgram("The Brief / Market Wrap Global", "21:30", "23:00", "Global economic summary and international market trends."),
            EpgProgram("Bernama World News Round-up", "23:00", "24:00", "Rangkuman menyeluruh peristiwa terpenting 24 jam.")
        ),

        "live_cna" to listOf(
            EpgProgram("CNA 24/7 Global Rolling News", "00:00", "06:00", "Round-the-clock international news and Asian current affairs."),
            EpgProgram("Asia First (Live Morning Bulletin)", "06:00", "09:00", "CNA's premier live morning show setting the Asian business agenda."),
            EpgProgram("Asia Now (Morning Edition)", "09:00", "12:00", "Real-time market movements, political developments and breaking news."),
            EpgProgram("Headline Asia & Singapore Preview", "12:00", "14:00", "Midday corporate updates, commodity checks and diplomatic ties."),
            EpgProgram("Asia Now (Afternoon Focus & Politics)", "14:00", "17:00", "Tracking Asia's economic resurgence and regional diplomacy."),
            EpgProgram("East Asia Tonight", "17:00", "19:00", "Focus on China, Japan, the Koreas, Taiwan and cross-border developments."),
            EpgProgram("Asia Tonight (Flagship Evening Edition)", "19:00", "20:00", "Comprehensive coverage of the day's biggest stories shaping Asia."),
            EpgProgram("Insight & Undercover Asia", "20:00", "21:00", "Award-winning investigative documentary series exposing untold truths."),
            EpgProgram("Singapore Tonight (Live National Bulletin)", "21:00", "22:00", "CNA's signature prime-time news broadcast covering Singapore and ASEAN."),
            EpgProgram("World Tonight (International In-Depth)", "22:00", "23:00", "Comprehensive global perspective on international security and world affairs."),
            EpgProgram("CNA Correspondent & Business Showcase", "23:00", "24:00", "Dispatches from foreign correspondents across global capitals.")
        ),

        "live_okey" to listOf(
            EpgProgram("Okey Chillout & Sinema Nusantara", "00:00", "06:00", "Hiburan santai waktu malam dan tayangan sinema serantau."),
            EpgProgram("Okey Pagi Ini & Bayu Di Hatiku", "06:00", "09:00", "Menyemai keceriaan pagi bersama info gaya hidup segar."),
            EpgProgram("Rentak Borneo / Kenali Budaya", "09:00", "12:00", "Memartabatkan keunikan tradisi etnik Sabah dan Sarawak."),
            EpgProgram("Sinema Sabah & Sarawak", "12:00", "15:00", "Filem dan telemovie berlatarbelakangkan keindahan bumi Borneo."),
            EpgProgram("Variasi Seni Kita / Komedi Santai", "15:00", "18:00", "Program sketsa gelak tawa dan bakat seni generasi muda."),
            EpgProgram("Berita Borneo & Rangkuman Wilayah", "18:00", "20:00", "Liputan berita terperinci pembangunan Sabah, Sarawak dan Labuan."),
            EpgProgram("Teater Okey / Drama Etnik Perdana", "20:00", "22:00", "Siri drama perdana mengetengahkan dialek dan kebudayaan tempatan."),
            EpgProgram("Okey Muzik & Dokumentari Warisan", "22:00", "24:00", "Persembahan akustik muzik pribumi dan rakaman khazanah alam.")
        ),

        "live_sukan_rtm" to listOf(
            EpgProgram("Sorotan Gol & Kejohanan Antarabangsa", "00:00", "06:00", "Aksi gol terindah dan rakaman kejohanan sukan berprestij."),
            EpgProgram("Aksi Sukan Pagi & Sorotan Liga", "06:00", "10:00", "Rangkuman keputusan perlawanan sukan semalam dan jadual hari ini."),
            EpgProgram("Replay Perlawanan Khas / Badminton Dunia", "10:00", "13:00", "Aksi mendebarkan skuad badminton kebangsaan di pentas dunia."),
            EpgProgram("Arena Global & Temubual Atlet Kebangsaan", "13:00", "15:00", "Diari latihan atlet negara dan persiapan ke temasya antarabangsa."),
            EpgProgram("Liga Malaysia / Aksi Bolasepak", "15:00", "18:00", "Sorotan saingan Piala FA, Liga Super dan Piala Malaysia."),
            EpgProgram("Nadi Sukan Live & Berita Sukan", "18:00", "20:00", "Liputan pantas dan temuramah eksklusif dari gelanggang sukan."),
            EpgProgram("Siaran Langsung Kejohanan Sukan Utama", "20:00", "23:00", "Siaran langsung aksi perlawanan sukan panas pilihan."),
            EpgProgram("Analisis Taktikal & Sorotan Harian", "23:00", "24:00", "Ulasan mendalam jurulatih dan penganalisis sukan bertauliah.")
        ),

        "live_berita_rtm" to listOf(
            EpgProgram("Berita RTM Siaran 24 Jam", "00:00", "06:00", "Saluran berita berterusan membawakan fakta tepat dan telus."),
            EpgProgram("Berita Terkini Pagi & Akhbar Harian", "06:00", "09:00", "Ulasan tajuk akhbar utama dan laporan cuaca negara."),
            EpgProgram("Buletin Ekonomi & Parlimen Live", "09:00", "12:00", "Kemas kini sektor kewangan dan liputan persidangan dewan."),
            EpgProgram("Berita Tengah Hari & Laporan Khas", "12:00", "15:00", "Laporan khas wartawan penyiaran dari seluruh pelosok tanah air."),
            EpgProgram("Bicara Petang & Rangkuman Antarabangsa", "15:00", "18:00", "Diskusi isu rakyat dan rangkuman perkembangan dunia."),
            EpgProgram("Liputan Berita Utama & Analisis Semasa", "18:00", "21:00", "Liputan eksklusif peristiwa sensasi dan dasar kerajaan."),
            EpgProgram("Sorotan Berita 10 & Kupasan Isu", "21:00", "23:00", "Kupasan kritis peristiwa penting sepanjang hari."),
            EpgProgram("Laporan Tengah Malam & Berita Global", "23:00", "24:00", "Rangkuman akhir berita sebelum fajar menyinsing.")
        ),

        "live_parlimen_rakyat" to listOf(
            EpgProgram("Sorotan Hansard & Arkib Perbahasan", "00:00", "10:00", "Rakaman perbahasan usul penting Dewan Rakyat Parlimen Malaysia."),
            EpgProgram("Siaran Langsung: Soal Jawab Lisan & Perbahasan", "10:00", "13:00", "Siaran langsung sesi pagi waktu pertanyaan menteri dan jawapan lisan."),
            EpgProgram("Rehat Tengah Hari Parlimen", "13:00", "14:30", "Penangguhan seketika persidangan Dewan Rakyat."),
            EpgProgram("Siaran Langsung: Rang Undang-Undang & Penggulungan", "14:30", "17:30", "Siaran langsung sesi petang perbahasan RUU dan jawapan penggulungan."),
            EpgProgram("Rangkuman Prosiding & Analisis Hansard", "17:30", "24:00", "Sorotan intipati perbahasan ahli-ahli Yang Berhormat hari ini.")
        ),

        "live_parlimen_negara" to listOf(
            EpgProgram("Sorotan Hansard Senat Malaysia", "00:00", "10:00", "Arkib perbahasan dan usul Dewan Negara Parlimen Malaysia."),
            EpgProgram("Siaran Langsung: Sesi Pagi Dewan Negara", "10:00", "13:00", "Siaran langsung semakan rang undang-undang oleh ahli-ahli Senat."),
            EpgProgram("Rehat Tengah Hari Parlimen", "13:00", "14:30", "Penangguhan seketika persidangan Dewan Negara."),
            EpgProgram("Siaran Langsung: Sesi Petang Dewan Negara", "14:30", "17:30", "Siaran langsung kelulusan usul perundangan dan kenyataan kementerian."),
            EpgProgram("Rangkuman Prosiding & Arkib Perbahasan", "17:30", "24:00", "Analisis pencapaian legislatif dan semak imbang dasar negara.")
        ),

        "live_rtm_asean" to listOf(
            EpgProgram("ASEAN Nightline & Documentary Showcase", "00:00", "06:00", "Showcasing the vibrant heritage and nature of Southeast Asian nations."),
            EpgProgram("ASEAN Morning Chronicle (English)", "06:00", "12:00", "Regional news, business partnerships and community development."),
            EpgProgram("Southeast Asia Culture & Business", "12:00", "18:00", "Cross-border trade, tech innovations and travel spotlights."),
            EpgProgram("ASEAN Prime Insights & Diplomacy Forum", "18:00", "22:00", "High-level diplomatic summits and dialogue between member states."),
            EpgProgram("ASEAN Panorama & Regional Bulletin", "22:00", "24:00", "Daily roundup of achievements across the ASEAN community.")
        ),

        "live_8tv" to listOf(
            EpgProgram("Drama Bersiri Asia & Muzik Mandarin", "00:00", "06:00", "华语连续剧与流行音乐"),
            EpgProgram("Senamrobik & Gaya Hidup Cergas", "06:00", "07:00", "活力早晨"),
            EpgProgram("Berita 8 Pagi (八度早晨新闻)", "07:00", "08:00", "Berita awal pagi hal ehwal semasa masyarakat Cina dan global."),
            EpgProgram("Living Delight (活力加油站)", "08:00", "09:30", "Program bual bicara pagi gaya hidup sihat, kesihatan dan tips berguna."),
            EpgProgram("Drama Bersiri TVB Klasik (经典港剧)", "09:30", "11:30", "Koleksi drama Hong Kong TVB kegemaran ramai."),
            EpgProgram("Berita Terkini Mandarin (八度快讯)", "11:30", "12:30", "Rangkuman ringkas berita terkini dalam dan luar negara."),
            EpgProgram("Varieti Makanan & Kembara Rasa Asia", "12:30", "14:00", "Meneroka gastronomi masakan Asia dan tradisi kulinari."),
            EpgProgram("Teater Petang: Drama Taiwan / Korea", "14:00", "17:00", "Siri melodrama petang penuh emosi dan kasih sayang."),
            EpgProgram("Siri Kartun & Animasi Mandarin", "17:00", "18:30", "Siri animasi popular untuk tontonan seisi keluarga."),
            EpgProgram("Berita 8TV Edisi Petang", "18:30", "19:00", "Sorotan berita tempatan dan perkembangan ekonomi pasaran."),
            EpgProgram("Drama Bersiri Perdana TVB (翡翠剧场)", "19:00", "20:00", "Siaran perdana drama aksi dan misteri TVB terkini."),
            EpgProgram("Berita Mandarin 8TV (八度空间华语新闻 Live)", "20:00", "21:00", "Buletin berita Mandarin utama nombor satu di Malaysia secara langsung."),
            EpgProgram("Drama Melodrama Terhebat (亚洲精选剧场)", "21:00", "22:30", "Siri drama mega blockbuster Asia pilihan penonton."),
            EpgProgram("Rancangan Varieti Hiburan & Muzik", "22:30", "23:30", "Rancangan realiti, konsert dan temu bual selebriti."),
            EpgProgram("Ringkasan Berita Dunia & Perniagaan", "23:30", "24:00", "Laporan ringkas berita dunia dan analisis pasaran kewangan.")
        ),

        "live_tv9" to listOf(
            EpgProgram("Koleksi Telefilem Nostalgia TV9", "00:00", "06:00", "Telefilem klasik karya sutradara terkemuka tanah air."),
            EpgProgram("Tanyalah Ustaz (Ulangan Pilihan)", "06:00", "07:00", "Bimbingan rohani, soal jawab agama dan panduan fardu ain."),
            EpgProgram("Animasi Jaguh Kartun (Zon Pagi)", "07:00", "08:30", "Siri kartun animasi ceria untuk kanak-kanak sebelum ke sekolah."),
            EpgProgram("Santai Pagi & Gaya Hidup Famili", "08:30", "10:00", "Inspirasi dekorasi, keibubapaan dan resepi mudah hidangan keluarga."),
            EpgProgram("Drama Melayu Slot Iris", "10:00", "12:00", "Siri drama menyentuh hati penuh pengajaran nilai kekeluargaan."),
            EpgProgram("Berita TV9 Tengah Hari", "12:00", "13:00", "Liputan berita harian ringkas dan padat dari seluruh pelosok negara."),
            EpgProgram("Telefilem Istimewa TV9", "13:00", "15:00", "Koleksi telemovie Melayu eksklusif pelbagai genre."),
            EpgProgram("Zon Animasi Hebat: Ejen Ali & BoBoiBoy", "15:00", "17:00", "Animasi wira tempatan kegemaran kanak-kanak Malaysia."),
            EpgProgram("Drama Remaja & Famili TV9", "17:00", "18:30", "Kisah persahabatan, cabaran remaja dan cita-cita hidup."),
            EpgProgram("Kapsul Menarik & Gaya Santai", "18:30", "19:00", "Paparan keunikan komuniti setempat dan makanan viral."),
            EpgProgram("Berita TV9 (Siaran Langsung Utama)", "19:00", "20:00", "Buletin berita perdana TV9 menyajikan isu rakyat secara telus."),
            EpgProgram("Drama Perdana Slot Diandra", "20:00", "21:30", "Slot drama perdana pilihan penonton dengan jalan cerita memukau."),
            EpgProgram("Rancangan Realiti & Komedi Famili", "21:30", "23:00", "Hiburan komedi dan persembahan bakat pentas sekeluarga."),
            EpgProgram("Sorotan Sukan & Berita Akhir", "23:00", "24:00", "Aksi gol menarik, sukan permotoran dan perkembangan sukan.")
        ),

        "live_tvs" to listOf(
            EpgProgram("Khazanah Borneo & Muzik Warisan", "00:00", "06:00", "Alunan sape dan dokumentari keunikan warisan etnik Sarawak."),
            EpgProgram("Fajar Kenyalang & Doa Pagi", "06:00", "07:00", "Doa harian dan kata-kata perangsang memulakan hari."),
            EpgProgram("Berita Wilayah TVS Pagi", "07:00", "08:30", "Berita terkini pembangunan dan komuniti seluruh bahagian Sarawak."),
            EpgProgram("Kamek Orang (Gaya Hidup & Komuniti)", "08:30", "10:00", "Mengenali personaliti tempatan, adat resam dan kraftangan Sarawak."),
            EpgProgram("Dokumentari Rimba Tropika Borneo", "10:00", "12:00", "Eksplorasi hidupan liar flora dan fauna di hutan hujan Borneo."),
            EpgProgram("Berita TVS Tengah Hari", "12:00", "13:00", "Liputan berita hal ehwal semasa di Kuching, Sibu, Miri dan Bintulu."),
            EpgProgram("Drama Sarawak & Teater Budaya", "13:00", "15:00", "Karya pementasan dan drama televisyen terbitan anak Sarawak."),
            EpgProgram("Rentak Borneo: Muzik Tradisi & Moden", "15:00", "17:00", "Menghimpunkan artis tempatan dan lagu-lagu popular Borneo."),
            EpgProgram("Eksplorasi Sungai & Eko-Pelancongan", "17:00", "18:30", "Kembara menyusuri sungai Rajang dan destinasi pelancongan eksotik."),
            EpgProgram("Berita Iban & Dialek Tempatan TVS", "18:30", "19:30", "Rangkuman berita dalam bahasa Iban dan dialek tempatan."),
            EpgProgram("Lensa Kenyalang & Isu Pembangunan", "19:30", "20:00", "Analisis inisiatif ekonomi digital dan kemajuan negeri Sarawak."),
            EpgProgram("Utama TVS (Buletin Berita Perdana)", "20:00", "21:00", "Berita perdana siaran langsung komprehensif Sarawak dan Malaysia."),
            EpgProgram("Drama Bersiri Kenyalang", "21:00", "22:30", "Siri drama eksklusif berlatarbelakangkan keindahan bumi kenyalang."),
            EpgProgram("Malam Apai / Muzik Santai Borneo", "22:30", "24:00", "Bual bicara santai larut malam bersama tetamu undangan istimewa.")
        ),

        "live_astro_awani" to listOf(
            EpgProgram("AWANI 7:45 (Rangkuman Berita Khas)", "00:00", "06:00", "Rangkuman menyeluruh peristiwa terpenting negara dan ulasan pakar."),
            EpgProgram("Fajar AWANI (Berita Subuh)", "06:00", "07:00", "Pemerhatian awal berita nasional dan tinjauan pasaran niaga."),
            EpgProgram("AWANI Pagi (Siaran Langsung Berita Utama)", "07:00", "09:00", "Laporan langsung wartawan di lapangan membawakan berita panas."),
            EpgProgram("Agenda AWANI (Wacana Isu Dasar Negara)", "09:00", "11:00", "Perbincangan kritikal bersama pembuat dasar dan pemimpin masyarakat."),
            EpgProgram("Niaga AWANI (Bursa Malaysia & Pasaran)", "11:00", "12:00", "Analisis indeks saham, perniagaan PKS dan ekonomi digital."),
            EpgProgram("AWANI Tengah Hari (Liputan Isu Hangat)", "12:00", "13:00", "Berita pantas mutakhir perkembangan semasa dalam dan luar negara."),
            EpgProgram("AWANI Borneo (Fokus Sabah & Sarawak)", "13:00", "14:00", "Liputan khusus pembangunan, politik dan masyarakat Borneo."),
            EpgProgram("Laporan Khas & Dokumentari Eksklusif", "14:00", "16:00", "Penyiasatan mendalam isu alam sekitar, sosial dan kemanusiaan."),
            EpgProgram("Niaga Petang & Analisis Kewangan Global", "16:00", "17:30", "Tinjauan penutupan pasaran saham dan tukaran mata wang."),
            EpgProgram("Buletin Petang AWANI", "17:30", "19:00", "Rangkuman peristiwa penting menjelang petang dari seluruh negeri."),
            EpgProgram("AWANI 7:45 (Berita Pilihan Utama Malaysia)", "19:00", "20:00", "Buletin berita perdana nombor satu dengan penceritaan berimpak."),
            EpgProgram("AWANI Tonight (Berita Bahasa Inggeris & Dunia)", "20:00", "21:00", "Global news highlights, diplomatic affairs and top regional stories."),
            EpgProgram("Dialog AWANI (Wawancara Eksklusif Tokoh)", "21:00", "22:00", "Temu bual mendalam bersama pemimpin kerajaan, korporat dan NGO."),
            EpgProgram("Consider This (Current Affairs Debate)", "22:00", "23:00", "Provocative, insightful debate on policies shaping our world."),
            EpgProgram("AWANI Global & Penutup Hari Ini", "23:00", "24:00", "Sorotan berita dunia antarabangsa dan ikhtisar peristiwa 24 jam.")
        ),

        "live_makkah" to listOf(
            EpgProgram("Tilawah Al-Quran & Qiamullail (Masjidil Haram)", "00:00", "04:30", "Siaran langsung solat sunat malam dan bacaan merdu ayat suci Al-Quran di hadapan Kaabah."),
            EpgProgram("Siaran Langsung: Solat Subuh Berjemaah Makkah", "04:30", "06:30", "Siaran langsung azan Subuh dan solat fardu berjemaah dari Masjidil Haram."),
            EpgProgram("Alunan Suci Al-Quran & Tafsir Terjemahan", "06:30", "11:30", "Tilawah ayat-ayat Al-Quranul Karim diselang-seli paparan jemaah bertawaf."),
            EpgProgram("Siaran Langsung: Solat Zohor Berjemaah Makkah", "11:30", "13:30", "Siaran langsung azan Zohor dan solat fardu berjemaah di Masjidil Haram."),
            EpgProgram("Tilawah Qari Dunia & Keindahan Kaabah", "13:30", "15:30", "Bacaan Al-Quran maqam hijaz dan bayati oleh imam-imam Masjidil Haram."),
            EpgProgram("Siaran Langsung: Solat Asar Berjemaah Makkah", "15:30", "17:30", "Siaran langsung azan Asar dan solat fardu berjemaah di Kaabah."),
            EpgProgram("Siaran Langsung: Solat Maghrib Berjemaah Makkah", "17:30", "19:30", "Siaran langsung solat Maghrib berjemaah dan suasana matahari terbenam di Makkah."),
            EpgProgram("Siaran Langsung: Solat Isyak Berjemaah & Doa", "19:30", "21:30", "Siaran langsung solat Isyak berjemaah dan doa munajat dari Masjidil Haram."),
            EpgProgram("Tawaf 24 Jam di Hadapan Kaabah & Al-Quran", "21:30", "24:00", "Siaran langsung jutaan jemaah mengelilingi Kaabah sambil bertalbiah dan berzikir.")
        )
    )

    private val defaultGenericSchedule = listOf(
        EpgProgram("Siaran Langsung HD", "00:00", "12:00", "Siaran rasmi berkualiti tinggi."),
        EpgProgram("Program Perdana Pilihan", "12:00", "24:00", "Rancangan televisyen pilihan penonton.")
    )

    /**
     * Resolves current program, next program, and completion progress for a given channel ID.
     */
    fun getEpgInfo(channelId: String, timeMillis: Long = System.currentTimeMillis()): ChannelEpgInfo {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kuala_Lumpur"))
        cal.timeInMillis = timeMillis
        val currentMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        val programs = scheduleMap[channelId] ?: defaultGenericSchedule
        var currentProg: EpgProgram? = null
        var nextProg: EpgProgram? = null

        for (i in programs.indices) {
            val prog = programs[i]
            if (currentMinute >= prog.startMinute && currentMinute < prog.endMinute) {
                currentProg = prog
                nextProg = if (i + 1 < programs.size) programs[i + 1] else programs.firstOrNull()
                break
            }
        }

        val effectiveCurrent = currentProg ?: programs.first()
        val totalDuration = (effectiveCurrent.endMinute - effectiveCurrent.startMinute).coerceAtLeast(1)
        val elapsed = (currentMinute - effectiveCurrent.startMinute).coerceIn(0, totalDuration)
        val progress = (elapsed.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)

        return ChannelEpgInfo(
            channelId = channelId,
            currentProgram = effectiveCurrent,
            nextProgram = nextProg,
            progress = progress
        )
    }

    /**
     * Helper string for compact cards: e.g. "Buletin Utama (20:00 - 21:00)"
     */
    fun getNowPlaying(channelId: String): String {
        val info = getEpgInfo(channelId)
        return "${info.currentProgram.title} (${info.currentProgram.startTime} - ${info.currentProgram.endTime})"
    }

    /**
     * Helper string for compact cards without timeslot: e.g. "Buletin Utama"
     */
    fun getNowPlayingTitle(channelId: String): String {
        return getEpgInfo(channelId).currentProgram.title
    }
}
