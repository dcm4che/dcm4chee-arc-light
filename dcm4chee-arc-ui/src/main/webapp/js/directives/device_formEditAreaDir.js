"use strict";

myApp.directive("editArea",function($schema, cfpLoadingBar, $log, DeviceService, $compile, schemas, $select){
	return{
		restrict:"A",
		templateUrl: 'templates/device_form.html',
		link: function(scope,elm,attr) {
    		// cfpLoadingBar.start();
            cfpLoadingBar.set(cfpLoadingBar.status()+(0.2));
            scope.form = scope.form || {};
            scope.form[scope.selectedElement] = scope.form[scope.selectedElement] || {};
            $log.debug("scope.form=",scope.form);

            if(scope.selectedElement === "device"){

            }else{
                console.log("in directive schema=",DeviceService.getSchema(scope.selectedElement));
                // console.log("schemas",schemas, "scope.form=",scope.form,",schemas[scope.selectedElement]=",schemas[scope.selectedElement][scope.selectedElement]["items"]);
                // $log.debug("$select[scope.selectedElement]=",$select[scope.selectedElement]);
                if($select[scope.selectedElement].type==="array"){
                    var timeout = 50;
                    var wait = setInterval(function(){
                        $log.debug("1schemas=",schemas);
                        $log.debug("1scope.selectedElement=",scope.selectedElement);
                        // $log.debug("scope.selectedElement=",schemas[scope.selectedElement][scope.selectedElement][scope.selectedElement]);
                        // $log.debug("length=",schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement]);
                        console.log("schemas=",JSON.parse(JSON.stringify(console.dir(schemas))));
                        // $log.debug("schemas[scope.selectedElement]=",schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement]);
                        
                        if(
                            schemas[scope.selectedElement] && 
                            schemas[scope.selectedElement][scope.selectedElement] && 
                            schemas[scope.selectedElement][scope.selectedElement]["items"] && 
                            schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement]
                           ){
                            clearInterval(wait);
                            DeviceService.setFormModel(scope);
                            scope.form[scope.selectedElement]["schema"] = schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement];
                            $log.debug("$select[scope.selectedElement].parentOf",$select[scope.selectedElement].parentOf);
                            if($select[scope.selectedElement].parentOf){
                                $log.debug("in if");
                                angular.forEach($select[scope.selectedElement].parentOf,function(m,i){
                                    delete scope.form[scope.selectedElement]["schema"].properties[$select[scope.selectedElement].parentOf[i]];
                                });
                            }

                            $log.debug("1scope.form[scope.selectedElement][schema]",scope.form[scope.selectedElement]["schema"]);
                            // scope.form[scope.selectedElement]["model"]  = scope.wholeDevice;
                        }else{
                            console.log("waiting", JSON.parse(JSON.stringify(console.dir(schemas))));
                            // $log.debug("waiting", schemas[dicomTransferCapability]);
                            // console.log("scope.selectedElemen=", JSON.parse(JSON.stringify(console.dir(scope.selectedElement))));
                        }
                        if(timeout<0){
                            clearInterval(wait);
                        }else{
                            $log.debug("temout=",timeout);
                            timeout--;
                        }
                    },100);
                }else{
                    var wait2 = setInterval(function(){
                        $log.debug("2schemas=",schemas);
                        $log.debug("2scope.selectedElement=",scope.selectedElement);
                        $log.debug("scope.selectedElement=",schemas[scope.selectedElement][scope.selectedElement][scope.selectedElement]);
                        // $log.debug("length=",schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement]);
                        if(schemas[scope.selectedElement][scope.selectedElement][scope.selectedElement]){
                            clearInterval(wait2);
                            DeviceService.setFormModel(scope);
                            scope.form[scope.selectedElement]["schema"] = schemas[scope.selectedElement][scope.selectedElement][scope.selectedElement];
                            // if($select[scope.selectedElement].parentOf){
                            //     angular.forEach($select[scope.selectedElement].parentOf,function(m,i){
                            //         delete scope.form[scope.selectedElement]["schema"].properties[$select[scope.selectedElement].parentOf[i]];
                            //     });
                            // }
                            $log.debug("2scope.form[scope.selectedElement][schema]",scope.form[scope.selectedElement]["schema"]);
                            // scope.form[scope.selectedElement]["model"]  = scope.wholeDevice;
                        }else{
                            $log.debug("waiting");
                        }
                    },100);
                }

            }
            
            $log.debug("selectedElement=",scope.selectedElement);
            $log.debug("scope.form=",scope.form);

            // $log.debug("in formEditAreaDir");
			if(scope.selectedElement === "device"){
                // $log.debug("status=",cfpLoadingBar.status());
                // $log.debug("in formEditArea wholeDevice",scope.wholeDevice);
                // $log.debug("in formEditArea scope.currentDevice=",scope.currentDevice);
                cfpLoadingBar.set(cfpLoadingBar.status()+(0.1));

				scope.deviceSchema = DeviceService.getDeviceSchema();
                $log.debug("scope.deviceSchema",scope.deviceSchema);
                if(!scope.deviceSchema){
                    // DeviceService.getDeviceSchema();
                    var timeOut = 0;
                    var schema  = {};
                    var waitForShema = setInterval(function(){
                        // $log.debug("tiemOut =",timeOut);
                        $log.debug("inwait scheam=",DeviceService.getDeviceSchema());
                        schema  = DeviceService.getDeviceSchema();
                        if(schema){
                            clearInterval(waitForShema);
                            scope.deviceSchema = schema;
                        }
                        if(timeOut > 100){  //If the program is waiting more than 10 sec than break up and show alert
                            clearInterval(waitForShema);
                            $log.error("Timeout error!");
                            vex.dialog.alert("Timeout error, can't get device information, please reload the page and try again!");
                        }
                        timeOut++;
                    }, 100);
                }
                //If the wholeDevice is undefined wait for it, otherwaise assigne it to deviceModel
                if(scope.wholeDevice === undefined || scope.wholeDevice.dicomDeviceName != scope.currentDevice){
                    var timeOut = 0;
                    var waitForWholeDevice = setInterval(function(){
                        // $log.debug("tiemOut =",timeOut);
                        if(scope.wholeDevice !== undefined){
                            clearInterval(waitForWholeDevice);
                            scope.deviceModel = scope.wholeDevice;
                        }
                        if(timeOut > 100){  //If the program is waiting more than 10 sec than break up and show alert
                            clearInterval(waitForWholeDevice);
                            $log.error("Timeout error!");
                            vex.dialog.alert("Timeout error, can't get device information, please reload the page and try again!");
                        }
                        timeOut++;
                    }, 100);
                }else{
                    // if(!scope.wholeDevice.dicomInstitutionCode){
                    //     $log.debug("in if add institutecode");
                    //     scope.wholeDevice["dicomInstitutionCode"] =  [];
                    //     scope.wholeDevice.dicomInstitutionCode.push(null);
                    // }
                    // $log.debug("123wholeDevice=",scope.wholeDevice);
                    // $log.debug("1deviceModel=",scope.deviceModel);
                    // if(!scope.deviceModel){
                    scope.deviceModel = scope.wholeDevice;
                    // }
                    // $log.debug("2deviceModel=",scope.deviceModel);
                    // setTimeout(function(){
                    //     if(scope.currentDevice == "CHANGE_ME"){
                    //         $log.debug("#dicomDeviceName=",$("#dicomDeviceName"));
                    //         $("#dicomDeviceName").css({"pointer-events":"auto","background":"white"});
                    //     }
                    // },500);
                }
                cfpLoadingBar.set(cfpLoadingBar.status()+(0.2));
                // $log.debug("1 scope.deviceForm=",scope.deviceForm);

                scope.deviceForm = DeviceService.getDeviceForm();
                // $log.debug("2 scope.deviceForm=",scope.deviceForm);
                // scope.deviceForm.startEmpty = true;
            }
            
			//If the first selectbutton was changed than show the NetworkConnection of dhe device
			if(scope.selectedElement === "dicomNetworkConnection" && scope.wholeDevice.dicomNetworkConnection){

                var dicomNetConnSchema 		    = DeviceService.getSchemaDicomNetworkConn();
	                scope.dicomNetConnSchema 	= dicomNetConnSchema.properties.dicomNetworkConnection.items;
                    $log.debug("dicomNetConnSchema=",dicomNetConnSchema);
                var index;

                angular.forEach(scope.wholeDevice.dicomNetworkConnection,function(value,key) {
                	if(value.cn === scope.selectedPart.dicomNetworkConnection){
                		index = key;
                	}
                });

                scope.dicomNetConnModel = scope.wholeDevice.dicomNetworkConnection[index];
                scope.dicomNetConnForm  = DeviceService.getFormDicomNetworkConn();
            }
            cfpLoadingBar.set(cfpLoadingBar.status()+(0.2));
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
            $log.debug("before dicomTransferCapability in formEditAreaDir scope.selectedElement=",scope.selectedElement,"scope.selectedPart.dicomTransferCapability=",scope.selectedPart.dicomTransferCapability);
            if(scope.selectedElement === 'dicomTransferCapability' && scope.selectedPart.dicomTransferCapability){
                $log.debug("dicomTransferCapability schema=",DeviceService.getShemaTransfareCap());
            	scope.transfareCapSchema = DeviceService.getShemaTransfareCap();
                scope.transfareCapForm   = DeviceService.getFormTransfareCap();
                angular.forEach(scope.wholeDevice.dicomNetworkAE,function(value1,key1) {
                	if(value1.dicomAETitle === scope.selectedPart.dicomNetworkAE){
		            	angular.forEach(scope.wholeDevice.dicomNetworkAE[key1].dicomTransferCapability,function(value,key) {
		            		if(value.cn === scope.selectedPart.dicomTransferCapability){
                                var model   = scope.wholeDevice.dicomNetworkAE[key1].dicomTransferCapability[key];
                                // $log.debug("transfercap model=",model);
                                scope.transfareCapModel = model;
		         				// $log.debug("transfareCapModel set=",scope.transfareCapModel);
		            		}
		            	});
                	}
                });
            }
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
