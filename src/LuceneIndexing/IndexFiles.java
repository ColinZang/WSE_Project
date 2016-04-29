package LuceneIndexing;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class IndexFiles {

  static List<String> total = new ArrayList<>();

  static String[] getDirectories(File file) {
    String[] directories = file.list(new FilenameFilter() {
      @Override
      public boolean accept(File current, String name) {
        return new File(current, name).isDirectory();
      }
    });
    return directories;
  }

  static List<String> totalDirectories(String[] directories, File file) {
    for (String dir: directories) {
      String dirPath = file.toString() + File.separator + dir;
      List<String> dirList = Arrays.asList(getDirectories(new File(dirPath)));
      for (String directory : dirList) {
        total.add(dirPath + File.separator + directory);
      }
    }
    return total;
  }

  public  static void main(String[] args){
    File file = new File(args[0]);
    String indexDirPath = args[1];
    String[] directoryList = getDirectories(file);
    totalDirectories(directoryList, file);
    try {
      PageIndexer.createWriter(indexDirPath);
      ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1000);
      for (String directory : total) {
        executor.execute(new PageIndexer(directory,indexDirPath, 0));
      }
      executor.shutdown();
      try {
        while (!executor.awaitTermination(60000, TimeUnit.SECONDS)) {
          System.err.println("Threads didn't finish in 60000 seconds!");
        }
      }catch (Exception e) {}

      PageIndexer.closeWriter();
    }catch (IOException e) {
      System.out.println(" caught a " + e.getClass() +
              "\n with message: " + e.getMessage());
    }
  }
}