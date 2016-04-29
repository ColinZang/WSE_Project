package PageRank;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Scanner;

/**
 * Created by Wenzhao on 4/25/16.
 */
public class RemoveDuplicates {
    public static void main(String[] args) {
        HashSet<URI> set = new HashSet<URI>();
        String filePath = args[0];
        if (!filePath.endsWith(File.separator)) {
            filePath += File.separator;
        }
        try {
            File dir = new File(filePath);
            File[] jobDirs = dir.listFiles();
            int count = 0;
            for (File job: jobDirs) {
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
                        // start parsing
                        String thisUrl = null;
                        try {
                            // ignore #ThisURL# tag
                            readFile.nextLine();
                            thisUrl = readFile.nextLine();
                        } catch (RuntimeException e) {
                            System.out.println("page " + page.getName() + " not successful, ignore");
                            Files.deleteIfExists(page.toPath());
                            System.out.println("deleted page " + page.getName() + " " + thisUrl);
                            continue;
                        }
                        readFile.close();
                        URI url = null;
                        try {
                            url = new URI(thisUrl);
                        } catch (URISyntaxException e) {
                            System.out.println("page " + page.getName() + " url invalid, ignore");
                            Files.deleteIfExists(page.toPath());
                            System.out.println("deleted page " + page.getName() + " " + thisUrl);
                            continue;
                        }
                        if (set.contains(url)) {
                            Files.deleteIfExists(page.toPath());
                            System.out.println("deleted page " + page.getName() + " " + thisUrl);
                        }
                        else {
                            set.add(url);
                        }
                        count++;
                        if (count % 10000 == 0) {
                            System.out.println("processed " + count + " pages");
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("finished, " + set.size());
    }
}
