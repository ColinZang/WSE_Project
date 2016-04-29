
var log4js = require('log4js');
var logger = log4js.getLogger('API');
var responses = require('./models/response');
var http = require('http');
var url = require('url');
var path = require('path');
var fs = require('fs');

/**
 * Handles search request (WHAT DOES THE REQUEST LOOK LIKE? it that '/search' in app.js),
 * encode search query and pass to getJSON method to create http request and display results.
 * @param req
 * @param res
 */
// if use exports, then the internal search function can be called outside .
// req and res here is event. request is an instance of http.IncomingMessage
// and response is an instance of http.ServerResponse
exports.search = function (req, res) {
    // 'query=string'

    // node> require('url').parse('/status?name=ryan', true)
    // { href: '/status?name=ryan'
    //    , search: '?name=ryan'
    //    , query: { name: 'ryan' }
    //, pathname: '/status'
    // }

    var queryObject = url.parse(req.url,true).query;
    // When the following line is used, can not call res.statusCode.
    // res.writeHead(200, {"Content-Type": "text/plain"});
    var params = "";
    // element is the name of the key.
    // key is just a numerical value for the array
    // _array is the array of all the keys
    /*
    GET ALL PARAMS FOR SEARCHING.
     */
    Object.keys(queryObject).forEach(function (ele, idx, arr) {
        if (idx != 0)
            params = params.concat("&");
        params = params.concat(encodeURIComponent(ele)).concat("=").concat(encodeURIComponent(queryObject[ele]));
    });
    var options = {
        host: 'localhost',
        //since we are listening on a custom port, we need to specify it by hand
        port: 23456,
        path: '/search?'.concat(params),
        method: 'GET',
        headers: {
            accept: 'application/json'
        }
    };
    // sending request to: localhost:23456/search?query=asdfasdf&max=100&pageResults=10&page=1
    logger.info("sending request to: " + options.host + ":" + options.port.toString() + options.path);
    getJSON(options, function (statusCode, result) {

        res.statusCode = statusCode;
        res.send(result);
    });
};

/*  See saved link. How-to-make-a-http-request
 *  Make http request to server on port 23456 based on options and display JSON results.
 */
getJSON = function (options, onResult) {

    var res = http.request(options,function(res){
        var str = '';
        res.on('data', function (chunk) {
            str += chunk;
        });

        res.on('end', function () {
            // preserve newlines, etc - use valid JSON
            str = str.replace(/\\n/g, "\\n")
                .replace(/\\'/g, "\\'")
                .replace(/\\"/g, '\\"')
                .replace(/\\&/g, "\\&")
                .replace(/\\r/g, "\\r")
                .replace(/\\t/g, "\\t")
                .replace(/\\b/g, "\\b")
                .replace(/\\f/g, "\\f");
// remove non-printable and other non-valid JSON chars
            str = str.replace(/[\u0000-\u0019]+/g,"");
            // Create object from JSON string.
            var obj = JSON.parse(str);
            onResult(res.statusCode, obj);
        });
    });
    res.end();
};

