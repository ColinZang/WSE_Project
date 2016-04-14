package controllers;

import play.mvc.*;

import views.html.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.Buffer;
import java.util.ArrayList;
import java.util.List;

/**
 * This controller contains an action to handle HTTP requests
 * to the application's home page.
 */
public class HomeController extends Controller {

    /**
     * An action that renders an HTML page with a welcome message.
     * The configuration in the <code>routes</code> file means that
     * this method will be called when the application receives a
     * <code>GET</code> request with a path of <code>/</code>.
     */

    public Result index() {
        return redirect("/task/:query");
    }

//    public Result show(String page) {
//        String content = Page.getContentOf(page);
//        response().setContentType("text/html");
//        return ok(content);
//    }

    public Result task(String query) {
        String content = "";
        String home = System.getProperty("user.home");
        System.out.println(home);
        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("-cp");
        StringBuilder sb = new StringBuilder();
        sb.append(home + "/IdeaProjects/play-java-intro/PS1_BinLi");
        try {
            File dir = new File(home + "/IdeaProjects/play-java-intro/PS1_BinLi/lib/");
            for (File f : dir.listFiles()) {
                if (f.isFile()) {
                    sb.append(":").append(f.getCanonicalPath());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        command.add(sb.toString());
        command.add("src.MySearchFiles");
        command.add("-index");
        command.add("index");
        command.add("-query");
        command.add(query);
        ProcessBuilder pb = new ProcessBuilder(command);

//        ProcessBuilder pb = new ProcessBuilder("java",
//                "-cp",
//                "\'../../PS1_BinLi:../../PS1_BinLi/lib/lucene-core-5.4.1.jar:../../PS1_BinLi/lib/*\'",
//                "src.MySearchFiles",
//                "-index",
//                "index",
//                "-query",
//                query);
        pb.redirectErrorStream(true);
        BufferedReader br;
        try {
            Process p = pb.start();
            String newLine;
            br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            while ((newLine = br.readLine()) != null) {
                content += newLine + "\n";
            }
            int exitVal = p.waitFor();
            br.close();
        } catch (IOException io) {
            io.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            return ok(content);

        }


    }

    public Result newTask() {
        return TODO;
    }

}

