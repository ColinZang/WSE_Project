package Parser;

/**
 * Created by ChenChen on 4/17/16.
 */

// used for token statistic analysis
public class LDPClass {
    public boolean hasLetter;
    public boolean hasDigit;
    public boolean hasPunctuation;
    public int letterNum;
    public int digitNum;
    public int punctuationNum;

    public LDPClass(int ln, int dn, int pn) {
        if (ln > 0) {
            hasLetter = true;
            letterNum = ln;
        } else {
            hasLetter = false;
            letterNum = 0;
        }
        if (dn > 0) {
            hasDigit = true;
            digitNum = dn;
        } else {
            hasDigit = false;
            digitNum = dn;
        }
        if (pn > 0) {
            hasPunctuation = true;
            punctuationNum = pn;
        } else {
            hasPunctuation = false;
            punctuationNum = pn;
        }
    }
}
