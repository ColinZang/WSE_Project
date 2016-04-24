
var app = angular.module('myApp', []);

function appCtrl($scope, $http, $location, $anchorScroll) {
    $scope.haveResults = false;
    $scope.haveKnowledge = false;
    $scope.haveSpellcheck = false;
    $scope.haveSearchResults = false;
    $scope.knowledgeMore = false;
    $scope.queryWord = "";
    $scope.totalItems = 10;
    $scope.currentPage = 1;
    $scope.start = 0;

    $scope.onKeyPress = function ($event) {
        // 13 means user press the enter key.
        if ($event.keyCode == 13 && $scope.queryWord != "") {
            $scope.go(true, 1, true);
        }
    };

    $scope.getNumber = function(num) {
        var array = [];
        if ($scope.currentPage < 6) {
            for (var i = 1; i <= 10; i++)
                array.push(i);
            return array;
        } else  {
            for (var i = $scope.currentPage - 4; i <= $scope.currentPage + 5; i++)
                array.push(i);
            return array;
        }
    }

}
