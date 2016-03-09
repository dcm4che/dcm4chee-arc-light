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
			$log.debug("in if unique id=",id);
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
	*Check if the same transfare capability is in the list
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
			$log.debug("is array =",(Object.prototype.toString.call(object) === '[object Array]'));
			console.log("removeEmptyArrays called");
			if(Object.prototype.toString.call(object) === '[object Array]'){
				angular.forEach(object,function(l,i){
					console.log("before recursivelly call object[",i,"]=",object[i]);
					if(object[i]){
						object[i] = removeEmptyArrays(object[i]);
					}else{
						object.splice(i,1);
					}
				});
			}else{

				angular.forEach(object,function(m,k){
					if((Object.prototype.toString.call(m) === '[object Array]' && m[0]==undefined && m[1]==undefined) || m == ""){
						console.log("about to delete object[",k,"]=",object[k]);
						delete object[k];
					}
				});
				// $log.debug("hasneobject",object);
				// $log.debug("hasnetworkAE",(object.dicomNetworkAE));
				if(object.dicomNetworkConnection){
					// $log.debug("in if dicomNetworkConnection");
					object.dicomNetworkConnection = removeEmptyArrays(object.dicomNetworkConnection);
				}
				if(object.dicomNetworkAE){
					$log.debug("in if dicomNetworkAE");
					object.dicomNetworkAE = removeEmptyArrays(object.dicomNetworkAE);
				}
				if(object.dicomTransferCapability){
					$log.debug("in if dicomTransferCapability");
					object.dicomTransferCapability = removeEmptyArrays(object.dicomTransferCapability);
				}
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
		if($model){	
        	angular.forEach($schema, function(k, i){
        		if(k.type === "array" && !$model[i]){
                    $model[i] =  [];
                    $model[i].push(null);
        		}
        	});
		}
	};

	var addEmptyArrayFieldsPrivate = function($scope){
    	$log.debug("in addEmptyArrayFields",$scope.wholeDevice);
    	if($scope.wholeDevice && $schema.properties){
        	addEmptyArrayToModel($scope.wholeDevice, $schema.properties);
        	if($scope.wholeDevice.dicomNetworkAE){
	        	angular.forEach($scope.wholeDevice.dicomNetworkAE, function(k, i){
	        		if($scope.wholeDevice.dicomNetworkAE[i] && $schema.properties.dicomNetworkAE.items.properties){
	        			addEmptyArrayToModel($scope.wholeDevice.dicomNetworkAE[i], $schema.properties.dicomNetworkAE.items.properties);
	        		}$schema.properties.dicomNetworkAE.items.properties
	        	});
        	}
    	}
	};
	var getObjectFromStringHelper = function(object, partName){
		angular.forEach(object, function(k,i){
		//TODO

		});
	};

	// var process = function(key, value, selectedElement) {
	//     console.log(key + " : "+value);
	//     if(key === "$ref"){

	//     }
	//     console.log("---process selectedElement=",selectedElement);
	//     if(key === selectedElement || value === selectedElement){
	//     	console.log("+++++++++++++++in if process");
	//     	// return
	//     }
	// };

	var traverse = function(o, selectedElement, newSchema) {
		console.log("***********in traverse o=",o);
	    for (var i in o) {
	        // func.apply(this, [i,o[i]] , selectedElement);
	        // console.log("before if selectedElement=",selectedElement,"o[=",[i,o[i]],"i=",i);
	        $log.warn("i=",i);
	        if(i === "$ref"){
	        	$log.debug("in ref, o",o);
	        }
	        	
	        if(i != selectedElement){
		        if (o[i] !== null && typeof(o[i])=="object") {
		            //going on step down in the object tree!!
		        	traverse(o[i], selectedElement, newSchema);
		        }
	        }else{
	        	// console.log("before return o=",[i,o[i]]);
	        	newSchema[selectedElement] = o[i];
	        }
	    }
	   	return newSchema;
	};

	var replaceRef = function(schema, selectedElement){
		for (var i in schema) {
			console.log("i=",i,"schema[i]=",schema[i]);
			if(i==="$ref"){
				console.log("i in if=",i,"schema[i]",schema[i]);
				if(schema[i].toString().indexOf(".json")>-1){

					$http({
				        method: 'GET',
				        url: 'schema/'+schema[i]
				        // url: '../devices'
				    }).then(function successCallback(response) {
				    	console.log("response",response);
				    	schema[selectedElement] = response.data;
				    	delete schema[i];
				    }, function errorCallback(response) {
				        $log.error("Error loading schema ref", response);
				    }); 
				}
			}else{
				if(schema[i] !== null && typeof(schema[i])=="object") {
					replaceRef(schema[i], selectedElement);
				}
			}
		}
	};


	return{

		/*
		*Generates Device schema from whole schema without dicomNetworkAE and dicomNetworkConnection
		*@return (Object) JSON-device schema
		*/
		getDeviceSchema : function(){
			var localShema = {};
			// $http({
   //      	method: 'GET',
   //      	url: 'schema/device.schema.json'
   //      // url: '../devices'
		 //    }).then(function successCallback(response) {
		 //    	$log.debug("old schema=",$schema);
		 //        $log.debug("new schema=",response.data);
		 //        angular.copy(response.data,localShema);
			// 	delete localShema.properties.dicomNetworkAE;
			// 	delete localShema.properties.dicomNetworkConnection;
			// 	delete localShema.properties.dcmAuditRecordRepository;
			// 	delete localShema.properties.hl7Application;
			// 	delete localShema.properties.dcmImageWriter;
			// 	delete localShema.properties.dcmImageReader;
			// 	delete localShema.properties.dcmAuditLogger;
			// 	delete localShema.properties.dcmArchiveDevice;
			// 	//Test
			// 		// delete localShema.properties.dicomDeviceName;
			// 		delete localShema.properties.dicomDescription;
			// 		delete localShema.properties.dicomManufacturer;
			// 		delete localShema.properties.dicomManufacturerModelName;
			// 		delete localShema.properties.dicomSoftwareVersion;
			// 		delete localShema.properties.dicomStationName;
			// 		delete localShema.properties.dicomDeviceSerialNumber;
			// 		delete localShema.properties.dicomPrimaryDeviceType;
			// 		delete localShema.properties.dicomInstitutionName;
			// 		delete localShema.properties.dicomInstitutionCode;
			// 		delete localShema.properties.dicomInstitutionAddress;
			// 		delete localShema.properties.dicomInstitutionDepartmentName;
			// 		delete localShema.properties.dicomIssuerOfPatientID;
			// 		delete localShema.properties.dicomIssuerOfAccessionNumber;
			// 		delete localShema.properties.dicomOrderPlacerIdentifier;
			// 		delete localShema.properties.dicomOrderFillerIdentifier;
			// 		delete localShema.properties.dicomIssuerOfAdmissionID;
			// 		delete localShema.properties.dicomIssuerOfServiceEpisodeID;
			// 		delete localShema.properties.dicomIssuerOfContainerIdentifier;
			// 		delete localShema.properties.dicomIssuerOfSpecimenIdentifier;
			// 		delete localShema.properties.dicomAuthorizedNodeCertificateReference;
			// 		delete localShema.properties.dicomThisNodeCertificateReference;
			// 		delete localShema.properties.dicomInstalled;
			// 		delete localShema.properties.dcmDevice;
			// 	//~Test
			// 	var localShema2 = {};
			// 	angular.copy($schema,localShema2);
			// 	delete localShema2.properties.dicomNetworkAE;
			// 	delete localShema2.properties.dicomNetworkConnection;
			// 	$log.debug("localShema2=",localShema2);
			// 	return localShema2;
			// 	// return localShema2;
		 //        // return response.data;
		 //    }, function errorCallback(response) {
		 //        $log.error("Error loading device names", response);
		 //        vex.dialog.alert("Error loading device names, please reload the page and try again!");
		 //    }); 
		// setTimeout(function(){
			var localShema = {};
			$log.debug("in service schemas=",schemas.device);
				angular.copy(schemas.device, localShema);
				$log.debug("in service localShema=",localShema);

				delete localShema.properties.dicomNetworkAE;
				delete localShema.properties.dicomNetworkConnection;
				delete localShema.properties.dcmAuditRecordRepository;
				delete localShema.properties.hl7Application;
				delete localShema.properties.dcmImageWriter;
				delete localShema.properties.dcmImageReader;
				delete localShema.properties.dcmAuditLogger;
				delete localShema.properties.dcmArchiveDevice;

				$log.debug("in service localShema2=",localShema);
				return localShema;

		// }, 400);
		// 	try{
		// 		angular.copy($schema,localShema);
		// 		delete localShema.properties.dicomNetworkAE;
		// 		delete localShema.properties.dicomNetworkConnection;
		// 		return localShema;

		// 	}catch(e){
		// 		$log.error("Error on splitting the device schema in factory DeviceService.js",e);
		// 		return {};
		// 	}
		},

		/*
		*Generates the device form from the schema
		*@return (Object) JSON-Form object
		*/
		getDeviceForm : function(){
			var localShema 	= {};
			var endArray 	= [];
			try{			
			// 	$http({
	  //       	method: 'GET',
	  //       	url: 'schema/device.schema.json'
	  //       	// url: '../devices'
			//     }).then(function successCallback(response) {
			//     	// $log.debug("old schema=",$schema);
			//      //    $log.debug("new schema=",response.data);
			//         angular.copy(response.data,localShema);
			// 		delete localShema.properties.dicomNetworkAE;
			// 		delete localShema.properties.dicomNetworkConnection;
			// 		delete localShema.properties.dcmAuditRecordRepository;
			// 		delete localShema.properties.hl7Application;
			// 		delete localShema.properties.dcmImageWriter;
			// 		delete localShema.properties.dcmImageReader;
			// 		delete localShema.properties.dcmAuditLogger;
			// 		delete localShema.properties.dcmArchiveDevice;
			// 		//
			// 		angular.forEach(localShema.properties, function(m,i){
			// 			endArray.push(i);
			// 			// if(m.type != "array"){
			// 			// 	if(i==="dicomInstalled"){
			// 			// 		endArray.push({
			// 			// 			"key":"dicomInstalled",
			// 			// 			"type":"radios",
			// 			// 			"titleMap":[
			// 			// 				{
			// 			// 					"value": true,
			// 			// 					"name":"True"
			// 			// 				},
			// 			// 				{
			// 			// 					"value": false,
			// 			// 					"name":"False"
			// 			// 				}
			// 			// 			]
			// 			// 		});
			// 			// 	}else{
			// 			// 		endArray.push(i);
			// 			// 	}
			// 			// }else{
			// 			// 	endArray.push({
			//    //                       "key":i,
			//    //                       "add": "Add",
			//    //                       "itmes":[
			//    //                          i+"[]"
			//    //                       ]
			//    //                      });
			// 			// }
			// 		});
			// 		$log.debug("endarray=",endArray);
			// 		return [];
			//         // return response.data;
			//     }, function errorCallback(response) {
			//         $log.error("Error loading device names", response);
			//         vex.dialog.alert("Error loading device names, please reload the page and try again!");
			//     }); 
				angular.copy(schemas.device,localShema);
				delete localShema.properties.dicomNetworkAE;
				delete localShema.properties.dicomNetworkConnection;
				delete localShema.properties.dcmAuditRecordRepository;
				// delete localShema.properties.hl7Application;
				// delete localShema.properties.dcmImageWriter;
				delete localShema.properties.dcmImageReader;
				delete localShema.properties.dcmAuditLogger;
				delete localShema.properties.dcmArchiveDevice;
				//return localShema;
				// $log.debug("localSchema",localShema.properties);
				angular.forEach(localShema.properties, function(m,i){
					// $log.debug("mitems$reflenght=",m.items.$ref.length);
					if(m && m.items && m.items.$ref){
						$log.debug("m=",m);
						$log.debug("m=",m.items.$ref);
						$log.debug("m=",Object.keys(m.items)[0]);
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

				$log.debug("2endArray=",endArray);
				return endArray;
			}catch(e){
				$log.error("Error on splitting the device schema in factory DeviceService.js",e);
				return {};
			}

/*			var endArray = [];
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
			return endArray;*/
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
		*Gets the form for the network dicomNetworkConnection with the connections as checkboxes in it
		*@connections (array) array of current device connections
		*@return (Object) JSON-Form
		*/
		getFormNetworkAe: function(connections){

			var form = [];
			var connObject = {};

			angular.forEach(connections, function(m,k){
				connObject["/dicomNetworkConnection/"+k] = m.cn;
			});

			angular.forEach(getSchemaNetworkAe().properties,function(value,k){
				if(k== "dicomNetworkConnectionReference"){
					form.push("select");
					form.push({
                                "key": "dicomNetworkConnectionReference",
                                "type": "checkboxes",
                                "titleMap": connObject
                        		});
				}else{
					if(k==="dicomInstalled"){
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
				// $log.debug("localModel=",localModel);
				return localModel;
			}catch(e){
				$log.error("Error on splitting the device model in factory DeviceService.js",e);
				return {};
			}
		},
		clearJson: function($scope){
			// var localDevice = {};
			//angular.clone($scope.wholeDevice, localDevice)
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
				// $log.debug("after return=",addCheckboxes($scope.wholeDevice, $schema.properties, ""));
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
        	// $log.debug("$scope.selectedElement",$scope.selectedElement);
        	// $log.debug("wholeDevice=",$scope.wholeDevice);
        	$log.debug("in validateform", $scope.selectedElement);
	        switch ($scope.selectedElement) {
	        	case "device":
	        		if($scope.deviceModel){
	        			if($scope.deviceModel.dicomDeviceName == "CHANGE_ME"){
	        				validForm = false;
		                    message += "- Please change the device name first!<br/>";
	        			}
	        		}
	        		break;
	            case "dicomNetworkConnection":
	            	if($scope.dicomNetConnModel){
	            		$log.debug("$scope.dicomNetConnModel=",$scope.dicomNetConnModel);
		                if (!$scope.dicomNetConnModel.cn) {
		                    validForm = false;
		                    message += "- Conneciton name is required<br/>";
		                }
		                if (!$scope.dicomNetConnModel.dicomHostname) {
		                    validForm = false;
		                    message += "- Hostname is required<br/>";
		                }
	            		angular.forEach($scope.wholeDevice.dicomNetworkConnection, function(m, i){
	            			if($scope.dicomNetConnModel != $scope.wholeDevice.dicomNetworkConnection[i] && m.cn === $scope.dicomNetConnModel.cn){
			                    validForm = false;
			                    message += "- Name have to be unique!<br/>";
	            			}
	            		});
	            	}
	                break;
	            case "dicomNetworkAE":
	            	if($scope.networkAeModel){
	            		$log.debug("$scope.networkAeModel=",$scope.networkAeModel);
		                if (!$scope.networkAeModel.dicomAETitle) {
		                    validForm = false;
		                    message += "- AE Title is required<br/>";
		                }
		                if (!$scope.networkAeModel.dicomNetworkConnectionReference || $scope.networkAeModel.dicomNetworkConnectionReference[0] == undefined) {
		                    validForm = false;
		                    message += "- Network Connection Reference is required<br/>";

		                }
		                if ($scope.networkAeModel.dicomAssociationInitiator != false && $scope.networkAeModel.dicomAssociationInitiator != true) {
		                    validForm = false;
		                    message += "- Association Initiator is required<br/>";

		                }
		                if ($scope.networkAeModel.dicomAssociationAcceptor != false && $scope.networkAeModel.dicomAssociationAcceptor != true) {
		                    validForm = false;
		                    message += "- Association Acceptor is required<br/>";

		                }
		               	angular.forEach($scope.wholeDevice.dicomNetworkAE, function(m, i){
	            			if($scope.networkAeModel != $scope.wholeDevice.dicomNetworkAE[i] && m.dicomAETitle === $scope.networkAeModel.dicomAETitle){
			                    validForm = false;
			                    message += "- Name have to be unique!<br/>";
	            			}
	            		});
	            	}
	                break;
	            case "dicomTransferCapability":
	            	if($scope.transfareCapModel){
		            	if(!$scope.transfareCapModel.cn){
		                    validForm = false;
		                    message += "- Transfare Capability name is required<br/>";
		            	}
		                if (!$scope.transfareCapModel.dicomSOPClass || $scope.transfareCapModel.dicomSOPClass == "") {
		                    validForm = false;
		                    message += "- SOP Class is required<br/>";
		                }
		                if (!$scope.transfareCapModel.dicomTransferRole) {
		                    validForm = false;
		                    message += "- Transfer Role is required<br/>";
		                }
		                if (!$scope.transfareCapModel.dicomTransferSyntax || $scope.transfareCapModel.dicomTransferSyntax[0] == undefined) {
		                    validForm = false;
		                    message += "- Transfer Syntax is required<br/>";
		                }
		                var check = checkTransfareRedondance($scope);
		                validForm = validForm && check.valid;
		                message  += check.msg;
	            		$log.debug("dicomTransferCapability validation if validForm=",validForm,",message=",message);
	            	}
	                break;
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
        	addEmptyArrayFieldsPrivate($scope);
        },

		// getDescendantProp : function(obj, desc){
		//     var arr = desc.split(".");
		//     while(arr.length && (obj = obj[arr.shift()]));
		//     return obj;
		// },

		getObjectFromString : function($scope, value, key){
			var partsArray = value.optionRef.split(".");
			// for(var arr = 1;arr<partsArray.length;arr++){
			// }
			// getObjectFromStringHelper();
				if($scope.selectedPart){
					// console.log("partsArray[0]=",partsArray[0]);
					// console.log("partsArray[0]2=",$select[partsArray[0]].optionValue);
					angular.forEach($scope.wholeDevice[partsArray[0]], function(m, i){
						// console.log("m=",m);
						// console.log("i=",i);
						if(m[$select[partsArray[0]].optionValue] === $scope.selectedPart[partsArray[0]]){
							// console.log("return=",$scope.wholeDevice[partsArray[0]][i][partsArray[1]]);
							$scope.selectModel[key] = $scope.wholeDevice[partsArray[0]][i][partsArray[1]];
							// return ;
						}
					});
				}
		},

		getSchema : function(selectedElement){
			// $log.debug("select in getschema=",$select[selectedElement].optionRef);
			$log.debug("in getSchema=", selectedElement);
			var localSchema = {};
			schemas[selectedElement] = schemas[selectedElement] || {};
			angular.copy(schemas.device, localSchema);
			
			if($select[selectedElement].optionRef.indexOf(".")>-1){
				$log.debug("in if getschema");
				//TODO
				traverse(localSchema, selectedElement, schemas[selectedElement]);
			}else{
				// $log.debug("selectedElement=",selectedElement);
				// $log.debug("schemas=",schemas);
				traverse(localSchema, selectedElement, schemas[selectedElement]);

				// $log.debug("get schema from traverse=",traverse(schemas.device, process, selectedElement, newSchema));
				// $log.debug("newSchema=",newSchema);
				// schemas[selectedElement] = newSchema[selectedElement];
				replaceRef(schemas[selectedElement], selectedElement);
				return schemas[selectedElement];
			}
			$log.debug("schemas=",schemas);
		},

		setFormModel : function(scope){
			$log.debug("in setFormModel,selectedElement=",scope.selectedElement);

			$log.debug("wholeDevice=",scope.wholeDevice);
			$log.debug("$select[selectedElement]=",$select[scope.selectedElement].optionRef);
			// scope.form[scope.selectedElement]["model"]
			if($select[scope.selectedElement].type === "array"){

				if($select[scope.selectedElement].optionRef.indexOf(".")>-1){

				}else{
					$log.debug("selectedPart=",scope.selectedPart[scope.selectedElement]);
					$log.debug("in else scope.wholeDevice[$select[scope.selectedElement].optionRef]=",scope.wholeDevice[$select[scope.selectedElement].optionRef]);
					if(scope.wholeDevice[$select[scope.selectedElement].optionRef]){
						angular.forEach(scope.wholeDevice[$select[scope.selectedElement].optionRef], function(m, i){
						$log.debug("scope.wholeDevice[$select[scope.selectedElement].optionRef][i][$select[scope.selectedElement].optionValue]=",scope.wholeDevice[$select[scope.selectedElement].optionRef][i][$select[scope.selectedElement].optionValue]);
							$log.debug("scope.selectedPart[scope.selectedElement]=",scope.selectedPart[scope.selectedElement]);
							if(scope.selectedPart[scope.selectedElement] === scope.wholeDevice[$select[scope.selectedElement].optionRef][i][$select[scope.selectedElement].optionValue]){
								scope.form[scope.selectedElement]["model"] = scope.wholeDevice[$select[scope.selectedElement].optionRef][i];
								$log.debug("1selected part mdoel=", scope.wholeDevice[$select[scope.selectedElement].optionRef][i]);
								$log.debug("scope.form[scope.selectedElement]['model']",scope.form[scope.selectedElement]["model"]);
							}
						});
					}
				}
			}else{
				if($select[scope.selectedElement].optionRef.indexOf(".")>-1){

				}else{
					$log.debug("for object model set, ",scope.wholeDevice[$select[scope.selectedElement].optionRef]);
					if(scope.wholeDevice[$select[scope.selectedElement].optionRef]){
						scope.form[scope.selectedElement]["model"] = scope.wholeDevice[$select[scope.selectedElement].optionRef];
					}
				}
			}
		}

	}

});