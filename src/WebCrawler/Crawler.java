package WebCrawler;

import java.util.*;
import java.net.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;

public class Crawler {
    private static ConcurrentLinkedQueue<URL> urlQueue =
            new ConcurrentLinkedQueue<URL>();
    private static ConcurrentHashMap<Integer, HashSet<URL>> internalHashMap =
            new ConcurrentHashMap<Integer, HashSet<URL>>();
    private static int searchLimit = 0;
    private static int pageCount = 0;
    private static final Object PAGE_COUNT_LOCK = new Object();
    private static final Object INTERNAL_HASHMAP_LOCK = new Object();

    /**
     * This method takes the input file Scanner and
     * the path to save the results
     */
    private static void run(Scanner readFile, String savePath) {
        initialize(readFile);
        crawl(savePath);
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
        synchronized (INTERNAL_HASHMAP_LOCK) {
            if (internalHashMap.containsKey(hashValue)) {
                hashSet = internalHashMap.get(hashValue);
            }
            else {
                hashSet = new HashSet<URL>();
                internalHashMap.put(hashValue, hashSet);
            }
            hashSet.add(url);
        }
    }

    /**
     * This method calculates the hashCode of a url
     */
    private static int hash(URL url) {
        final int EXTERNAL_HASHSET_COUNT = 100;
        return Math.abs(url.toString().hashCode()) % EXTERNAL_HASHSET_COUNT;
    }

    /**
     * This method is the crawling process
     */
    private static void crawl(String savePath) {
        setProxy();
        int totalThreadCount = 0;
        while (pageCount < searchLimit) {
            if (urlQueue.isEmpty()) {
                synchronized (INTERNAL_HASHMAP_LOCK) {
                    // add new urls from internal hashmap to queue and ignore duplicates
                    addToUrlQueue(savePath);
                    // must clear the internal hashmap before this round of crawling
                    internalHashMap.clear();
                }
                // if the urlQueue is still empty after adding new urls, it means no new urls are found,
                // the whole crawling process has to stop
                // how???
            }
            // the ratio between new url count and new thread count
            final double THREAD_RATIO = 1;
            final int MAX_THREAD_LIMIT = 1000;
            int newThreadCount = (int)(urlQueue.size() * THREAD_RATIO);
            int activeThreadCount = Crawling.getActiveThreadCount();
            newThreadCount = Math.min(newThreadCount, MAX_THREAD_LIMIT - activeThreadCount);
            if (newThreadCount > 0) {
                Thread[] threads = new Thread[newThreadCount];
                for (int i = 0; i < newThreadCount; i++) {
                    threads[i] = new Thread(new Crawling(totalThreadCount + 1, savePath));
                    // run the thread after creation
                    threads[i].start();
                    totalThreadCount++;
                }
            }
        }
    }

    /**
     * This method sets proxy and ports here, behind a firewall
     */
    private static void setProxy() {
        Properties props= new Properties(System.getProperties());
        props.put("http.proxySet", "true");
        props.put("http.proxyHost", "webcache-cup");
        props.put("http.proxyPort", "8080");
        Properties newprops = new Properties(props);
        System.setProperties(newprops);
    }

    /**
     * This method compares the internal hashmap and external hashset, add non-duplicate
     * urls to both the queue and external hashset, and ignore duplicates
     */
    @SuppressWarnings("unchecked")
    private static void addToUrlQueue(String savePath) {
        for (Map.Entry<Integer, HashSet<URL>> entry: internalHashMap.entrySet()) {
            // make a separate directory for the external hashsets, if haven't
            String dirPath = savePath + "HashSets" + File.separator;
            File dir = new File(dirPath);
            if (!dir.exists()) {
                dir.mkdir();
            }
            // index corresponds to the id of the external hashset
            int index = entry.getKey();
            String externalName = "External" + index + ".ser";
            File file = new File(dirPath + externalName);
            HashSet<URL> internalHashSet = entry.getValue();
            HashSet<URL> externalHashSet = null;
            if (file.exists()) {
                // load external hashset
                try {
                    FileInputStream fis = new FileInputStream(dirPath + externalName);
                    ObjectInputStream ois = new ObjectInputStream(fis);
                    externalHashSet = (HashSet<URL>) ois.readObject();
                    ois.close();
                } catch (IOException e) {
                    // ignore or add something?
                } catch (ClassNotFoundException e) {
                    // ignore or add something?
                }
            }
            // if the external hashset does not exist, create one
            // but if it exists but fails to load, should overwrite it?
            if (externalHashSet == null) {
                externalHashSet = new HashSet<URL>();
            }
            // iterate through the internal hashset, if the url is duplicated, just ignore,
            // if the url is new, add it to both the queue and external hashset
            for (URL url: internalHashSet) {
                if (!externalHashSet.contains(url)) {
                    externalHashSet.add(url);
                    urlQueue.add(url);
                }
            }
            // save external hashset back
            try {
                FileOutputStream fos = new FileOutputStream(dirPath + externalName);
                ObjectOutputStream oos = new ObjectOutputStream(fos);
                oos.writeObject(externalHashSet);
                oos.close();
            } catch (IOException e) {
                // ignore or add something?
            }
        }
    }

    /**
     * The run() method in this class specifies what each thread is doing
     */
    private static class Crawling implements Runnable {
        private static int activeThreadCount = 0;
        private int threadID;
        private String savePath;

        public Crawling(int id, String path) {
            threadID = id;
            savePath = path;
        }

        public void run() {
            System.out.println("thread " + threadID + " started");
            activeThreadCount++;
            URL url = null;
            // if use urlQueue.isEmpty() and urlQueue.poll() separately,
            // may have concurrency issue
            while (pageCount < searchLimit && (url = urlQueue.poll()) != null) {
                if (!isRobotSafe(url)) {
                    continue;
                }
                String page = getPage(url);
                // page equals empty indicates the page was not processed successfully
                // because of various reasons detailed in getPage() method
                if (page.equals("")) {
                    continue;
                }
                // use count as the file name, and only when the page is saved successfully,
                // the count increments
                synchronized (PAGE_COUNT_LOCK) {
                    try {
                        savePage(page, savePath, (pageCount + 1) + "");
                    } catch (IOException e) {
                        continue;
                    }
                    pageCount++;
                    System.out.println("thread " + threadID + " downloaded page " + pageCount);
                }
                indexPage(page);
                List<URL> newUrls = extractUrl(url, page);
                for (URL newUrl: newUrls) {
                    // if newUrl is duplicated in the internal hashmap, it will be ignored
                    // by addToInternalHashMap(newUrl), and when the next round of crawling first
                    // begins, the urls in internal hashmap will also be checked against external hashset
                    addToInternalHashMap(newUrl);
                }
            }
            if (pageCount >= searchLimit) {
                System.out.println("thread " + threadID + " terminated because search limit is reached");
            }
            else {
                System.out.println("thread " + threadID + " terminated because queue of the current round is empty");
            }
            activeThreadCount--;
        }

        public static int getActiveThreadCount() {
            return activeThreadCount;
        }
    }

    /**
     * This method returns whether the page is robot safe
     */
    private static boolean isRobotSafe(URL url) {
        String strHost = url.getHost();
        if (strHost.length() == 0) {
            return false;
        }
        // form URL of the robots.txt file
        String strRobot = "http://" + strHost + "/robots.txt";
        URL urlRobot;
        try {
            urlRobot = new URL(strRobot);
        } catch (MalformedURLException e) {
            // something weird is happening, so don't trust it
            return false;
        }
        InputStream urlRobotStream = null;
        try {
            URLConnection urlConnection = urlRobot.openConnection();
            urlConnection.setConnectTimeout(5000);
            urlConnection.setReadTimeout(5000);
            urlRobotStream = urlConnection.getInputStream();
        } catch (IOException e) {
            return false;
        }
        String strCommands = "";
        try {
            // read in entire file
            byte b[] = new byte[1000];
            int numRead = urlRobotStream.read(b);
            while (numRead != -1) {
                String newCommands = new String(b, 0, numRead);
                strCommands += newCommands;
                numRead = urlRobotStream.read(b);
            }
            urlRobotStream.close();
        } catch (IOException e) {
            // if there is no robots.txt file, it is OK to search
            return true;
        }
        // assume that this robots.txt refers to us and
        // search for "Disallow:" commands.
        String strURL = url.getFile();
        int index = 0;
        final String DISALLOW = "Disallow:";
        while ((index = strCommands.indexOf(DISALLOW, index)) != -1) {
            index += DISALLOW.length();
            String strPath = strCommands.substring(index);
            StringTokenizer st = new StringTokenizer(strPath);
            if (!st.hasMoreTokens()) {
                break;
            }
            String strBadPath = st.nextToken();
            // if the URL starts with a disallowed path, it is not safe
            if (strURL.indexOf(strBadPath) == 0) {
                return false;
            }
        }
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
        try {
            // try opening the URL
            URLConnection urlConnection = url.openConnection();
            urlConnection.setAllowUserInteraction(false);
            HttpURLConnection http = (HttpURLConnection)urlConnection;
            String type = http.getContentType();
            // reference: https://www.w3.org/Protocols/rfc1341/4_Content-Type.html
            // only get text type now, may add more permitted types later
            if (type == null || !type.toLowerCase().startsWith("text")) {
                return "";
            }
            InputStream urlStream = urlConnection.getInputStream();
            // search the input stream for links
            // first, read in the entire URL
            final int BYTE_COUNT = 1000;
            byte b[] = new byte[BYTE_COUNT];
            int numRead = urlStream.read(b);
            String content = "";
            boolean notKnownIfEnglish = true;
            while ((numRead != -1) ) {
                String newContent = new String(b, 0, numRead);
                content += newContent;
                // check if the page is in English right now,
                // and stop downloading immediately if not
                // notknownIfEnglish == false indicates already knowing it's in English
                if (notKnownIfEnglish) {
                    int index = content.indexOf("lang=\"");
                    // if the tag exists
                    if (index != -1) {
                        index += 6;
                        if (index + 2 <= content.length() && content.substring(index, index + 2).equals("en")) {
                            notKnownIfEnglish = false;
                        }
                        // lang==some other language, return
                        else if (index + 2 <= content.length()) {
                            return "";
                        }
                        // if index + 2 > content.length(), it means the tag happens to be in the middle,
                        // wait till next round to see
                    }
                }
                numRead = urlStream.read(b);
            }
            if (notKnownIfEnglish) {
                // if the language tag does not exist, count non-ASCII characters
                int count = 0;
                // arbitrary threshold
                final double THRESHOLD = 0.5;
                for (int i = 0; i < content.length(); i++) {
                    //if the current char is non-ASCII
                    if (content.charAt(i) > 127) {
                        count++;
                    }
                    if (count > content.length() * THRESHOLD) {
                        return "";
                    }
                }
            }
            return content;
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * This method should save page to local directory
     */
    private static void savePage(String page, String savePath, String fileName)
            throws IOException {
        fileName += ".html";
        File file = new File(savePath + fileName);
        file.createNewFile();
        FileWriter writer = new FileWriter(file);
        writer.write(page);
        writer.close();
    }

    /**
     * This method should connect to indexing and pageRank calculation
     */
    private static void indexPage(String page) {

    }

    /**
     * This method should parse the page and extract the urls it contains,
     * and return them in an arraylist
     */
    private static List<URL> extractUrl(URL url, String page) {
        List<URL> results = new ArrayList<URL>();
        // Page in lower case
        String lcPage = page.toLowerCase();
        // position in page
        int index = 0;
        int iEndAngle, ihref, iURL, iCloseQuote, iHatchMark, iEnd;
        while ((index = lcPage.indexOf("<a", index)) != -1) {
            iEndAngle = lcPage.indexOf(">", index);
            ihref = lcPage.indexOf("href", index);
            if (ihref != -1) {
                iURL = lcPage.indexOf("\"", ihref) + 1;
                if ((iURL != -1) && (iEndAngle != -1) && (iURL < iEndAngle)) {
                    iCloseQuote = lcPage.indexOf("\"", iURL);
                    iHatchMark = lcPage.indexOf("#", iURL);
                    if ((iCloseQuote != -1) && (iCloseQuote < iEndAngle)) {
                        iEnd = iCloseQuote;
                        if ((iHatchMark != -1) && (iHatchMark < iCloseQuote)) {
                            iEnd = iHatchMark;
                        }
                        String newUrlString = page.substring(iURL, iEnd);
                        URL newUrl = null;
                        try {
                            newUrl = new URL(url, newUrlString);
                            results.add(newUrl);
                        } catch (MalformedURLException e) {
                            //ignore invalid urls
                        }
                    }
                }
            }
            index = iEndAngle;
        }
        return results;
    }

    public static void main(String[] args) {
        System.out.println("\nPlease remember to clear the result folder before running");
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            // ignore
        }
        String usage = "usage: java Crawler [-root rootURLFile] [-path savePath] [-max searchLimit]";
        final int ARG_COUNT = 6;
        if (args.length != ARG_COUNT) {
            System.out.println(usage);
            System.exit(1);
        }
        Scanner readFile = null;
        String savePath = null;
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
            else if (args[index].equals("-path")) {
                savePath = args[index + 1];
                if (savePath == null) {
                    System.out.println(usage);
                    System.exit(1);
                }
                final Path docDir = Paths.get(savePath);
                if (!Files.isReadable(docDir)) {
                    System.out.println("Document directory '" + docDir.toAbsolutePath() + "' does not "
                            + "exist or is not readable, please check the path");
                    System.exit(1);
                }
                if (!Files.isDirectory(docDir)) {
                    System.out.println("Please provide the path of a directory");
                    System.exit(1);
                }
                if (!savePath.endsWith(File.separator)) {
                    savePath += File.separator;
                }
                index += 2;
            }
            else if (args[index].equals("-max")) {
                try {
                    searchLimit = Integer.parseInt(args[index + 1]);
                    index += 2;
                } catch (NumberFormatException e) {
                    System.out.println("Please provide an integer value for searchLimit");
                    System.exit(1);
                }
            }
            else {
                System.out.println(usage);
                System.exit(1);
            }
        }
        run(readFile, savePath);
    }

}
