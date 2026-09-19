import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Test2 {
    public static void main(String[] args) throws Exception {
        String html = new String(java.nio.file.Files.readAllBytes(new File("movie_page.html").toPath()), "UTF-8");
        Document doc = Jsoup.parse(html, "https://katakatamutiara.com/this-that-and-everything-in-between-2026/");
        
        Elements serverContainers = doc.select(".muvipro-player-tabs, .player-tabs, .gmr-player-nav, .gmr-server-wrap, #player-option-1, #player-option-2, #player-option-3, .server-list, .list-server, .source-box, .sources-list, .mirror-list, .list-server-items, .server-wrap, .player-options");
        
        Elements elements;
        if (!serverContainers.isEmpty()) {
            elements = serverContainers.select("a, li, span, [data-post][data-n], [data-index], .do-player-option, .server-item, .gmr-player-option, .btn-server, .source, .mirror");
        } else {
            elements = doc.select(".do-player-option, .server-item, .source-box, a:contains(Server), [data-post][data-n], [data-index], .player-option, .gmr-player-option, .btn-server, .source-box a, .mirror-item, a[data-url], a[data-link], .server a, .mirror a");
        }
        
        System.out.println("Found " + elements.size() + " elements");
        for (Element el : elements) {
            String link = el.attr("abs:href");
            if (link.isEmpty()) link = el.attr("data-link");
            if (link.isEmpty()) link = el.attr("data-url");
            if (link.isEmpty()) link = el.attr("abs:src");
            if (link.isEmpty()) link = el.attr("href");
            
            System.out.println("Element: " + el.tagName() + ", text: " + el.text().trim() + ", link: " + link);
        }
    }
}
