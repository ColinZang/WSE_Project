package Retriever;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Created by BINLI on 4/27/16.
 */
public class QueryHandler implements HttpHandler {

    public static class QueryArgs {
        public String _query = "";

        private int _max = 20;

        private int _pageResults = 10;

        // What is the format of URIQuery? query=machine&max=20&pageResults=10 ???
        public QueryArgs(String URIQuery) {
            String[] params = URIQuery.split("&");
            for (String param : params) {
                String[] pair = param.split("=", 2);
                if (pair.length < 2) {
                    continue;
                }
                String key = pair[0].toLowerCase();
                String val = pair[1];
                if (key.equals("query")) {
                    _query = val;
                } else if (key.equals("max")) {
                    try {
                        _max = Integer.parseInt(val);
                    } catch (NumberFormatException) {
                        // Ignored.
                    }
                } else if (key.equals("pageresults")) {
                    try {
                        _pageResults = Integer.parseInt(val);
                    } catch (NumberFormatException) {
                        // Ignored.
                    }
                }
            }
        }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String requestType = exchange.getRequestMethod();
        // Only handles GET request.
        if (!requestType.equalsIgnoreCase("GET")) {
            return;
        }

        // Print out client request headers in Map format.
        Headers reqHeaders = exchange.getRequestHeaders();
        System.out.println("The request is: ");
        for (String name : reqHeaders.keySet()) {
            System.out.print(name + ":" + reqHeaders.get(name) + ";");
        }
        System.out.println();

        // Check the coming request to see if it is valid.
        // URIQuery is "field1=value1&field2=value2&field3=value3..."
        //
        String URIQuery = exchange.getRequestURI().getQuery();
        String URIPath = exchange.getRequestURI().getPath();
        if (URIPath == null || URIQuery == null) {
            outClientMsg(exchange, "URI is not correct");
        }
        if (!URIPath.equals("/search")) {
            outClientMsg(exchange, "Only \"/search\" is handled here.");
        }
        System.out.println("Query: " + URIQuery);

        QueryArgs qArgs = new QueryArgs(URIQuery);

        // NOTE here _query is not decoded. so "machine learning" could be
        // "machine+learning" or "machine%20learning"
        if (qArgs._query.isEmpty()) {
            outClientMsg(exchange, "Query is null.");
        }

        /**
         * TODO
         * Get all pages for query(Retriever, ProcessBuilder).
         * Construct HTML output with given pages(List).
         * Renders it to frontend.
         */
        List<Page> scoredPages = Retriever.run(qArgs._query, qArgs._pageResults,
                qArgs._max, indexPath, pagePath, stopwordspath);



    }

    private void outClientMsg(HttpExchange exchange, String msg) throws IOException {
        Headers resHeaders = exchange.getResponseHeaders();
        resHeaders.set("Content-Type", "text/plain");
        exchange.sendResponseHeaders(200, 0);
        OutputStream resBody = exchange.getResponseBody();
        resBody.write(msg.getBytes());
        resBody.close();
    }


}
