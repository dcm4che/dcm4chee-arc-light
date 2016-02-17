"use strict";

myApp.controller('ArchiveCtrl', function ($scope, $http) {
    $scope.logoutUrl = myApp.logoutUrl();
    $scope.status = null;
    $scope.message = '';
    $scope.fetchStatus = function() {
        $http.get("../ctrl/status").then(function (res) {
            $scope.status = res.data.status;
            $scope.message = '';
        })
    };
    $scope.start = function() {
        $http.get("../ctrl/start").then(function (res) {
            $scope.status = 'STARTED';
            $scope.message = '';
        })
    };
    $scope.stop = function() {
        $http.get("../ctrl/stop").then(function (res) {
            $scope.status = 'STOPPED';
            $scope.message = '';
        })
    };
    $scope.reload = function() {
        $http.get("../ctrl/reload").then(function (res) {
            $scope.message = 'Reload successful';
        })
    };
    $scope.fetchStatus();
});
