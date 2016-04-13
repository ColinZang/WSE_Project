package controllers;

import play.mvc.*;

import views.html.*;

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
        return ok("Got request: " + request() + "!");
    }

//    public Result show(String page) {
//        String content = Page.getContentOf(page);
//        response().setContentType("text/html");
//        return ok(content);
//    }

    public Result task() {
        return TODO;
    }

    public Result newTask() {
        return TODO;
    }

}
