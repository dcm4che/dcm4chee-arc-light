"use strict";

var myApp = angular.module('myApp', ['ngRoute','schemaForm','angular-loading-bar','ngTouch', 'angular-clockpicker']);

myApp.config(function ($routeProvider) {
    $routeProvider.when('/studies', {
        templateUrl: 'templates/study_list.html',
        controller: 'StudyListCtrl'
    })
    .when('/queues', {
        templateUrl: 'templates/queue_mgt.html',
        controller: 'QueueMgtCtrl'
    })
    .when('/ctrl', {
        templateUrl: 'templates/control.html',
        controller: 'ArchiveCtrl'
    })
    .when('/devices', {
        templateUrl: 'templates/device_main.html',
        controller: 'DeviceController'
    })
    .otherwise({
        redirectTo: '/studies'
    });

});

myApp.value("schemas", {});

myApp.logoutUrl = function() {
    var host = location.protocol + "//" + location.host
    return host + "/auth/realms/dcm4che/tokens/logout?redirect_uri="
        + encodeURIComponent(host + location.pathname);
}
