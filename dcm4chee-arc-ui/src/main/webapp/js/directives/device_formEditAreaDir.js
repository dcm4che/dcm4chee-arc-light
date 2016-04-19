"use strict";

myApp.directive("editArea",function(cfpLoadingBar, $log, DeviceService, $compile, schemas, $select){
    var execute = function(scope,elm,attr){
            console.log("in execute");
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
                    // $log.debug("in else selectedElement=",scope.selectedElement);
                    $log.debug("in formEditAreaDir else before getSchema call");
                    DeviceService.getSchema(scope.selectedElement);
                    var form = DeviceService.getForm(scope);
                    // console.log("form=",angular.copy(form));
                    scope.form[scope.selectedElement] = scope.form[scope.selectedElement] || {};
                    scope.dynamic_form = scope.form[scope.selectedElement]["form"];
                    // console.log("scope.dynamic_form",angular.copy(scope.dynamic_form));
                    var timeout = 300;
                    var wait = setInterval(function(){
                        console.log("in wait interval form editareadir");
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
                                // form &&
                                checkItems
                                // schemas[scope.selectedElement] && 
                                // schemas[scope.selectedElement][scope.selectedElement] && 
                                // schemas[scope.selectedElement][scope.selectedElement]["items"] && 
                                // schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement]
                            )
                            ||
                            (
                                // form &&
                                checkProp
                                // schemas[scope.selectedElement] && 
                                // schemas[scope.selectedElement][scope.selectedElement] && 
                                // schemas[scope.selectedElement][scope.selectedElement][scope.selectedElement] &&
                                // schemas[scope.selectedElement][scope.selectedElement][scope.selectedElement]["properties"]
                            )
                            ||
                            checkPropShort
                           ){
                            clearInterval(wait);
                            // console.log("form=",angular.copy(form));
                            // console.log("in if scope.dynamic_form",angular.copy(scope.dynamic_form));
                            if($select[scope.selectedElement].parentOf){
                                angular.forEach($select[scope.selectedElement].parentOf,function(m,i){
                                    if(     
                                        checkItems &&
                                        schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement].properties &&
                                        schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement].properties[$select[scope.selectedElement].parentOf[i]]
                                        ){
                                        console.log("in first if formEditAreaDir",schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement].properties[$select[scope.selectedElement].parentOf[i]]);
                                        delete schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement].properties[$select[scope.selectedElement].parentOf[i]];
                                    }
                                    if(     
                                        checkProp &&
                                        schemas[scope.selectedElement][scope.selectedElement][scope.selectedElement].properties[$select[scope.selectedElement].parentOf[i]]
                                        ){
                                        console.log("in second if formEditAreaDir",schemas[scope.selectedElement][scope.selectedElement][scope.selectedElement].properties[$select[scope.selectedElement].parentOf[i]]);
                                        delete schemas[scope.selectedElement][scope.selectedElement][scope.selectedElement].properties[$select[scope.selectedElement].parentOf[i]];
                                    }
                                    if(     
                                        checkPropShort &&
                                        schemas[scope.selectedElement][scope.selectedElement].properties[$select[scope.selectedElement].parentOf[i]]
                                        ){
                                        delete schemas[scope.selectedElement][scope.selectedElement].properties[$select[scope.selectedElement].parentOf[i]];
                                    }

                                    // if(checkItems){

                                    // }
                                    // if(
                                    //         schemas[scope.selectedElement][scope.selectedElement] &&
                                    //         schemas[scope.selectedElement][scope.selectedElement]["items"] &&
                                    //         schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement] &&
                                    //         schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement].properties &&
                                    //         schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement].properties[$select[scope.selectedElement].parentOf[i]]
                                    //     ){
                                    //     console.log("before delete, m=",m);
                                    //     console.log("before delete, i=",i);
                                    // }else{
                                    //     console.log("in form edit else delete");
                                    // }
                                });
                            }
                            if(checkItems){
                                scope.dynamic_schema = schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement];
                            }else{
                                if(checkProp){
                                    scope.dynamic_schema = schemas[scope.selectedElement][scope.selectedElement][scope.selectedElement];
                                }else{
                                    scope.dynamic_schema = schemas[scope.selectedElement][scope.selectedElement];
                                }
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
                    // $log.warn("before createPart if");
                    // $log.debug("$select[scope.selectedElement].optionRef.length=",$select[scope.selectedElement].optionRef.length);
                    // $log.debug("scope.selectedElement=",scope.selectedElement);
                    // $log.debug("$select[scope.selectedElement].optionRef=",$select[scope.selectedElement].optionRef);
                    if(($select[scope.selectedElement].optionRef.length > 1 && $select[$select[scope.selectedElement].optionRef[1]].type === "object") || ($select[scope.selectedElement].optionRef.length === 1 && $select[$select[scope.selectedElement].optionRef[0]].type === "object")){
                        // $log.debug("$select[$select[scope.selectedElement].optionRef[1]].type=",$select[$select[scope.selectedElement].optionRef[1]].type);
                        $log.debug("before createPart call");
                        DeviceService.createPart(scope);
                    }
                    $log.debug("after set schema=",scope.dynamic_schema);
                    $log.debug("formemodel=",scope.form);
                    $log.debug("model=",scope.dynamic_model);
                    
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
                execute(scope,elm,attr);
            }, true);
            execute(scope,elm,attr);
            cfpLoadingBar.set(cfpLoadingBar.status()+(0.2));
            scope.form = scope.form || {};
            scope.form[scope.selectedElement] = scope.form[scope.selectedElement] || {};
            // execute(scope,elm,attr);
            // warnEvent(scope);
            cfpLoadingBar.set(cfpLoadingBar.status()+(0.2));
            scope.editMode         = true;
            cfpLoadingBar.complete();
        }

    }
});