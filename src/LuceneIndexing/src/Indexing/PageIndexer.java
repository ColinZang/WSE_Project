package Indexing;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;

public class PageIndexer implements Runnable {
    private String dirPath;
    private String indexPath;
    private int threadNumber;
    private static IndexWriter writer;


    public static void createWriter(String indexPath) throws IOException {
        Directory dir = FSDirectory.open(Paths.get(indexPath));
        Analyzer analyzer = new StandardAnalyzer();
        IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
        iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        writer = new IndexWriter(dir, iwc);
    }

    public static void closeWriter() throws IOException{
        writer.close();
    }

    public PageIndexer(String dirPath, String indexPath, int threadNumber){
        this.dirPath = dirPath;
        this.indexPath = indexPath;
        this.threadNumber = threadNumber;
    }

    private void indexDocs(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    try {
                        indexDoc( file, attrs.lastModifiedTime().toMillis());
                    } catch (IOException ignore) {
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            indexDoc(path, Files.getLastModifiedTime(path).toMillis());
        }
    }

    /**
     * get file extension
     * @param file
     * @return
     */
    static String getExtension (Path file) {
        String extension = "";
        String fileName = file.toString();
        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            extension = fileName.substring(i + 1);
        }
        return extension;
    }

    /** Indexes a single document */
    private void indexDoc(Path file, long lastModified) throws IOException {
//        String fileExtension = getExtension(file);

        // Only index html or htm files. Sometimes system will generate some files we don't want to index.
//        if (fileExtension.equals("html") || fileExtension.equals("htm")) {
            try (InputStream fileStream = Files.newInputStream(file)) {
                //Indexing specific pages
                ParsePage parsePage = new ParsePage();
                String[] parsedInfo = parsePage.getInfos(file.toString());
                Document doc = new Document();

                //Indexing html file using JTidy package.
//                JTidyParser jTidyParser = new JTidyParser();
//                String[] parsedInfo = jTidyParser.parser(file.toString());
//                Document doc = new Document();
//                if (parsedInfo[0] == "") {
//                    if (parsedInfo[2] != "") {
//                        parsedInfo[0] = parsedInfo[2];
//                    } else if (parsedInfo[3] != "") {
//                        parsedInfo[0] = parsedInfo[3];
//                    } else if (parsedInfo[4] != "") {
//                        parsedInfo[0] = parsedInfo[4];
//                    }
//                }
                doc.add(new StringField("title", parsedInfo[0], Field.Store.YES));
                Field pathField = new StringField("path", file.toString(), Field.Store.YES);
                doc.add(pathField);
                doc.add(new LongField("modified", lastModified, Field.Store.NO));
                InputStream stream = new ByteArrayInputStream(parsedInfo[1].getBytes(StandardCharsets.UTF_8));
                doc.add(new TextField("contents", new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))));
                System.out.println("thread" + threadNumber + " " + file.toString());
                if (writer.getConfig().getOpenMode() == IndexWriterConfig.OpenMode.CREATE) {
                    System.out.println("adding " + file);
                    writer.addDocument(doc);
                } else {
                    System.out.println("updating " + file);
                    writer.updateDocument(new Term("path", file.toString()), doc);
                }
            }
//        }
    }


    /** Index all text files under a directory. */
    private void indexStart(String indexPath, String dirPath) {
        String usage = "java org.apache.lucene.demo.IndexFiles"
                + " [-index INDEX_PATH] [-docs DOCS_PATH] [-update]\n\n"
                + "This indexes the documents in DOCS_PATH, creating a Lucene index"
                + "in INDEX_PATH that can be searched with SearchFiles";

        if (dirPath == null) {
            System.err.println("Usage: " + usage);
            System.exit(1);
        }

        final Path docDir = Paths.get(dirPath);
        if (!Files.isReadable(docDir)) {
            System.out.println("Document directory '" +docDir.toAbsolutePath()+ "' does not exist or is not readable, please check the path");
            System.exit(1);
        }
        Date start = new Date();
        try {
            System.out.println("Indexing to directory '" + indexPath + "'...");
            indexDocs(docDir);
            Date end = new Date();
            System.out.println(end.getTime() - start.getTime() + " total milliseconds");


        } catch (IOException e) {
            System.out.println(" caught a " + e.getClass() +
                    "\n with message: " + e.getMessage());
        }
    }


    @Override
    public void run(){
        indexStart(indexPath, dirPath);
    }
}
