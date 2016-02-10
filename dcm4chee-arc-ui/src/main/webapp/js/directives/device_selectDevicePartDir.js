"use strict";

myApp.directive("selectDevicePart",function($schema,$http,$compile, cfpLoadingBar, $log, $timeout, DeviceService){
    return{
        restrict: "A",
        templateUrl: 'templates/device_selectDevicePart.html',
        link: function(scope,elm,attr) {
            //cfpLoadingBar.start();
            cfpLoadingBar.set(cfpLoadingBar.status()+(0.2));
            //If the user was on the page once, than dont reinitate the whole device
            // if(!scope.wholeDevice && !scope.wholeDeviceCopy){
/*                if(scope.currentDevice == "CHANGE_ME"){
                    scope.wholeDevice = {"dicomDeviceName":"CHANGE_ME"};
                    scope.currentDevice = scope.wholeDevice.dicomDeviceName;
                    scope.devicename = scope.wholeDevice.dicomDeviceName;
                    $log.debug("currentDevice after reset=",scope.currentDevice);
                }else
*/
$log.debug("in selectedDevicepart");
                if(scope.currentDevice != "CHANGE_ME"){
                    cfpLoadingBar.set(cfpLoadingBar.status()+(0.1));
                    $http({
                      method: 'GET',
                      url: '../devices/'+scope.currentDevice
                      }).then(function successCallback(response) {
                        cfpLoadingBar.set(cfpLoadingBar.status()+(0.3));

                        var wholeDeviceCopy                 = {};
                            //scope.selectedNetworkConnection = response.data.dicomNetworkConnection;
                            scope.networkae                 = response.data.dicomNetworkAE;
                            scope.wholeDevice               = response.data;
                            DeviceService.addEmptyArrayFields(scope);
                            cfpLoadingBar.set(cfpLoadingBar.status()+(0.1));
                            angular.copy(scope.wholeDevice,wholeDeviceCopy);
                            cfpLoadingBar.set(cfpLoadingBar.status()+(0.1));
                            scope.wholeDeviceCopy           = wholeDeviceCopy;
                            cfpLoadingBar.set(cfpLoadingBar.status()+(0.1));

                        elm.bind('change', function() {
                            $log.debug("in change selectDevicePart");
                            scope.showSave                  = true; 
                            scope.lastBorder                = "active_border";
                            $log.debug("in change selectDevice before addeditarea wholeDevice=",scope.wholeDevice);
                            $timeout(function() {
                                scope.
                                $apply(function() {
                                    angular.element(document.getElementById('add_edit_area'))
                                           .html($compile("<div edit-area></div>")(scope));
                                });
                            });
                            cfpLoadingBar.complete();
                        });
                        elm.find("#showDeviceForm").bind('click', function() {
                            $timeout(function() {
                                $log.debug("pos1");
                                scope.
                                $apply(function() {
                                    angular.element(document.getElementById('add_edit_area'))
                                           .html($compile("<div edit-area></div>")(scope));
                                });
                            });
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