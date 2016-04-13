package PageCompress;

/**
 * Created by ChenChen on 4/13/16.
 */
import java.util.ArrayList;
import java.util.List;

public class PageFile {
    String PageID;
    String title;
    List<String> subURLs;
    String content;

    PageFile() {
        subURLs = new ArrayList<String>();
    }

    PageFile(String id, String t, List<String> urls, String c) {
        PageID = id;
        title = t;
        subURLs = urls;
        content = c;
    }
}
