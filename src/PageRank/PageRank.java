package PageRank;

import java.util.*;

/**
 * Created by Wenzhao on 4/15/16.
 */
public class PageRank {
    private static HashMap<Integer, HashMap<String, String>> urlToId =
            new  HashMap<Integer, HashMap<String, String>>();
    private static HashMap<Integer, HashMap<String, Page>> idToPage =
            new HashMap<Integer, HashMap<String, Page>>();
    private static List<Page> pageList = new ArrayList<Page>();
    private static int n;
    private static int[] base;

    private static void run() {
        loadMap(mapPath);
        readThruFiles(filePath);
        double[] result = calculate(f);
        saveResult(result, savePath);
    }

    private static void readThruFiles() {
        for () {
            for () {
                Scanner readFile = new Scanner(new File(filePath + fileName));
                while (readFile.hasNext()) {
                    Page current = getPage(id, url);
                    current.setLength();
                    for (int i = 0; i < subPage.size(); i++) {
                        Page currentSub = getPage("unknown", subPage.get(i));
                        if (currentSub == null) {
                            continue;
                        }
                        curerntSub.addParentPage(current.getIndex());
                    }
                }
            }
        }
    }

    private static Page getPage(String id, String url) {
        if (id.equals("unknown")) {
            id = getId(url);
            if (id == null) {
                return null;
            }
        }
        int pos = hashId(id);
        HashMap<String, Page> current = idToPage.get(pos);
        if (current == null) {
            current = new HashMap<String, Page>();
            idToPage.put(pos, current);
        }
        Page page = current.get(id);
        if (page == null) {
            page = new Page(id, pageList.size());
            pageList.add(page);
            current.put(id, page);
            fixMap(url);
        }
        return page;
    }

    private static String getId(String url) {
        int pos = hashUrl(url);
        if (!urlToId.containsKey(pos)) {
            return null;
        }
        HashMap<String, String> current = urlToId.get(pos);
        return current.get(url);
    }

    private static int hashId(String id) {
        int threadId = id.substring();
        return id % urlToId.size();
    }

    private static int hashUrl(String url) {
        return url.hashCode() % urlToId.size();
    }

    private static class Page {
        private String id;
        private int index;
        private int length;
        private List<Integer> parentPage;

        public Page(String id, int index) {
            this.id = id;
            this.index = index;
            length = 0;
            parentPage = new ArrayList<Integer>();
        }

        public void addParentPage(Integer pageIndex) {
            parentPage.add(pageIndex);
        }

        public void setLength(int length) {
            this.length = length;
        }

        public int getLength() {
            return length;
        }

        public List<Integer> getParentPages() {
            return parentPage;
        }

        public String getId() {
            return id;
        }

        public int getIndex() {
            return index;
        }
    }

    public static void main(String[] args) {
        checkArgs(args);
        run();
    }
}
