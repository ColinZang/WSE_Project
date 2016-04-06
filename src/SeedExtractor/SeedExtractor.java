package SeedExtractor;

/**
 * Created by ChenChen on 4/5/16.
 */

import java.io.*;
import java.net.MalformedURLException;
import java.util.*;



public class SeedExtractor {
    private String filePath;
    private String phpPath;
    private String resultPath;

    private int MaxPages = 20;
    private boolean DEBUG = false;

    // Google API limitation: each time call php, can only get 4 resutlts
    private static final int APILimit = 4;

    public SeedExtractor() {
        MaxPages = 20;
        DEBUG = false;
        filePath = "/Users/ChenChen/Documents/IntelliJ_Workspace/WSE_Project/data/SeedExtracter/quertlist";
        phpPath = "/Users/ChenChen/Documents/IntelliJ_Workspace/WSE_Project/src/SeedExtractor/GoogleCrawler.php";
        resultPath = "/Users/ChenChen/Documents/IntelliJ_Workspace/WSE_Project/result/SeedExtracter/";
    }

    private void Process() {
        int lineCount = 0;
        String curLine = "";    // current query
        List<MyPage> results = new ArrayList<MyPage>();     //final results

        try {
            InputStreamReader read = new InputStreamReader(new FileInputStream(new File(filePath)), "UTF-8");
            BufferedReader bufferedReader = new BufferedReader(read);

            while((curLine = bufferedReader.readLine()) != null){
                lineCount++;
                if (curLine.equals("") || curLine.equals("\n")) {
                    continue;
                }

                System.out.println("Line " + lineCount + ": " + curLine);

                for (int startIndex = 0; startIndex < MaxPages; startIndex+=APILimit) {
                    String phpResult = ExecPHP(curLine, startIndex);
                    List<MyPage> oneCallResult = ExtractPHPResults(phpResult);

                    // is PHP result has something wrong (PHP call fail),
                    // we need roll back to do it again with same start index
                    // there are a lot of reasons to make PHP call fail
                    // common one is Google identify you are crawling him and temparaly black you
                    if (oneCallResult.isEmpty()) {
                        startIndex -= APILimit;
                        continue;
                    }

                    if (DEBUG) {
                        for (int i = 0; i < oneCallResult.size(); i++) {
                            int num = startIndex + i + 1;
                            System.out.println("Page " + num + ":");
                            System.out.println("Title: " + oneCallResult.get(i).getTitle());
                            System.out.println("URL: " + oneCallResult.get(i).getUrl());
                        }
                    }

                    results.addAll(oneCallResult);

                    // in order to respect Google, thread will sleep 1s after each call php
                    // to be hones, even I don't respect Google, I also need to sleep for a while
                    // otherwise, Google will block you with high posibility
                    // last round, we don't have to sleep at here
                    if (startIndex + APILimit < MaxPages) {
                        Thread.currentThread().sleep(3000);
                    }
                }
                // after cralwing each query, we sleep a longer while
                Thread.currentThread().sleep(5000);
                System.out.println("done");
            }

            StoreResults(results);

        } catch (MalformedURLException e) {
            System.out.println("Cannot generate URL from URLstr");
        } catch (FileNotFoundException e) {
            System.out.println("Didn't fide the data file!");
        } catch (IOException e) {
            System.out.println("Cannot read data!");
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (DEBUG) {
            System.out.println("Total Line: " + lineCount);
        }
    }

    /*
     * call PHP and return the result after runing PHP script
     */
    private String ExecPHP(String curline, int start) {
        String query = BuildQuery(curline);

        if (DEBUG) {
            System.out.println("After encoding query: " + query);
        }

        BufferedReader input = null;
        String content = "";
        try {
            String newLine;
            String commenline = "php " +phpPath + " " + query + " " + start;
            Process p = Runtime.getRuntime().exec(commenline);
            input = new BufferedReader(new InputStreamReader(p.getInputStream()));

            while ((newLine = input.readLine()) != null) {
                content += newLine + "\n";
                p.destroy();
            }

            if(newLine == null){
                p.destroy();
            }
            input.close();
        } catch (MalformedURLException e) {
            System.out.println("Cannot generate URL from local PHP");
        } catch (IOException e) {
            // somethimes, newLine = input.readLine() will throw IOException after finish working
            // even it will not affect our process
            // but inorder to make result clear, don't do anything here
            // e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            return content;
        }
    }

    /*
     * because query can contain several tokens seperated by space
     * we need encode space manually
     * e.g. query = "machine learning"
     * in url, it should be: machine%20learning
     */
    private String BuildQuery(String line) {
        String delims = "[ ]+";
        String[] tokens = line.split(delims);
        String query = "";
        for (int i = 0; i < tokens.length; i++) {
            if (i == tokens.length - 1) {
                query += tokens[i];
            } else {
                query += tokens[i] + "%20";
            }
        }
        return query;
    }

    /*
     * each page information contains 3 lines:
     * Result #
     * URL: ...
     * Title: ...
     */
    private List<MyPage> ExtractPHPResults(String PHPresult) {
        List<MyPage> res = new ArrayList<MyPage>();
        Set<String> uniqueURL = new HashSet<String>();
        String[] lines = PHPresult.split("\n");

        // deal with PHP call fail case
        if (lines.length != 12) {
            System.out.println("PHP result is wrong, cannot use!");

            try {
                Thread.currentThread().sleep(5000);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                return res;
            }
        }

        for (int i = 0; i < lines.length; i++) {
            if (lines[i] == null && lines[i].equals("")) {
                continue;
            }
            if (lines[i].length() >= 8) {
                String head = lines[i].substring(0, 6);
                if (head.equals("Result")) {
                    String urlLine = "";
                    String titleLine = "";
                    if (i + 1 < lines.length) {
                        urlLine = lines[i + 1];
                    } else {
                        System.out.print(lines);
                    }
                    if (i + 2 < lines.length) {
                        titleLine = lines[i + 2];
                    } else {
                        System.out.print(lines);
                    }

                    if (urlLine.length() < 4 || !urlLine.substring(0, 4).equals("URL:")) {
                        System.out.println("URL line has something wrong");
                        continue;
                    }
                    if (titleLine.length() < 6 || !titleLine.substring(0, 6).equals("Title:")) {
                        System.out.println("Title line has something wrong");
                        continue;
                    }

                    // create new page
                    // the only reason I design page like this is for debugging
                    MyPage page = new MyPage();
                    page.setUrl(urlLine.substring(5));
                    page.setTitle(titleLine.substring(7));

                    // in order to filter duplicate URL (even this case is rare)
                    if (!uniqueURL.contains(page.getUrl())) {
                        res.add(page);
                    }
                    i += 2;
                } else {
                    continue;
                }
            }
        }
        return res;
    }

    private void StoreResults(List<MyPage> results) {
        String content = "";
        for (MyPage page : results) {
            content += page.getUrl() + "\n";
        }

        // sometimes, the folder path people give doesn't include '/' at the end
        // e.g. "/Users/ChenChen" not "/Users/ChenChen/"
        if (resultPath.charAt(resultPath.length() - 1) != '/') {
            resultPath += "/";
        }

        try {
            File file = new File(resultPath + "seedResult.txt");

            if (!file.exists()) {
                file.createNewFile();
            }

            FileWriter fw = new FileWriter(file.getAbsoluteFile());
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(content);
            bw.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
     * args can at least has 3 parameter, as most has 5 parameter
     * [file path], [php script path], [result folder path], [MaxPages#], [DEBUG]
     *
     * default value:
     * MaxPages = 20, DEBUG = false
     * -f means: DEBUG = true, otherwise(include empty) DEBUG = false
     */
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("You should give paths for your query list and php script");
            System.exit(1);
        }

        SeedExtractor se = new SeedExtractor();
        se.filePath = args[0];
        se.phpPath = args[1];
        se.resultPath = args[2];

        if (args.length > 3) {
            se.MaxPages = Integer.parseInt(args[3]);
        }

        if (args.length > 4) {
            String flag = args[4];
            if (flag.equals("-f")) {
                se.DEBUG = true;
            }
        }

        se.Process();
        System.out.println("Finish!");
    }
}
