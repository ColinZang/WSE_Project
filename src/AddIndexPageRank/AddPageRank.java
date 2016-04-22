package AddIndexPageRank;

import java.io.*;

public class AddPageRank {

  public String getPageRank(String rankFile, String ID) {
    try {
      BufferedReader rankReader = new BufferedReader(new FileReader(rankFile));
      String line = "";
      while(!line.equals(ID)) {
        line = rankReader.readLine();
      }
      return rankReader.readLine();
    }catch (IOException e) {}
    return "";
  }

  public int getPageID(String originalID) {
    int firstDash = originalID.indexOf('_');
    int secondDash = originalID.indexOf('_', firstDash + 1);
    int id = Integer.parseInt(originalID.substring(firstDash + 1, secondDash));
    return id % 1000;
  }

  public File createTargetFile (String targetPath, String fileName) {
    File file = new File(targetPath + "/" + fileName);
    try {
      new File(targetPath).mkdirs();
      file.createNewFile();
      return file;
    }catch (IOException e){}
    return null;
  }

  public void addRankToID(String indexPath, String rankPath, String targetPath) {
    try {
      if (!indexPath.endsWith("/")) {
        indexPath += "/";
      }
      if (!rankPath.endsWith("/")) {
        rankPath += "/";
      }
      if (!targetPath.endsWith("/")) {
        targetPath += "/";
      }
      File folder = new File(indexPath);
      File[] listOfFiles = folder.listFiles();
      for (File file : listOfFiles) {
        BufferedReader indexReader = new BufferedReader(new FileReader(file));
        File targetFile = createTargetFile(targetPath, file.getName());
        BufferedWriter targetWriter = new BufferedWriter(new FileWriter(targetFile));
        String line;
        while((line = indexReader.readLine()) != null) {
          String originalID = line;
          int pageID = getPageID(line);
          String pageRank = getPageRank(rankPath + "pageRank_" + pageID + ".rank", originalID);
          targetWriter.write(originalID + "\n");
          targetWriter.write(pageRank + "\n");
        }
        targetWriter.close();
      }
    } catch (IOException e){}
  }

  public static void main(String[] args) {
    AddPageRank addPageRank = new AddPageRank();
    addPageRank.addRankToID(args[0], args[1], args[2]);
  }
}
