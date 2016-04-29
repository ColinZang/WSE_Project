package Retriever;

import Parser.*;
import org.tartarus.snowball.ext.englishStemmer;

import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Comparator;
import java.util.Collections;
import java.util.PriorityQueue;
import java.net.URI;

/**
 * Created by Wenzhao on 4/21/16.
 */
public class Retriever {
    private static final String USAGE =
            "USAGE: java Retriever [-query QUERY] [-index INDEX_PATH] [-page PAGE_PATH] " +
                    "[-total TOTAL_PAGE] [-max MAX_RESULT] [-stop STOP_PATH]";
    private static int n;
    private static int max;
    private static String indexPath = null;
    private static String pagePath;
    private static List<String> queryWords = new ArrayList<String>();
    private static HashMap<Integer, Double> wordWeights =
            new HashMap<Integer, Double>();
    private static List<Sequence> seqList = new ArrayList<Sequence>();
    private static HashMap<Sequence, Double> seqWeight =
            new HashMap<Sequence, Double>();
    private static HashMap<String, Page> seenPages =
            new HashMap<String, Page>();
    private static HashMap<Sequence, HashSet<Page>> pages =
            new HashMap<Sequence, HashSet<Page>>();
    private static PriorityQueue<Page> results =
            new PriorityQueue<Page>(new PageComp());
    private static HashSet<String> stopList =
            new HashSet<String>();
    private static String warning = null;

    public static List<Page> run(String query) {
        if (indexPath == null) {
            overallInitialize();
        }
        initialize();
        System.out.println("query is " + query);
        final int MAX_QUERY_LENGTH = 256;
        if (query.length() > MAX_QUERY_LENGTH) {
            warning = "Query exceeds " + MAX_QUERY_LENGTH + " characters long, " +
                    "please try something shorter";
            System.out.println(warning);
            return new ArrayList<Page>();
        }
        parseQuery(query);
        if (warning != null) {
            return new ArrayList<Page>();
        }
        getPages();
        calculate();
        return returnResults();
    }

    public static String getWarning() {
        return warning;
    }

    private static void overallInitialize() {
        n = 960000;
        max = 50;
        indexPath = "../results/indexWithRank/";
        pagePath = "../results/pages/";
        String stopFile = "../data/ShotStopList.txt";
        loadStop(stopFile);
    }

    private static void initialize() {
        queryWords = new ArrayList<String>();
        wordWeights = new HashMap<Integer, Double>();
        seqList = new ArrayList<Sequence>();
        seqWeight = new HashMap<Sequence, Double>();
        seenPages = new HashMap<String, Page>();
        pages = new HashMap<Sequence, HashSet<Page>>();
        results = new PriorityQueue<Page>(new PageComp());
        warning = null;
        System.out.println("Initialize successful");
    }

    private static void runMain(String query, String stopFile) {
        final int MAX_QUERY_LENGTH = 256;
        if (query.length() > MAX_QUERY_LENGTH) {
            System.out.println("Query exceeds " + MAX_QUERY_LENGTH + " characters long, " +
                    "please try something shorter");
            System.exit(1);
        }
        loadStop(stopFile);
        parseQuery(query);
        if (warning != null) {
            System.exit(1);
        }
        getPages();
        calculate();
        returnResults();
    }

    private static void parseQuery(String query) {
        Parser parser = new Parser(query, stopList);
        parser.Parse();
        List<String> tokens = parser.GetResTokens();
        List<String> types = parser.GetTokensType();
        int lastPartition = -1;
        for (int i = 0; i < tokens.size(); i++) {
            String type = types.get(i);
            if (type.equals("WORD")) {
                queryWords.add(tokens.get(i));
            }
            else if (type.equals("EMAIL")) {
                List<Sequence> temp = genSeq(lastPartition + 1, queryWords.size() - 1);
                for (Sequence seq: temp) {
                    seqList.add(seq);
                }
                queryWords.add(tokens.get(i));
                lastPartition = queryWords.size() - 1;
            }
            else {
                List<Sequence> temp = genSeq(lastPartition + 1, queryWords.size() - 1);
                for (Sequence seq: temp) {
                    seqList.add(seq);
                }
                lastPartition = queryWords.size() - 1;
            }
        }
        List<Sequence> temp = genSeq(lastPartition + 1, queryWords.size() - 1);
        for (Sequence seq: temp) {
            seqList.add(seq);
        }
        if (queryWords.size() == 0) {
            warning = "Query may be too general or contains unsupported characters, " +
                    "please try something else";
            System.out.println(warning);
            return;
//            System.exit(1);
        }
        Collections.sort(seqList, new SeqComp());
    }

    private static List<Sequence> genSeq(int left, int right) {
        List<Sequence> result = new ArrayList<Sequence>();
        for (int length = 1; length <= right - left + 1; length++) {
            for (int i = left; i <= right; i++) {
                int j = i + length - 1;
                if (j > right) {
                    break;
                }
                result.add(new Sequence(queryWords, i, j));
            }
        }
        return result;
    }

    private static void getPages() {
        for (int i = 0; i < seqList.size(); i++) {
            Sequence seq = seqList.get(i);
            if (pages.containsKey(seq)) {
                continue;
            }
            if (seq.getRight() == seq.getLeft()) {
                HashSet<Page> result = readIndex(seq);
                pages.put(seq, result);
//                System.out.println("Read index for word " + seq.getToken() + " finished, with "
//                        + result.size() + " pages");
            }
            else {
                Sequence partOne = new Sequence(queryWords, seq.getLeft(), seq.getRight() - 1);
                Sequence partTwo = new Sequence(queryWords, seq.getRight(), seq.getRight());
                HashSet<Page> shorter = pages.get(partOne);
                HashSet<Page> longer = pages.get(partTwo);
                if (shorter.size() > longer.size()) {
                    HashSet<Page> temp = shorter;
                    shorter = longer;
                    longer = temp;
                }
                HashSet<Page> result = new HashSet<Page>();
                for (Page page: shorter) {
                    // use default equals(), which checks the reference
                    if (longer.contains(page)) {
                        result.add(page);
                    }
                }
                pages.put(seq, result);
//                System.out.println("Read index for word " + seq.getToken() + " finished, with "
//                        + result.size() + " pages");
            }
        }
    }

    private static void calculate() {
//        System.out.println("Calculating...");
        long startTime = System.currentTimeMillis();
        HashSet<Sequence> seenSeqs = new HashSet<Sequence>();
        HashSet<URI> seenUrls = new HashSet<URI>();
        HashSet<String> seenTitles = new HashSet<String>();
        final int customMax = 10000;
        int i = seqList.size() - 1;
        // group the same length seq together
        while (i >= 0) {
//            System.out.println(i + " round");
            int currentLength = seqList.get(i).getRight() - seqList.get(i).getLeft();
            List<Page> current = new ArrayList<Page>();
            while (i >= 0 &&
                    seqList.get(i).getRight() - seqList.get(i).getLeft() == currentLength) {
                Sequence seq = seqList.get(i);
                i--;
                if (seenSeqs.contains(seq)) {
                    continue;
                } else {
                    seenSeqs.add(seq);
                }
                HashSet<Page> set = pages.get(seq);
                for (Page page : set) {
                    if (page.getMatch() != -1) {
                        continue;
                    }
                    if (page.isSeen() && !page.isValid()) {
                        continue;
                    }
                    if (page.isSeqEmpty()) {
                        current.add(page);
                    }
                    page.addSeq(seq);
                }
            }
            if (current.size() == 0) {
                continue;
            }
            Collections.sort(current, new PageRankComp());
            for (Page page : current) {
                if (!page.isSeen()) {
                    page.parsePage();
                    if (!page.isValid()) {
                        continue;
                    }
                    URI url = null;
                    try {
                        url = new URI(page.getUrl());
                    } catch (URISyntaxException e) {
//                        System.out.println("not a url: " + page.getUrl());
                        page.setValid(false);
                        continue;
                    }
                    if (seenUrls.contains(url)) {
                        page.setValid(false);
                        continue;
                    }
                    else {
                        seenUrls.add(url);
                    }
                    if (seenTitles.contains(page.getTitle())) {
                        page.setValid(false);
                        continue;
                    }
                    else {
                        seenTitles.add(page.getTitle());
                    }
                }
                page.calculateScore();
//                for (int index = i; index >= 0; index--) {
//                    Sequence currentSeq = seqList.get(index);
//                    String token = currentSeq.getToken();
//                    double weight = getWeight(currentSeq);
//                    page.calculateScore(token, weight);
//                }
                if (page.getMatch() != -1) {
                    results.add(page);
                    //System.out.println("Calculate score finished for page " + page.getID());
                }
                if (results.size() >= customMax) {
                    return;
                }
                if (System.currentTimeMillis() - startTime > 3000) {
                    return;
                }
            }
        }
    }

    private static List<Page> returnResults() {
        List<Page> finalResults = new ArrayList<Page>();
        if (results.size() == 0) {
            warning = "No relevant results are available, sorry, " +
                    "please try something else";
            System.out.println(warning);
            return finalResults;
        }
        int count = 0;
//        Collections.sort(results, new PageComp());
//        for (Page page: results) {
//            System.out.println(page);
////            System.out.println(results.poll().getScoreInfo());
//            count++;
//            if (count >= max) {
//                return;
//            }
//        }
        while (!results.isEmpty()) {
            Page page = results.poll();
            System.out.println(page);
//            System.out.println(results.poll().getScoreInfo());
            count++;
            finalResults.add(page);
            if (count >= max) {
                break;
            }
        }
        return finalResults;
    }

    public static double getWeight(Sequence seq) {
        if (seqWeight.containsKey(seq)) {
            return seqWeight.get(seq);
        }
        int left = seq.getLeft();
        int right = seq.getRight();
        double maxWeight = wordWeights.get(left);
        for (int i = left + 1; i <= right; i++) {
            maxWeight = Math.max(maxWeight, wordWeights.get(i));
        }
        seqWeight.put(seq, maxWeight);
        return maxWeight;
    }

    // by Guo Min
    private static HashSet<Page> readIndex(Sequence seq) {
        HashSet<Page> pageSet = new HashSet<Page>();
        String word = seq.getToken();
        word = StemEnglishWord(word.toLowerCase());
//        System.out.println("stemmed is " + word);
        int count = 0;
        try {
            final int MODULE = 500;
            int wordHash = Math.abs(word.hashCode()) % MODULE;
//            System.out.println("folder name is " + wordHash);
            File file = new File(indexPath + wordHash + File.separator + word + ".word");
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line = null;
            while((line = reader.readLine()) != null) {
                String pageID = line;
                double pageRank = Double.parseDouble(reader.readLine());
                if (seenPages.containsKey(pageID)) {
                    pageSet.add(seenPages.get(pageID));
                    //System.out.println(pageID + " has been seen");
                }
                else {
                    Page page = new Page(pageID, pageRank, pagePath);
                    pageSet.add(page);
                    seenPages.put(pageID, page);
                    //System.out.println(pageID + " has been added");
                }
                count++;
            }
            reader.close();
            int index = seq.getLeft();
            wordWeights.put(index, calculateWeight(count));
        } catch (IOException e) {
//            System.out.println("Read index not successful for word " + seq.getToken());
        }
        return pageSet;
    }

    private static class SeqComp implements Comparator<Sequence> {
        public int compare(Sequence one, Sequence two) {
            int oneLength = one.getRight() - one.getLeft();
            int twoLength = two.getRight() - two.getLeft();
            int diff = oneLength - twoLength;
            if (diff < 0) {
                return -1;
            }
            else if (diff > 0) {
                return 1;
            }
            else if (one.getLeft() > two.getLeft()) {
                return -1;
            }
            else if (one.getLeft() < two.getLeft()) {
                return 1;
            }
            else {
                return 0;
            }
        }
    }

    private static class PageComp implements Comparator<Page> {
        public int compare(Page one, Page two) {
            double oneScore = one.finalScore();
            double twoScore = two.finalScore();
            if (one.getMatch() > two.getMatch()) {
                return -1;
            }
            else if (one.getMatch() < two.getMatch()) {
                return 1;
            }
            else if (one.titleContains() && !two.titleContains()) {
                return -1;
            }
            else if (!one.titleContains() && two.titleContains()) {
                return 1;
            }
            else if (oneScore > twoScore) {
                return -1;
            }
            else if (oneScore < twoScore) {
                return 1;
            }
            else {
                return 0;
            }
        }
    }

    private static class PageRankComp implements Comparator<Page> {
        public int compare(Page one, Page two) {
            double oneRank = one.getPageRank();
            double twoRank = two.getPageRank();
            if (oneRank > twoRank) {
                return -1;
            }
            else if (oneRank < twoRank) {
                return 1;
            }
            else {
                return 0;
            }
        }
    }

    public static double calculateWeight(int count) {
        return 1 + Math.log((double)n / count) / Math.log(2);
    }

    // by Chen Chen
    private static String StemEnglishWord(String token) {
        englishStemmer stemmer = new englishStemmer();
        stemmer.setCurrent(token);
        if (stemmer.stem()) {
            return stemmer.getCurrent();
        }
        return token;
    }

    private static void loadStop(String filePath) {
        try {
            FileReader fileReader = new FileReader(filePath);
            BufferedReader reader = new BufferedReader(fileReader);
            String word = null;
            while ((word = reader.readLine()) != null) {
                stopList.add(word);
            }
            reader.close();
            System.out.println("Read in stop list successful");
        } catch (IOException e) {
//            System.out.println("Read in stop list not successful");
        }
    }

//    private static void checkArgs(String[] args) {
//        if (args.length != 12) {
////            System.out.println(USAGE);
//            System.exit(1);
//        }
//        if (args[0].equals("-query")) {
//            final int MAX_QUERY_LENGTH = 256;
//            String query = args[1];
//            if (query.length() > MAX_QUERY_LENGTH) {
//                System.out.println("Query exceeded maximum length, please try something else");
//                System.exit(1);
//            }
//        }
//        else {
////            System.out.println(USAGE);
//            System.exit(1);
//        }
//        if (args[2].equals("-index")) {
//            checkPath(args[3]);
//        }
//        else {
////            System.out.println(USAGE);
//            System.exit(1);
//        }
//        if (args[4].equals("-page")) {
//            checkPath(args[5]);
//        }
//        else {
////            System.out.println(USAGE);
//            System.exit(1);
//        }
//        if (args[6].equals("-total")) {
//            int number = 0;
//            try {
//                number = Integer.parseInt(args[7]);
//            } catch (RuntimeException e) {
////                System.out.println("Please provide a positive integer for total");
//                System.exit(1);
//            }
//            if (number <= 0) {
////                System.out.println("Please provide a positive integer for total");
//                System.exit(1);
//            }
//        }
//        else {
////            System.out.println(USAGE);
//            System.exit(1);
//        }
//        if (args[8].equals("-max")) {
//            int number = 0;
//            try {
//                number = Integer.parseInt(args[9]);
//            } catch (RuntimeException e) {
////                System.out.println("Please provide a positive integer for max");
//                System.exit(1);
//            }
//            if (number <= 0) {
////                System.out.println("Please provide a positive integer for max");
//                System.exit(1);
//            }
//        }
//        else {
////            System.out.println(USAGE);
//            System.exit(1);
//        }
//        if (!args[10].equals("-stop")) {
////            System.out.println(USAGE);
//            System.exit(1);
//        }
//    }
//
//    private static void checkPath(String docsPath) {
//        if (docsPath == null) {
////            System.out.println(USAGE);
//            System.exit(1);
//        }
//        Path docDir = Paths.get(docsPath);
//        if (!Files.isReadable(docDir)) {
////            System.out.println("Directory '" + docDir.toAbsolutePath() + "' does not "
////                    + "exist or is not readable, please check the path");
//            System.exit(1);
//        }
//        if (!Files.isDirectory(docDir)) {
////            System.out.println("Please provide the path of a directory");
//            System.exit(1);
//        }
//    }

    public static void main(String[] args) {
//        checkArgs(args);
        String query = args[1];
        indexPath = args[3];
        if (!indexPath.endsWith(File.separator)) {
            indexPath += File.separator;
        }
        pagePath = args[5];
        if (!pagePath.endsWith(File.separator)) {
            pagePath += File.separator;
        }
        n = Integer.parseInt(args[7]);
        max = Integer.parseInt(args[9]);
        String stopFile = args[11];
        runMain(query, stopFile);
    }
}
