package Retriever;

import Parser.*;
import org.tartarus.snowball.ext.englishStemmer;

import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Comparator;
import java.util.Collections;

/**
 * Created by Wenzhao on 4/21/16.
 */
public class Retriever {
    private static final String USAGE =
            "USAGE: java Retriever [-query QUERY] [-index INDEX_PATH] [-page PAGE_PATH] " +
                    "[-total TOTAL_PAGE] [-max MAX_RESULT] [-stop STOP_PATH]";
    private static int n;
    private static int max;
    private static String indexPath;
    private static String pagePath;
    private static List<String> queryWords = new ArrayList<String>();
    private static HashMap<Integer, Double> wordWeights =
            new HashMap<Integer, Double>();
    private static List<Sequence> seqList = new ArrayList<Sequence>();
    private static HashMap<String, Page> seenPages =
            new HashMap<String, Page>();
    private static HashMap<Sequence, HashSet<Page>> pages =
            new HashMap<Sequence, HashSet<Page>>();

    private static void run(String query, String stopFile) {
        HashSet<String> stopList = loadStop(stopFile);
        parseQuery(query, stopList);
        for (Sequence seq: seqList) {
            System.out.println(seq.getLeft() + " " + seq.getRight() + " " + seq.getToken());
        }
        System.exit(1);
        getPages();
        calculate();
        returnResults();
    }

    private static void parseQuery(String query, HashSet<String> stopList) {
        Parser parser = new Parser(query, stopList);
        parser.Parse();
        List<String> tokens = parser.GetResTokens();
        List<String> types = parser.GetTokensType();
        int lastPartition = -1;
        for (int i = 0; i < tokens.size(); i++) {
            System.out.println(tokens.get(i) + " " + types.get(i));
        }
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
            System.out.println("Query contains non-English words, invalid words or are all stop words");
            System.exit(1);
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
        for (Sequence seq: seqList) {
            if (pages.containsKey(seq)) {
                continue;
            }
            if (seq.getRight() == seq.getLeft()) {
                pages.put(seq, readIndex(seq));
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
            }
        }
    }

    private static void calculate() {
        for (Map.Entry<Sequence, HashSet<Page>> entry: pages.entrySet()) {
            Sequence seq = entry.getKey();
            String token = seq.getToken();
            double weight = getWeight(seq);
            HashSet<Page> set = entry.getValue();
            for (Page page: set) {
                page.calculateScore(token, weight);
            }
        }
    }

    private static void returnResults() {
        List<Page> result = new ArrayList<Page>();
        for (Map.Entry<String, Page> entry: seenPages.entrySet()) {
            Page page = entry.getValue();
            result.add(page);
        }
        Collections.sort(result, new PageComp());
        int count = 0;
        HashSet<String> seen = new HashSet<String>();
        while (count < max && count < result.size()) {
            String url = result.get(count).getUrl();
            if (seen.contains(url)) {
                continue;
            }
            else {
                seen.add(url);
            }
            System.out.println(result.get(count));
            System.out.println(result.get(count).getScoreInfo());
            count++;
        }
    }

    private static double getWeight(Sequence seq) {
        int left = seq.getLeft();
        int right = seq.getRight();
        double maxWeight = wordWeights.get(left);
        for (int i = left + 1; i <= right; i++) {
            maxWeight = Math.max(maxWeight, wordWeights.get(i));
        }
        return maxWeight;
    }

    // by Guo Min
    private static HashSet<Page> readIndex(Sequence seq) {
        HashSet<Page> pageSet = new HashSet<Page>();
        String word = seq.getToken();
        word = StemEnglishWord(word.toLowerCase());
        int count = 0;
        try {
            if (!indexPath.endsWith(File.separator)) {
                indexPath += File.separator;
            }
            File file = new File(indexPath + word + ".word");
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line = null;
            while((line = reader.readLine()) != null) {
                String pageID = line;
                double pageRank = Double.parseDouble(reader.readLine());
                if (seenPages.containsKey(pageID)) {
                    pageSet.add(seenPages.get(pageID));
                }
                else {
                    Page page = new Page(pageID, pageRank, pagePath);
                    pageSet.add(page);
                    seenPages.put(pageID, page);
                }
                count++;
            }
            reader.close();
            int index = seq.getLeft();
            wordWeights.put(index, calculateWeight(count));
        } catch (IOException e) {
            System.out.println("Read index not successful for word " + seq.getToken());
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
            else {
                return 0;
            }
        }
    }

    private static class PageComp implements Comparator<Page> {
        public int compare(Page one, Page two) {
            double oneScore = one.finalScore();
            double twoScore = two.finalScore();
            if (oneScore > twoScore) {
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

    private static HashSet<String> loadStop(String filePath) {
        HashSet<String> stopList = new HashSet<String>();
        try {
            FileReader fileReader = new FileReader(filePath);
            BufferedReader reader = new BufferedReader(fileReader);
            String word = null;
            while ((word = reader.readLine()) != null) {
                stopList.add(word);
            }
            reader.close();
        } catch (IOException e) {
            System.out.println("Read in stop list not successful");
        }
        return stopList;
    }

    private static void checkArgs(String[] args) {
        if (args.length != 12) {
            System.out.println(USAGE);
            System.exit(1);
        }
        if (args[0].equals("-query")) {
            final int MAX_QUERY_LENGTH = 256;
            String query = args[1];
            if (query.length() > MAX_QUERY_LENGTH) {
                System.out.println("Query exceeded maximum length");
                System.exit(1);
            }
        }
        else {
            System.out.println(USAGE);
            System.exit(1);
        }
        if (args[2].equals("-index")) {
            checkPath(args[3]);
        }
        else {
            System.out.println(USAGE);
            System.exit(1);
        }
        if (args[4].equals("-page")) {
            checkPath(args[5]);
        }
        else {
            System.out.println(USAGE);
            System.exit(1);
        }
        if (args[6].equals("-total")) {
            int number = 0;
            try {
                number = Integer.parseInt(args[7]);
            } catch (RuntimeException e) {
                System.out.println("Please provide a positive integer for total");
                System.exit(1);
            }
            if (number <= 0) {
                System.out.println("Please provide a positive integer for total");
                System.exit(1);
            }
        }
        else {
            System.out.println(USAGE);
            System.exit(1);
        }
        if (args[8].equals("-max")) {
            int number = 0;
            try {
                number = Integer.parseInt(args[9]);
            } catch (RuntimeException e) {
                System.out.println("Please provide a positive integer for max");
                System.exit(1);
            }
            if (number <= 0) {
                System.out.println("Please provide a positive integer for max");
                System.exit(1);
            }
        }
        else {
            System.out.println(USAGE);
            System.exit(1);
        }
        if (!args[10].equals("-stop")) {
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
        String query = args[1];
        indexPath = args[3];
        pagePath = args[5];
        n = Integer.parseInt(args[7]);
        max = Integer.parseInt(args[9]);
        String stopFile = args[11];
        run(query, stopFile);
    }
}
