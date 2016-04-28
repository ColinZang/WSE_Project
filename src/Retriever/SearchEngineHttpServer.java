package Retriever;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Created by BINLI on 4/27/16.
 */
public class SearchEngineHttpServer {
    private static int PORT = 23456;

    public static void main (String[] args) throws IOException, ClassNotFoundException {

        QueryHandler handler = new QueryHandler();
        // Set up parameters for searching pages for query string.
        Retriever.prepare();
        // Start http server to serve incoming request.
        InetSocketAddress address = new InetSocketAddress(SearchEngineHttpServer.PORT);
        HttpServer server = HttpServer.create(address, -1);
        server.createContext("/", handler);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("Listening or port: " + Integer.toString(SearchEngineHttpServer.PORT));
    }
}
