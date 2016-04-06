"use strict";

myApp.directive("editArea",function($schema, cfpLoadingBar, $log, DeviceService, $compile, schemas, $select){
	return{
		restrict:"A",
		templateUrl: 'templates/device_form.html',
		link: function(scope,elm,attr) {
            scope.$watch("[selectedElement,selectedPart]",function(newValue,oldValue) {
                if(scope.selectedElement === "device"){
                    cfpLoadingBar.set(cfpLoadingBar.status()+(0.1));

                    scope.dynamic_schema    = DeviceService.getDeviceSchema();
                    scope.dynamic_form      = DeviceService.getDeviceForm();
                    // $log.debug("scope.deviceSchema",scope.deviceSchema);
                    if(!scope.deviceSchema){
                        var timeOut = 0;
                        var schema  = {};
                        var waitForShema = setInterval(function(){
                            schema  = DeviceService.getDeviceSchema();
                            if(schema){
                                clearInterval(waitForShema);
                                scope.dynamic_schema = schema;
                            }
                            if(timeOut > 100){  //If the program is waiting more than 10 sec than break up and show alert
                                clearInterval(waitForShema);
                                $log.error("Timeout error!");
                                vex.dialog.alert("Timeout error, can't get device information, please reload the page and try again!");
                            }
                            timeOut++;
                        }, 100);
                    }
                    //If the wholeDevice is undefined wait for it, otherwaise assigne it to dynamic_model
                    if(scope.wholeDevice === undefined || scope.wholeDevice.dicomDeviceName != scope.currentDevice){
                        var timeOut = 0;
                        var waitForWholeDevice = setInterval(function(){
                            if(scope.wholeDevice !== undefined){
                                clearInterval(waitForWholeDevice);
                                scope.dynamic_model = scope.wholeDevice;
                            }
                            if(timeOut > 100){  //If the program is waiting more than 10 sec than break up and show alert
                                clearInterval(waitForWholeDevice);
                                $log.error("Timeout error!");
                                vex.dialog.alert("Timeout error, can't get device information, please reload the page and try again!");
                            }
                            timeOut++;
                        }, 100);
                    }else{
                        scope.dynamic_model = scope.wholeDevice;
                    }
                    cfpLoadingBar.set(cfpLoadingBar.status()+(0.2));
                    // scope.dynamic_form = DeviceService.getDeviceForm();!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                }else{

                    DeviceService.getSchema(scope.selectedElement);

                    var timeout = 50;
                    var wait = setInterval(function(){
                        if(
                            (
                                schemas[scope.selectedElement] && 
                                schemas[scope.selectedElement][scope.selectedElement] && 
                                schemas[scope.selectedElement][scope.selectedElement]["items"] && 
                                schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement]
                            )
                            ||
                            (
                                schemas[scope.selectedElement] && 
                                schemas[scope.selectedElement][scope.selectedElement] && 
                                schemas[scope.selectedElement][scope.selectedElement][scope.selectedElement] &&
                                schemas[scope.selectedElement][scope.selectedElement][scope.selectedElement]["properties"]
                            )
                       ){
                            clearInterval(wait);
                            if($select[scope.selectedElement].parentOf){
                                angular.forEach($select[scope.selectedElement].parentOf,function(m,i){
                                    delete schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement].properties[$select[scope.selectedElement].parentOf[i]];
                                });
                            }
                            if(schemas[scope.selectedElement][scope.selectedElement]["items"]){
                                scope.dynamic_schema = schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement];
                            }else{
                                scope.dynamic_schema = schemas[scope.selectedElement][scope.selectedElement][scope.selectedElement];
                            }
                        }
                        if(timeout<0){
                            clearInterval(wait);
                            vex.dialog.alert("Timeout error, can't get device information, please reload the page and try again!");
                        }else{
                            timeout--;
                        }

                    },10);

                    DeviceService.setFormModel(scope);
                    if(scope.form[scope.selectedElement] && scope.form[scope.selectedElement]["model"]){
                        scope.dynamic_model = scope.form[scope.selectedElement]["model"];
                    }else{
                        scope.dynamic_model = null;
                    }
                }
            }, true);
    		// cfpLoadingBar.start();
            cfpLoadingBar.set(cfpLoadingBar.status()+(0.2));
            scope.form = scope.form || {};
            scope.form[scope.selectedElement] = scope.form[scope.selectedElement] || {};

            cfpLoadingBar.set(cfpLoadingBar.status()+(0.2));

            cfpLoadingBar.complete();
            //Warning the user if he start to change the device name
            setTimeout(function(){
                angular.element(document.getElementById('dicomDeviceName')).bind("click focus keydown",function(){
                    if(scope.currentDevice != "CHANGE_ME"){
                        DeviceService.msg(scope, {
                          "title": "Warning",
                          "text": "If you change the name of the device, on save will try the system to create another copy of the device with the new name and delete the old one.",
                          "status": "warning",
                          "timeout": 15000
                        });
                    }
                });
            },1000);
            scope.showSave = true;
        }

    }
});