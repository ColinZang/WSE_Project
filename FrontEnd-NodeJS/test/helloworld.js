/**
 * Created by BINLI on 4/23/16.
 */

var http = require('http');
// the annoymenous function is a cb function. Once there is a connection, this
// function will be called.
http.createServer(function(request, response) {
    response.writeHead(200, {'Content-Type' : 'text/plain'});
    response.end('hello world. \n');
}).listen(8080);

console.log("Server started.");
