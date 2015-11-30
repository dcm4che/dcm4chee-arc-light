"use strict";

myApp.controller('QueueMgtCtrl', function ($scope, $http, QmgtService) {
    $scope.logoutUrl = myApp.logoutUrl();
    $scope.matches = [];
    $scope.limit = 20;
    $scope.queues = [];
    $scope.queueName = null;
    $scope.status = "*";
    $scope.before = today();
    $scope.search = function(offset) {
        QmgtService.search($scope.queueName, $scope.status, offset, $scope.limit)
        .then(function (res) {
            $scope.matches = res.data.map(function(properties, index) {
                return {
                    offset: offset + index,
                    properties: properties,
                    showProperties: false
                };
            });
        });
    };
    $scope.cancel = function(match) {
        QmgtService.cancel($scope.queueName, match.properties.id)
            .then(function (res) {
                match.properties.status = 'CANCELED';
            });
    };
    $scope.reschedule = function(match) {
        QmgtService.reschedule($scope.queueName, match.properties.id)
            .then(function (res) {
                $scope.search(0);
            });
    };
    $scope.delete = function(match) {
        QmgtService.delete($scope.queueName, match.properties.id)
            .then(function (res) {
                $scope.search($scope.matches[0].offset);
            });
    };
    $scope.flushBefore = function() {
        QmgtService.flush($scope.queueName, $scope.status, $scope.before)
            .then(function (res) {
                $scope.search(0);
            });
    };
    $scope.hasOlder = function(objs) {
        return objs && (objs.length === $scope.limit);
    };
    $scope.hasNewer = function(objs) {
        return objs && objs.length && objs[0].offset;
    };
    $scope.newerOffset = function(objs) {
        return Math.max(0, objs[0].offset - $scope.limit);
    };
    $scope.olderOffset = function(objs) {
        return objs[0].offset + $scope.limit;
    };
    function today() {
        var now = new Date();
        return new Date(now.getFullYear(), now.getMonth(), now.getDate());
    }
    function init() {
        $http.get("../queue").then(function (res) {
            $scope.queues = res.data;
            $scope.queueName = res.data[0].name;
        })
    }
    init();
});
