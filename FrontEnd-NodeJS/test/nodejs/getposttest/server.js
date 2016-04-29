/**
 * Created by BINLI on 4/28/16.
 */

var log4js = require("log4js");
var logger = log4js.getLogger("SERVER");
var express = require("express");
var app = express();

app.use(express.static(__dirname));
logger.info("Directory name is: " + __dirname);
app.get('/index.html', function(req, res) {

    res.sendfile(__dirname + "/index.html");
    // console.log("index.html is rendered to client!");
});

app.get('/process_text', function(req, res) {
    var response = {
        firstname : req.query.first_name,
        lastname : req.query.last_name
    };

    res.send(JSON.stringify(response));
    logger.info(response);

});

var PORT = process.env.port || 8080;
app.listen(PORT);

logger.info("Server is listening on http://localhost:" + PORT);

