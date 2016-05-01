/**
 * Created by BINLI on 4/24/16.
 */

/**
 * Demonstrate the non-blocking nature of node.js
 * If you go to http://localhost:8080/wait and immediately switch to http://localhost:8080,
 * the latter will not hang up, since the previous call started up a child process that runs
 * in background.
 */

var http = require('http');
var url = require('url');
var cp = require('child_process');

function onRequest(request, response) {
    var name = url.parse(request.url).pathname;
    if (name == '/wait') {
        // This function registers a callback function.
        // Start a new node (thread) to run blocking.js. Once it is finished, call myCallBack.
        cp.exec('node blocking.js', myCallBack);
    } else {
        response.writeHead(200, {'Content-Type': 'text/plain'});
        response.write('hello! \n');
        response.end();
    }
    console.log("New Connection!");
    function myCallBack() {
        response.writeHead(200, {'Content-Type': 'text/plain'});
        response.write("Thanks for waiting! \n");
        response.end();
    }
}

http.createServer(onRequest).listen(8080);
console.log("Server started.");
