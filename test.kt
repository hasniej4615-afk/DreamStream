
fun main() {
    val path = "https://ww44.pencurimovie.baby/country/malaysia/"
    if (path.isEmpty() || path == "/") { println("/"); return }
    if (path.startsWith("http")) { println(path.trim()); return }
    
    val repaired = path.trim().removePrefix("/")
    if (repaired.startsWith("https:") || repaired.startsWith("http:")) {
        println(repaired.replaceFirst(":/", "://").replace(Regex("(?<=://)/{1,}"), ""))
        return
    }
}

