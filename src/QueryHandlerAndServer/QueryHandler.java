package QueryHandlerAndServer;

import Retriever.*;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

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
                    } catch (NumberFormatException e) {
                        // Ignored.
                    }
                } else if (key.equals("pageresults")) {
                    try {
                        _pageResults = Integer.parseInt(val);
                    } catch (NumberFormatException e) {
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

        String query = qArgs._query.replace("%20", " ").replace("+", " ");
        System.out.println("Before passing the retriever, query is: " + query);

        /**
         * Get all pages for query(Retriever, ProcessBuilder).
         * Construct HTML output with given pages(List).
         * Renders it to frontend
         * NOTE: _query is user input query.
         */
        List<Page> scoredPages = Retriever.run(query);
        if (scoredPages.isEmpty()) {
            outClientMsg(exchange, Retriever.getWarning());
        }

        if (URIPath.equals("/search")) {
            StringBuilder res = new StringBuilder();
            getJSONPages(scoredPages, res);
            outClientMsg(exchange, res.toString());
            System.out.println("Finished query: " + qArgs._query);
        }

    }

    /**
     * Render HTML output to client.
     */
    private void getJSONPages(final List<Page> pages, StringBuilder response) {
        response.append("{\n\"results\":[ \n");
        for (Page page : pages) {
            response.append(page.toJSONResult());
            response.append(",\n");
        }
        if (pages.size() != 0) {
            response.deleteCharAt(response.length() - 2);
        }
        response.append("]").append("\n}");
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

