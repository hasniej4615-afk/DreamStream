
@file:Repository("https://repo1.maven.org/maven2/")
@file:DependsOn("org.jsoup:jsoup:1.16.1")

import org.jsoup.Jsoup
import java.io.File

val html = File("pencuri.html").readText()
val doc = Jsoup.parse(html)
val els = doc.select(".item, .ml-item, .post, article")
println("Found ${els.size} items")
if (els.isNotEmpty()) {
    val el = els[0]
    println("Testing element 0:")
    var titleEl = el.selectFirst(".entry-title, h2, h3, .title, .title-movie")
    var rawTitle = titleEl?.text()?.trim() ?: ""
    println("rawTitle from h2/etc: '$rawTitle'")
    
    if (rawTitle.isEmpty()) {
        val aEl = el.selectFirst("a[oldtitle], a[title]")
        rawTitle = aEl?.attr("oldtitle")?.takeIf { it.isNotEmpty() }
            ?: aEl?.attr("title")?.takeIf { it.isNotEmpty() }
            ?: aEl?.text()?.trim()
            ?: ""
        println("rawTitle from a tag fallback: '$rawTitle'")
    }
}

