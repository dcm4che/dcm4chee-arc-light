"use strict";

myApp.factory('DeviceService', function($schema, $log, cfpLoadingBar, $http, $compile, schemas, $select) {

	/*
	*The time after how many miliseconds should disapper the message
	*/
	var msgTimeout = 10000;

	/*
	*Renders an unique random integer between 1 and 100 for the msg class
	*@m (array of Objects) array of the current messages
	*/
	var getUniqueRandomId = function(m){
		if(m && m[0]){					//If there is no message in the array just create some rendom number
			var buffer 		= 15; 		//Create a security to prevent infinite loop
			var isAvailable = false;	//Check parameter to see if some message has alredy the new id
			var id 			= 0;		

			while(!isAvailable && buffer>0){
				id = Math.floor((Math.random() * 100) + 1);	//Render int between 1 and 100
				angular.forEach(m, function(k,i){
					if(k.id === id){
						isAvailable = true;
					}
				});
				buffer--;
			}
			if(buffer===0 && isAvailable === true){
				return 999;
			}else{
				return id;
			}
		}else{
			return Math.floor((Math.random() * 100) + 1); //Render int between 1 and 100
		}
	};
	/*
	*Check if the same transfare capability is in the list (form validation)
	*@$scope (Object) angularjs $scope
	*/
	var checkTransfareRedondance = function($scope){
		var localObject = {};
		var valid = true;
		var message = "";

		angular.forEach($scope.transfcap, function(m,i){
			if($scope.transfcap[i] != $scope.transfareCapModel){	
				if(m.cn === $scope.transfareCapModel.cn){
					valid = false;
					message += "- Name have to be unique!<br/>";
				}
				if(m.dicomSOPClass === $scope.transfareCapModel.dicomSOPClass && m.dicomTransferRole === $scope.transfareCapModel.dicomTransferRole){
					valid = false;
					message += "- This combination of SOP Class and Transfer Role exists already<br/>";
				}
			}
		});

		return{
			"valid":valid,
			"msg":message
		}
	};

	/*
	*Puts msg to $scope.msg array
	*@$scope (Object) the angularjs scope
	*@m (Object) the new message object
	*/
	var msg = function($scope, m){
			var timeout = m.timeout || msgTimeout;
			var isInArray = false;
			var presentId = "";
        	angular.forEach($scope.msg,function(k,i){
        		if(k.text === m.text && k.status === m.status){
        			presentId = k.id;
        			isInArray = true;
        		}
        	});
        	if(isInArray){ //If the same message is already in the array, then just put the class pulse (To simulate a pulse) and remove it again
        		angular.element(document.getElementsByClassName("msg_"+presentId)).removeClass("slideInRight").addClass('pulse');
	        	setTimeout(function(){
	        		angular.element(document.getElementsByClassName("msg_"+presentId)).removeClass("pulse");
	        	},500);
        	}else{
	        	var id = getUniqueRandomId($scope.msg); 
	        	m.id = id;
		    	$scope.msg.push(m);
		    	msgCounter(id,timeout);
		    	setTimeout(function(){ 
		            $scope.$apply(function() {
		              removeMsg($scope, id);
		            });
	        	}, timeout);
        	}
        };

    /*
    *Add directive/html to the child of the given id @element
    *@$scope (Object): angular scope
    *@element (String): html-id where to put the directive
    *@markup (String): the markup (html) witch should bee put in the element
    *@hide (boolean): If it's true than after adding html hide the html-elemnt with the id @element
    */
    var addDirective = function($scope, element, markup, hide) {
        angular
            .element(document.getElementById(element)).html(
                $compile(markup)($scope)
        );
        if(hide){
	        angular
	            .element(document.getElementById(element))
	            .hide();
        }
    };
	
	/*
	*After getting the json from the form/model, the fields that were empty in the form are with null arrays in json
	*thats a problem for the serverside, thats why we need to clear the json object
	*@object (Object) JSON-Object with tho hole device
	*/
	var removeEmptyArrays = function(object){
			if(Object.prototype.toString.call(object) === '[object Array]'){
				angular.forEach(object,function(l,i){
					// console.log("before recursivelly call object[",i,"]=",object[i]);
					if(object[i]){
						object[i] = removeEmptyArrays(object[i]);
					}else{
						object.splice(i,1);
					}
				});
			}else{
				angular.forEach(object,function(m,k){
					if((Object.prototype.toString.call(m) === '[object Array]' && m[0]==undefined && m[1]==undefined) || m == ""){
						if(object[k] && (object[k][0] === undefined || object[k][0] === "" || !object[k][0])){
							delete object[k];
						}
					}
					if((Object.prototype.toString.call(m) === '[object Array]') || (object[k] !== null && typeof(object[k])=="object")){
						object[k] = removeEmptyArrays(object[k]);
					}
				});
			}
			return object;
	};
	var byString = function(o, s) {
	    s = s.replace(/\[(\w+)\]/g, '.$1'); // convert indexes to properties
	    s = s.replace(/^\./, '');           // strip a leading dot
	    var a = s.split('.');
	    for (var i = 0, n = a.length; i < n; ++i) {
	        var k = a[i];
	        if (k in o) {
	            o = o[k];
	        } else {
	            return;
	        }
	    }
	    return o;
	};

	/*
	*Adding false if the boolean fields are missing in json, acording to schema
	*object (Object) wholeDevice json object
	*schema (Object) JSON-Object schema
	*/
	var addCheckboxes = function(object, schema){
		angular.forEach(schema, function(m, i){
			if(m.type=== "array"){
				if(object[i]){
					angular.forEach(object[i], function(k,j){
						object[i][j] = addCheckboxes(k,schema[i].items.properties);
					});
				}
			}else{
				if(m.type==="boolean"){
					if(!object[i]){
						object[i]=false;
					}
				}
			}
		});
		return object;
	};

	/*
	*Takes the localschema makes a copy of it and from the copy removes everything but the "dicomNetworkAE" and from that removes transfareCapability
	*@return (Object) JSON-Object of schema with dicomNetworkAE
	*/
	var	getSchemaNetworkAe = function(){
			var localShema = {};
			try{
				angular.copy($schema,localShema);
                angular.forEach(localShema.properties,function(value,key) {
                	if(key!=="dicomNetworkAE"){
                		delete localShema.properties[key];
                	}
                });
                delete localShema.properties.dicomNetworkAE.items.properties.dicomTransferCapability;
				return localShema.properties.dicomNetworkAE.items;

			}catch(e){
				$log.error("Error on splitting the NetworkAe schema in factory DeviceService.js",e);
				return {};
			}
	};

	/*
	*Remove message from desplay (fadeOut) and from scope
	*/
	var removeMsg = function($scope, id){
		angular.forEach($scope.msg, function(m, k){
			if(m.id==id){
                $(".msg_container li."+"msg_"+id).fadeOut("400",function(){
					removeMsgFromArray($scope, id);
                });
			}
		});
	};

	/*
	*Show timeout as progressbar on the bottom  of the message
	*@id (int) messages id
	*/
	var msgCounter = function(id, timeout) {
		var cssClass = ".msg_"+id; 
		var x = 0;
		var interval = setInterval(function() {
		    $(cssClass).find(".progress").css("width",(x*10000/timeout)+"%");
		    if(x==(timeout/100)) {
		        clearInterval(interval);
		    }
		    x++;
		}, 100);
	};

	/*
	*Remove Message from scope array, is called from removeMsg()
	*/
	var removeMsgFromArray = function($scope, id){
		angular.forEach($scope.msg,function(m,k){
			if(m.id==id){
				$scope.msg.splice(k, 1);
			}
		});
	};

	/*
	*Add empty arrays to the model if there are in the schema and not i the model
	*@$model (Object) wholeDevice part of the scope.wholeDevice
	*@$schema (Object) the schema in the position where the whole device model is
	*/
	var addEmptyArrayToModel = function($model, $schema){
		// console.warn("in addEmptyArray $model=",$model);
		// console.log("$schema=",$schema);
		$model = $model || {};
		// if($model){	
        	angular.forEach($schema, function(k, i){
        		// console.log("k=",k);
        		// console.log("i=",i);
        		if(k !== null && typeof(k)==="object" && k.type && k.type==="array" && !$model[i]){
	                    $model[i] =  [];
	                    $model[i].push(null);
        		}
        	});
		// }
		return $model;
	};

	var addEmptyArrayFieldsPrivate = function(scope){
    		if(scope.dynamic_schema && scope.dynamic_schema.properties){
    			scope.dynamic_model = addEmptyArrayToModel(scope.dynamic_model, scope.dynamic_schema.properties);
    		}
	};

	var traverse = function(o, selectedElement, newSchema) {
	    for (var i in o) {
	        if(i != selectedElement){
		        if (o[i] !== null && typeof(o[i])=="object") {
		        	traverse(o[i], selectedElement, newSchema);
		        }
	        }else{
	        	newSchema[selectedElement] = o[i];
	        }
	    }
	   	return newSchema;
	};

	var replaceRef = function(schema, selectedElement, parent, grandpa){
		for (var i in schema) {
			if(i==="$ref" && (parent === $select[selectedElement].optionRef[0] || grandpa === $select[selectedElement].optionRef[0] || parent === $select[selectedElement].optionRef[1] || grandpa === $select[selectedElement].optionRef[1])){
				if(schema[i].toString().indexOf(".json")>-1){
					$http({
				        method: 'GET',
				        url: 'schema/'+schema[i]
				        // url: '../devices'
				    }).then(function successCallback(response) {
				    	if($select[selectedElement].optionRef.length > 1){
							if(parent === "items" && grandpa != "properties"){
					    		schema[grandpa] = response.data;
					    	}else{
					    		schema[parent] = response.data;
					    	}
					    	delete schema[i];
				    	}else{
					    	if(parent === "items" && grandpa != "properties"){
					    		schema[grandpa] = response.data;
					    	}else{
					    		schema[parent] = response.data;
					    	}
					    	delete schema[i];
				    	}
				    }, function errorCallback(response) {
				        $log.error("Error loading schema ref", response);
				    }); 
				}
			}else{
				if(schema[i] !== null && typeof(schema[i])=="object") {
					replaceRef(schema[i], selectedElement, i, parent);
				}
			}
		}
	};

		/*
		*Gets the form for the network dicomNetworkConnection with the connections as checkboxes in it
		*@connections (array) array of current device connections
		*@return (Object) JSON-Form
		*/
	var getFormNetworkAe = function(connections){

			var form = [];
			var connObject = {};

			angular.forEach(connections, function(m,k){
				connObject["/dicomNetworkConnection/"+k] = m.cn;
			});

			angular.forEach(getSchemaNetworkAe().properties,function(value,k){
				if(k === "dicomNetworkConnectionReference"){
					form.push("select");
					form.push({
                                "key": "dicomNetworkConnectionReference",
                                "type": "checkboxes",
                                "titleMap": connObject
                        		});
				}else{
					if(k === "dicomInstalled"){
						form.push({
							"key":"dicomInstalled",
							"type":"radiobuttons",
							"allowMultiple":false,
							"titleMap":[
								{
									"name":"Inherit"
								},
								{
									"value": true,
									"name":"True"
								},
								{
									"value": false,
									"name":"False"
								}
							]
						});
					}else{
						form.push(k);
					}
				}
			});

			return form;
		};
	return{

		/*
		*Generates Device schema from whole schema without dicomNetworkAE and dicomNetworkConnection
		*@return (Object) JSON-device schema
		*/
		getDeviceSchema : function(){
			var localShema = {};
				angular.copy(schemas.device, localShema);
				delete localShema.properties.dicomNetworkAE;
				delete localShema.properties.dicomNetworkConnection;
				delete localShema.properties.dcmAuditRecordRepository;
				delete localShema.properties.hl7Application;
				delete localShema.properties.dcmImageWriter;
				delete localShema.properties.dcmImageReader;
				delete localShema.properties.dcmAuditLogger;
				delete localShema.properties.dcmArchiveDevice;
				return localShema;
		},

		/*
		*Generates the device form from the schema
		*@return (Object) JSON-Form object
		*/
		getDeviceForm : function(){
			var localShema 	= {};
			var endArray 	= [];
			try{			
				angular.copy(schemas.device,localShema);
				angular.forEach($select,function(m,i){
					if(localShema.properties[i]){
						delete localShema.properties[i];
					}
				});
				console.log("localSchema=",angular.copy(localShema));
				angular.forEach(localShema.properties, function(m,i){
					// $log.debug("mitems$reflenght=",m.items.$ref.length);
					if(m && m.items && m.items.$ref){
						endArray.push({
							"key":i,
							"type":"ref",
							"json":m.items.$ref
						});
					}else{

					if(m.type != "array"){
						if(i==="dicomInstalled"){
							endArray.push({
								"key":"dicomInstalled",
								"type":"radios",
								"titleMap":[
									{
										"value": true,
										"name":"True"
									},
									{
										"value": false,
										"name":"False"
									}
								]
							});
						}else{
							endArray.push(i);
						}
					}else{
						endArray.push({
		                         "key":i,
		                         "add": "Add",
		                         "itmes":[
		                            i+"[]"
		                         ]
		                        });
					}
				}
				});
				return endArray;
			}catch(e){
				$log.error("Error on splitting the device schema in factory DeviceService.js",e);
				return {};
			}
		},

		/*
		*Check if two json objects are equal. (External Library used "jsondiffpatch" see index.html for the link)
		*obj1 (Object) the first JSON-Object
		*obj2 (Object) the second JSON-Object
		*/
		equalJSON : function(obj1, obj2){
       		if(obj1 && obj2){
				var diff 	= jsondiffpatch.diff(JSON.parse(angular.toJson(obj1)), JSON.parse(angular.toJson(obj2)));
	       		var equal 	= true;

	       		angular.forEach(diff, function(m, k) {
	       			if(Object.prototype.toString.call(m) === '[object Array]'){
		       			if(m[0] != undefined && m[0][0] == null){
		       				equal = equal && true;
		       			}else{
		       				equal = false;
		       			}
	       			}else{
	       				equal = false;
	       			}
	       		});
	       		return equal;
	       	}else{
	       		return true;
	       	}
		},

		/*
		*Calls the private function getSchemaNetworkAe()
		*@return (Function) getSchemaNetworkAe()
		*/
		getSchemaNetworkAe: function(){
			return getSchemaNetworkAe();
		},



		/*
		*Gets the schema for transfareCapabilitys
		*@return JSON schema or empty JSON when error
		*/
		getShemaTransfareCap:function(){
			try{
				return $schema.properties.dicomNetworkAE.items.properties.dicomTransferCapability.items;
			}catch(e){
				$log.error("Error on splitting the NetworkAe schema",e);
				return {};
			}
		},

		/*
		*Get form for transfare capabilitys
		*@return form as json object for transfarecapabilitys
		*/
		getFormTransfareCap:function(){

			var endArray = [];
			angular.forEach($schema.properties.dicomNetworkAE.items.properties.dicomTransferCapability.items.properties, function(k, i){
				if(i==="dicomTransferSyntax"){
					endArray.push({
		                         "key":"dicomTransferSyntax",
		                         "add": "Add new dicom transfare syntax",
		                         "itmes":[
		                            "dicomTransferSyntax[]"
		                         ]
		                        });
				}else{
					endArray.push(i);
				}
			});
			return endArray;
		},
		getSchemaDicomNetworkConn:function(){
			var localShema = {};
			try{
				angular.copy($schema,localShema);
                angular.forEach(localShema.properties,function(value,key) {

                	if(key!=="dicomNetworkConnection"){
                		delete localShema.properties[key];
                	}
                });
				return localShema;

			}catch(e){
				$log.error("Error on splitting the dicomNetworkConnection schema in factory DeviceService.js",e);
				return {};
			}
		},
		getFormDicomNetworkConn:function(){
			var endArray = [];
			angular.forEach($schema.properties.dicomNetworkConnection.items.properties,function(m, i){
				if(i==="dicomInstalled"){
					endArray.push({
						"key":"dicomInstalled",
						"type":"radiobuttons",
						"titleMap":[
							{
								"name":"Inherit"
							},
							{
								"value": true,
								"name":"True"
							},
							{
								"value": false,
								"name":"False"
							}
						]
					});
				}else{
					endArray.push(i);
				}
			});
			$log.debug("getformdicomnetworkconn endarray=",endArray);
			return endArray;

		},
		getDeviceModel: function(wholeDeviceModel){
			var localModel = {};
			try{
				angular.copy(wholeDeviceModel,localModel);
				delete localModel.dicomNetworkAE;
				delete localModel.dicomNetworkConnection;
				return localModel;
			}catch(e){
				$log.error("Error on splitting the device model in factory DeviceService.js",e);
				return {};
			}
		},
		clearJson: function($scope){
			try{
				$scope.wholeDevice = removeEmptyArrays($scope.wholeDevice);
			}catch(e){
				$log.error("Error on clearing json",e);
			}
			//return JSON.parse(angular.toJson($scope.wholeDevice));
		},
		addMissingCheckboxes: function($scope){
			try{
				if($scope.wholeDevice && $schema.properties){
					$scope.wholeDevice = addCheckboxes($scope.wholeDevice, $schema.properties);
				}
			}catch(e){
				$log.error("Error on adding checkboxes",e);
			}
		},
		clearReference: function(key, object){
			if(object.dicomNetworkAE){
				angular.forEach(object.dicomNetworkAE, function(m,k){
					if(m.dicomNetworkConnectionReference){
						angular.forEach(m.dicomNetworkConnectionReference, function(l,i){
							if(l){
								var exploded = l.split("/");
								if(exploded[exploded.length-1]>key){
									object.dicomNetworkAE[k].dicomNetworkConnectionReference[i] = "/dicomNetworkConnection/"+(exploded[exploded.length-1]-1);
								}
							}
						});
						angular.forEach(m.dicomNetworkConnectionReference, function(l,i){
							if(l){
								var exploded = l.split("/");
								if(key == exploded[exploded.length-1]){
									delete object.dicomNetworkAE[k].dicomNetworkConnectionReference.splice(i,1);
								}
							}
						});
					}

				});
			}
			return object;
		},
		validateForm: function($scope){
			var validForm = true;
        	var message = "";
        	var count 	= 0;
        	if($select[$scope.selectedElement] && $select[$scope.selectedElement].type === "array" && $scope.selectModel[$scope.selectedElement] && $scope.selectModel[$scope.selectedElement].length > 0){
        		angular.forEach($scope.selectModel[$scope.selectedElement],function(m,i){
        			if(m[$select[$scope.selectedElement].optionValue] === $scope.dynamic_model[$select[$scope.selectedElement].optionValue]){
        				count++;
        			}
        		});
        	}
        	if(count >1){
        		validForm = false;
        		$log.debug("in count if",$select[$scope.selectedElement].required[$select[$scope.selectedElement].optionValue]);
        		message += "- "+$select[$scope.selectedElement].required[$select[$scope.selectedElement].optionValue]+" have to be unique!<br/>";
        	}
        	if($select[$scope.selectedElement] && $select[$scope.selectedElement].required){
	        	angular.forEach($select[$scope.selectedElement].required,function(m,i){
	        		if(!$scope.dynamic_model[i] || $scope.dynamic_model[i] === ""){
	        			validForm = false;
	        			message += "- "+m+" is required!<br/>";
	        		}
	        	});
        	}
	        return{
	        	"valid": validForm,
	        	"message":message
	        };
		},
        deleteDevice: function($scope) {
        	// $log.debug("want to delete=",$scope.devicename);
        	cfpLoadingBar.start();
            $http.delete("../devices/" + $scope.devicename)
                .success(function(data, status, headers, config) {
                    //Delete the device from the dropdown list to.
                    angular.forEach($scope.devices, function(value, key) {
                        if (value.dicomDeviceName === $scope.devicename) {
                            $scope.devices.splice(key, 1);
                            $scope.devicename = "";
                        }
                    });
                    $scope.devicename       = "";
	                $scope.currentDevice    = "";
	                $scope.newDevice        = true;
	                $scope.middleBorder     = "";
	                $scope.lastBorder       = "";
	                $scope.editMode         = false;
	                $scope.deviceModel      = {};
	                $scope.wholeDevice      = {};
	                $scope.validForm        = true;
	                $scope.selectedElement  = "";
			        $scope.activeMenu       = "";
			        $scope.showCancel       = false;
			        $scope.showSave         = false;
			        $scope.lastBorder       = "";
			        msg($scope,{
			          "title":"Info",
			          "text":"Device deleted successfully!",
			          "status":"info"
					});
                angular.element(document.getElementById("add_dropdowns")).html("");
                    cfpLoadingBar.complete();
                    return true;
                })
                .error(function(data, status, headers, config) {
                    $log.error("Error deleting device", status);
                    msg($scope,{
			          "title":"error",
			          "text":"Error deleting device!",
			          "status":"error"
			        });
                    cfpLoadingBar.complete();
                    return false;
                });
        },
        save: function($scope) {
        	if($scope.wholeDevice.dicomDeviceName && $scope.wholeDevice.dicomDeviceName != undefined){
			    $http.put("../devices/" + $scope.wholeDevice.dicomDeviceName, $scope.wholeDevice)
	                .success(function(data, status, headers, config) {
	                    $scope.saved = true;
	                    cfpLoadingBar.complete();
	                    $scope.showCancel = false;
	                    $log.debug("$scope.devicename=",$scope.devicename);
	                    $log.debug("$scope.devices=",$scope.devices);
	                    addEmptyArrayFieldsPrivate($scope); //Add empty array fileds becouse there were cleared before save, if we don't then the array fields will be missing
					    msg($scope,{
					          "title":"Info",
					          "text":"Changes saved successfully!",
					          "status":"info"
					    });
	                })
	                .error(function(data, status, headers, config) {
	                    $log.error("Error sending data on put!", status);
	                    msg($scope,{
				          "title":"error",
				          "text":"Error, changes could not be saved!",
				          "status":"error"
				        });
	                    cfpLoadingBar.complete();
	                });
        	}else{
			    msg($scope,{
		          "title":"error",
		          "text":"Error, changes could not be saved!",
		          "status":"error"
		        });
        	}
        },
        saveWithChangedName:function($scope) {
            // msg($scope, {
            //     "title": "Info",
            //     "text": "Creting new device with a new name "+$scope.wholeDevice.dicomDeviceName+"!",
            //     "status": "info"
            // });
			if($scope.wholeDevice.dicomDeviceName != undefined){
            	$http
            	.put("../devices/" + $scope.wholeDevice.dicomDeviceName, $scope.wholeDevice)
                .success(function(data, status, headers, config) {
                	msg($scope, {
		                "title": "Info",
		                "text": "A new device with the name "+$scope.wholeDevice.dicomDeviceName+" created successfully!",
		                "status": "info"
		            });
		            
		            addEmptyArrayFieldsPrivate($scope);

                	$http
                	// .delete("../devices/" + $scope.devicename)
                	.delete("../devices/" + $scope.currentDevice)
	                .success(function(data, status, headers, config) {
	                    //Delete the device from the dropdown list to.
	                    angular.forEach($scope.devices, function(value, key) {
	                        if (value.dicomDeviceName === $scope.devicename) {

	                            value.dicomDeviceName = $scope.wholeDevice.dicomDeviceName;
	                            $scope.devicename = $scope.wholeDevice.dicomDeviceName;
	                        }
	                    });
	                   	$scope.currentDevice = $scope.wholeDevice.dicomDeviceName;
	                    $scope.saved = true;
	                    $scope.showCancel = true;
	                    cfpLoadingBar.complete();
	                    msg($scope,{
				          "title":"Info",
				          "text":"Old device deleted successfully!",
				          "status":"info"
				        });
	                })
	                .error(function(data, status, headers, config) {
	                    $log.error("Error deleting device", status);
	                   	msg($scope,{
				          "title":"Error",
				          "text":"Error deleting the old device!",
				          "status":"error"
				        });
	                    cfpLoadingBar.complete();
	                    return false;
	                });
                })
                .error(function(data, status, headers, config) {
                    $log.error("Error sending data on put!", status);
                	msg($scope, {
		                "title": "Error",
		                "text": "Error creating new device with the name "+$scope.wholeDevice.dicomDeviceName+"!",
		                "status": "error"
		            });
                    cfpLoadingBar.complete();
                });
            }else{
        	    msg($scope, {
	                "title": "Info",
	                "text": "Device name can not be undefined!",
	                "status": "info"
	            });
            }
        },
        msg:function($scope, m){
        	return msg($scope, m);
        },
        addDirectiveToDom: function($scope, element, markup){

        	switch(element){
        		case "add_dropdowns":

        			$scope.showDropdownLoader 		  = true;
            		$scope.middleBorder 			  = "";
        			addDirective($scope, element, markup, true);

		            var watchDropdownLoader = setInterval(function() {
		                if(angular.element(document.getElementById('endLoadSelect')).length > 0) {
		                    clearInterval(watchDropdownLoader);
		                    $scope.middleBorder 	  = "active_border";
		                    $scope.showDropdownLoader = false;
		                    angular.element(document.getElementById(element)).show();
		                }
		            }, 100);

        			break;
        		case "add_edit_area":

        			$scope.showFormLoader 			  = true;
        			$scope.lastBorder 				  = "";
        			addDirective($scope, element, markup, true);
        			var elementLenght = angular.element(document.querySelectorAll('#editDevice bootstrap-decorator')).length;
        			// $log.warn("elementLength=",elementLenght);
        			var watchFormnLoader = setInterval(function() {
        			$log.warn("elementLength in watch=",(document.querySelectorAll('#editDevice bootstrap-decorator')).length);
        			$log.warn("elementLength=",elementLenght);
		                if(angular.element(document.querySelectorAll('#editDevice bootstrap-decorator')).length === elementLenght) {
		                    clearInterval(watchFormnLoader);
		                	$log.debug("in if",element);
		                	// $scope.$apply(function(){
			                    $scope.lastBorder 	  = "active_border";
			                    $scope.showFormLoader = false;
		                		$log.debug("in apply");
		                	// });
		                    angular.element(document.getElementById(element)).show();
		                		$log.debug("document.getElementById(element)=",document.getElementById(element));
		                
		                }else{
		                	$log.debug("in else");
		                	elementLenght = angular.element(document.querySelectorAll('#editDevice bootstrap-decorator')).length;
		                }

		            }, 200);
        			break;
        		default:

        			addDirective($scope, element, markup, false);

        	}

        },
        /*
        *Remove empty json part (Needet when the user clicks cancle)
        *@element (Object) the tree from the wholeDevice where the empty part is
        *@key (String) thats the name of one requared field that shuld need to be checked so we don't delete something part thats not empty
        */
        removeEmptyPart : function(element, key){
        	$log.debug("element=",element,"key=",key);
        	if(element){
	        	angular.forEach(element, function(k, i){
	        		// $log.debug("k=",k);
	        		if(!k || (k && (!k[key] || k[key] === ""))){
	        			$log.debug("element about to delete=element[",i,"]=",element[i]);
	        			element.splice(i, 1);
	        		}
	        	});
        	}
        	$log.debug("ende removeEmptyPart");
        },
        /*
        *Add empty arrays to the wholeDevice, becouse without them sometimes the form doesn't show the array fields
        *$scope (Object) angularjs scope
        */
        addEmptyArrayFields : function($scope){
        	$log.debug("in addEmptyArrayFields dynamic_schema=",angular.copy($scope.dynamic_schema));
        	$log.debug("dynamic_model=",angular.copy($scope.dynamic_model));
        	addEmptyArrayFieldsPrivate($scope);
        },

		// getDescendantProp : function(obj, desc){
		//     var arr = desc.split(".");
		//     while(arr.length && (obj = obj[arr.shift()]));
		//     return obj;
		// },

		getObjectFromString : function($scope, value, key){
			var partsArray = value.optionRef;
					if(partsArray.length === 2){

						if($select[partsArray[0]].type==="object" && $scope.wholeDevice[partsArray[0]] && $scope.wholeDevice[partsArray[0]][partsArray[1]]){
							$scope.selectModel[key] = $scope.wholeDevice[partsArray[0]][partsArray[1]];
						}else{
							if($scope.selectedPart && $scope.wholeDevice[partsArray[0]]){
								angular.forEach($scope.wholeDevice[partsArray[0]], function(m, i){
									// $log.debug("fromstring m",m);
									if((m && m[$select[partsArray[0]].optionValue]) && (m[$select[partsArray[0]].optionValue] === $scope.selectedPart[partsArray[0]])){
										$scope.selectModel[key] = $scope.wholeDevice[partsArray[0]][i][partsArray[1]];
									}
								});
							}
						}
					}else{
						$log.warn("In TODO");
						//TODO I don't know if we need it, we will see
					}
					// $log.debug("in getObjectFromString selectModel",$scope.selectModel);
		},
		getForm:function(scope){
			if(scope.selectedElement === "dicomNetworkAE"){
				console.log("dicomNetworkAE=",scope.wholeDevice.dicomNetworkConnection);
				scope.form[scope.selectedElement]["form"] = getFormNetworkAe(scope.wholeDevice.dicomNetworkConnection);
			}else{
				console.warn("in getForm else",schemas);
				var timeout = 100;
				var waitforschema = setInterval(function(){
				var checkItems = (
	                                schemas[scope.selectedElement] && 
	                                schemas[scope.selectedElement][scope.selectedElement] && 
	                                schemas[scope.selectedElement][scope.selectedElement]["items"] && 
	                                schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement]&&
	                                schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement]["properties"]
                            	);
				var checkProp = (
	                                schemas[scope.selectedElement] && 
	                                schemas[scope.selectedElement][scope.selectedElement] && 
	                                schemas[scope.selectedElement][scope.selectedElement][scope.selectedElement] &&
	                                schemas[scope.selectedElement][scope.selectedElement][scope.selectedElement]["properties"]
                            	);
					if(checkItems || checkProp){
						console.warn("in getForm else if",schemas);
						clearInterval(waitforschema);	
						var endArray = [];
						if(checkItems){
							var schema = schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement]["properties"];
						}else{
							var schema = schemas[scope.selectedElement][scope.selectedElement][scope.selectedElement]["properties"];
						}
						// var schema = schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement]["properties"] ||
						// 			schemas[scope.selectedElement][scope.selectedElement][scope.selectedElement]["properties"];
						console.log("schema =",angular.copy(schema));
						angular.forEach(schema, function(m,i){

							if(i === "dicomNetworkConnectionReference"){
								var connObject = [];
								$log.debug("scope.wholeDevice.dicomNetworkConnection=",scope.wholeDevice.dicomNetworkConnection);
								angular.forEach(scope.wholeDevice.dicomNetworkConnection, function(m,k){
									$log.debug("m=",m);
									$log.debug("k=",k);
									var path = {
										value:"/dicomNetworkConnection/"+k,
										name:m.cn
									};
									// path["/dicomNetworkConnection/"+k] = m.cn;

									connObject.push(path);
									$log.debug("connObject=",connObject);
								});
								$log.debug("connObject=",connObject);
								endArray.push("select");
								var temp = {
			                                "key": "dicomNetworkConnectionReference",
			                                "type": "checkboxes",
			                                "titleMap": angular.copy(connObject)
			                        		};
			                    $log.debug("temp=",temp);
								endArray.push(temp);
								$log.debug("endarrayinif=",JSON.stringify(endArray));
							}else{

								if(m.type === "array"){
									endArray.push({
					                         "key":i,
					                         "add": "Add",
					                         "itmes":[
					                            i+"[]"
					                         ]
					                        });
								}else{
									if(i==="dicomInstalled"){
										endArray.push({
											"key":"dicomInstalled",
											"type":"radios",
											"titleMap":[
												{
													"value": true,
													"name":"True"
												},
												{
													"value": false,
													"name":"False"
												}
											]
										});
									}else{
										endArray.push(i);
									}

								}
							}
						});
						// return Object.keys(schemas[scope.selectedElement][scope.selectedElement].items[scope.selectedElement].properties);
						console.log("endarray=",endArray);
						console.log("endarray=",JSON.stringify(endArray));
						scope.form[scope.selectedElement] = scope.form[scope.selectedElement] || {};
						scope.form[scope.selectedElement]["form"] = endArray;
						$log.debug("in serviceFact getschema scope.formselectedelementform=",scope.form[scope.selectedElement]["form"]);
						// return endArray;
					}
					if(timeout>0){
						timeout--;
					}else{
						console.warn("in getForm timeout",schemas);
						clearInterval(waitforschema);
					}
				},10);
			}
		},
		getSchema : function(selectedElement){
			// console.log("in geetSchema selectedElement=",selectedElement);
			// console.log("in geetSchema select=",$select);
			var localSchema = {};

			if($select[selectedElement].optionRef.length > 1){
				// $log.warn("in if getschema");
				schemas[selectedElement] = schemas[selectedElement] || {};
				// console.log("Object.keys(obj).length=",Object.keys(schemas[selectedElement]).length);

				if(Object.keys(schemas[$select[selectedElement].optionRef[1]]).length < 1){
					// $log.warn("in if 2 getschema",$select[selectedElement].optionRef[1]);
					// $log.warn("in if 2 getschema",schemas);
					// $log.warn("in if 2 getschema",Object.keys(schemas[$select[selectedElement].optionRef[1]]).length);

					var refs = $select[selectedElement].optionRef;
					// angular.copy(schemas.whole, localSchema);
					var localSchema2 = {};
					// if(Object.keys(schemas[$select[selectedElement].optionRef[0]]).length < 1){
						angular.copy(schemas.device, localSchema);
						// $log.debug("$select[selectedElement].optionRef[0]=",$select[selectedElement].optionRef[0]);
						// $log.debug("before traversecall schemas[$select[selectedElement].optionRef[0]=",schemas[$select[selectedElement].optionRef[0]]);
						if(!schemas[$select[selectedElement].optionRef[0]]){
							schemas[$select[selectedElement].optionRef[0]] = {};
						}
						traverse(localSchema, $select[selectedElement].optionRef[0], schemas[$select[selectedElement].optionRef[0]]);
						// $log.warn("after first traverse=",angular.copy(schemas));
						replaceRef(schemas[$select[selectedElement].optionRef[0]], selectedElement, "", "");
						// $log.warn("after first replace=",angular.copy(schemas));
					// }
					// console.warn("schemas[$select[selectedElement].optionRef[0]]=",schemas[$select[selectedElement].optionRef[0]]);
					
					var waitfirstlevel = setInterval(function(){
						// console.log("schemas[$select[selectedElement].optionRef[0]]=",angular.copy(schemas[$select[selectedElement].optionRef[0]][$select[selectedElement].optionRef[0]][$select[selectedElement].optionRef[0]]));
						if(	(
								schemas[$select[selectedElement].optionRef[0]] &&
								schemas[$select[selectedElement].optionRef[0]][$select[selectedElement].optionRef[0]] &&
								schemas[$select[selectedElement].optionRef[0]][$select[selectedElement].optionRef[0]]["items"] &&
								schemas[$select[selectedElement].optionRef[0]][$select[selectedElement].optionRef[0]]["items"][$select[selectedElement].optionRef[0]]
								) 
							|| 
								(	
									schemas[$select[selectedElement].optionRef[0]] &&
									schemas[$select[selectedElement].optionRef[0]][$select[selectedElement].optionRef[0]] &&
									schemas[$select[selectedElement].optionRef[0]][$select[selectedElement].optionRef[0]][$select[selectedElement].optionRef[0]] &&
									schemas[$select[selectedElement].optionRef[0]][$select[selectedElement].optionRef[0]][$select[selectedElement].optionRef[0]]["properties"]								
								)
							){	
								clearInterval(waitfirstlevel);						
								angular.copy(schemas[$select[selectedElement].optionRef[0]], localSchema2);
								traverse(localSchema2, $select[selectedElement].optionRef[1], schemas[$select[selectedElement].optionRef[1]]);
								// console.log("after traverse schemas",angular.copy(schemas));
								replaceRef(schemas[$select[selectedElement].optionRef[1]], selectedElement, "", "");
								// console.log("localSchema2",localSchema2);
							// if(schemas[$select[selectedElement].optionRef[0]][$select[selectedElement].optionRef[0]]["properties"]){
							// 	// replaceRef(schemas[$select[selectedElement].optionRef[1]], selectedElement, "", "");
							// }else{

							// 	// clearInterval(waitfirstlevel);						
							// 	// angular.copy(schemas[$select[selectedElement].optionRef[0]], localSchema2);
							// 	// traverse(localSchema2, $select[selectedElement].optionRef[1], schemas[$select[selectedElement].optionRef[1]]);
							// 	// console.log("after traverse schemas",angular.copy(schemas));
							// 	// replaceRef(schemas[$select[selectedElement].optionRef[1]], selectedElement, "", "");
							// 	// console.log("localSchema2",localSchema2);
							// 	// replaceRef(schemas[$select[selectedElement].optionRef[1]], selectedElement, "", "");
							// }
								return schemas[selectedElement];
						}else{
							// console.log("waiting");
						}
					},10);
				}else{
					return schemas[selectedElement];
				}
			}else{
				// $log.warn("in else getschema");
				schemas[selectedElement] = schemas[selectedElement] || {};
				if(Object.keys(schemas[selectedElement]).length < 1){
					// console.log("in if");
					angular.copy(schemas.device, localSchema);
					// $log.debug("selectedElement=",selectedElement);
					// $log.warn("schemas=",schemas);
					traverse(localSchema, selectedElement, schemas[selectedElement]);
					// console.log("schema after traverse=",angular.copy(schemas));
					replaceRef(schemas[selectedElement], selectedElement, "", "");
					// console.log("schema after replace=",angular.copy(schemas));
					return schemas[selectedElement];
					// replaceRef(schemas.whole, selectedElement, "","");
				}else{
					// console.log("schemas[selectedElement]=",schemas[selectedElement]);
					return schemas[selectedElement];
				}

			}
			// $log.debug("schemas=",schemas);
			$log.debug("schemasLAST=",schemas);
			// schemas.whole = {};
			
		},

		setFormModel : function(scope){
			$log.warn("in setFormModel",scope.wholeDevice[$select[scope.selectedElement].optionRef[0]]);
			$log.debug("selectedElement=",scope.selectedElement);
			if($select[scope.selectedElement].type === "array"){

				if($select[scope.selectedElement].optionRef.length > 1){
					if(scope.wholeDevice[$select[scope.selectedElement].optionRef[0]]){
						$log.warn("$select[$select[scope.selectedElement].optionRef[0]].type=",$select[$select[scope.selectedElement].optionRef[0]].type);
						if($select[$select[scope.selectedElement].optionRef[0]].type==="object"){
							// angular.forEach(scope.wholeDevice[$select[scope.selectedElement].optionRef[0]], function(m, i){
								// if(scope.selectedPart[$select[scope.selectedElement].optionRef[0]] === scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i][$select[$select[scope.selectedElement].optionRef[0]].optionValue]){
									$log.debug("in first part without foreach=",scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][$select[scope.selectedElement].optionRef[1]]);
									angular.forEach(scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][$select[scope.selectedElement].optionRef[1]],function(k, j){
										// console.log("in second foreach k=",k);
										if(scope.selectedPart[$select[scope.selectedElement].optionRef[1]] === scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][$select[scope.selectedElement].optionRef[1]][j][$select[scope.selectedElement].optionValue]){
											if(!scope.form[scope.selectedElement]){
												scope.form[scope.selectedElement] = {};
											}
											// scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][$select[scope.selectedElement].optionRef[1]][j] = 
											// addEmptyArrayToModel(
											// 	scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][$select[scope.selectedElement].optionRef[1]][j],
											// 	scope.form[scope.selectedElement].schema);
											scope.form[scope.selectedElement]["model"] = scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][$select[scope.selectedElement].optionRef[1]][j];
											// console.log("scope.form[scope.selectedElement]1",scope.form[scope.selectedElement].model);
										}

									});
								// }
							// });
						}else{

							angular.forEach(scope.wholeDevice[$select[scope.selectedElement].optionRef[0]], function(m, i){
								if(scope.selectedPart[$select[scope.selectedElement].optionRef[0]] === scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i][$select[$select[scope.selectedElement].optionRef[0]].optionValue]){
									angular.forEach(scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i][$select[scope.selectedElement].optionRef[1]],function(k, j){
										// console.log("in second foreach k=",k);
										if(scope.selectedPart[$select[scope.selectedElement].optionRef[1]] === scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i][$select[scope.selectedElement].optionRef[1]][j][$select[scope.selectedElement].optionValue]){
											if(!scope.form[scope.selectedElement]){
												scope.form[scope.selectedElement] = {};
											}
											// scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i][$select[scope.selectedElement].optionRef[1]][j] = 
											// addEmptyArrayToModel(
											// 	scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i][$select[scope.selectedElement].optionRef[1]][j],
											// 	scope.form[scope.selectedElement].schema);
											scope.form[scope.selectedElement]["model"] = scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i][$select[scope.selectedElement].optionRef[1]][j];
											console.log("scope.form[scope.selectedElement]2",scope.form[scope.selectedElement].model);
										}

									});
								}
							});
						}
					}
				}else{
					if(scope.wholeDevice[$select[scope.selectedElement].optionRef[0]]){
						console.log("in if setFormmodel",scope.selectedElement);
						angular.forEach(scope.wholeDevice[$select[scope.selectedElement].optionRef[0]], function(m, i){
							if(scope.selectedPart[scope.selectedElement] === scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i][$select[scope.selectedElement].optionValue]){
								// console.log("in if2setFormmodel");
								if(!scope.form[scope.selectedElement]){
									scope.form[scope.selectedElement] = {};
								}
								// scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i] = 
								// addEmptyArrayToModel(
								// 	scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i],
								// 	scope.form[scope.selectedElement].schema);
								scope.form[scope.selectedElement]["model"] = scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i];
								console.log("scope.form[scope.selectedElement]3",scope.form[scope.selectedElement].model);
							}
						});
					}
				}
			}else{
				$log.warn("in else setFormModel");
				if($select[scope.selectedElement].optionRef.length > 1){
					$log.warn("in todo serviceFact setFormModel ,scope.selectedElement=",scope.selectedElement);
					$log.debug("scope.wholeDevice",scope.wholeDevice);
					$log.debug("$select[scope.selectedElement].optionRef[0]",$select[scope.selectedElement].optionRef[0]);
					$log.debug("scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][$select[scope.selectedElement].optionRef[1]]",scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][0][$select[scope.selectedElement].optionRef[1]]);
					if(scope.wholeDevice[$select[scope.selectedElement].optionRef[0]]){
						if($select[$select[scope.selectedElement].optionRef[0]].type==="object"){
							// angular.forEach(scope.wholeDevice[$select[scope.selectedElement].optionRef[0]], function(m, i){
								// if(scope.selectedPart[$select[scope.selectedElement].optionRef[0]] === scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i][$select[$select[scope.selectedElement].optionRef[0]].optionValue]){
									$log.debug("in first part without foreach=",scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][$select[scope.selectedElement].optionRef[1]]);
									angular.forEach(scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][$select[scope.selectedElement].optionRef[1]],function(k, j){
										// console.log("in second foreach k=",k);
										if(scope.selectedPart[$select[scope.selectedElement].optionRef[1]] === scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][$select[scope.selectedElement].optionRef[1]][j][$select[scope.selectedElement].optionValue]){
											if(!scope.form[scope.selectedElement]){
												scope.form[scope.selectedElement] = {};
											}
											console.log("scope.form[scope.selectedElement]0=",angular.copy(scope.form));
											console.log("scope.form[scope.selectedElement]=",scope.form);
											// scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][$select[scope.selectedElement].optionRef[1]][j] = 
											// addEmptyArrayToModel(
											// 	scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][$select[scope.selectedElement].optionRef[1]][j],
											// 	scope.form[scope.selectedElement].schema);
											scope.form[scope.selectedElement]["model"] = scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][$select[scope.selectedElement].optionRef[1]][j];
											console.log("scope.form[scope.selectedElement]4",scope.form[scope.selectedElement].model);
										}

									});
								// }
							// });
						}else{
							angular.forEach(scope.wholeDevice[$select[scope.selectedElement].optionRef[0]], function(m, i){
								if(scope.selectedPart[$select[scope.selectedElement].optionRef[0]] === scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i][$select[$select[scope.selectedElement].optionRef[0]].optionValue]){
									angular.forEach(scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i][$select[scope.selectedElement].optionRef[1]],function(k, j){
										// console.log("in second foreach k=",k);
										if(scope.selectedPart[$select[scope.selectedElement].optionRef[1]] === scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i][$select[scope.selectedElement].optionRef[1]][j][$select[scope.selectedElement].optionValue]){
											if(!scope.form[scope.selectedElement]){
												scope.form[scope.selectedElement] = {};
											}
											// setTimeout(function(){
												// console.log("scope.form[scope.selectedElement]1=",angular.copy(scope.form));
												// console.log("scope.form[scope.selectedElement]=",scope.form);
												// console.log("in timeout j",j); 
												// console.log("$select[scope.selectedElement].optionRef",$select[scope.selectedElement].optionRef); 
												// console.log("scope.wholeDevice[$select[scope.selectedElement].optionRef[0]]=",scope.wholeDevice[$select[scope.selectedElement].optionRef[0]]);
												// console.log("scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i]=",scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i]);
												// console.log("scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i][$select[scope.selectedElement].optionRef[1]]=",scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i][$select[scope.selectedElement].optionRef[1]]);
												// console.log("scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i][$select[scope.selectedElement].optionRef[1]][j]=",scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i][$select[scope.selectedElement].optionRef[1]][j]);
												// scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i][$select[scope.selectedElement].optionRef[1]] = 
												// addEmptyArrayToModel(
												// 	scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i][$select[scope.selectedElement].optionRef[1]],
												// 	scope.form[scope.selectedElement].schema);
												scope.form[scope.selectedElement]["model"] = scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i][$select[scope.selectedElement].optionRef[1]];
												console.log("scope.form[scope.selectedElement][model]5",scope.form[scope.selectedElement]["model"]);
											// },1000);
										}

									});
								}
							});
						}
					}
				}else{
					if(scope.wholeDevice[$select[scope.selectedElement].optionRef[0]]){
						if(!scope.form[scope.selectedElement]){
							scope.form[scope.selectedElement] = {};
						}
						// scope.wholeDevice[$select[scope.selectedElement].optionRef[0]] = 
						// 		addEmptyArrayToModel(
						// 			scope.wholeDevice[$select[scope.selectedElement].optionRef[0]],
						// 			scope.form[scope.selectedElement].schema);
						scope.form[scope.selectedElement]["model"] = scope.wholeDevice[$select[scope.selectedElement].optionRef[0]];
					}
				}
			}
		},
		createPart : function($scope){
			if($select[$scope.selectedElement].optionRef.length > 1){
				$scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]] = $scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]] || {};
					if($select[$select[$scope.selectedElement].optionRef[0]].type === "array"){
						angular.forEach($scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]],function(k,i){
							if(k[$select[$select[$scope.selectedElement].optionRef[0]].optionValue] === $scope.selectedPart[$select[$scope.selectedElement].optionRef[0]]){
								if($select[$select[$scope.selectedElement].optionRef[1]].type === "array"){
									$scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]][i][$select[$scope.selectedElement].optionRef[1]] = $scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]][i][$select[$scope.selectedElement].optionRef[1]] || [];
									$scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]][i][$select[$scope.selectedElement].optionRef[1]].push($scope.dynamic_model);
								}else{
									$scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]][i][$select[$scope.selectedElement].optionRef[1]] = $scope.dynamic_model;
								}
							}
						});
					}else{
						$scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]] = $scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]] || {};
						if($select[$select[$scope.selectedElement].optionRef[1]].type === "array"){
							if(!$scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]][$select[$scope.selectedElement].optionRef[1]]){
								$scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]][$select[$scope.selectedElement].optionRef[1]] = [];
							}
							$scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]][$select[$scope.selectedElement].optionRef[1]].push($scope.dynamic_model);
						}else{
							if(!$scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]][$select[$scope.selectedElement].optionRef[1]]){
								$scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]][$select[$scope.selectedElement].optionRef[1]] = {};
							}
							$scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]][$select[$scope.selectedElement].optionRef[1]] = $scope.dynamic_model;
						}
					}

			}else{
				if($select[$scope.selectedElement].type === "array"){
					$scope.wholeDevice[$scope.selectedElement] = $scope.wholeDevice[$scope.selectedElement] || [];
					$scope.wholeDevice[$scope.selectedElement].push($scope.dynamic_model);
				}else{
					$scope.wholeDevice[$scope.selectedElement] = $scope.dynamic_model;
				}
			}
			addEmptyArrayFieldsPrivate($scope);
		},
		cancle : function($scope){
			if($select[$scope.selectedElement].optionRef.length > 1){
				if($select[$select[$scope.selectedElement].optionRef[0]].type === "array"){
					angular.forEach($scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]],function(k,i){
						if(k[$select[$select[$scope.selectedElement].optionRef[0]].optionValue] === $scope.selectedPart[$select[$scope.selectedElement].optionRef[0]]){
							if($select[$select[$scope.selectedElement].optionRef[1]].type === "array"){
								angular.forEach($scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]][i][$select[$scope.selectedElement].optionRef[1]],function(m,j){
									if(m[$select[$select[$scope.selectedElement].optionRef[1]].optionValue] === $scope.dynamic_model[$select[$select[$scope.selectedElement].optionRef[1]].optionValue]){
										$scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]][i][$select[$scope.selectedElement].optionRef[1]].splice(j,1);
									}
								});
							}
						}
					});
				}else{
					if($select[$select[$scope.selectedElement].optionRef[1]].type === "array"){
						angular.forEach($scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]][$select[$scope.selectedElement].optionRef[1]],function(m,j){
							if(m[$select[$select[$scope.selectedElement].optionRef[1]].optionValue] === $scope.dynamic_model[$select[$select[$scope.selectedElement].optionRef[1]].optionValue]){
								$scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]][$select[$scope.selectedElement].optionRef[1]].splice(j,1);
							}
						});
					}
				}
			}else{
				if($select[$select[$scope.selectedElement].optionRef[0]].type === "array"){
					angular.forEach($scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]],function(k,i){
						if(k[$select[$select[$scope.selectedElement].optionRef[0]].optionValue] === $scope.dynamic_model[$select[$select[$scope.selectedElement].optionRef[0]].optionValue]){
							$scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]].splice(i,1);
						}
					});
				}
			}
		}

	}

});