package Indexter;


import org.tartarus.snowball.ext.englishStemmer;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.*;

import static java.lang.Thread.sleep;

/**
 * Created by ChenChen on 4/18/16.
 */
public class Indexter {
    private String mainPath;        // path to main folder (data)
    private String resultPath;      // path to result folder
    private String stopWordsPath;

    private static Set<String> StopWordList;   // for each thread, read only

    private int     CRAWLER_THREADS_NUM;  // how many threads Crawler created
    private int     CRAWLER_JOB_NUM;      // how many jobs Crawler created
    private int     THREAD_NUM;         // how many threads you want to create
    private boolean NO_NUM_MODEL;    // when do index, this model decide whether save number into index file
    private boolean STOP_WORDS_MODEL;

    private static final int    WORDS_MAP_THRESHOLD = 10000;
    private static final int    EMAIL_MAP_THRESHOLD = 1000;
    private static final int    DIFF_DIGIT_LETTER       = 5;
    private static final int    TOKEN_LENGTH_THRESHOLD  = 30;
    private static final String DELIMS_1            = "[ \n\r\t ]+";
    private static final String DELIMS_2            = "[~#%&*{}\\:<>?/|!$=+;_()\'\"^]+";
    private static final String IS_ENGLISH_REGEX    = "^[ \\w \\d \\s \\. \\& \\+ \\- \\, \\! \\@ \\# \\$ \\% \\^ \\* \\( \\) \\; \\\\ \\/ \\| \\< \\> \\\" \\' \\? \\= \\: \\[ \\] ]*$";

    Indexter(String mp, String rp, String sp, int ctn, int cjn, int tn, boolean numModel) {
        mainPath = mp;
        resultPath = rp;
        stopWordsPath = sp;
        StopWordList = new HashSet<String>();

        CRAWLER_THREADS_NUM = ctn;
        CRAWLER_JOB_NUM = cjn;
        THREAD_NUM = tn;
        NO_NUM_MODEL = numModel;


        if ("".equals(stopWordsPath)) {
            STOP_WORDS_MODEL = false;
        } else {
            STOP_WORDS_MODEL = true;
        }
    }

    private void Process() {
        File mainDir = new File(mainPath);
        File resultDir = new File(resultPath);
        if (!mainDir.exists() || !mainDir.isDirectory() || !resultDir.exists() || !resultDir.isDirectory()) {
            System.out.println("Data or result path is not a directory!");
            System.exit(1);
        }

        if (STOP_WORDS_MODEL) {
            ReadStopWordList();
        }

        Thread[] threads = new Thread[THREAD_NUM];
        for (int i = 0; i < THREAD_NUM; i++) {
            IndexterThread indexterThread = new IndexterThread(i, CRAWLER_JOB_NUM, mainPath);
            indexterThread.Initialize(CRAWLER_THREADS_NUM, THREAD_NUM);
            threads[i] = new Thread(indexterThread);
            threads[i].start();
        }

        // let every thread finish its job
        for (int i = 0; i < THREAD_NUM; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                System.out.println("Thread_" + i + ": cannot stop");
            } catch (Exception e) {
                System.out.println("Thread_" + i + ": meet other exception!");
            }
        }
    }

    /*
     * read stop words list from dist
     */
    private void ReadStopWordList() {
        String curline = "";
        try {
            InputStreamReader read = new InputStreamReader(new FileInputStream(new File(stopWordsPath)),"UTF-8");
            BufferedReader bufferedReader = new BufferedReader(read);
            while((curline = bufferedReader.readLine()) != null){
                if (!curline.equals("")) {
                    StopWordList.add(curline);
                }
            }
            bufferedReader.close();
            read.close();
        } catch (FileNotFoundException e) {
            System.out.println("Stop words list file doesn't exit!");
            System.exit(1);
        } catch (UnsupportedEncodingException e) {
            System.out.println("Cannot encode stop word list file!");
            System.exit(1);
        } catch (IOException e) {
            System.out.println("Cannot read stop word list file!");
            System.exit(1);
        }
    }

    /*
     * The reason I choose to use inner class not indepedent class for IndexterThread
     * is that inner class can use outter class's member and method directly
     */
    private class IndexterThread implements Runnable {
        private int threadID;
        private String mainPath;    // path of main folder (data)
        private int jobIDUpper;     // upper bound for job index
        private int beginID;        // beginID and endID is for threads folders
        private int endID;
        // each thread has its own WordsToDocs and EmailToDocs maps
        private Map<String, Set<String>> wordsToDocs;
        private Map<String, Set<String>> emailToDocs;

        public IndexterThread(int tid, int upper, String path) {
            threadID = tid;
            jobIDUpper = upper;
            mainPath = path;
            wordsToDocs = new HashMap<String, Set<String>>();
            emailToDocs = new HashMap<String, Set<String>>();
        }

        public void Initialize(int CrawlerThreadsNum, int threadsNum) {
            // in general, we need Crawler thread num > Index thread num
            if (CrawlerThreadsNum <= threadsNum) {
                beginID = threadID;
                endID = threadID;
            } else {
                beginID = CrawlerThreadsNum / threadsNum * threadID;
                endID = CrawlerThreadsNum / threadsNum * (threadID + 1) - 1;
            }
        }

        public void run() {
            // job number begins from 1
            for (int jobID = 1; jobID <= jobIDUpper; jobID++) {
                for (int folderID = beginID; folderID <= endID; folderID++) {
                    String curFolderPath = BuildFolderPath(jobID, folderID);
                    File curFolder = new File(curFolderPath);

                    // ignore the case that folder doesn't exit or has something wrong
                    if (!curFolder.exists() || !curFolder.isDirectory()) {
                        System.out.println("There is no folder: " + curFolder.getName());
                        continue;
                    }

                    // begin to deal with each crawler thread folder
                    File[] files = curFolder.listFiles();
                    for (File file : files) {
                        // because there are some hided files in folder which we need ignore
                        // we can use file extension
                        String name = file.getName();
                        String ext = GetExtension(name);
                        if (ext.equals("")) {
                            ProcessOneFile(file);
                            System.out.println("Thread_" + threadID + " builded indexer for file:\t" + name);
                        }
                    }
                }
            }
            if (!emailToDocs.isEmpty()) {
                WriteEmailMap();
            }
            if (!wordsToDocs.isEmpty()) {
                WriteWordsMap();
            }
        }

        // create crawler thread folder path
        private String BuildFolderPath(int jobID, int folderID) {
            String foldername = jobID + "_" + folderID;
            return mainPath + File.separator + "result_" + jobID + File.separator + foldername;
        }

        private String GetExtension(String filename) {
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
         * key method, in this method, transfer a token to a valid term in index file
         */
        private void ProcessOneFile(File file) {
            String pageID = file.getName();
            String content = GetFileContent(file);
            if (content == null || "".equals(content)) {
                return;
            }

            // we need use split method twice
            // first use " " to split whose content
            // then use special characters to split token
            // the reason do make this design is that using other character to splic content
            // might broken a whole token, such as URL
            // be careful, in DELIMS_1 the first space " " is different with the last space " "
            String[] tokens = content.split(DELIMS_1);
            for (String token : tokens) {
                // 1. first delete useless chars at token's head and tail
                // also can filter those token which are not english word
                // 2. then transfer to lower case
                String tempToken = FilterTwoSidePunctuation(token);
                tempToken = tempToken.toLowerCase();

                // 3. check if token is in stop words list
                if (STOP_WORDS_MODEL) {
                    if (StopWordList.contains(tempToken)) {
                        continue;
                    }
                }

                // 4. check if it is URL (don't store URL)
                if (IsValidURL(tempToken)) {
                    continue;
                }

                // 5. check if it is email address
                // if it is email address, store it to a special file
                // which is only for email
                // the file name is "EMAIL" and the format is:
                // email    pageID (same line, seperator: \t)
                // (we can consider give "EMAIL" a special file extension) ???
                if (IsValidEmailAddress(tempToken)) {
                    PutIntoEmailPostingList(tempToken, pageID);
                }

                // we can use ldpClass to know the statistic value of token for letter, digit and punctuation
                LDPClass ldpClass = GetLDPClass(token);

                // 6. check if doesn't has letter
                // e.g. 12:30
                if (NO_NUM_MODEL && !ldpClass.hasLetter) {
                    continue;
                }

                // 7. check if token only contains english characters, digits and punctuation
                if (!isEnglish(tempToken)) {
                    continue;
                }

                // 8. skip the case that token only has digit and letter,
                // and the number of digit is much more than that of letter
                // 9. or the length of token is over-long
                if (!ldpClass.hasPunctuation) {
                    int diff = ldpClass.digitNum - ldpClass.letterNum;
                    if (diff >= DIFF_DIGIT_LETTER) {
                        continue;
                    }
                    if (ldpClass.letterNum + ldpClass.digitNum > TOKEN_LENGTH_THRESHOLD) {
                        continue;
                    }
                }

                // second place to use split
                // since we already make sure that the token is not email and url
                // use characters are not allowed to use in file or folder name: ~#%&*{}\:<>?/|
                // and use characters is not valid in english word such as + ;
                // cannot use ",", because number can have it like 100,000
                if (ldpClass.hasPunctuation) {
                    String[] smallTokens = tempToken.split(DELIMS_2);
                    if (smallTokens.length > 1) {
                        for (String smallToken : smallTokens) {
                            // for each smallToken, do the same thing again
                            String modifySmallToken = FilterTwoSidePunctuation(smallToken);

                            if (STOP_WORDS_MODEL) {
                                if (StopWordList.contains(modifySmallToken)) {
                                    continue;
                                }
                            }

                            LDPClass tempLDPClass = GetLDPClass(modifySmallToken);

                            if (NO_NUM_MODEL && !tempLDPClass.hasLetter) {
                                continue;
                            }

                            if (!isEnglish(modifySmallToken)) {
                                continue;
                            }

                            if (!tempLDPClass.hasPunctuation) {
                                int tempDiff = tempLDPClass.digitNum - tempLDPClass.letterNum;
                                if (tempDiff >= DIFF_DIGIT_LETTER) {
                                    continue;
                                }
                                if (tempLDPClass.letterNum + tempLDPClass.digitNum > TOKEN_LENGTH_THRESHOLD) {
                                    continue;
                                }
                            }

                            modifySmallToken = StemEnglishWord(modifySmallToken);
                            PutIntoWordsPostingList(modifySmallToken, pageID);
                        }
                        continue;
                    }
                } else {
                    // if second delims doesn't splice the token
                    tempToken = StemEnglishWord(tempToken);
                    PutIntoWordsPostingList(tempToken, pageID);
                }
            }
        }

        /*
         * extract page content from page file
         */
        private String GetFileContent(File file) {
            String curline = "";
            String content = "";
            boolean recordFlag = false;
            try {
                InputStreamReader read = new InputStreamReader(new FileInputStream(file),"UTF-8");
                BufferedReader bufferedReader = new BufferedReader(read);

                while((curline = bufferedReader.readLine()) != null){
                    if (recordFlag) {
                        content += curline;
                    } else {
                        if (curline.equals("#Content#")) {
                            recordFlag = true;
                        }
                    }
                }

                bufferedReader.close();
                read.close();
            } catch (FileNotFoundException e) {
                // if there is no such file, just ignore it
            } catch (UnsupportedEncodingException e) {
                // if cannot encode the file, just ignore it
            } catch (Exception e) {
                e.printStackTrace();
            }
            return content;
        }

        /*
         * filter punctuations at token's head and tail
         * e.g. tomorrow. -> tomorrow       "aaa" -> aaa
         * if token only has punctuation or other language character, return ""
         */
        private String FilterTwoSidePunctuation(String token) {
            int begin = 0;
            int end = token.length() - 1;
            while (begin < token.length()) {
                if (!Character.isLetterOrDigit(token.charAt(begin))) {
                    begin++;
                } else {
                    break;
                }
            }
            while (end >= 0) {
                if (!Character.isLetterOrDigit(token.charAt(end))) {
                    end--;
                } else {
                    break;
                }
            }
            if (begin <= end) {
                return token.substring(begin, end+1);
            } else {
                return "";
            }
        }

        /*
         * Check whether string is valid URL
         */
        private boolean IsValidURL(String token) {
            boolean result = true;
            try {
                URL url = new URL(token);
            } catch (MalformedURLException e) {
                result = false;
            }
            return result;
        }

        /*
         * do statistic analysis for token
         */
        private LDPClass GetLDPClass(String token) {
            int ln = 0;
            int dn = 0;
            int pn = 0;
            for (int i = 0; i < token.length(); i++) {
                if (Character.isDigit(token.charAt(i))) {
                    dn++;
                } else if (Character.isLetter(token.charAt(i))) {
                    ln++;
                } else {
                    pn++;
                }
            }
            return new LDPClass(ln, dn, pn);
        }

        /*
         * Check whether string is a email address
         */
        private boolean IsValidEmailAddress(String token) {
            boolean result = true;
            try {
                InternetAddress emailAddr = new InternetAddress(token);
                emailAddr.validate();
            } catch (AddressException ex) {
                result = false;
            } catch (Exception e) {
                result = false;
            }
            return result;
        }

        /*
         * use regular expression to check whether text only contains letter, digit and punctuation
         * it can filter other language and too special characters
         */
        private boolean isEnglish(String text) {
            if (text == null) {
                return false;
            }
            return text.matches(IS_ENGLISH_REGEX);
        }

        /*
         * stermming
         * use snawball.jar to transfer english word back to its prototype
         */
        private String StemEnglishWord(String token) {
            englishStemmer stemmer = new englishStemmer();
            stemmer.setCurrent(token);
            if (stemmer.stem()) {
                return stemmer.getCurrent();
            }
            return token;
        }

        /*
         * insert email and related pageID into map
         * if map size is bigger than threshold, store them back to email file
         */
        private void PutIntoEmailPostingList(String email, String pageID) {
            if (!emailToDocs.containsKey(email)) {
                Set<String> tempSet = new HashSet<String>();
                tempSet.add(pageID);
                emailToDocs.put(email, tempSet);
            } else {
                emailToDocs.get(email).add(pageID);
            }

            if (emailToDocs.size() >= EMAIL_MAP_THRESHOLD) {
                WriteEmailMap();
            }
        }


        /*
         * insert token and related pageID into map
         * if map size is bigger than threshold, store them back to words file
         */
        private void PutIntoWordsPostingList(String token, String pageID) {
            if (!wordsToDocs.containsKey(token)) {
                Set<String> tempSet = new HashSet<String>();
                tempSet.add(pageID);
                wordsToDocs.put(token, tempSet);
            } else {
                wordsToDocs.get(token).add(pageID);
            }

            if (wordsToDocs.size() >= WORDS_MAP_THRESHOLD) {
                WriteWordsMap();
            }
        }

        /*
         * use file lock to deal with concurrency problem
         */
        private void WriteEmailMap() {
            RandomAccessFile emailFile = null;
            FileChannel emailFileChannel = null;
            FileLock emailFileLock = null;
            String emailFilePath = BuildEmailFilePath();
            String emailContent = "";
            for (String key : emailToDocs.keySet()) {
                Set<String> postingList = emailToDocs.get(key);
                emailContent = key + "\t";
                for (String pageID : postingList) {
                    emailContent += pageID + "\t";
                }
                emailContent += "\n";
            }
            if (!"".equals(emailContent)) {
                // use trylock()
                try {
                    emailFile = new RandomAccessFile(emailFilePath, "rw");
                    emailFileChannel = emailFile.getChannel();

                    while (true) {
                        try {
                            emailFileLock = emailFileChannel.tryLock();
                            break;
                        } catch (Exception e) {
                            System.out.println("Thread: " + threadID + " sleep 10ms (Other thread is using the file)");
                            sleep(10);
                        }
                    }

                    emailFile.seek(emailFile.length());
                    emailFile.write(emailContent.getBytes());
                    emailFileLock.release();
                    emailFileChannel.close();
                    emailFile.close();
                } catch (FileNotFoundException e) {
                    System.out.println("There is no EMAIL file");
                    return;
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            emailToDocs.clear();
        }

        /*
         * use file lock to deal with concurrency problem
         */
        private void WriteWordsMap() {
            for (String key : wordsToDocs.keySet()) {
                Set<String> postingList = wordsToDocs.get(key);
                String content = "";
                for (String pageID : postingList) {
                    content += pageID + "\n";
                }
                String filePath = BuildWordFilePath(key);
                RandomAccessFile file = null;
                FileChannel fileChannel = null;
                FileLock fileLock = null;

                // use trylock()
                try {
                    file = new RandomAccessFile(filePath, "rw");
                    fileChannel = file.getChannel();

                    while (true) {
                        try {
                            fileLock = fileChannel.lock();
                            break;
                        } catch (Exception e) {
                            System.out.println("Thread: " + threadID + " sleep 10ms (Other thread is using the file)");
                            sleep(10);
                        }
                    }

                    file.seek(file.length());
                    file.write(content.getBytes());
                    fileLock.release();
                    fileChannel.close();
                    file.close();
                } catch (FileNotFoundException e) {
                    System.out.println("There is no EMAIL file");
                    return;
                } catch (IOException e) {
                    System.out.println("There is IOException when write token information");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            wordsToDocs.clear();
        }

        private String BuildEmailFilePath() {
            return resultPath + File.separator + "EMAIL.ema";
        }

        // each word file has a special extension ".word"
        private String BuildWordFilePath(String word) {
            return resultPath + File.separator + word + ".word";
        }
    }

    public static void main(String[] args) {
        String dataPath = "";
        String resultPath = "";
        String stopWordsPath = "";
        int ctn = 0;
        int cjn = 0;
        int tn = 100;
        boolean numModle = true;

        for (int i = 0; i < args.length; i++) {
            if ("-d".equals(args[i])) {
                dataPath = args[i+1];
                i++;
            } else if ("-r".equals(args[i])) {
                resultPath = args[i+1];
                i++;
            } else if ("-ct".equals(args[i])) {
                // Crawler thread number
                ctn = Integer.valueOf(args[i+1]);
                i++;
            } else if ("-cj".equals(args[i])) {
                // Crawler job number
                cjn = Integer.valueOf(args[i+1]);
                i++;
            } else if ("-t".equals(args[i])) {
                // indexer thread number
                tn = Integer.valueOf(args[i+1]);
                i++;
            } else if ("-nm".equals(args[i])) {
                // whether open delete number model
                // only "off" can close delete number model
                if ("off".equals(args[i+1])) {
                    numModle = false;
                }
                i++;
            } else if ("-s".equals(args[i])) {
                stopWordsPath = args[i+1];
                i++;
            } else {
                // for the freature funtions
            }
        }

        if ("".equals(dataPath) || "".equals(resultPath) || ctn == 0 || cjn == 0) {
            System.out.println("There are something wrong for your 4 parameters:\n[Data folder path], [Result path], [Crawler thread num], [Crawler job num]");
            System.exit(1);
        }

        Indexter indexter = new Indexter(dataPath, resultPath, stopWordsPath, ctn, cjn, tn, numModle);

        indexter.Process();
        System.out.println("FINISH");
    }
}
