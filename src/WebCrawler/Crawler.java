package WebCrawler;

import java.util.*;
import java.net.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

public class Crawler {
    private static UrlQueue urlQueue =
            new UrlQueue();
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
        // make a separate directory for the external hashsets
        String dirPath = savePath + "HashSets" + File.separator;
        File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdir();
        }
        addToUrlQueue(savePath);
        final int THREAD_COUNT = 1500;
        Thread[] threads = new Thread[THREAD_COUNT];
        for (int i = 0; i < THREAD_COUNT; i++) {
            threads[i] = new Thread(new Crawling(i, savePath));
            // run the thread after creation
            threads[i].start();
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
        synchronized (INTERNAL_HASHMAP_LOCK) {
            // although this method is only called when urlQueue is empty, it may be possible
            // that the urlQueue just became non-empty, in this case the thread should return,
            // because there's no need to frequently update the urlQueue, as I/O is expensive
            if (!urlQueue.isEmpty()) {
                return;
            }
            for (Map.Entry<Integer, HashSet<URL>> entry: internalHashMap.entrySet()) {
                String dirPath = savePath + "HashSets" + File.separator;
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
                        System.out.println("Load external hashset " + index + " successfully");
                    } catch (IOException e) {
                        System.out.println("Load external hashset " + index + " not successfully");
                    } catch (ClassNotFoundException e) {
                        System.out.println("Load external hashset " + index + " not successfully");
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
                    System.out.println("Save external hashset " + index + " successfully");
                } catch (IOException e) {
                    System.out.println("Save external hashset " + index + " not successfully");
                }
            }
            // must clear the internal hashmap after this
            internalHashMap.clear();
        }
    }

    /**
     * The run() method in this class specifies what each thread is doing
     */
    private static class Crawling implements Runnable {
        private int threadID;
        private String savePath;

        public Crawling(int id, String path) {
            threadID = id;
            savePath = path;
        }

        public void run() {
            System.out.println("thread " + threadID + " started");
            URL url = null;
            while (pageCount < searchLimit) {
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
                    return;
                }
                // if the urlQueue becomes empty, the thread will actively initiate moving urls from internal
                // hashmap to urlQueue, by calling addToUrlQueue(). The first thread which gets the lock
                // will make the urlQueue non-empty, but the other threads which are already waiting still have
                // to call addToUrlQueue() before returning to crawl, because there's no way for them to know
                // the urlQueue becomes non-empty. Is there a better way?
                System.out.println("thread " + threadID + " paused because queue of the current round is empty");
                addToUrlQueue(savePath);
            }
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
        long startTime = System.currentTimeMillis();
        try {
            // try opening the URL
            URLConnection urlConnection = url.openConnection();
            urlConnection.setConnectTimeout(5000);
            urlConnection.setReadTimeout(5000);
            urlConnection.setAllowUserInteraction(false);
            HttpURLConnection http = (HttpURLConnection)urlConnection;
            String type = null;
            if (http != null) {
                type = http.getContentType();
            }
            // reference: https://www.w3.org/Protocols/rfc1341/4_Content-Type.html
            // only get text type now, may add more allowed types later
            // how to handle type == null? Allow them now because pages with type == null
            // seems all to be textual type actually
            if (type != null && !type.toLowerCase().startsWith("text")) {
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
                // check if the page is in English right now,
                // and stop downloading immediately if not
                // notknownIfEnglish == false indicates already knowing it's in English
                // only check newContent for efficiency
                if (notKnownIfEnglish) {
                    int index = newContent.indexOf("lang=\"");
                    // if the tag exists
                    if (index != -1) {
                        index += 6;
                        if (index + 2 <= newContent.length() && newContent.substring(index, index + 2).equals("en")) {
                            notKnownIfEnglish = false;
                        }
                        // lang==some other language, return
                        else if (index + 2 <= newContent.length()) {
                            return "";
                        }
                        // if index + 2 > newContent.length(), it means the tag happens to be in the middle
                        // may have some mistakes, ignore? is there a better way?
                    }
                }
                content += newContent;
                numRead = urlStream.read(b);
                // if lang tag does not exist, allow them. It seems that counting foreign characters
                // does not work well
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
        // position in page
        int index = 0;
        int newIndex = 0;
        int iEndAngle, ihref, iURL, iCloseQuote, iHatchMark, iEnd;
        while ((newIndex = page.indexOf("<a", index)) != -1
                || (newIndex = page.indexOf("<A", index)) != -1) {
            index = newIndex;
            iEndAngle = page.indexOf(">", index);
            if ((ihref = page.indexOf("href", index)) != -1
                    || (ihref = page.indexOf("HREF", index)) != -1) {
                iURL = page.indexOf("\"", ihref) + 1;
                if ((iURL != -1) && (iEndAngle != -1) && (iURL < iEndAngle)) {
                    iCloseQuote = page.indexOf("\"", iURL);
                    iHatchMark = page.indexOf("#", iURL);
                    if ((iCloseQuote != -1) && (iCloseQuote < iEndAngle)) {
                        iEnd = iCloseQuote;
                        if ((iHatchMark != -1) && (iHatchMark < iCloseQuote)) {
                            iEnd = iHatchMark;
                        }
                        String newUrlString = page.substring(iURL, iEnd).toLowerCase();
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

    /**
     * This class tries to simulate a concurrent queue
     */
    private static class UrlQueue {
        // it consists of many small queues
        final int LIST_COUNT = 1000;
        boolean isEmpty = true;
        HashMap<Integer, LinkedList<URL>> listMap =
                new HashMap<Integer, LinkedList<URL>>();
        private final Object[] LIST_LOCK = new Object[LIST_COUNT];

        public UrlQueue() {
            for (int i = 0; i < LIST_COUNT; i++) {
                listMap.put(i, new LinkedList<URL>());
                LIST_LOCK[i] = new Object();
            }
        }

        public boolean isEmpty() {
            return isEmpty;
        }

        public void add(URL url) {
            int index = (int)(Math.random() * LIST_COUNT);
            synchronized (LIST_LOCK[index]) {
                LinkedList<URL> current = listMap.get(index);
                current.add(url);
            }
        }

        public URL poll() {
            for (int i = 0; i < 5; i++) {
                int index = (int)(Math.random() * LIST_COUNT);
                synchronized (LIST_LOCK[index]) {
                    LinkedList<URL> current = listMap.get(index);
                    URL url = current.poll();
                    if (url != null) {
                        isEmpty = false;
                        return url;
                    }
                }
            }
            // if a thread tries ten times but does not get a url,
            // consider the whole queue to be empty
            isEmpty = true;
            return null;
        }
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
