package PageRank;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.HashSet;

/**
 * Created by Wenzhao on 4/15/16.
 */
public class PageRank {
    private static final String USAGE =
            "USAGE: java PageRank [-mapPath MAP_PATH] [-filePath FILE_PATH] [-savePath SAVE_PATH] [-f Parameter_F]";
    private static HashMap<Integer, HashMap<String, String>> urlToId =
            new  HashMap<Integer, HashMap<String, String>>();
    private static HashMap<Integer, HashMap<String, Page>> idToPage =
            new HashMap<Integer, HashMap<String, Page>>();
    private static List<Page> pageList = new ArrayList<Page>();
    private final static int HASH_MOD = 1000;
    private static HashSet<String> set = new HashSet<String>();

    private static void run(String mapPath, String filePath, String savePath, double f) {
        try {
            loadMap(mapPath);
        } catch (IOException e) {
            System.out.println("Read in map unsuccessful");
            System.exit(1);
        }
        try {
            readThruFiles(filePath);
        } catch (IOException e) {
            System.out.println("Read in files unsuccessful");
            System.exit(1);
        }
        double[] result = calculate(f);
        try {
            saveResult(result, savePath);
        } catch (IOException e) {
            System.out.println("Write results unsuccessful");
            System.exit(1);
        }
        System.out.println("finished");
    }

    private static void loadMap(String mapPath)
            throws IOException {
        if (!mapPath.endsWith(File.separator)) {
            mapPath += File.separator;
        }
        File dir = new File(mapPath);
        File[] maps = dir.listFiles();
        for (File map: maps) {
            if (!map.getName().endsWith(".mapping")) {
                continue;
            }
            Scanner reader = new Scanner(new FileReader(map));
            while (reader.hasNextLine()) {
                String url = reader.nextLine();
                String id = reader.nextLine();
                PutToUrlToId(id, url);
            }
            reader.close();
        }
    }

    private static void readThruFiles(String filePath)
            throws IOException {
        if (!filePath.endsWith(File.separator)) {
            filePath += File.separator;
        }
        File dir = new File(filePath);
        File[] jobDirs = dir.listFiles();
        int count = 0;
        for (File job: jobDirs) {
//            String dirName = job.getName();
//            int pos = dirName.indexOf('_');
//            int jobID = Integer.parseInt(dirName.substring(pos + 1, dirName.length()));
//            if (jobID > 100) {
//                continue;
//            }
            System.out.println(job.getName());
            File[] threadDirs = job.listFiles();
            if (threadDirs == null) {
                continue;
            }
            for (File thread: threadDirs) {
                File[] pages = thread.listFiles();
                if (pages == null) {
                    continue;
                }
                for (File page: pages) {
                    if (!page.getName().endsWith(".page")) {
                        continue;
                    }
                    Scanner readFile = new Scanner(new FileReader(page));
                    String id = page.getName();
                    int extPos = id.indexOf('.', 0);
                    id = id.substring(0, extPos);
                    // start parsing
                    String thisUrl = null;
                    List<String> subUrls = new ArrayList<String>();
                    int length = 0;
                    try {
                        // ignore #ThisURL# tag
                        readFile.nextLine();
                        thisUrl = readFile.nextLine();
                        // ignore #SubURL# tag
                        readFile.nextLine();
                        String line = null;
                        while (readFile.hasNextLine() && !(line = readFile.nextLine()).equals("#Length#")) {
                            subUrls.add(line);
                        }
                        length = Integer.parseInt(readFile.nextLine());
                    } catch (RuntimeException e) {
                        System.out.println("page " + id + " incomplete, ignore");
                        continue;
                    }
                    processPage(id, thisUrl, subUrls, length);
                    count++;
                    if (count % 10000 == 0) {
                        System.out.println("processed " + count + " pages");
                    }
                    readFile.close();
                }
            }
        }
        System.out.println(pageList.size());
    }

    private static double[] calculate(double f) {
        double[] base = calculateBase(f);
        calculateParentWeight(f);
        int n = pageList.size();
        System.out.println("n=" + n + " waiting for array");
        double[] result = new double[n];
        System.out.println("allocate successful");
        System.arraycopy(base, 0, result, 0, n);
        int round = 1;
        while (true) {
            System.out.println("calculating round " + round);
            double[] newResult = new double[n];
            System.out.println("allocate successful");
            System.arraycopy(base, 0, newResult, 0, n);
            for (int i = 0; i < n; i++) {
                double sum = 0;
                Page current = pageList.get(i);
                List<Integer> parentPage = current.getParentPage();
                List<Double> parentWeight = current.getParentWeight();
                for (int pos = 0; pos < parentPage.size(); pos++) {
                    int index = parentPage.get(pos);
                    sum += parentWeight.get(pos) * result[index];
                }
                newResult[i] += sum;
            }
            if (noDiff(newResult, result)) {
                break;
            }
            result = newResult;
            round++;
        }
        return result;
    }

    private static void saveResult(double[] result, String savePath)
            throws IOException {
        if (!savePath.endsWith(File.separator)) {
            savePath += File.separator;
        }
        BufferedWriter[] writer = new BufferedWriter[HASH_MOD];
        for (int i = 0; i < HASH_MOD; i++) {
            String fileName = "pageRank_" + i + ".rank";
            FileWriter temp = new FileWriter(new File(savePath + fileName));
            writer[i] = new BufferedWriter(temp);
        }
        for (int i = 0; i < pageList.size(); i++) {
            Page current = pageList.get(i);
            String id = current.getId();
            int index = hashId(id);
            writer[index].write(id + "\n" + result[i] + "\n");
            //writer[index].write(id + "\n" + String.format("%.10f", result[i]) + "\n");
        }
        for (int i = 0; i < HASH_MOD; i++) {
            writer[i].close();
        }
    }

    private static void processPage(String id, String thisUrl, List<String> subUrls, int length) {
        Page current = getPage(id, thisUrl);
        current.setLength(length);
        int outLink = 0;
        for (int i = 0; i < subUrls.size(); i++) {
            Page currentSub = getPage("unknown", subUrls.get(i));
            if (currentSub == null) {
                continue;
            }
            currentSub.addParentPage(current.getIndex());
            outLink++;
        }
        current.setOutLink(outLink);
    }

    private static Page getPage(String id, String url) {
        boolean needFix = true;
        if (id.equals("unknown")) {
            needFix = false;
            id = getFromUrlToId(url);
            if (id == null) {
                return null;
            }
        }
//        int first = id.indexOf('_', 0);
//        int jobID = Integer.parseInt(id.substring(0, first));
//        int second = id.indexOf('_', first + 1);
//        String dirName = id.substring(0, second);
//        String filePath = dirPath + "result_" + jobID + File.separator + dirName +
//                File.separator + id + ".page";
//        File file = new File(filePath);
//        if (!file.exists()) {
//            return null;
//        }
        int pos = hashId(id);
        HashMap<String, Page> current = idToPage.get(pos);
        if (current == null) {
            current = new HashMap<String, Page>();
            idToPage.put(pos, current);
        }
        Page page = current.get(id);
        if (page == null) {
            page = new Page(id, pageList.size());
            if (set.contains(id)) {
                System.out.println("duplicate" + id);
            }
            else {
                set.add(id);
            }
            pageList.add(page);
            current.put(id, page);
            if (needFix) {
                PutToUrlToId(id, url);
            }
        }
        return page;
    }

    private static String getFromUrlToId(String url) {
        int pos = hashUrl(url);
        if (!urlToId.containsKey(pos)) {
            return null;
        }
        HashMap<String, String> current = urlToId.get(pos);
        return current.get(url);
    }

    private static void PutToUrlToId(String id, String url) {
        int index = hashUrl(url);
        HashMap<String, String> current = null;
        if (urlToId.containsKey(index)) {
            current = urlToId.get(index);
        }
        else {
            current = new HashMap<String, String>();
            urlToId.put(index, current);
        }
        current.put(url, id);
    }

    private static int hashId(String id) {
        int first = id.indexOf('_', 0);
        int second = id.indexOf('_', first + 1);
        int threadId = Integer.parseInt(id.substring(first + 1, second));
        return threadId % HASH_MOD;
    }

    private static int hashUrl(String url) {
        return url.hashCode() % HASH_MOD;
    }

    private static double[] calculateBase(double f) {
        int n = pageList.size();
        double[] base = new double[n];
        double sum = 0;
        for (int i = 0; i < n; i++) {
            Page current = pageList.get(i);
            int length = current.getLength();
            if (length > 0) {
                base[i] = Math.log(length) / Math.log(2);
                sum += base[i];
            }
        }
        for (int i = 0; i < n; i++) {
            base[i] = base[i] / sum * (1 - f);
        }
        return base;
    }

    private static void calculateParentWeight(double f) {
        int n = pageList.size();
        for (int i = 0; i < n; i++) {
            Page current = pageList.get(i);
            List<Integer> parentPage = current.getParentPage();
            for (Integer index: parentPage) {
                int parentOutLink = pageList.get(index).getOutLink();
                current.addParentWeight(f / parentOutLink);
            }
        }
    }

    private static boolean noDiff(double[] one, double[] two) {
        final double diff = 10E-10;
        for (int i = 0; i < one.length; i++) {
            if (Math.abs(one[i] - two[i]) > diff) {
                return false;
            }
        }
        return true;
    }

    private static class Page {
        private String id;
        private int index;
        private int length;
        private int outLink;
        private List<Integer> parentPage;
        private List<Double> parentWeight;

        public Page(String id, int index) {
            this.id = id;
            this.index = index;
            length = 0;
            outLink = 0;
            parentPage = new ArrayList<Integer>();
            parentWeight = new ArrayList<Double>();
        }

        public List<Integer> getParentPage() {
            return parentPage;
        }

        public void addParentPage(int pageIndex) {
            parentPage.add(pageIndex);
        }

        public int getLength() {
            return length;
        }

        public void setLength(int length) {
            this.length = length;
        }

        public String getId() {
            return id;
        }

        public int getIndex() {
            return index;
        }

        public List<Double> getParentWeight() {
            return parentWeight;
        }

        public void addParentWeight(double weight) {
            parentWeight.add(weight);
        }

        public int getOutLink() {
            return outLink;
        }

        public void setOutLink(int outLink) {
            this.outLink = outLink;
        }
    }

    private static void checkArgs(String[] args) {
        if (args.length != 8) {
            System.out.println(USAGE);
            System.exit(1);
        }
        if (args[0].equals("-mapPath")) {
            String mapPath = args[1];
            checkPath(mapPath);
        }
        else {
            System.out.println(USAGE);
            System.exit(1);
        }
        if (args[2].equals("-filePath")) {
            String filePath = args[3];
            checkPath(filePath);
        }
        else {
            System.out.println(USAGE);
            System.exit(1);
        }
        if (args[4].equals("-savePath")) {
            String savePath = args[5];
            checkPath(savePath);
        }
        else {
            System.out.println(USAGE);
            System.exit(1);
        }
        if (args[6].equals("-f")) {
            double f = 0;
            try {
                f = Double.parseDouble(args[7]);
            } catch (Exception e) {
                System.out.println("Please provide a numeric value for f");
                System.exit(1);
            }
            if (f < 0 || f > 1) {
                System.out.println("The value of f should be between 0 and 1");
                System.exit(1);
            }
        }
        else {
            System.out.println(USAGE);
            System.exit(1);
        }
    }

    private static void checkPath(String docsPath) {
        if (docsPath == null) {
            System.out.println(USAGE);
            System.exit(1);
        }
        Path docDir = Paths.get(docsPath);
        if (!Files.isReadable(docDir)) {
            System.out.println("Directory '" + docDir.toAbsolutePath() + "' does not "
                    + "exist or is not readable, please check the path");
            System.exit(1);
        }
        if (!Files.isDirectory(docDir)) {
            System.out.println("Please provide the path of a directory");
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        checkArgs(args);
        run(args[1], args[3], args[5], Double.parseDouble(args[7]));
    }
}
