package PageCompress;

/**
 * Created by ChenChen on 4/13/16.
 */

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

public class PageCompress {
    private String pageID;  // file name
    private String pageHTML;    // page content (tag+text)

    public PageCompress(String name, String page) {
        pageID = name;
        pageHTML = page;
    }

    /*
     * We use "Jsoup" to extract data that we need from HTML
     * "Jsoup" is much more efficient and stable then "Jtidy",
     * and it can filter <script> in <body> as well while "Jtidy"
     * cannot do it
     */
    public PageFile GetPageFile() throws Exception {

        if (pageID == null || pageID.equals("")) {
            throw new Exception("pageID cannot be empty!");
        }

        // manually modify some special tag case
        pageHTML = MakeupPageHTML(pageHTML);

        Document doc = Jsoup.parse(pageHTML);

        // get page title
        String title = doc.title();
        if (title == null || title.equals("")) {
            title = "NOTITLE";
        }

        // get page body text content
        String bodyText = doc.body().text();

        // we can filter duplicated subURL at here
        // And we also can filter those invalid urls at here (I didn't do it in this code)
        Set<String> subURLs = new HashSet<String>();
        Elements links = doc.select("a");
        for (Element link : links) {
            String url = link.attr("href");
            if (url == null || "".equals(url)) {
                continue;
            }
            subURLs.add(url);
        }
        List<String> uniqueSubURLs = new ArrayList<>(subURLs);

        PageFile pageFile = new PageFile(pageID, title, uniqueSubURLs, bodyText);
        return pageFile;
    }

    /*
     * manually modify some special tag case
     */
    private String MakeupPageHTML(String pageHTML) {
        // tricky case1: for some tag, such like <option>, if there is not seperator in text
        // HTML parser will make spaceless concatenation problem
        // this is not the fault of Jsoup, it's the falut of UI designer
        // (we should know this method is not stable, if we meet more special case,
        // we need add them into here)
        pageHTML = pageHTML.replaceAll("</option>", " </option>");
        return pageHTML;
    }
}
