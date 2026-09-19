import org.jsoup.Jsoup
import java.io.File

fun main() {
    val file = File("husband.html")
    val doc = Jsoup.parse(file, "UTF-8", "https://ohionewsnow.com")
    val title = "The Husband (2026)"
    
    val isExplicitSeries = title.contains("season", ignoreCase = true) || 
                           title.contains("episode", ignoreCase = true) || 
                           doc.select(".gmr-moviedata:contains(Serial TV)").isNotEmpty()
                           
    println("isExplicitSeries: \")
    
    val episodes = mutableListOf<String>()
    val mainArea = doc.select("#muvipro_player_content_id, .muvipro-player-tabs, .muvipro-listepisode, .list-episode, .episodios, .gmr-listseries, .eps-list, .episode-list").firstOrNull() ?: doc
    mainArea.select("a").forEach { el ->
        val epName = el.text().trim(); val epUrl = el.attr("abs:href"); val lowUrl = epUrl.lowercase(); val lowName = epName.lowercase()
        val isServer = lowName.contains("server") || lowName.contains("mirror") || lowUrl.contains("player=") || lowUrl.contains("mirror=")
        val isEpisodeLink = (lowUrl.contains("/eps/") || lowUrl.contains("/episode/") || lowUrl.contains("-episode-")) && 
                           !lowName.contains("next") && !lowName.contains("prev") && !lowName.contains("lanjut") && !lowName.contains("sebelum") && !lowName.contains("halaman")
                           
        if (epUrl.isNotEmpty() && !isServer && isEpisodeLink) {
            episodes.add(epName)
        }
    }
    println("Episodes count: \")
}
