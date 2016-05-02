
var app = angular.module('myApp', []);

function appCtrl($scope, $http, $location, $anchorScroll) {
    $scope.haveResults = false;
    // $scope.haveKnowledge = false;
    $scope.haveSearchResults = false;
    // $scope.knowledgeMore = false;
    $scope.queryWord = "";
    $scope.totalItems = 5;
    // Will change to the argu in go().
    $scope.currentPage = 1;
    $scope.start = 0;

    $scope.onKeyPress = function ($event) {
        // 13 means user press the enter key.
        if ($event.keyCode == 13 && $scope.queryWord != "") {
            $scope.go(1);
        }
    };

    $scope.getNumber = function(num) {
        var array = [];
        if ($scope.currentPage < 4) {
            for (var i = 1; i <= 5; i++)
                array.push(i);
            return array;
        } else  {
            for (var i = $scope.currentPage - 2; i <= $scope.currentPage + 2; i++)
                array.push(i);
            return array;
        }
    };

    /**
     * Send http request and let server handle the search. Returned results are documents that matched.
     * List the documents
     * @param spellcheck
     * @param pageNum
     * @param know
     */
    $scope.go = function(pageNum) {
        // This is the core part. Non-encoded query
        // pageNum is the page user wants to go to. Suppose returned docs have 10 pages. if pageNum is 1, means
        // user wants to go to the first page.
        // This request is encoded in api.js using encodeURIComponents.
        $http.get('/search?query=' + $scope.queryWord + '&max=50'
        + '&pageResults=10&page=' + pageNum)
            .success(function(data) {
                $scope.currentPage = pageNum;
                $scope.haveResults = true;
                // THE DOCS HERE SHOULD ONLY RETURNS THE RESULTS ON THE pageNum.
                var docs = data.results;
                var docus = [];
                if (docs.length != 1) {
                    docs.forEach(function (ele, idx, arr) {
                        var docu = {};
                        docu.url = ele.url;
                        docu.title = decodeURIComponent(ele.title).replace(/\+/g, ' ');
                        docu.preview = decodeURIComponent(ele.preview).replace(/\+/g, ' ');
                        // docu.filePath = ele.filePath;
                        docus.push(docu);
                    });
                }
                
                // Pagination.
                if (pageNum * 10 <= docus.length) {
                    $scope.documents = docus.slice((pageNum - 1) * 10, (pageNum - 1) * 10 + 10);
                } else {
                    $scope.documents = docus.slice((pageNum - 1) * 10, docus.length);
                }
                // $scope.documents = docus;

                $scope.haveSearchResults = docus.length != 0;
                

            });
    };

    $scope.showMore = function () {
        $scope.knowledgeMore = false;
    };

    $scope.showLess = function () {
        $scope.knowledgeMore = true;
    };

    $scope.pageChanged = function (pageNum) {
        if (pageNum == 1)
            $scope.go(1);
        else {
            $anchorScroll();
            $scope.go(pageNum);
        }
    };
}