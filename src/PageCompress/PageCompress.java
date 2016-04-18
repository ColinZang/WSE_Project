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

        boolean hasSpecialPunc = HashSpecialPunctuation(pageHTML);

        Document doc = Jsoup.parse(pageHTML);

        // get page title
        String title = doc.title();
        if (title == null || title.equals("")) {
            title = "NOTITLE";
        }

        // get page body text content
        // in some pages, they don't have <body>...</body>
        String bodyText = "";
        if (pageHTML.indexOf("<body") != -1 || pageHTML.indexOf("<BODY") != -1) {
            bodyText = doc.body().text();
            bodyText = MakeupPageBodyContent(bodyText, hasSpecialPunc);
            // bodyText = bodyText.replace("&nbsp", " ");
        }

        // we can filter duplicated subURL at here
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
        pageHTML = pageHTML.replace("</option>", " </option>");
        pageHTML = pageHTML.replace("</name>", " </name>");


        // delete imbeded <code> tag
        pageHTML = pageHTML.replace("<CODE", "<code");
        pageHTML = pageHTML.replace("/CODE>", "/code>");
        String res = pageHTML;
        if (res.indexOf("<code>") != -1) {
            int index = 0;
            int iEndCode;
            while ((index = res.indexOf("<code")) != -1) {
                iEndCode = res.indexOf("/code>",index) + 5;
                if (iEndCode < index) {
                    iEndCode = res.length() - 1;
                }
                // delete <code>...</code>
                if (iEndCode < res.length() - 1) {
                    res = res.substring(0, index) + res.substring(iEndCode+1);
                } else {
                    res = res.substring(0, index+1);
                }
            }
            return res;
        } else {
            return pageHTML;
        }
    }

    /*
     * check whether html page has special punctuation "<" or ">" which is not tag but in text
     */
    private boolean HashSpecialPunctuation(String pageHTML) {
        if (pageHTML.indexOf("&lt;") != -1 || pageHTML.indexOf("&gt;") != -1) {
            return true;
        } else {
            return false;
        }
    }

    private String MakeupPageBodyContent(String content, boolean hasSpecialPunc) {
        // skip<script> and <style>
        String modifyContent = content;
        modifyContent.replace("<SCRIPT", "<script");
        modifyContent.replace("<STYLE", "<style");
        modifyContent.replace("/SCRIPT>", "/script>");
        modifyContent.replace("/STYLE>", "/style>");

        // delete <script>...</script>
        if (modifyContent.indexOf("<script") != -1) {
            int index = 0;
            int iEnd;
            while ((index = modifyContent.indexOf("<script")) != -1) {
                iEnd = modifyContent.indexOf("/script>",index) + 7;
                if (iEnd < index) {
                    iEnd = modifyContent.length() - 1;
                }
                if (iEnd < modifyContent.length() - 1) {
                    modifyContent = modifyContent.substring(0, index) + modifyContent.substring(iEnd+1);
                } else {
                    modifyContent = modifyContent.substring(0, index+1);
                }
            }
        }

        // delete <style>...</style>
        if (modifyContent.indexOf("<style") != -1) {
            int index = 0;
            int iEnd;
            while ((index = modifyContent.indexOf("<style")) != -1) {
                iEnd = modifyContent.indexOf("/style>",index) + 6;
                if (iEnd < index) {
                    iEnd = modifyContent.length() - 1;
                }
                if (iEnd < modifyContent.length() - 1) {
                    modifyContent = modifyContent.substring(0, index) + modifyContent.substring(iEnd+1);
                } else {
                    modifyContent = modifyContent.substring(0, index+1);
                }
            }
        }

        // hasSpecialPunc is true means in text has "&lt;" (<) or "&gt;" (>)
        // then we cannot do second <tag> pass
        if (hasSpecialPunc) {
            return modifyContent;
        } else {
            // flag: true means is in <>; false means not in <>
            boolean flag = false;
            int count = 0;
            String res = "";
            for (int i = 0; i < modifyContent.length(); i++) {
                if (flag) {
                    if (modifyContent.charAt(i) == '>') {
                        count--;
                        if (count == 0) {
                            flag = false;
                        }
                    } else if (modifyContent.charAt(i) == '<'){
                        count++;
                    } else {
                        continue;
                    }
                } else {
                    if (modifyContent.charAt(i) == '<') {
                        count++;
                        flag = true;
                    } else {
                        res += modifyContent.charAt(i);
                    }
                }
            }
            return res;
        }
    }
}
