/**
 * Created by BINLI on 4/24/16.
 */
var express = require('express');
var app = express();

app.get('/', function(req, res) {
    res.send("Hello World!");
});

var server = app.listen(8081, function() {
    var host = server.address().address;
    var port = server.address().port;
    console.log("app listenning at http://%s:%s", host, port);
});

console.log("server started.");