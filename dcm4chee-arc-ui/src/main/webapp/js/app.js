"use strict";

var myApp = angular.module('myApp', ['ngRoute','schemaForm','angular-loading-bar', 'angular-clockpicker', 'ui.bootstrap',
  'io.dennis.contextmenu']);

myApp.config(function ($routeProvider,$locationProvider) {
    $locationProvider.hashPrefix('');
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
    .when('/devicelist', {
        templateUrl: 'templates/devicelist.html',
        controller: 'DeviceController'
        // controller: 'DeviceListController'
    })
    .otherwise({
        redirectTo: '/studies'
    });

});

myApp.value('user', {});
myApp.value("schemas", {});

// myApp.logoutUrl = function() {
//     var host = location.protocol + "//" + location.host
//     return host + "/auth/realms/dcm4che/protocol/openid-connect/logout?redirect_uri="
//         + encodeURIComponent(host + location.pathname);
// }
  // $http({
  //     method: 'GET',
  //     url: 'rs/realm'
  // }).then(function successCallback(response) {
  //   myApp.user = {
  //       username:response.data.user,
  //       isRole:function(role){
  //           if(response.data.user === null && response.data.roles.length === 0){
  //             return true;
  //           }else{ 
  //             if(response.data.roles && response.data.roles.indexOf(role) > -1){
  //               return true;
  //             }else{
  //               return false;
  //             }
  //           }
  //       }
  //   }
  // }, function errorCallback(response) {
  //     // vex.dialog.alert("Error loading device names, please reload the page and try again!");
  // }); 


