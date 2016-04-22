package Parser;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created by ChenChen on 4/22/16.
 */
public class Parser {
    private String content;
    private Set<String> StopWordList;
    private List<String> resTokens;
    private List<String> tokensType;

    private static final int    DIFF_DIGIT_LETTER       = 5;
    private static final int    TOKEN_LENGTH_THRESHOLD  = 30;
    private static final String DELIMS_1            = "[ \n\r\t ]+";
    private static final String DELIMS_2            = "[~#%&*{}\\:<>?/|!$=+;_()\"^]+";
    private static final String IS_ENGLISH_REGEX    = "^[ \\w \\d \\s \\. \\& \\+ \\- \\, \\! \\@ \\# \\$ \\% \\^ \\* \\( \\) \\; \\\\ \\/ \\| \\< \\> \\\" \\' \\? \\= \\: \\[ \\] ]*$";

    public Parser(String str, Set<String> stopwordlist) {
        content = str;
        StopWordList = stopwordlist;
        resTokens = new ArrayList<String>();
        tokensType = new ArrayList<String>();
    }

    public void Parse() {
        // we need use split method twice
        // first use " " to split whose content
        // then use special characters to split token
        // the reason do make this design is that using other character to splic content
        // might broken a whole token, such as URL
        // be careful, in DELIMS_1 the first space " " is different with the last space " "
        String[] tokens = content.split(DELIMS_1);
        for (String token : tokens) {
            // delete useless chars at token's head and tail
            // also can filter those token which are not english word
            String tempToken = FilterTwoSidePunctuation(token);

            // stop word list
            if (StopWordList.contains(tempToken.toLowerCase())) {
                resTokens.add(tempToken);
                tokensType.add("STOPWORD");
                continue;
            }

            // check if it is URL
            if (IsValidURL(tempToken)) {
                resTokens.add(tempToken);
                tokensType.add("URL");
                continue;
            }

            // check if it is email address
            // if it is email address, store it to a special file
            // which is only for email
            // the file name is "EMAIL" and the format is:
            // email    pageID (same line, seperator: \t)
            if (IsValidEmailAddress(tempToken)) {
                resTokens.add(tempToken);
                tokensType.add("EMAIL");
                continue;
            }

            // we can use ldpClass to know the statistic value of token for letter, digit and punctuation
            LDPClass ldpClass = GetLDPClass(tempToken);

            // check if doesn't has letter
            // e.g. 12:30
            if (!ldpClass.hasLetter) {
                resTokens.add(tempToken);
                tokensType.add("NUM");
                continue;
            }

            // check if token only contains english characters, digits and punctuation
            if (!IsEnglish(tempToken)) {
                continue;
            }

            // skip the case that token only has digit and letter,
            // and the number of digit is much more than that of letter
            // or the length of token is over-long
            if (!ldpClass.hasPunctuation) {
                int diff = ldpClass.digitNum - ldpClass.letterNum;
                if (diff >= DIFF_DIGIT_LETTER) {
                    continue;
                }
                if (ldpClass.letterNum + ldpClass.digitNum > TOKEN_LENGTH_THRESHOLD) {
                    continue;
                }
            }

            // second place to use split
            // since we already make sure that the token is not email and url
            // use characters are not allowed to use in file or folder name: ~#%&*{}\:<>?/|
            // and use characters is not valid in english word such as + ;
            // cannot use ",", because number can have it like 100,000
            if (ldpClass.hasPunctuation) {
                String[] smallTokens = tempToken.split(DELIMS_2);
                if (smallTokens.length > 1) {
                    for (String smallToken : smallTokens) {
                        String modifySmallToken = FilterTwoSidePunctuation(smallToken);

                        // stop word list
                        if (StopWordList.contains(modifySmallToken.toLowerCase())) {
                            resTokens.add(modifySmallToken);
                            tokensType.add("STOPWORD");
                            continue;
                        }

                        // email
                        if (IsValidEmailAddress(modifySmallToken)) {
                            resTokens.add(modifySmallToken);
                            tokensType.add("EMAIL");
                            continue;
                        }

                        LDPClass tempLDPClass = GetLDPClass(modifySmallToken);
                        if (!tempLDPClass.hasLetter) {
                            resTokens.add(modifySmallToken);
                            tokensType.add("NUM");
                            continue;
                        }

                        if (!tempLDPClass.hasPunctuation) {
                            int tempDiff = tempLDPClass.digitNum - tempLDPClass.letterNum;
                            if (tempDiff >= DIFF_DIGIT_LETTER) {
                                continue;
                            }
                            if (tempLDPClass.letterNum + tempLDPClass.digitNum > TOKEN_LENGTH_THRESHOLD) {
                                continue;
                            }
                        }

                        resTokens.add(modifySmallToken);
                        tokensType.add("WORD");
                    }
                }
            } else {
                // if second delims doesn't splice the token
                resTokens.add(tempToken);
                tokensType.add("WORD");
            }

        }
    }

    /*
     * filter punctuations at token's head and tail
     * e.g. tomorrow. -> tomorrow       "aaa" -> aaa
     * if token only has punctuation or other language character, return ""
     */
    private String FilterTwoSidePunctuation(String token) {
        int begin = 0;
        int end = token.length() - 1;
        while (begin < token.length()) {
            if (!Character.isLetterOrDigit(token.charAt(begin))) {
                begin++;
            } else {
                break;
            }
        }
        while (end >= 0) {
            if (!Character.isLetterOrDigit(token.charAt(end))) {
                end--;
            } else {
                break;
            }
        }
        if (begin <= end) {
            return token.substring(begin, end+1);
        } else {
            return "";
        }
    }

    /*
     * Check whether string is valid URL
     */
    private boolean IsValidURL(String token) {
        boolean result = true;
        try {
            URL url = new URL(token);
        } catch (MalformedURLException e) {
            result = false;
        }
        return result;
    }

    /*
     * Check whether string is a email address
     */
    private boolean IsValidEmailAddress(String token) {
        boolean result = true;
        try {
            InternetAddress emailAddr = new InternetAddress(token);
            emailAddr.validate();
        } catch (AddressException ex) {
            result = false;
        } catch (Exception e) {
            result = false;
        }
        return result;
    }

    /*
     * do statistic analysis for token
     */
    private LDPClass GetLDPClass(String token) {
        int ln = 0;
        int dn = 0;
        int pn = 0;
        for (int i = 0; i < token.length(); i++) {
            if (Character.isDigit(token.charAt(i))) {
                dn++;
            } else if (Character.isLetter(token.charAt(i))) {
                ln++;
            } else {
                pn++;
            }
        }
        return new LDPClass(ln, dn, pn);
    }

    /*
     * use regular expression to check whether text only contains letter, digit and punctuation
     * it can filter other language and too special characters
     */
    private boolean IsEnglish(String text) {
        if (text == null) {
            return false;
        }
        return text.matches(IS_ENGLISH_REGEX);
    }

    /*
     * return list contain all token's original format
     */
    public List<String> GetResTokens() {
        return resTokens;
    }

    /*
     * return list contain all token's type
     */
    public List<String> GetTokensType() {
        return tokensType;
    }

    /*
     * filter all nums from results
     */
    public List<String> GetTokensWithoutNum() {
        List<String> res = new ArrayList<String>();
        for (int i = 0; i < resTokens.size(); i++) {
            if (tokensType.get(i).equals("NUM")) {
                continue;
            } else {
                res.add(resTokens.get(i));
            }
        }
        return res;
    }

}
