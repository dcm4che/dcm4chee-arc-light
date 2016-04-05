"use strict";

myApp.directive("editArea",function($schema, cfpLoadingBar, $log, DeviceService, $compile, schemas, $select){
	return{
		restrict:"A",
		templateUrl: 'templates/device_form.html',
		link: function(scope,elm,attr) {
            // angular.forEach(schemas, function(m,i){
            //     console.log("i=",i,"m=",m);
            //     if(i!="device"){
            //         // schemas[i].destroy();
            //         // schemas[i].clear();
            //         // delete scope.form[i];
            //         // schemas[i].$destroy();
            //             scope.form[i] = null;
            //             schemas[i] = null;
            //             delete schemas[i];
            //         // scope.$destroy(function(){

            //         // });
            //         // delete schemas[i];
            //     }
            //     console.log("schemas=",schemas);
            // });
            /////////////////////////////////////////
            scope.$watch("[selectedElement,selectedPart]",function(newValue,oldValue) {
                console.log("1234selctedElement=",scope.selectedElement);
                console.log("1234elm",elm);
                console.log("1234scope",scope);
                if(scope.selectedElement != "device"){

                    DeviceService.getSchema(scope.selectedElement);
                    // if(!schemas[scope.selectedElement] || !schemas[scope.selectedElement][scope.selectedElement]){
                    //   DeviceService.getSchema(scope.selectedElement);
                    // }
                    // console.log("newSchema",newSchema);
                    console.log("schemas ref=",angular.copy(schemas));
/*                    var timeout = 500;
                    (function waitForSchema(){
                        // console.log("in waitForSchema",schemas);
                        console.log("scope.selectedElement=",scope.selectedElement);
                        if( newSchema[scope.selectedElement] && 
                            newSchema[scope.selectedElement][scope.selectedElement] && 
                            newSchema[scope.selectedElement][scope.selectedElement]["items"] && 
                            newSchema[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement]
                            ){
                            // console.log("newSchema in if = ",angular.copy(schemas[scope.selectedElement]));
                            scope.dynamic_schema = newSchema[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement];
                        }else{
                            console.log("waiting,newSchema=",newSchema);
                            timeout--;
                            if(timeout>0){
                                setTimeout(waitForSchema(),30);
                            }else{
                                // console.log("scope.dynamic_schema",schemas);
                                // console.log("scope.dynamic_schema",scope.selectedElement);
                                // console.log("scope.dynamic_schema",angular.copy(schemas[scope.selectedElement][scope.selectedElement]));
                                // console.log("scope.dynamic_schema",schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement]);
                                // newSchema = DeviceService.getSchema(scope.selectedElement);
                                console.log("+schemas=",angular.copy(schemas));
                                scope.dynamic_schema = schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement];
                                console.log("scope.dynamic_schema=",scope.dynamic_schema);
                            }
                            // waitForSchema();
                        }
                    })();*/
                    var timeout = 50;
                    //     angular.forEach(schemas, function(m,i){
                    //     console.log("i=",i,"m=",m);
                    //     if(i!="device"){
                    //         // schemas[i].destroy();
                    //         // schemas[i].clear();
                    //         // delete scope.form[i];
                    //         // schemas[i].$destroy();
                    //             scope.form[i] = null;
                    //             schemas[i] = null;
                    //             delete schemas[i];
                    //         // scope.$destroy(function(){

                    //         // });
                    //         // delete schemas[i];
                    //     }
                    //     console.log("schemas=",schemas);
                    // });
                    // scope.dynamic_schema = null;
                    $log.debug("scope.selectedElement",scope.selectedElement);
                    $log.debug("scope.selectedElement",schemas);
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
                            console.log("scope.dynamic_schema=",angular.copy(scope.dynamic_schema));
                            console.log("schemas=",angular.copy(schemas));
                            console.log("selectedElement=",angular.copy(scope.selectedElement));
                            if($select[scope.selectedElement].parentOf){
                                $log.warn("in if DELETE selectedElement=", scope.selectedElement);
                                angular.forEach($select[scope.selectedElement].parentOf,function(m,i){
                                    delete schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement].properties[$select[scope.selectedElement].parentOf[i]];
                                });
                            }
                            if(schemas[scope.selectedElement][scope.selectedElement]["items"]){
                                scope.dynamic_schema = schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement];
                            }else{
                                console.log("in else properties", schemas[scope.selectedElement][scope.selectedElement][scope.selectedElement]);
                                scope.dynamic_schema = schemas[scope.selectedElement][scope.selectedElement][scope.selectedElement];
                            }
                                console.log("scope.dynamic_schema=",angular.copy(scope.dynamic_schema));
                        }
                        if(timeout<0){
                            clearInterval(wait);
                        }else{
                            console.log("selectedElement=",scope.selectedElement);
                            console.log("in wait",schemas);
                            $log.debug("temout=",timeout);
                            timeout--;
                        }

                    },10);

                    DeviceService.setFormModel(scope);
                    console.log("selectedElement=",scope.selectedElement);
                    console.log("scope.form=",scope.form);
                    if(scope.form[scope.selectedElement] && scope.form[scope.selectedElement]["model"]){
                        scope.dynamic_model = scope.form[scope.selectedElement]["model"];
                    }else{
                        scope.dynamic_model = null;
                    }
                    console.log("dynamic_model=",scope.dynamic_model);


                    // setTimeout(function(){
                    //     console.log("newSchema=",newSchema);
                    //     console.log("dynamic_schema=",angular.copy(scope.dynamic_schema));
                    //     scope.dynamic_schema = schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement];
                    // },500);
                    // if(scope.selectedElement == "dicomNetworkConnection"){

                }
            }, true);
            /////////////////////////////////////////
    		// cfpLoadingBar.start();
            cfpLoadingBar.set(cfpLoadingBar.status()+(0.2));
            scope.form = scope.form || {};
            scope.form[scope.selectedElement] = scope.form[scope.selectedElement] || {};
            // $log.debug("scope.form=",scope.form);
            // console.log("in fromeditareadir, schemas=",schemas);
            //Empty memory
            // console.log("$scope.form[$scope.selectedElement]",scope.form);
            // angular.forEach(schemas, function(m,i){
            //     console.log("i=",i,"m=",m);
            //     if(i!="device"){
            //         // schemas[i].destroy();
            //         // schemas[i].clear();
            //         // delete scope.form[i];
            //         // schemas[i].$destroy();
            //             scope.form[i] = null;
            //             schemas[i] = null;
                        
            //         // scope.$destroy(function(){

            //         // });
            //         delete schemas[i];
            //     }
            //     console.log("schemas=",schemas);
            // });
            console.log("*********selectedElement for device if",scope.selectedElement);
            if(scope.selectedElement === "device"){
                cfpLoadingBar.set(cfpLoadingBar.status()+(0.1));

                scope.dynamic_schema = DeviceService.getDeviceSchema();
                // $log.debug("scope.deviceSchema",scope.deviceSchema);
                if(!scope.deviceSchema){
                    var timeOut = 0;
                    var schema  = {};
                    var waitForShema = setInterval(function(){
                        $log.debug("inwait scheam=",DeviceService.getDeviceSchema());
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
                // scope.dynamic_form = DeviceService.getDeviceForm();!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                console.log("scope.dynamic_model",angular.copy(scope.dyanmic_model));
            }else{
                console.log("scope.dynamic_model",angular.copy(scope.dyanmic_model));
                scope.dynamic_form = null;
                
            /*
                
                if(!schemas[scope.selectedElement] || !schemas[scope.selectedElement][scope.selectedElement]){
                    DeviceService.getSchema(scope.selectedElement);
                }
                
                if($select[scope.selectedElement].type==="array"){
                    var timeout = 50;
                    var wait = setInterval(function(){
                        $log.debug("1schemas=",angular.copy(schemas));
                        $log.debug("1scope.selectedElement=",scope.selectedElement);
                        
                        if(
                            schemas[scope.selectedElement] && 
                            schemas[scope.selectedElement][scope.selectedElement] && 
                            schemas[scope.selectedElement][scope.selectedElement]["items"] && 
                            schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement]
                           ){
                            clearInterval(wait);
                            console.log("for setformmodel 21");
                            DeviceService.setFormModel(scope);
                            scope.form[scope.selectedElement]["schema"] = schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement];
                            if($select[scope.selectedElement].parentOf){
                                $log.debug("in if");
                                angular.forEach($select[scope.selectedElement].parentOf,function(m,i){
                                    delete scope.form[scope.selectedElement]["schema"].properties[$select[scope.selectedElement].parentOf[i]];
                                });
                            }
                            $log.debug("1scope.form[scope.selectedElement][schema]",scope.form[scope.selectedElement]["schema"]);
                            $log.debug("schemas=",schemas);
                        }
                        if(timeout<0){
                            clearInterval(wait);
                        }else{
                            $log.debug("temout=",timeout);
                            timeout--;
                        }

                    },50);
                }else{
                    var wait2 = setInterval(function(){
                        if(schemas[scope.selectedElement][scope.selectedElement][scope.selectedElement]){
                            clearInterval(wait2);
                            console.log("for setformmodel 23");
                            DeviceService.setFormModel(scope);
                            if(!scope.form[scope.selectedElement]){
                                scope.form[scope.selectedElement] = {};
                            }
                            scope.form[scope.selectedElement]["schema"] = schemas[scope.selectedElement][scope.selectedElement][scope.selectedElement];
                            $log.debug("2scope.form[scope.selectedElement][schema]",scope.form[scope.selectedElement]["schema"]);
                            scope.$apply();
                        }
                    },50);
                }
            */}
            

            
			//If the first selectbutton was changed than show the NetworkConnection of dhe device
			// if(scope.selectedElement === "dicomNetworkConnection" && scope.wholeDevice.dicomNetworkConnection){

   //              var dicomNetConnSchema 		    = DeviceService.getSchemaDicomNetworkConn();
	  //               scope.dicomNetConnSchema 	= dicomNetConnSchema.properties.dicomNetworkConnection.items;
   //                  $log.debug("dicomNetConnSchema=",dicomNetConnSchema);
   //              var index;

   //              angular.forEach(scope.wholeDevice.dicomNetworkConnection,function(value,key) {
   //              	if(value.cn === scope.selectedPart.dicomNetworkConnection){
   //              		index = key;
   //              	}
   //              });

   //              scope.dicomNetConnModel = scope.wholeDevice.dicomNetworkConnection[index];
   //              scope.dicomNetConnForm  = DeviceService.getFormDicomNetworkConn();
   //          }
            // cfpLoadingBar.set(cfpLoadingBar.status()+(0.2));
  /*          if(scope.selectedElement === "dicomNetworkAE" && scope.selectedPart.dicomNetworkAE){

                    scope.networkAeSchema   = DeviceService.getSchemaNetworkAe();
                    // scope.networkAeSchema 	= $schema;
                    
                    // $log.debug("networkAeSchema=",scope.networkAeSchema);
                    // $log.debug("networkAeSchema=",DeviceService.getSchemaNetworkAe());
                var networAeToEdit 	        = {};
                var toEditKey;

                angular.forEach(scope.wholeDevice.dicomNetworkAE,function(value,key) {
                	if(value.dicomAETitle === scope.selectedPart.dicomNetworkAE){
                		networAeToEdit      = value;
                        toEditKey           = key;
                	}
                });

                scope.transfcap             = scope.wholeDevice.dicomNetworkAE[toEditKey].dicomTransferCapability;
                scope.networkAeModel        = scope.wholeDevice.dicomNetworkAE[toEditKey];
                scope.networkAeForm         = DeviceService.getFormNetworkAe(scope.wholeDevice.dicomNetworkConnection);
                // $log.debug("geetFormNetworkAe=",DeviceService.getFormNetworkAe(scope.wholeDevice.dicomNetworkConnection));
/*                scope.networkAeForm = [
                                        "dicomAETitle",
                                        "select",
                                        {
                                        "key": "dicomNetworkConnectionReference",
                                        "type": "checkboxes",
                                        "titleMap": {
                                                  "/dicomNetworkConnection/0": "A",
                                                  "/dicomNetworkConnection/1": "B",
                                                  "/dicomNetworkConnection/2": "C"
                                                }
                                        },
                                        "dicomAssociationInitiator"
                                      ];*/
                // $log.debug("geetFormNetworkAe2=",scope.networkAeForm);
                // $log.debug("wholeDevice=",scope.wholeDevice);
                
                // angular.element(document.getElementById('SelectDicomTransferCapability'))
                //                    .html($compile("<div select-transfare-capability></div>")(scope));

            /*}
            */
            cfpLoadingBar.set(cfpLoadingBar.status()+(0.2));
            // $log.debug("before dicomTransferCapability in formEditAreaDir scope.selectedElement=",scope.selectedElement,"scope.selectedPart.dicomTransferCapability=",scope.selectedPart.dicomTransferCapability);
            // if(scope.selectedElement === 'dicomTransferCapability' && scope.selectedPart.dicomTransferCapability){
            //     $log.debug("dicomTransferCapability schema=",DeviceService.getShemaTransfareCap());
            // 	scope.transfareCapSchema = DeviceService.getShemaTransfareCap();
            //     scope.transfareCapForm   = DeviceService.getFormTransfareCap();
            //     angular.forEach(scope.wholeDevice.dicomNetworkAE,function(value1,key1) {
            //     	if(value1.dicomAETitle === scope.selectedPart.dicomNetworkAE){
		          //   	angular.forEach(scope.wholeDevice.dicomNetworkAE[key1].dicomTransferCapability,function(value,key) {
		          //   		if(value.cn === scope.selectedPart.dicomTransferCapability){
            //                     var model   = scope.wholeDevice.dicomNetworkAE[key1].dicomTransferCapability[key];
            //                     // $log.debug("transfercap model=",model);
            //                     scope.transfareCapModel = model;
		         	// 			// $log.debug("transfareCapModel set=",scope.transfareCapModel);
		          //   		}
		          //   	});
            //     	}
            //     });
            // }
            cfpLoadingBar.complete();
            //Warning the user if he start to change the device name
            setTimeout(function(){
                angular.element(document.getElementById('dicomDeviceName')).bind("click focus keydown",function(){
                    // $log.debug("this=",this.value);
                    // $log.debug("scope.wholeDevice=",scope.wholeDevice);
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