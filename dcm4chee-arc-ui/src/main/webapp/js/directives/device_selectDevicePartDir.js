"use strict";

myApp.directive("selectDevicePart",function($http,$compile, cfpLoadingBar, $log, $timeout, DeviceService){
    return{
        restrict: "A",
        templateUrl: 'templates/device_selectDevicePart.html',
        link: function(scope,elm,attr) {
            //cfpLoadingBar.start();
            cfpLoadingBar.set(cfpLoadingBar.status()+(0.2));
                if(scope.currentDevice != "CHANGE_ME"){
                    cfpLoadingBar.set(cfpLoadingBar.status()+(0.1));
                    $http({
                      method: 'GET',
                      url: '../devices/'+scope.currentDevice
                      }).then(function successCallback(response) {
                        cfpLoadingBar.set(cfpLoadingBar.status()+(0.3));
                            scope.wholeDevice               = {};
                            scope.wholeDevice               = response.data;
                            DeviceService.addEmptyArrayFields(scope);
                            cfpLoadingBar.set(cfpLoadingBar.status()+(0.3));
                        elm.bind('change', function() {
                            scope.showSave                  = true; 
                            scope.lastBorder                = "active_border";
                            cfpLoadingBar.complete();
                        });

                        elm.find("#showDeviceForm").bind('click', function() {
                            if(scope.devicename === "CHANGE_ME"){
                                $timeout(function() {
                                    scope.
                                    $apply(function() {
                                        DeviceService
                                        .addDirectiveToDom(
                                            scope, 
                                            "add_edit_area",
                                            "<div edit-area></div>"
                                        );
                                    });
                                });
                            }

                            scope.showSave                  = true;
                            scope.lastBorder                = "active_border";
                            cfpLoadingBar.complete();
                        });
                    }, function errorCallback(response) {
                        scope.wholeDevice               = {};
                        if(response.status && response.statusText){
                            DeviceService.msg(scope, {
                                "title": "Error",
                                "text": response.status+":"+response.statusText,
                                "status": "error"
                            });
                        }else{
                            DeviceService.msg(scope, {
                                "title": "Error",
                                "text": "Error loading device!",
                                "status": "error"
                            });
                        }
                        $log.error("Error",response);
                    });
                }
            scope.showSave = true;
        }
    }
});