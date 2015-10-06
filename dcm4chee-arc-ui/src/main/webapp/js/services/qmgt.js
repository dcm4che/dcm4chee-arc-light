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

    // Public API
    return {
        search: function(url, params) {
            return srv.search(url, params);
        }
    };
});
