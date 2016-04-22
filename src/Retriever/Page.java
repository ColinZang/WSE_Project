package Retriever;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Created by Wenzhao on 4/22/16.
 */
public class Page {
    private String id;
    private double pageRank;
    private String content;
    private String lowerContent;
    private String url;
    private String title;
    private double dependencyScore;
    private int length;
    private String preview = " ";
    private int previewTokenSize = 0;

    public Page(String id, double pageRank, String pagePath) {
        this.id = id;
        this.pageRank = pageRank;
        parsePage(pagePath);
    }

    private void parsePage(String pagePath) {
        int first = id.indexOf('_', 0);
        int second = id.indexOf('_', first + 1);
        String firstDir = "result_" + id.substring(0, first);
        String secondDir = id.substring(0, second);
        String fileName = id + ".page";
        if (!pagePath.endsWith(File.separator)) {
            pagePath += File.separator;
        }
        String wholePath = pagePath + firstDir + File.separator + secondDir
                + File.separator + fileName;
        try {
            BufferedReader reader = new BufferedReader(new FileReader(wholePath));
            String line = null;
            while((line = reader.readLine()) != null) {
                if (line.equals("#ThisURL#")) {
                    url = reader.readLine();
                }
                else if (line.equals("#Length#")) {
                    length = Integer.parseInt(reader.readLine());
                }
                else if (line.equals("Title")) {
                    title = reader.readLine();
                }
                else if (line.equals("#Content#")) {
                    content = reader.readLine();
                    lowerContent = content.toLowerCase();
                    break;
                }
            }
            reader.close();
        } catch (IOException e) {
            System.out.println("Parse page " + id + " not successful");
        }
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return title + "\n" + url + "\n" + preview + "\n";
    }

    public String getID() {
        return id;
    }

    public void calculateScore(String token, double wordWeight) {
        int lowerCount = getCount(token.toLowerCase(), lowerContent);
        if (lowerCount == 0) {
            return;
        }
        int originalCount = getCount(token, content);
        lowerCount = lowerCount - originalCount;
        double originalScore = formula(wordWeight, originalCount);
        double lowerScore = formula(wordWeight, lowerCount);
        final double weight = 1.5;
        double addedScore = weight * originalScore + lowerScore;
        dependencyScore += addedScore;
    }

    private int getCount(String token, String content) {
        int index = 0;
        int count = 0;
        String[] parts = token.split("\\s+");
        int size = parts.length;
        boolean hasPreview = false;
        while ((index = content.indexOf(token, index)) != -1) {
            if (size >= previewTokenSize && !hasPreview) {
                preview = content.substring(index, Math.min(content.length(), index + 100));
                hasPreview = true;
                previewTokenSize = size;
            }
            count++;
            index += token.length();
        }
        return count;
    }

    private double formula(double wordWeight, int count) {
        if (count == 0) {
            return 0;
        }
        double score = 1 + Math.log(count) / Math.log(2);
        return score * wordWeight;
    }

    public double finalScore() {
        final double weight = 1.5E7;
        return dependencyScore + weight * pageRank;
    }
}