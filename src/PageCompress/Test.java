package PageCompress;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Created by ChenChen on 4/13/16.
 */
public class Test {
    /*
     * get file extension
     */
    private static String getExtension(String filename) {
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
     * read pages from file
     */
    private static String readPage(File file) {
        String wholePage = "";
        try {
            InputStream stream = new FileInputStream(file);
            byte[] tempbytes = new byte[1000];
            int byteread = 0;
            while ((byteread = stream.read(tempbytes)) != -1) {
                String newContent = new String(tempbytes, 0, byteread);
                wholePage += newContent;
            }
            stream.close();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            return wholePage;
        }
    }

    private static void SavePageFile(PageFile pf, File dir) {
        String path = dir.getAbsolutePath() + File.separator + pf.getPageID();
        File file = new File(path);
        try (FileOutputStream fos = new FileOutputStream(file)){
            if (!file.exists()) {
                file.createNewFile();
            }
            String fileContent = "#TITLE#\n" + pf.getTitle() + "\n\n" + "#SUBURLS#\n";
            for (String url : pf.getSubURLs()) {
                fileContent += url + "\n";
            }
            fileContent += "\n#CONTENT#\n";
            fileContent += pf.getContent();

            byte[] contentInBytes = fileContent.getBytes();

            fos.write(contentInBytes);
            fos.flush();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Save page: " + pf.getPageID());
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("You should give pathes for data and result folder");
            System.exit(1);
        }

        String dataPath = args[0];
        String resultPath = args[1];

        File dataDir = null;
        File resultDir = null;
        try {
            dataDir = new File(dataPath);
            resultDir = new File(resultPath);
        } catch (Exception e) {
            System.out.println("data path or result path is not directory!");
            System.exit(1);
        }

        File[] files = dataDir.listFiles();
        for (File file : files) {
            String name = file.getName();
            String ext = getExtension(name);
            if (!ext.equals("html")) {
                System.out.println("File \"" + name + "\" is not a web page");
                continue;
            }
            String nameWoExt = name.substring(0, name.length() - 5);
            String pageHTML = readPage(file);
            PageCompress pc = new PageCompress(nameWoExt, pageHTML);
            try {
                PageFile pf = pc.GetPageFile();
                SavePageFile(pf, resultDir);
            } catch (Exception e) {
                System.out.println(e);
            }
        }

    }
}
