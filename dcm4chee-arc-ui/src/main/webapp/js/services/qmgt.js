"use strict";

myApp.factory('QmgtService', function($http, $filter) {
    var srv = {};

    srv.search = function(queueName, status, offset, limit) {
        return $http.get(url(queueName), config(queryParams(status, offset, limit)));
    };

    srv.cancel = function(queueName, msgId) {
        return $http.post(url3(queueName, msgId, 'cancel'));
    };

    srv.reschedule = function(queueName, msgId) {
        return $http.post(url3(queueName, msgId, 'reschedule'));
    };

    srv.delete = function(queueName, msgId) {
        return $http.delete(url2(queueName, msgId));
    };

    srv.flush = function(queueName, status, before) {
        return $http.delete(url(queueName), config(flushParams(status, before)));
    };

    function url(queueName) {
        return '../queue/' + queueName;
    }

    function url2(queueName, msgId) {
        return url(queueName) + '/' + msgId;
    }

    function url3(queueName, msgId, command) {
        return url2(queueName, msgId) + '/' + command;
    }

    function config(params) {
        return {
            headers: {Accept: 'application/json'},
            params: params
        }
    }

    function queryParams(status, offset, limit) {
        var params = {
            offset: offset,
            limit: limit
        }
        if (status != "*")
            params.status = status;
        return params;
    }

    function flushParams(status, before) {
        var params = {}
        if (status != "*")
            params.status = status;
        if (before != null)
            params.updatedBefore = $filter('date')(before, 'yyyy-MM-dd');
        return params;
    }

    // Public API
    return {
        search: function(queueName, status, offset, limit) {
            return srv.search(queueName, status, offset, limit);
        },
        cancel: function(queueName, msgId) {
            return srv.cancel(queueName, msgId);
        },
        reschedule: function(queueName, msgId) {
            return srv.reschedule(queueName, msgId);
        },
        delete: function(queueName, msgId) {
            return srv.delete(queueName, msgId);
        },
        flush: function(queueName, status, before) {
            return srv.flush(queueName, status, before);
        }
    };
});
