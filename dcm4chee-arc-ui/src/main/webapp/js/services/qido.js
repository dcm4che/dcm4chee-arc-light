"use strict";

myApp.factory('QidoService', function($http) {
    var srv = {};

    srv._config = function(params) {
        return {
            headers: {Accept: 'application/json'},
            params: params
        }
    };

    srv.queryPatients = function(url, params) {
        console.log("srv._config(params)",srv._config(params));
        return $http.get(url + '/patients', srv._config(params));
    };

    srv.queryStudies = function(url, params) {
        return $http.get(url + '/studies', srv._config(params));
    };
    srv.queryMwl = function(url, params) {
        return $http.get(url + '/mwlitems', srv._config(params));
    };

    srv.querySeries = function(url, studyIUID, params) {
        return $http.get(url + '/studies/' + studyIUID + '/series', srv._config(params));
    };

    srv.queryInstances = function(url, studyIUID, seriesIUID, params) {
        return $http.get(url
            + '/studies/' + studyIUID
            + '/series/' + seriesIUID
            + '/instances',
            srv._config(params));
    };

   // Public API
    return {
        queryPatients: function(url, params) {
            return srv.queryPatients(url, params);
        },
        queryStudies: function(url, params) {
            return srv.queryStudies(url, params);
        },
        queryMwl: function(url, params) {
            return srv.queryMwl(url, params);
        },
        querySeries: function(url, studyIUID, params) {
            return srv.querySeries(url, studyIUID, params);
        },
        queryInstances: function(url, studyIUID, seriesIUID, params) {
            return srv.queryInstances(url, studyIUID, seriesIUID, params);
        }
    };
});
