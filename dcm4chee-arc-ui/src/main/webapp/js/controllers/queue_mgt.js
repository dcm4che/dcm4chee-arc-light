"use strict";

myApp.controller('QueueMgtCtrl', function ($scope, $http, $filter, QmgtService) {
    $scope.matches = [];
    $scope.limit = 20;
    $scope.queues = [];
    $scope.queueName = null;
    $scope.status = "*";
    $scope.before = today();
    $scope.search = function(offset) {
        QmgtService.search(qmgtURL(), queryParams(offset))
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
        QmgtService.cancel(qmgtURL(), match.properties.id)
            .then(function (res) {
                match.properties.status = 'CANCELED';
            });
    };
    $scope.reschedule = function(match) {
        QmgtService.reschedule(qmgtURL(), match.properties.id)
            .then(function (res) {
                $scope.search(0);
            });
    };
    $scope.delete = function(match) {
        QmgtService.delete(qmgtURL(), match.properties.id)
            .then(function (res) {
                $scope.search($scope.matches[0].offset);
            });
    };
    $scope.flushBefore = function() {
        QmgtService.flush(qmgtURL(), flushParams())
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
    function qmgtURL() {
        return "../queue/" + $scope.queueName;
    }
    function queryParams(offset) {
        var params = {
            offset: offset,
            limit: $scope.limit
        }
        if ($scope.status != "*")
            params.status = $scope.status;
        return params;
    }
    function flushParams() {
        var params = {}
        if ($scope.status != "*")
            params.status = $scope.status;
        if ($scope.before != null)
            params.updatedBefore = $filter('date')($scope.before, 'yyyy-MM-dd');
        return params;
    }
    function init() {
        $http.get("../queue").then(function (res) {
            function qmgtURL() {
                return "../queue/" + $scope.queueName;
            }
            $scope.queues = res.data;
            $scope.queueName = res.data[0].name;
        })
    }
    init();
});
