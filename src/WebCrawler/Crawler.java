package WebCrawler;

import PageCompress.*;
import java.util.*;
import java.net.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 1) USAGE: java Crawler [-path savePath] [-time duration] [-id jobID]
 *
 * 2) Please put 'PageCompress' directory besides the 'WebCrawler' directory under the same parent directory, and
 * download "jsoup-1.8.3.jar". For example, to compile and run, please cd to the parent directory and type:
 * javac -cp "../lib/jsoup-1.8.3.jar" PageCompress/*.java
 * javac -cp "../lib/jsoup-1.8.3.jar:." WebCrawler/*.java
 * java -cp "../lib/jsoup-1.8.3.jar:." WebCrawler/Crawler -path ../results -time 300 -id 1
 *
 * 3) The unit of duration is second. Search limit is not used because it may never be reached.
 *
 * 4) Under the directory variable 'savePath' the user provides, the following two sub-directories should have
 * been created before running: (please use the same capitalization)
 * a directory called 'hashSets', containing the external hashSets from last round, or empty if it's the first round
 * a directory called 'roots', containing url root files named as 'root_1', 'root_2'... the number of such files
 * should be the same with the number of rounds the program to be run, so if we plan to run the program
 * 200 times, then the files 'root_1' - 'root_200' (no extensions) should all exist in this directory
 */

public class Crawler {
    private static final String USAGE = "USAGE: java Crawler [-path savePath] [-time duration] [-id jobID]";
    private static String savePath;
    private static int jobID;
    private static final int MAX_DEPTH = 3;
    private static UrlQueue urlQueue =
            new UrlQueue();
    private static HashMap<Integer, HashSet<MyURI>> internalHashMap =
            new HashMap<Integer, HashSet<MyURI>>();
    private static int searchLimit = 20000;
    private static int pageCount = 0;
    private static final int THREAD_COUNT = 1500;
    private static final int EXTERNAL_HASHSET_COUNT = 1000;
    private static final Object[] INTERNAL_HASHSET_LOCK = new Object[EXTERNAL_HASHSET_COUNT];
    private static long startTime;
    private static long duration;
    private static BufferedWriter logWriter;
    private static final Object LOG_WRITER_LOCK = new Object();
    private static BufferedWriter[] idWriter = new BufferedWriter[EXTERNAL_HASHSET_COUNT];
    private static BufferedWriter[] urlWriter = new BufferedWriter[EXTERNAL_HASHSET_COUNT];
    private static final Object[] ID_WRITER_LOCK = new Object[EXTERNAL_HASHSET_COUNT];
    private static final Object[] URI_WRITER_LOCK = new Object[EXTERNAL_HASHSET_COUNT];

    /**
     * This method is the overall running process
     */
    private static void run(Scanner readFile) {
        startTime = System.currentTimeMillis();
        output("Crawling round " + jobID + " has started");
        initialize(readFile);
        crawl();
    }

    /**
     * This method adds the root urls into the internal hashmap, later when crawling first begins,
     * the root urls will be added to the queue and the external hashset.
     * The input file should have each url in a new line
     */
    private static void initialize(Scanner readFile) {
        // initialize locks
        for (int i = 0; i < EXTERNAL_HASHSET_COUNT; i++) {
            INTERNAL_HASHSET_LOCK[i] = new Object[i];
            ID_WRITER_LOCK[i] = new Object[i];
            URI_WRITER_LOCK[i] = new Object[i];
        }
        while (readFile.hasNextLine()) {
            try {
                MyURI url = new MyURI(new URI(readFile.nextLine()), 1);
                addToInternalHashMap(url);
            } catch (URISyntaxException e) {
                //ignore invalid urls
            }
        }
    }

    /**
     * This method adds the newly extracted urls (or root urls) to the internal hashmap,
     * if the url is duplicated, just ignore. It also indexes the urls by calculating
     * its hashCode
     */
    private static void addToInternalHashMap(MyURI url) {
        int hashValue = hash(url);
        HashSet<MyURI> hashSet = null;
        synchronized (INTERNAL_HASHSET_LOCK[hashValue]) {
            if (internalHashMap.containsKey(hashValue)) {
                hashSet = internalHashMap.get(hashValue);
            }
            else {
                hashSet = new HashSet<MyURI>();
                internalHashMap.put(hashValue, hashSet);
            }
            hashSet.add(url);
        }
    }

    /**
     * This method calculates the hashCode of a url
     */
    private static int hash(MyURI url) {
        return Math.abs(url.getURI().toString().hashCode()) % EXTERNAL_HASHSET_COUNT;
    }

    /**
     * This method is the crawling process
     */
    private static void crawl() {
        setProxy();
        // assume the hashSets directory has been created
        String dirPath = savePath + "hashSets" + File.separator;
        // based on the new addToUrlQueue() design, no real need to call addToUrlQueue() here
        Crawling[] crawlings = new Crawling[THREAD_COUNT];
        Thread[] threads = new Thread[THREAD_COUNT];
        for (int i = 0; i < THREAD_COUNT; i++) {
            crawlings[i] = new Crawling(i);
            threads[i] = new Thread(crawlings[i]);
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
    private static void addToUrlQueue() {
        // only randomly pick one index, instead of iterating over the whole hashmap,
        // so after this, urlQueue.isEmpty() may still be true, but more threads will come
        int index = (int)(Math.random() * EXTERNAL_HASHSET_COUNT);
        // only save URI to disk, removing depth and avoiding serializable
        synchronized (INTERNAL_HASHSET_LOCK[index]) {
            if (!urlQueue.isEmpty()) {
                return;
            }
            if (!internalHashMap.containsKey(index)) {
                return;
            }
            String dirPath = savePath + "hashSets" + File.separator;
            // index corresponds to the id of the external hashset
            String externalName = "External" + index + ".ser";
            File file = new File(dirPath + externalName);
            HashSet<MyURI> internalHashSet = internalHashMap.get(index);
            HashSet<URI> externalHashSet = null;
            if (file.exists()) {
                // load external hashset
                try {
                    FileInputStream fis = new FileInputStream(dirPath + externalName);
                    ObjectInputStream ois = new ObjectInputStream(fis);
                    externalHashSet = (HashSet<URI>) ois.readObject();
                    ois.close();
                    //output("Load external hashset " + index + " successfully");
                } catch (IOException e) {
                    output("Load external hashset " + index + " not successfully");
                } catch (ClassNotFoundException e) {
                    output("Load external hashset " + index + " not successfully");
                }
            }
            // if the external hashset does not exist, create one
            // but if it exists but fails to load, should overwrite it?
            if (externalHashSet == null) {
                externalHashSet = new HashSet<URI>();
            }
            // iterate through the internal hashset, if the url is duplicated, just ignore,
            // if the url is new, add it to both the queue and external hashset
            for (MyURI url: internalHashSet) {
                if (!externalHashSet.contains(url)) {
                    externalHashSet.add(url.getURI());
                    urlQueue.add(url);
                }
            }
            // save external hashset back
            try {
                FileOutputStream fos = new FileOutputStream(dirPath + externalName);
                ObjectOutputStream oos = new ObjectOutputStream(fos);
                oos.writeObject(externalHashSet);
                oos.close();
                //output("Save external hashset " + index + " successfully");
            } catch (IOException e) {
                output("Save external hashset " + index + " not successfully");
            }
            // clear the current hashset, not the entire hashmap
            internalHashSet.clear();
        }
    }

    /**
     * The run() method in this class specifies what each thread is doing
     */
    private static class Crawling implements Runnable {
        private int threadID;
        private int downloadCount;

        public Crawling(int id) {
            threadID = id;
            downloadCount = 0;
        }

        public void run() {
            MyURI url = null;
            while (pageCount < searchLimit) {
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
                    // use count as the part of the file name, and only when the page is
                    // saved successfully, the count increments
                    String fileName = jobID + "_" + threadID + "_" + (downloadCount + 1);
                    // generate PageFile object, including pageID, title, subURLs and body text
                    PageCompress pc = new PageCompress(fileName, page);
                    PageFile pageFile = null;
                    try {
                        pageFile = pc.GetPageFile();
                    } catch (Exception e) {
                        output("process page " + fileName + " not successfully");
                        continue;
                    }
                    if (pageFile == null) {
                        // to be safe, stop here when pageFile == null
                        output("process page " + fileName + " not successfully");
                        continue;
                    }
                    // filter out empty content
                    if (pageFile.getContent().matches("^\\s*$")) {
                        continue;
                    }
                    // examine the sub urls
                    List<MyURI> newUrls = rmInvalidUrls(url, pageFile.getSubURLs());
                    newUrls = rmSameDomain(newUrls);
                    // save page to disk
                    try {
                        savePage(fileName, url, newUrls, pageFile.getTitle(), pageFile.getContent());
                    } catch (IOException e) {
                        output("save page " + fileName + " not successfully");
                        continue;
                    }
                    downloadCount++;
                    output("thread " + threadID + " downloaded page " + fileName + " with depth " + url.getDepth());
                    try {
                        writeToMapping(fileName, url, threadID);
                    } catch (IOException e) {
                        output("save mapping for " + fileName + " not successfully");
                        continue;
                    }
                    // only when depth does not exceed maximum, add new suburls to queue,
                    // if newUrl is duplicated in the internal hashmap,
                    // it will be ignored by addToInternalHashMap(newUrl), and the urls in
                    // internal hashmap will also be checked against external hashset before
                    // being added to queue
                    if (url.getDepth() < MAX_DEPTH) {
                        for (MyURI newUrl: newUrls) {
                            addToInternalHashMap(newUrl);
                        }
                    }
                    if (System.currentTimeMillis() - startTime > duration) {
                        try {
                            stop();
                        } catch (IOException e) {
                            // ignore
                        }
                    }
                }
                // if the current queue is empty, initiate addToUrlQueue() method
                addToUrlQueue();
            }
        }

        public int getDownloadCount() {
            return downloadCount;
        }
    }

    /**
     * This method returns whether the page is robot safe
     */
    private static boolean isRobotSafe(MyURI myUrl) {
        URI url = myUrl.getURI();
        String strHost = url.getHost();
        if (strHost == null) {
            return false;
        }
        // form url of the robots.txt file
        String strRobot = "http://" + strHost + "/robots.txt";
        URI urlRobot;
        try {
            urlRobot = new URI(strRobot);
        } catch (URISyntaxException e) {
            // something weird is happening, so don't trust it
            return false;
        }
        InputStream urlRobotStream = null;
        try {
            URLConnection urlConnection = urlRobot.toURL().openConnection();
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
        String strURI = null;
        try {
            strURI = url.toURL().getFile();
        } catch (MalformedURLException e) {
            return false;
        }
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
            // if the URI starts with a disallowed path, it is not safe
            if (strURI.indexOf(strBadPath) == 0) {
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
    private static String getPage(MyURI myUrl) {
        URI url = myUrl.getURI();
        try {
            // try opening the URI
            URLConnection urlConnection = url.toURL().openConnection();
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
            // first, read in the entire URI
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
                        // if index + 2 > newContent.length(), it means the tag happens to be in the middle,
                        // this may have some mistakes, ignore them? is there a better way?
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

    /*
     * This method removes invalid sub urls and assembles valid sub urls (by Chen Chen)
     */
    private static List<MyURI> rmInvalidUrls(MyURI myUrl, List<String> subURLs) {
        URI url = myUrl.getURI();
        int depth = myUrl.getDepth();
        List<MyURI> results = new ArrayList<MyURI>();
        for (String suburl : subURLs) {
            URL newUrl = null;
            try {
                newUrl = new URL(url.toURL(), suburl);
            } catch (MalformedURLException e) {
                //ignore invalid urls
                continue;
            }
            try {
                results.add(new MyURI(new URI(newUrl.toString()), depth + 1));
            } catch (URISyntaxException e) {
                //ignore invalid urls
                continue;
            }
        }
        return results;
    }

    /**
     * This method discards some of the urls with very frequent domains
     */
    private static List<MyURI> rmSameDomain(List<MyURI> urlList) {
        List<MyURI> newUrlList = new ArrayList<MyURI>();
        final int MAX_TOLERANCE = 10;
        HashMap<String, Integer> repeat = new HashMap<String, Integer>();
        for (int i = 0; i < urlList.size(); i++) {
            String domain = urlList.get(i).getURI().getHost();
            if (repeat.containsKey(domain)) {
                int count = repeat.get(domain);
                if (count < MAX_TOLERANCE) {
                    repeat.put(domain, count + 1);
                    newUrlList.add(urlList.get(i));
                }
            }
            else {
                repeat.put(domain, 1);
                newUrlList.add(urlList.get(i));
            }
        }
        return newUrlList;
    }

    /**
     * This method saves page to disk (by Chen Chen)
     */
    private static void savePage(String fileName, MyURI thisUrl, List<MyURI> newUrls, String title,
                                 String content)
            throws IOException {
        String filePath = savePath + "pages" + File.separator + "result_" + jobID + File.separator;
        FileWriter writer = new FileWriter(filePath + fileName);
        BufferedWriter bufferedWriter = new BufferedWriter(writer);
        // write its own url
        bufferedWriter.write("#ThisURL#" + "\n" + thisUrl.getURI().toString() + "\n");
        // write its suburls
        bufferedWriter.write("#SubURL#" + "\n");
        for (MyURI url: newUrls) {
            bufferedWriter.write(url.getURI().toString() + "\n");
        }
        // write its title
        bufferedWriter.write("#Title#" + "\n" + title + "\n");
        // write its content
        bufferedWriter.write("#Content#" + "\n" + content + "\n");
        bufferedWriter.close();
    }

    /**
     * This method saves the pageID - url pair to disk
     */
    private static void writeToMapping(String id, MyURI url, int threadID)
            throws IOException {
        // id to url
        int index = threadID % EXTERNAL_HASHSET_COUNT;
        synchronized (ID_WRITER_LOCK[index]) {
            idWriter[index].write(id + "\n" + url.getURI().toString() + "\n");
        }
        // url to id
        index = hash(url);
        synchronized (URI_WRITER_LOCK[index]) {
            urlWriter[index].write(url.getURI().toString() + "\n" + id + "\n");
        }
    }

    /**
     * This class tries to simulate a concurrent queue
     */
    private static class UrlQueue {
        // it consists of many small queues
        final int LIST_COUNT = 1000;
        int emptyPos = 0;
        HashMap<Integer, LinkedList<MyURI>> listMap =
                new HashMap<Integer, LinkedList<MyURI>>();
        private final Object[] LIST_LOCK = new Object[LIST_COUNT];

        public UrlQueue() {
            for (int i = 0; i < LIST_COUNT; i++) {
                listMap.put(i, new LinkedList<MyURI>());
                LIST_LOCK[i] = new Object();
            }
        }

        public boolean isEmpty() {
            return emptyPos != -1;
        }

        // there's no lock for emptyPos, so cannot guarantee emptyPos won't change,
        // but doesn't hurt, and when hit miss is rare, should work well
        public void add(MyURI url) {
            int index = emptyPos;
            if (index == -1) {
                index = (int)(Math.random() * LIST_COUNT);
            }
            synchronized (LIST_LOCK[index]) {
                LinkedList<MyURI> current = listMap.get(index);
                current.add(url);
            }
            emptyPos = -1;
        }

        public MyURI poll() {
            int index = (int)(Math.random() * LIST_COUNT);
            MyURI url = null;
            synchronized (LIST_LOCK[index]) {
                LinkedList<MyURI> current = listMap.get(index);
                url = current.poll();
            }
            // if a thread does not get a url from a small queue,
            // store its index, and the next add() call will add
            // a url to that position
            if (url == null) {
                emptyPos = index;
            }
            else {
                emptyPos = -1;
            }
            return url;
        }
    }

    /**
     * This method prints the message to the console, and write to the work log
     */
    private static void output(String message) {
        System.out.println(message);
        try {
            synchronized (LOG_WRITER_LOCK) {
                logWriter.write(message + "\n");
            }
        } catch (IOException e) {
            System.out.println("Write to work log not successfully");
        }
    }

    /**
     * This method closes all writers and exits the program
     */
    private static void stop() throws IOException {
        for (int i = 0; i < EXTERNAL_HASHSET_COUNT; i++) {
            synchronized (ID_WRITER_LOCK[i]) {
                idWriter[i].close();
            }
            synchronized (URI_WRITER_LOCK[i]) {
                urlWriter[i].close();
            }
        }
        output("Crawling round " + jobID + " has ended");
        synchronized (LOG_WRITER_LOCK) {
            logWriter.close();
        }
        System.exit(0);
    }

    /**
     * This class adds depth value to URI class
     */
    private static class MyURI {
        private URI url;
        private int depth;

        public MyURI(URI url, int depth) {
            this.url = url;
            this.depth = depth;
        }

        public URI getURI() {
            return url;
        }

        public int getDepth() {
            return depth;
        }
    }

    /**
     * This method checks the inputs and exits the program if inputs are not valid
     */
    private static void checkArgs(String[] args) {
        System.out.println("\nPlease remember to create everything listed at code line 10 before running");
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            // ignore
        }
        final int ARG_COUNT = 6;
        if (args.length != ARG_COUNT) {
            System.out.println(USAGE);
            System.exit(1);
        }
        savePath = null;
        int index = 0;
        while (index < args.length) {
            if (args[index].equals("-path")) {
                savePath = args[index + 1];
                if (savePath == null) {
                    System.out.println(USAGE);
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
            else if (args[index].equals("-time")) {
                try {
                    duration = Long.parseLong(args[index + 1]) * 1000;
                    index += 2;
                } catch (NumberFormatException e) {
                    System.out.println("Please provide an integer value for duration");
                    System.exit(1);
                }
            }
            else if (args[index].equals("-id")) {
                try {
                    jobID = Integer.parseInt(args[index + 1]);
                    index += 2;
                } catch (NumberFormatException e) {
                    System.out.println("Please provide an integer value for jobID");
                    System.exit(1);
                }
            }
            else {
                System.out.println(USAGE);
                System.exit(1);
            }
        }
    }

    /**
     * This method creates all the necessary directories and creates file reader and writer
     */
    public static Scanner prepare() {
        // assume the roots directory has been created, read in the root file
        Scanner readFile = null;
        try {
            readFile = new Scanner(new FileReader(savePath + "roots" + File.separator + "root_" + jobID));
        } catch (FileNotFoundException e) {
            System.out.println("The root file does not exist");
            System.exit(1);
        }
        // create the directory to save pages for this round of crawling
        String resultPath = savePath + "pages" + File.separator;
        File resultDir = new File(resultPath);
        // only useful when it's the first time
        if (!resultDir.exists()) {
            resultDir.mkdir();
        }
        resultPath += "result_" + jobID + File.separator;
        resultDir = new File(resultPath);
        if (!resultDir.exists()) {
            resultDir.mkdir();
        }
        // create the directory to save pageID - url mapping
        String mappingPath = savePath + "pageID" + File.separator;
        File mappingDir = new File(mappingPath);
        // only useful when it's the first time
        if (!mappingDir.exists()) {
            mappingDir.mkdir();
        }
        String insidePath = mappingPath + "idToUrl" + File.separator;
        File insideDir = new File(insidePath);
        // only useful when it's the first time
        if (!insideDir.exists()) {
            insideDir.mkdir();
        }
        insidePath = mappingPath + "urlToId" + File.separator;
        insideDir = new File(insidePath);
        // only useful when it's the first time
        if (!insideDir.exists()) {
            insideDir.mkdir();
        }
        // create a work_log directory (if haven't) and create the work_log file
        String dirPath = savePath + "work_log" + File.separator;
        File dir = new File(dirPath);
        // only useful when it's the first time
        if (!dir.exists()) {
            dir.mkdir();
        }
        // initialize log writer
        try {
            FileWriter writer = new FileWriter(dirPath + "workLog_" + jobID);
            logWriter = new BufferedWriter(writer);
        } catch (IOException e) {
            System.out.println("Create workLog_" + jobID + " not successfully");
        }
        // initialize id and url writer
        for (int i = 0; i < EXTERNAL_HASHSET_COUNT; i++) {
            String fileName = savePath + "pageID" + File.separator + "idToUrl" + File.separator + "idToUrl_" + i;
            try {
                FileWriter writer = new FileWriter(fileName, true);
                idWriter[i] = new BufferedWriter(writer);
            } catch (IOException e) {
                System.out.println("Create idToUrl_" + i + " not successfully");
            }
            fileName = savePath + "pageID" + File.separator + "urlToId" + File.separator + "urlToId_" + i;
            try {
                FileWriter writer = new FileWriter(fileName, true);
                urlWriter[i] = new BufferedWriter(writer);
            } catch (IOException e) {
                System.out.println("Create urlToId_" + i + " not successfully");
            }
        }
        return readFile;
    }

    public static void main(String[] args) {
        checkArgs(args);
        Scanner readFile = prepare();
        run(readFile);
    }
}
