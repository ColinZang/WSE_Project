package WebCrawler;

/**
 * Created by Min on 4/4/16.
 */

import java.util.*;
import java.net.*;
import java.io.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;

public class Crawler {
    private static ConcurrentLinkedQueue<URL> urlQueue =
            new ConcurrentLinkedQueue<URL>();
    private static ConcurrentHashMap<Integer, HashSet<URL>> internalHashMap =
            new ConcurrentHashMap<Integer, HashSet<URL>>();
    private static int count = 0;
    private static final Object COUNT_LOCK = new Object();

    public static void main(String[] args) {
        String usage = "usage: java Crawler [-root rootURLFile] [-max maxPage]";
        final int ARG_COUNT = 4;
        if (args.length != ARG_COUNT) {
            System.out.println(usage);
            System.exit(1);
        }
        Scanner readFile = null;
        int maxPage = 0;
        int index = 0;
        while (index < args.length) {
            if (args[index].equals("-root")) {
                try {
                    readFile = new Scanner(new FileReader(args[index + 1]));
                    index += 2;
                } catch (FileNotFoundException e) {
                    System.out.println("Please provide a valid file name");
                    System.exit(1);
                }
            }
            else if (args[index].equals("-max")) {
                try {
                    maxPage = Integer.parseInt(args[index + 1]);
                    index += 2;
                } catch (NumberFormatException e) {
                    System.out.println("Please provide an integer value for maxPage");
                    System.exit(1);
                }
            }
            else {
                System.out.println(usage);
                System.exit(1);
            }
        }
        initialize(readFile);
        crawl(maxPage);
    }

    /**
     * This method adds the root urls into the internal hashmap, later when crawling first begins,
     * the root urls will be added to the queue and the external hashset.
     * The input file should have each url in a new line
     */
    private static void initialize(Scanner readFile) {
        while (readFile.hasNextLine()) {
            try {
                URL url = new URL(readFile.nextLine());
                addToInternalHashMap(url);
            } catch (MalformedURLException e) {
                //ignore invalid urls
            }
        }
    }

    /**
     * This method adds the newly extracted urls (or root urls) to the internal hashmap,
     * if the url is duplicated, just ignore. It also indexes the urls by calculating
     * its hashCode
     */
    private static void addToInternalHashMap(URL url) {
        int hashValue = hash(url);
        HashSet<URL> hashSet = null;
        if (internalHashMap.containsKey(hashValue)) {
            hashSet = internalHashMap.get(hashValue);
        }
        else {
            hashSet = new HashSet<URL>();
            internalHashMap.put(hashValue, hashSet);
        }
        hashSet.add(url);
    }

    /**
     * This method calculates the hashCode of a url
     */
    private static int hash(URL url) {
        final int EXTERNAL_HASHSET_COUNT = 100;
        return url.toString().hashCode() % EXTERNAL_HASHSET_COUNT;
    }

    /**
     * This method is the crawling process
     */
    private static void crawl(int maxPage) {
        final int THREAD_COUNT = 100;
        while (count < maxPage) {
            // clear the urlQueue first, in case there're remaining urls from the last round
            urlQueue.clear();
            // add new urls from internal hashmap to queue and remove duplicates
            addToUrlQueue();
            // if the urlQueue is still empty after adding new urls, it means no new urls are found,
            // the whole crawling process has to stop
            if (urlQueue.isEmpty()) {
                break;
            }
            // must clear the internal hashmap before the next round of crawling
            internalHashMap.clear();
            Thread[] threads = new Thread[THREAD_COUNT];
            for (int i = 0; i < THREAD_COUNT; i++) {
                threads[i] = new Thread(new crawling());
                // run the thread after creation
                threads[i].start();
            }
            long startTime = System.currentTimeMillis();
            final long waitTime = 5000;
            int finished = 0;
            // keep counting how many threads are still alive, if after a certain waitTime,
            // some threads are still running, have to stop them
            while (finished < THREAD_COUNT && System.currentTimeMillis() - startTime < waitTime) {
                finished = 0;
                for (int i = 0; i < THREAD_COUNT; i++) {
                    if (!threads[i].isAlive()) {
                        finished++;
                    }
                }
            }
            // stop the remaining alive threads
            if (finished < THREAD_COUNT) {
                for (int i = 0; i < THREAD_COUNT; i++) {
                    if (threads[i].isAlive()) {
                        threads[i].interrupt();
                    }
                }
            }
        }
    }

    /**
     * This method compares the internal hashmap and external hashset, remove duplicates
     * from the internal hashmap, and add non-duplicates to both the queue and external hashset
     */
    private static void addToUrlQueue() {
        for (Map.Entry<Integer, HashSet<URL>> entry: internalHashMap.entrySet()) {
            // index corresponds to the id of the external hashset
            int index = entry.getKey();
            HashSet<URL> internalHashSet = entry.getValue();
            HashSet<URL> externalHashSet = null;
            if (/* external hashSet of this index exists */ true) {
                /*load into memory*/
            }
            else {
                externalHashSet = new HashSet<URL>();
            }
            // iterate through the internal hashset, if the url is duplicated, just ignore,
            // if the the url is new, add it to both the queue and external hashset
            for (URL url: internalHashSet) {
                if (!externalHashSet.contains(url)) {
                    externalHashSet.add(url);
                    urlQueue.add(url);
                }
            }
            /* save external hashset back */
        }
    }

    /**
     * The run() method in this class specifies what each thread is doing
     */
    private static class crawling implements Runnable {
        public void run() {
            while (!urlQueue.isEmpty()) {
                URL url = urlQueue.poll();
                // url == null indicates the urlQueue just became empty, should stop
                // not sure if it's necessary to check
                if (url == null) {
                    break;
                }
                if (!isRobotSafe(url)) {
                    continue;
                }
                String page = getPage(url);
                // page equals empty indicates either the page was not processed successfully
                // because of various reasons detailed in getPage() method
                if (page.equals("")) {
                    continue;
                }
                try {
                    saveHTML(page);
                    indexHTML(page);
                } catch (Exception e) {
                    continue;
                }
                // if the page is saved and indexes without exception, increment the count of pages
                // successfully processed so far
                incrementCount();
                List<URL> newUrls = extractUrl(page);
                for (URL newUrl: newUrls) {
                    // if newUrl is duplicated in the internal hashmap, it will be ignored
                    // by addToInternalHashMap(newUrl), when the next round of crawling first begins,
                    // the urls in internal hashmap will also be checked against external hashset
                    addToInternalHashMap(newUrl);
                }
            }
        }
    }

    /**
     * This method returns whether the page is robot safe
     */
    private static boolean isRobotSafe(URL url) {
        return true;
    }

    /**
     * This method downloads the page into a string
     * while downloading the page, it should:
     * (1) determine the type of the page, if it's of non-textual type, no need to continue
     * (2) determine if the page is in English, it not no need to continue
     * (3) filter out images etc.
     * (4) ...
     */
    private static String getPage(URL url) {
        String page = "";
        try {

        } catch (Exception e) {
            return "";
        }
        return page;
    }

    /**
     * This method should save HTML to local directory
     */
    private static void saveHTML(String page) {

    }

    /**
     * This method should connect to indexing and pageRank calculation
     */
    private static void indexHTML(String page) {

    }

    /**
     * This method should parse the HTML and extract the urls it contains,
     * and return them in an arraylist
     */
    private static List<URL> extractUrl(String page) {
        return new ArrayList<URL>();
    }

    /**
     * This method increments the count of pages successfully processed so far
     */
    public static void incrementCount() {
        synchronized (COUNT_LOCK) {
            count++;
        }
    }
}
