"use strict";

myApp.directive("selectDevicePart",function($schema,$http,$compile, cfpLoadingBar, $log, $timeout, DeviceService){
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

                        // var wholeDeviceCopy                 = {};
                            //scope.selectedNetworkConnection = response.data.dicomNetworkConnection;
                            scope.dicomNetworkAE            = response.data.dicomNetworkAE;
                            scope.wholeDevice               = response.data;
                            DeviceService.addEmptyArrayFields(scope);
                            cfpLoadingBar.set(cfpLoadingBar.status()+(0.3));
                            // angular.copy(scope.wholeDevice,wholeDeviceCopy);
                            // cfpLoadingBar.set(cfpLoadingBar.status()+(0.1));
                            // scope.wholeDeviceCopy           = wholeDeviceCopy;
                            // cfpLoadingBar.set(cfpLoadingBar.status()+(0.1));

                        elm.bind('change', function() {
                            $log.debug("in change selectDevicePart");
                            scope.showSave                  = true; 
                            scope.lastBorder                = "active_border";
                            $log.debug("in change selectDevice before addeditarea wholeDevice=",scope.wholeDevice);
                            // $timeout(function() {
                            //     scope.
                            //     $apply(function() {
                            //         // angular.element(document.getElementById('add_edit_area'))
                            //         //        .html($compile("<div edit-area></div>")(scope));
                            //         DeviceService
                            //         .addDirectiveToDom(
                            //             scope, 
                            //             "add_edit_area",
                            //             "<div edit-area></div>"
                            //         );
                            //     });
                            // });
                            cfpLoadingBar.complete();
                        });

                        elm.find("#showDeviceForm").bind('click', function() {
                            // $log.debug("in click");
                            if(scope.devicename === "CHANGE_ME"){
                                $timeout(function() {
                                    // $log.debug("pos1");
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
                        // $timeout(function() {
                        //     scope.$apply(function() {
                        //         scope.loaderElement = "testTest";
                        //         $log.debug("loaderElement set = ",scope.loaderElement);
                        //     });
                        // });
                    }, function errorCallback(response) {
                        $log.error("Error",response);
                    });
                }
            // }else{
            //     $log.debug("in else link");
            // }
            scope.showSave = true;
        }
    }
});