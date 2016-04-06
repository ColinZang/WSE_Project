package SeedExtractor;

/**
 * Created by ChenChen on 4/5/16.
 */

public class MyPage {
    private String title;
    private String url;

    MyPage() {
        title = "NULL";
        url = "NULL";
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String t) {
        this.title = t;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String u) {
        this.url = u;
    }
}
