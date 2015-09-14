"use strict";

var myApp = angular.module('myApp', ['ngRoute']);

myApp.config(function ($routeProvider) {
    $routeProvider.when('/studies', {
        templateUrl: 'templates/study_list.html',
        controller: 'StudyListCtrl'
    })
    .otherwise({
        redirectTo: '/studies'
    });

});