package Indexing;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class IndexFiles {

    static String[] getDirectories(File file) {
        String[] directories = file.list(new FilenameFilter() {
            @Override
            public boolean accept(File current, String name) {
                return new File(current, name).isDirectory();
            }
        });
        return directories;
    }

    public  static void main(String[] args){
        File file = new File(args[0]);
        String indexDirPath = args[1];
        String[] directoryList = getDirectories(file);
        List<Thread> threadList = new ArrayList<Thread>();
        try {
            PageIndexer.createWriter(indexDirPath);
            for (int threadNumber = 1; threadNumber <= directoryList.length; threadNumber++) {
                String subPageDir = args[0] + directoryList[threadNumber - 1];
                Thread thread = new Thread(new PageIndexer(subPageDir, indexDirPath, threadNumber));
                threadList.add(thread);
                thread.start();
                System.out.println(threadNumber + "start");
            }
            for (Thread thread : threadList) {
                try {
                    thread.join();
                } catch (InterruptedException e) {

                }
            }
            PageIndexer.closeWriter();
        }catch (IOException e) {
            System.out.println(" caught a " + e.getClass() +
                    "\n with message: " + e.getMessage());
        }
    }
}