package PageCompress;

/**
 * Created by ChenChen on 4/13/16.
 */

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

public class PageCompress {
    private String dataPath;
    private String resultPath;

    PageCompress(String dp, String rp) {
        dataPath = dp;
        resultPath = rp;
    }

    private void run() {
        File dataDir = null;
        File resultDir = null;
        try {
            dataDir = new File(dataPath);
            resultDir = new File(resultPath);
        } catch (Exception e) {
            System.out.println("data path or result path is not directory!");
            System.exit(1);
        }

        File[] files = dataDir.listFiles();
        for (File file : files) {
            String name = file.getName();
            String ext = getExtension(name);
            if (!ext.equals("html")) {
                System.out.println("File \"" + name + "\" is not a web page");
                continue;
            }
            String nameWoExt = name.substring(0, name.length() - 5);
            String pageHTML = readPage(file);
            pageHTML = MakeupPageHTML(pageHTML);
            PageFile pageFile = GetPage(nameWoExt, pageHTML);
            SavePageFile(pageFile, resultDir);
        }
    }

    /*
     * get file extension
     */
    private String getExtension(String filename) {
        if (filename == null) {
            return null;
        }
        int extensionPos = filename.lastIndexOf('.');
        int lastUnixPos = filename.lastIndexOf('/');
        int lastWindowsPos = filename.lastIndexOf('\\');
        int lastSeparator = Math.max(lastUnixPos, lastWindowsPos);

        int index = lastSeparator > extensionPos ? -1 : extensionPos;
        if (index == -1) {
            return "";
        } else {
            return filename.substring(index + 1);
        }
    }

    /*
     * read pages from file
     */
    private String readPage(File file) {
        String wholePage = "";
        try {
            InputStream stream = new FileInputStream(file);
            byte[] tempbytes = new byte[1000];
            int byteread = 0;
            while ((byteread = stream.read(tempbytes)) != -1) {
                String newContent = new String(tempbytes, 0, byteread);
                wholePage += newContent;
            }
            stream.close();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            return wholePage;
        }
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

    /*
     * We use "Jsoup" to extract data that we need from HTML
     * "Jsoup" is much more efficient and stable then "Jtidy",
     * and it can filter <script> in <body> as well while "Jtidy"
     * cannot do it
     */
    private PageFile GetPage(String nameWoExt, String wholePage) {
        Document doc = Jsoup.parse(wholePage);

        String title = doc.title();
        if (title == null || title.equals("")) {
            title = "NOTITLE";
        }

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

        PageFile pageFile = new PageFile(nameWoExt, title, uniqueSubURLs, bodyText);
        return pageFile;
    }

    /*
     * save compressed page content into file
     */
    private void SavePageFile(PageFile pf, File dir) {
        String path = dir.getAbsolutePath() + File.separator + pf.PageID;
        File file = new File(path);
        try (FileOutputStream fos = new FileOutputStream(file)){
            if (!file.exists()) {
                file.createNewFile();
            }
            String fileContent = "#TITLE#\n" + pf.title + "\n\n" + "#SUBURLS#\n";
            for (String url : pf.subURLs) {
                fileContent += url + "\n";
            }
            fileContent += "\n#CONTENT#\n";
            fileContent += pf.content;

            byte[] contentInBytes = fileContent.getBytes();

            fos.write(contentInBytes);
            fos.flush();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Save page: " + pf.PageID);
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("You should give pathes for data and result folder");
            System.exit(1);
        }

        String dataPath = args[0];
        String resultPath = args[1];
        PageCompress pc = new PageCompress(dataPath, resultPath);
        pc.run();
    }
}
