"use strict";

myApp.factory('QmgtService', function($http) {
    var srv = {};

    srv._config = function(params) {
        return {
            headers: {Accept: 'application/json'},
            params: params
        }
    };

    srv.search = function(url, params) {
        return $http.get(url, srv._config(params));
    };

    srv.cancel = function(url, msgId) {
        return $http.get(url + '/' + msgId + '/cancel');
    };

    srv.reschedule = function(url, msgId) {
        return $http.get(url + '/' + msgId + '/reschedule');
    };

    srv.delete = function(url, msgId) {
        return $http.delete(url + '/' + msgId);
    };

    srv.flush = function(url, params) {
        return $http.delete(url, srv._config(params));
    };

    // Public API
    return {
        search: function(url, params) {
            return srv.search(url, params);
        },
        cancel: function(url, msgId) {
            return srv.cancel(url, msgId);
        },
        reschedule: function(url, msgId) {
            return srv.reschedule(url, msgId);
        },
        delete: function(url, msgId) {
            return srv.delete(url, msgId);
        },
        flush: function(url, params) {
            return srv.flush(url, params);
        }
    };
});
