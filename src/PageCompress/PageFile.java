package PageCompress;

/**
 * Created by ChenChen on 4/13/16.
 */
import java.util.ArrayList;
import java.util.List;

public class PageFile {
    private String pageID;
    private String title;
    private List<String> subURLs;
    private String content;

    public PageFile() {
        subURLs = new ArrayList<String>();
    }

    public PageFile(String id, String t, List<String> urls, String c) {
        pageID = id;
        title = t;
        subURLs = urls;
        content = c;
    }

    public String getPageID() {
        return pageID;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public List<String> getSubURLs() {
        return subURLs;
    }

    public int getWordsCount() {
        if (content == null || "".equals(content)) {
            return 0;
        } else {
            String[] words = content.split("\\s+");
            return words.length;
        }
    }
}
