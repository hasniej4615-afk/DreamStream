import java.io.File

fun extractStableId(url: String): String = url.substringBefore('?').trimEnd('/').substringAfterLast('/')

fun main() {
    val title = "The Husband (2026)"
    val isExplicitSeries = title.contains("season", ignoreCase = true) || title.contains("episode", ignoreCase = true) || true
    println("isExplicitSeries: \")
}
