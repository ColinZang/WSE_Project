/**
 * Created by BINLI on 4/24/16.
 */

/**
 * listen on 8080 for http request. Show the file content once request comes in.
 */

var http = require('http');
var fileSystem = require('fs');

http.createServer(function(request, response) {
    response.writeHead(200, {'Content-Type' : 'text/plain'});
    var read_stream = fileSystem.createReadStream('data/myfile.txt');
    // the data received will be passed to writeCallBack method.
    read_stream.on('data', writeCallBack);
    read_stream.on('close', closeCallBack);

    function writeCallBack(data) {
        response.write(data);
    }
    function closeCallBack() {
        response.end();
    }
}).listen(8080);

console.log('Server started.');