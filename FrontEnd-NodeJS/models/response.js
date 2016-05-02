
exports.SuccessResponse = function(message, results) {
    this.status = "SUCCESS";
    this.message = message;
    this.results = results;
}

exports.ErrorResponse = function(message) {
    this.status = "ERROR";
    this.message = message;
    this.results = null;
}
