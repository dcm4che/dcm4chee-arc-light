"use strict";

myApp.directive("editArea",function($schema, cfpLoadingBar, $log, DeviceService, $compile, schemas, $select){
    return{
        restrict:"A",
        templateUrl: 'templates/device_form.html',
        link: function(scope,elm,attr) {

            // DeviceService.addEmptyArrayFields(scope);
            
            scope.$watch("[selectedElement,selectedPart]",function(newValue,oldValue) {
            if(scope.selectedElement === "device"){
                cfpLoadingBar.set(cfpLoadingBar.status()+(0.1));

                scope.dynamic_schema = DeviceService.getDeviceSchema();
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
                            console.log("wholeDevice",scope.dynamic_model);
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
                    console.log("2wholeDevice",scope.dynamic_model);
                }
                cfpLoadingBar.set(cfpLoadingBar.status()+(0.2));
                scope.dynamic_form = DeviceService.getDeviceForm();
            }else{
                    $log.debug("in else selectedElement=",scope.selectedElement);
                    DeviceService.getSchema(scope.selectedElement);
                    var form = DeviceService.getForm(scope);
                    // console.log("form=",angular.copy(form));
                    scope.form[scope.selectedElement] = scope.form[scope.selectedElement] || {};
                    scope.dynamic_form = scope.form[scope.selectedElement]["form"];
                    // console.log("scope.dynamic_form",angular.copy(scope.dynamic_form));
                    var timeout = 300;
                    var wait = setInterval(function(){
                        if(
                            (
                                // form &&
                                schemas[scope.selectedElement] && 
                                schemas[scope.selectedElement][scope.selectedElement] && 
                                schemas[scope.selectedElement][scope.selectedElement]["items"] && 
                                schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement]
                            )
                            ||
                            (
                                // form &&
                                schemas[scope.selectedElement] && 
                                schemas[scope.selectedElement][scope.selectedElement] && 
                                schemas[scope.selectedElement][scope.selectedElement][scope.selectedElement] &&
                                schemas[scope.selectedElement][scope.selectedElement][scope.selectedElement]["properties"]
                            )
                           ){
                            clearInterval(wait);
                            // console.log("form=",angular.copy(form));
                            // console.log("in if scope.dynamic_form",angular.copy(scope.dynamic_form));
                            if($select[scope.selectedElement].parentOf){
                                angular.forEach($select[scope.selectedElement].parentOf,function(m,i){
                                    if(
                                            schemas[scope.selectedElement][scope.selectedElement] &&
                                            schemas[scope.selectedElement][scope.selectedElement]["items"] &&
                                            schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement] &&
                                            schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement].properties &&
                                            schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement].properties[$select[scope.selectedElement].parentOf[i]]
                                        ){
                                        delete schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement].properties[$select[scope.selectedElement].parentOf[i]];
                                    }
                                });
                            }
                            if(schemas[scope.selectedElement][scope.selectedElement]["items"]){
                                scope.dynamic_schema = schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement];
                            }else{
                                scope.dynamic_schema = schemas[scope.selectedElement][scope.selectedElement][scope.selectedElement];
                            }
                            scope.dynamic_form = scope.form[scope.selectedElement]["form"];
                            DeviceService.addEmptyArrayFields(scope);
                        }
                        if(timeout<0){
                            // console.log("in timeout");
                            // console.log("scope.dynamic_form",angular.copy(form));
                            clearInterval(wait);
                        }else{
                            timeout--;
                        }

                    },10);

                    DeviceService.setFormModel(scope);
                    if(scope.form[scope.selectedElement] && scope.form[scope.selectedElement]["model"]){
                        $log.warn("in if set model form=",scope.form);
                        scope.dynamic_model = scope.form[scope.selectedElement]["model"];
                    }else{
                        $log.warn("in else, from=",form);
                        scope.dynamic_model = {};
                    }
                    $log.warn("before createPart if");
                    $log.debug("$select[scope.selectedElement].optionRef.length=",$select[scope.selectedElement].optionRef.length);
                    $log.debug("scope.selectedElement=",scope.selectedElement);
                    $log.debug("$select[scope.selectedElement].optionRef=",$select[scope.selectedElement].optionRef);
                    if(($select[scope.selectedElement].optionRef.length > 1 && $select[$select[scope.selectedElement].optionRef[1]].type === "object") || ($select[scope.selectedElement].optionRef.length === 1 && $select[$select[scope.selectedElement].optionRef[0]].type === "object")){
                        // $log.debug("$select[$select[scope.selectedElement].optionRef[1]].type=",$select[$select[scope.selectedElement].optionRef[1]].type);
                        $log.debug("before createPart call");
                        DeviceService.createPart(scope);
                    }
                    $log.debug("after set schema=",scope.dynamic_schema);
                    $log.debug("model=",scope.dynamic_model);
                    
                }
            }, true);

            cfpLoadingBar.set(cfpLoadingBar.status()+(0.2));
            scope.form = scope.form || {};
            scope.form[scope.selectedElement] = scope.form[scope.selectedElement] || {};

            if(scope.selectedElement === "device"){
                cfpLoadingBar.set(cfpLoadingBar.status()+(0.1));

                scope.dynamic_schema = DeviceService.getDeviceSchema();
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
                            console.log("wholeDevice",scope.dynamic_model);
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
                    console.log("2wholeDevice",scope.dynamic_model);
                }
                cfpLoadingBar.set(cfpLoadingBar.status()+(0.2));
                scope.dynamic_form = DeviceService.getDeviceForm();
            }

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