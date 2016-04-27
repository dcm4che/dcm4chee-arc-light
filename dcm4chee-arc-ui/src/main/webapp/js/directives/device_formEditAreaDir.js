"use strict";

myApp.directive("editArea",function(cfpLoadingBar, $log, DeviceService, $compile, schemas, $select){
    var execute = function(scope,elm,attr){
            // console.log("in execute");
                scope.dynamic_schema    = {};
                scope.dynamic_model     = {};
                scope.dynamic_form      = [];
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
                    DeviceService.getSchema(scope.selectedElement);
                    DeviceService.getForm(scope);
                    scope.form[scope.selectedElement] = scope.form[scope.selectedElement] || {};
                    scope.dynamic_form = scope.form[scope.selectedElement]["form"];
                    var timeout = 300;
                    var wait = setInterval(function(){
                            var checkItems = (
                                schemas[scope.selectedElement] &&
                                schemas[scope.selectedElement][scope.selectedElement] &&
                                schemas[scope.selectedElement][scope.selectedElement]["items"] &&
                                schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement]                            );
                            var checkProp = (
                                schemas[scope.selectedElement] &&
                                schemas[scope.selectedElement][scope.selectedElement] &&
                                schemas[scope.selectedElement][scope.selectedElement][scope.selectedElement] &&
                                schemas[scope.selectedElement][scope.selectedElement][scope.selectedElement]["properties"]
                            );                      
                            var checkPropShort = (
                                schemas[scope.selectedElement] &&
                                schemas[scope.selectedElement][scope.selectedElement] &&
                                schemas[scope.selectedElement][scope.selectedElement]["properties"]
                            );
                        if(
                            (
                                checkItems
                            )
                            ||
                            (
                                checkProp
                            )
                            ||
                            checkPropShort
                           ){
                            clearInterval(wait);
                            if($select[scope.selectedElement].parentOf){
                                angular.forEach($select[scope.selectedElement].parentOf,function(m,i){
                                    if(     
                                        checkItems &&
                                        schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement].properties &&
                                        schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement].properties[$select[scope.selectedElement].parentOf[i]]
                                        ){
                                        delete schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement].properties[$select[scope.selectedElement].parentOf[i]];
                                    }
                                    if(     
                                        checkProp &&
                                        schemas[scope.selectedElement][scope.selectedElement][scope.selectedElement].properties[$select[scope.selectedElement].parentOf[i]]
                                        ){
                                        delete schemas[scope.selectedElement][scope.selectedElement][scope.selectedElement].properties[$select[scope.selectedElement].parentOf[i]];
                                    }
                                    if(     
                                        checkPropShort &&
                                        schemas[scope.selectedElement][scope.selectedElement].properties[$select[scope.selectedElement].parentOf[i]]
                                        ){
                                        delete schemas[scope.selectedElement][scope.selectedElement].properties[$select[scope.selectedElement].parentOf[i]];
                                    }
                                });
                            }
                            if(checkItems){
                                scope.dynamic_schema = schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement];
                                console.log("in if checkitems",angular.copy(scope.dynamic_schema));
                            }else{
                                if(checkProp){
                                    scope.dynamic_schema = schemas[scope.selectedElement][scope.selectedElement][scope.selectedElement];
                                    console.log("in else1 checkitems",angular.copy(scope.dynamic_schema));
                                }else{
                                    scope.dynamic_schema = schemas[scope.selectedElement][scope.selectedElement];
                                    console.log("in else2 checkitems",angular.copy(scope.dynamic_schema));
                                }
                            }
                            scope.dynamic_form = scope.form[scope.selectedElement]["form"];
                            DeviceService.addEmptyArrayFields(scope);
                        }
                        if(timeout<0){
                            clearInterval(wait);
                        }else{
                            timeout--;
                        }

                    },10);

                    DeviceService.setFormModel(scope);
                    // var waitSchemaForm = setInterval(function(){
                    //     if(Object.keys(scope.dynamic_schema).length > 0 && scope.dynamic_form && scope.dynamic_form.length > 0){
                    //         clearInterval(waitSchemaForm);
                    //         console.log("xscope.dynamic_form=",angular.copy(scope.dynamic_form));
                    //         console.log("scope.dynamic_model=",angular.copy(scope.dynamic_schema));
                            if(scope.form[scope.selectedElement] && scope.form[scope.selectedElement]["model"]){
                                scope.dynamic_model = scope.form[scope.selectedElement]["model"];
                            }else{
                                scope.dynamic_model = {};
                            }
                            if(($select[scope.selectedElement].optionRef.length > 1 && $select[$select[scope.selectedElement].optionRef[1]].type === "object") || ($select[scope.selectedElement].optionRef.length === 1 && $select[$select[scope.selectedElement].optionRef[0]].type === "object")){
                                DeviceService.createPart(scope);
                            }
                    //     }else{
                    //         console.log("in wait");
                    //     }
                    // },10);
                    console.log("selectedElement=",scope.selectedElement);
                    console.log("form=",scope.form);
                    console.log("scope.dynamic_schema=",scope.dynamic_schema);
                    console.log("scope.dynamic_form=",scope.dynamic_form);
                    // console.log("scope.dynamic_model=",scope.dynamic_model);
                }
                scope.showSave = true;
    };

    return{
        restrict:"A",
        templateUrl: 'templates/device_form.html',
        link: function(scope,elm,attr) {

            // DeviceService.addEmptyArrayFields(scope);
            scope.changeNameWarning = false;
            scope.$watch("[selectedElement,selectedPart]",function(newValue,oldValue) {
                if(newValue[0]!=""){
                    execute(scope,elm,attr);
                }
            }, true);
            execute(scope,elm,attr);
            cfpLoadingBar.set(cfpLoadingBar.status()+(0.2));
            scope.form = scope.form || {};
            scope.form[scope.selectedElement] = scope.form[scope.selectedElement] || {};
            cfpLoadingBar.set(cfpLoadingBar.status()+(0.2));
            scope.editMode         = true;
            cfpLoadingBar.complete();
        }

    }
});