package Retriever;

import java.util.List;

/**
 * Created by Wenzhao on 4/27/16.
 */
public class RetrieverAPI {
    public static void main(String[] args) {

        // enter busy waiting
        for (;;) {
            String query = null;

            // get query from user, parse to original format

            List<Page> scoredPages = Retriever.run(query);
            if (scoredPages.size() == 0) {
                String warning = Retriever.getWarning();
                // display this warning to user
            }
            else {
                // return pages to user
            }
        }
    }
}
