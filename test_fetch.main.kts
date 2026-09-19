
@file:Repository("https://repo1.maven.org/maven2/")
@file:DependsOn("org.jsoup:jsoup:1.16.1")

import org.jsoup.Jsoup
import java.net.URL

val html = URL("https://actors-pictures.com/country/malaysia/").readText()
val doc = Jsoup.parse(html)
val els = doc.select(".ml-item h2")
println("Found ${els.size} items on main site:")
els.take(10).forEach { println(it.text()) }

