
import org.jsoup.Jsoup
import java.io.File

fun main() {
    val html = File("movie_page.html").readText()
    val doc = Jsoup.parse(html, "https://katakatamutiara.com/this-that-and-everything-in-between-2026/")
    
    val serverContainers = doc.select(".muvipro-player-tabs, .player-tabs, .gmr-player-nav, .gmr-server-wrap, #player-option-1, #player-option-2, #player-option-3, .server-list, .list-server, .source-box, .sources-list, .mirror-list, .list-server-items, .server-wrap, .player-options")
    val elements = if (serverContainers.isNotEmpty()) {
        serverContainers.select("a, li, span, [data-post][data-n], [data-index], .do-player-option, .server-item, .gmr-player-option, .btn-server, .source, .mirror")
    } else {
        doc.select(".do-player-option, .server-item, .source-box, a:contains(Server), [data-post][data-n], [data-index], .player-option, .gmr-player-option, .btn-server, .source-box a, .mirror-item, a[data-url], a[data-link], .server a, .mirror a")
    }
    
    println("Found " + elements.size + " elements")
    for (el in elements) {
        val link = el.attr("abs:href")
        println("El: " + el.text() + " -> " + link)
    }
}

