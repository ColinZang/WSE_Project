package Retriever;

import java.util.List;

/**
 * Created by Wenzhao on 4/22/16.
 */
public class Sequence {
    private int left;
    private int right;
    private String token;

    public Sequence(List<String> queryWords, int left, int right) {
        this.left = left;
        this.right = right;
        token = queryWords.get(left);
        for (int pos = left + 1; pos <= right; pos++) {
            token += " " + queryWords.get(pos);
        }
    }

    public String getToken() {
        return token;
    }

    public int getLeft() {
        return left;
    }

    public int getRight() {
        return right;
    }

    @Override
    public boolean equals(Object obj) {
        Sequence seq = (Sequence)obj;
        return this.getToken().equals(seq.getToken());
    }

    @Override
    public int hashCode() {
        return token.hashCode();
    }
}
