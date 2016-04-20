package Indexing;


import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class ParsePage {
    private static String[] docInfo = new String[2];

    public static String[] getInfos(String pagePath) {
        try {
            BufferedReader reader = new BufferedReader((new FileReader(pagePath)));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.equals("#Title#")) {
                    String title = reader.readLine();
                    docInfo[0] = title;
                } else if (line.equals("#Content#")) {
                    String content = "";
                    while ((line = reader.readLine()) != null) {
                        content += line + " ";
                    }
                    docInfo[1] = content;
                }
            }
        }catch (IOException e) {

        }
        return docInfo;
    }

    public static void main(String[] args) {
        getInfos(args[0]);
    }
}

