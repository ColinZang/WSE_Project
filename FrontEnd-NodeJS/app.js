var log4js = require('log4js');
ar logger = log4js.getLogger('Application');
GLOBAL.systemLogger = logger;
logger.info("Server starting");

var express = require('express');
var app = express();
app.use(express.bodyParser());
app.use(app.router);
app.use(express.static(__dirname));

var api = require('./routes/api');

app.get('/search', api.search);
app.get('/cached', api.cached);

var port = process.env.PORT || 8000;
app.listen(port);

logger.info("Server started, running at http://localhost:" + port);
