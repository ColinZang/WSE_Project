package Indexing;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.w3c.tidy.Tidy;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class JTidyParser {
    static String[] docInfo = new String[5];

    /*
    return file title, body, h1, h2, h3 as String Array.
     */
    public String[] getDocInfo(InputStream is) {
        Tidy tidy = new Tidy();
        tidy.setQuiet(true);
        tidy.setShowWarnings(false);
        org.w3c.dom.Document root = tidy.parseDOM(is, null);
        Element rawDoc = root.getDocumentElement();

        String title = getTitle(rawDoc);
        docInfo[0] = title;
        String body = getBody(rawDoc);
        docInfo[1] = body;
        String h1 = getH1(rawDoc);
        docInfo[2] = h1;
        String h2 = getH2(rawDoc);
        docInfo[3] = h2;
        String h3 = getH3(rawDoc);
        docInfo[4] = h3;
        return docInfo;
    }

    /**
     * Gets the title text of the HTML document.
     *
     * @rawDoc the DOM Element to extract title Node from
     * @return the title text
     */
    protected String getTitle(Element rawDoc) {
        if (rawDoc == null) {
            return null;
        }

        String title = "";

        NodeList children = rawDoc.getElementsByTagName("title");
        if (children.getLength() > 0) {
            Element titleElement = ((Element) children.item(0));
            Text text = (Text) titleElement.getFirstChild();
            if (text != null) {
                title = text.getData();
            }
        }
        return title;
    }

    /**
     * Gets the body text of the HTML document.
     *
     * @rawDoc the DOM Element to extract body Node from
     * @return the body text
     */
    protected String getBody(Element rawDoc) {
        if (rawDoc == null) {
            return null;
        }

        String body = "";
        NodeList children = rawDoc.getElementsByTagName("body");
        if (children.getLength() > 0) {
            body = getText(children.item(0));
        }
        return body;
    }

    /**
     * Extracts text from the DOM node.
     *
     * @param node a DOM node
     * @return the text value of the node
     */
    protected String getText(Node node) {
        NodeList children = node.getChildNodes();
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            switch (child.getNodeType()) {
                case Node.ELEMENT_NODE:
                    sb.append(getText(child));
                    sb.append(" ");
                    break;
                case Node.TEXT_NODE:
                    sb.append(((Text) child).getData());
                    break;
            }
        }
        return sb.toString();
    }

    public String getH1(Element rawDoc) {
        if (rawDoc == null) {
            return null;
        }

        String h1 = "";

        NodeList children = rawDoc.getElementsByTagName("h1");
        if (children.getLength() > 0) {
            h1 = getText(children.item(0));
        }
        return h1;
    }

    public String getH2(Element rawDoc) {
        if (rawDoc == null) {
            return null;
        }

        String h2 = "";

        NodeList children = rawDoc.getElementsByTagName("h2");
        if (children.getLength() > 0) {
            h2 = getText(children.item(0));
        }
        return h2;
    }

    public String getH3(Element rawDoc) {
        if (rawDoc == null) {
            return null;
        }

        String h3 = "";

        NodeList children = rawDoc.getElementsByTagName("h3");
        if (children.getLength() > 0) {
            h3 = getText(children.item(0));
        }
        return h3;
    }

    public String[] parser(String path) throws IOException{
        JTidyParser handler = new JTidyParser();
        return handler.getDocInfo(
                new FileInputStream(new File(path)));
    }
}

