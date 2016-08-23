"use strict";

myApp.controller("DeviceListController", function($scope, $http, $timeout, $log, cfpLoadingBar, $compile, DeviceService, $parse, schemas, $select) {
    $timeout(function() {
        $scope.$apply(function() {
            $http({
                method: 'GET',
                // url: 'json/devices.json'
                url: '../devices'
            }).then(function successCallback(response) {
                $scope.devices = response.data;
            }, function errorCallback(response) {
                $log.error("Error loading device names", response);
                vex.dialog.alert("Error loading device names, please reload the page and try again!");
            }); 
            $http({
                method: 'GET',
                // url: 'json/devices.json'
                url: '../aes'
            }).then(function successCallback(response) {
                $scope.aes = response.data;
            }, function errorCallback(response) {
                $log.error("Error loading device names", response);
                vex.dialog.alert("Error loading device names, please reload the page and try again!");
            });            
        });
    });
});