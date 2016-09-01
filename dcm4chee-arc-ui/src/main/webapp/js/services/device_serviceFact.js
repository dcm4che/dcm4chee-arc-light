"use strict";

myApp.factory('DeviceService', function($log, cfpLoadingBar, $http, $compile, schemas, $select) {

    /*
     *The time after how many miliseconds should disapper the message
     */
    var msgTimeout = 10000;

    var getModelTestHelper = function(wholeDevice, selectedElement){
        for (var i in wholeDevice) {
            if (i != selectedElement) {
                if (wholeDevice[i] !== null && typeof(wholeDevice[i]) === "object") {
                    getModelTestHelper(wholeDevice[i], selectedElement);
                }
            } else {
                return wholeDevice[i];
            }
        }
    };
    /*
     *Renders an unique random integer between 1 and 100 for the msg class
     *@m (array of Objects) array of the current messages
     */
    var getUniqueRandomId = function(m) {
        if (m && m[0]) { //If there is no message in the array just create some rendom number
            var buffer = 15; //Create a security to prevent infinite loop
            var isAvailable = false; //Check parameter to see if some message has alredy the new id
            var id = 0;

            while (!isAvailable && buffer > 0) {
                id = Math.floor((Math.random() * 100) + 1); //Render int between 1 and 100
                angular.forEach(m, function(k, i) {
                    if (k.id === id) {
                        isAvailable = true;
                    }
                });
                buffer--;
            }
            if (buffer === 0 && isAvailable === true) {
                return 999;
            } else {
                return id;
            }
        } else {
            return Math.floor((Math.random() * 100) + 1); //Render int between 1 and 100
        }
    };
    /*
     *Check if the same transfare capability is in the list (form validation)
     *@$scope (Object) angularjs $scope
     */
    var checkTransfareRedondance = function($scope) {
        var localObject = {};
        var valid = true;
        var message = "";

        angular.forEach($scope.transfcap, function(m, i) {
            if ($scope.transfcap[i] != $scope.transfareCapModel) {
                if (m.cn === $scope.transfareCapModel.cn) {
                    valid = false;
                    message += "- Name have to be unique!<br/>";
                }
                if (m.dicomSOPClass === $scope.transfareCapModel.dicomSOPClass && m.dicomTransferRole === $scope.transfareCapModel.dicomTransferRole) {
                    valid = false;
                    message += "- This combination of SOP Class and Transfer Role exists already<br/>";
                }
            }
        });

        return {
            "valid": valid,
            "msg": message
        }
    };

    /*
     *Puts msg to $scope.msg array
     *@$scope (Object) the angularjs scope
     *@m (Object) the new message object
     */
    var msg = function($scope, m) {
        var timeout = m.timeout || msgTimeout;
        var isInArray = false;
        var presentId = "";
        angular.forEach($scope.msg, function(k, i) {
            if (k.text === m.text && k.status === m.status) {
                presentId = k.id;
                isInArray = true;
            }
        });
        if (isInArray) { //If the same message is already in the array, then just put the class pulse (To simulate a pulse) and remove it again
            angular.element(document.getElementsByClassName("msg_" + presentId)).removeClass("slideInRight").addClass('pulse');
            setTimeout(function() {
                angular.element(document.getElementsByClassName("msg_" + presentId)).removeClass("pulse");
            }, 500);
        } else {
            var id = getUniqueRandomId($scope.msg);
            m.id = id;
            $scope.msg.push(m);
            msgCounter(id, timeout);
            setTimeout(function() {
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
        if (hide) {
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
    var removeEmptyArrays = function(object) {
        if (Object.prototype.toString.call(object) === '[object Array]') {
            angular.forEach(object, function(l, i) {
                if (object[i]) {
                    object[i] = removeEmptyArrays(object[i]);
                } else {
                    object.splice(i, 1);
                }
            });
        } else {
            angular.forEach(object, function(m, k) {
                if ((Object.prototype.toString.call(m) === '[object Array]' && m[0] == undefined && m[1] == undefined) || m == "") {
                    if (object[k] && (object[k][0] === undefined || object[k][0] === "" || !object[k][0])) {
                        delete object[k];
                    }
                }
                if ((Object.prototype.toString.call(m) === '[object Array]') || (object[k] !== null && typeof(object[k]) == "object")) {
                    object[k] = removeEmptyArrays(object[k]);
                }
            });
        }
        return object;
    };
    var byString = function(o, s) {
        s = s.replace(/\[(\w+)\]/g, '.$1'); // convert indexes to properties
        s = s.replace(/^\./, ''); // strip a leading dot
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
    var addCheckboxes = function(object, schema) {
        angular.forEach(schema, function(m, i) {
            if (m.type === "array") {
                if (object[i]) {
                    angular.forEach(object[i], function(k, j) {
                        object[i][j] = addCheckboxes(k, schema[i].items.properties);
                    });
                }
            } else {
                if (m.type === "boolean") {
                    if (!object[i]) {
                        object[i] = false;
                    }
                }
            }
        });
        return object;
    };

    /*
     *Remove message from desplay (fadeOut) and from scope
     */
    var removeMsg = function($scope, id) {
        angular.forEach($scope.msg, function(m, k) {
            if (m.id == id) {
                $(".msg_container li." + "msg_" + id).fadeOut("400", function() {
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
        var cssClass = ".msg_" + id;
        var x = 0;
        var interval = setInterval(function() {
            $(cssClass).find(".progress").css("width", (x * 10000 / timeout) + "%");
            if (x == (timeout / 100)) {
                clearInterval(interval);
            }
            x++;
        }, 100);
    };

    /*
     *Remove Message from scope array, is called from removeMsg()
     */
    var removeMsgFromArray = function($scope, id) {
        angular.forEach($scope.msg, function(m, k) {
            if (m.id == id) {
                $scope.msg.splice(k, 1);
            }
        });
    };

    /*
     *Add empty arrays to the model if there are in the schema and not i the model
     *@$model (Object) wholeDevice part of the scope.wholeDevice
     *@$schema (Object) the schema in the position where the whole device model is
     */
    var addEmptyArrayToModel = function($model, $schema) {
        $model = $model || {};
        angular.forEach($schema, function(k, i) {
            if (k !== null && typeof(k) === "object" && k.type && k.type === "array" && !$model[i]) {
                $model[i] = [];
                $model[i].push(null);
            }
        });
        return $model;
    };

    var addEmptyArrayFieldsPrivate = function(scope) {
        if (scope.dynamic_schema && scope.dynamic_schema.properties) {
            scope.dynamic_model = addEmptyArrayToModel(scope.dynamic_model, scope.dynamic_schema.properties);
        }
    };

    var traverse = function(o, selectedElement, newSchema) {
        for (var i in o) {
            if (i != selectedElement) {
                if (o[i] !== null && typeof(o[i]) == "object") {
                    traverse(o[i], selectedElement, newSchema);
                }
            } else {
                newSchema[selectedElement] = o[i];
            }
        }
        return newSchema;
    };

    var replaceRef = function(schema, selectedElement, parent, grandpa) {
        for (var i in schema) {
            if (i === "$ref" && (parent === $select[selectedElement].optionRef[0] || grandpa === $select[selectedElement].optionRef[0] || parent === $select[selectedElement].optionRef[1] || grandpa === $select[selectedElement].optionRef[1])) {
                if (schema[i].toString().indexOf(".json") > -1) {
                    $http({
                        method: 'GET',
                        url: 'schema/' + schema[i]
                    }).then(function successCallback(response) {
                        if ($select[selectedElement].optionRef.length > 1) {
                            if (parent === "items" && grandpa != "properties") {
                                schema[grandpa] = response.data;
                            } else {
                                schema[parent] = response.data;
                            }
                            delete schema[i];
                        } else {
                            if (parent === "items" && grandpa != "properties") {
                                schema[grandpa] = response.data;
                            } else {
                                schema[parent] = response.data;
                            }
                            delete schema[i];
                        }
                    }, function errorCallback(response) {
                        $log.error("Error loading schema ref", response);
                    });
                }
            } else {
                if (schema[i] !== null && typeof(schema[i]) == "object") {
                    replaceRef(schema[i], selectedElement, i, parent);
                }
            }
        }
    };

    var removeChilde = function(selectedElement){
		if($select[selectedElement].parentOf){
		  angular.forEach($select[selectedElement].parentOf,function(m,i){
            try{
                if(schemas[selectedElement][selectedElement][selectedElement] && schemas[selectedElement][selectedElement][selectedElement].properties[$select[selectedElement].parentOf[i]] != undefined){
                    delete schemas[selectedElement][selectedElement][selectedElement].properties[$select[selectedElement].parentOf[i]];
                }
            }catch(e){
                console.log("catch e",e);
            }
		  });
		}
    };
//$scope.wholeDevice , selectedElement, selectedPart, $select[selectedElement].optionRef[0])
    var clonePartHelper = function($scope, wholeDevice , selectedElement, selectedPart, current, newCloneName){
        if($select[current].type === "array"){
            angular.forEach(wholeDevice[current], function(m, i){
                if(m[$select[current].optionValue] === selectedPart[current]){
                    if($select[selectedElement].optionRef[$select[selectedElement].optionRef.length-1] === current){
                        var isAlreadyThere = false;
                        angular.forEach(wholeDevice[current], function(m){
                            if(m[$select[selectedElement].optionValue] === newCloneName){
                                isAlreadyThere = true;
                            }
                        });
                        if(!isAlreadyThere){
                            var clone = {};
                            angular.copy(wholeDevice[current][i], clone);
                            clone[$select[current].optionValue] = newCloneName;
                            wholeDevice[current].push(clone);
                            msg($scope, {
                                "title": "Info",
                                "text": 'Clone with the name "'+newCloneName+'" created sucessfully!',
                                "status": "info"
                            });
                        }else{
                            msg($scope, {
                                "title": "Error",
                                "text": 'Name need to be unique!',
                                "status": "error"
                            });
                        }
                        cfpLoadingBar.complete();
                    }else{
                        var index = $select[selectedElement].optionRef.indexOf(current);
                        if(index >= 0 && index < $select[selectedElement].optionRef.length - 1){
                          var nextItem = $select[selectedElement].optionRef[index + 1];
                        }
                        clonePartHelper($scope, wholeDevice[current][i], selectedElement, selectedPart, nextItem, newCloneName);
                    }
                }
            });
        }else{
            if($select[selectedElement].optionRef[$select[selectedElement].optionRef.length-1] === current){
                var isAlreadyThere = false;
                angular.forEach(wholeDevice[current], function(m){
                    if(m[$select[selectedElement].optionValue] === newCloneName){
                        isAlreadyThere = true;
                    }
                });
                if(!isAlreadyThere){
                    var clone = {};
                    angular.copy(wholeDevice[current][i], clone);
                    clone[$select[current].optionValue] = newCloneName;
                    wholeDevice[current].push(clone);
                    msg($scope, {
                        "title": "Info",
                        "text": 'Clone with the name "'+newCloneName+'" created sucessfully!',
                        "status": "info"
                    });
                }else{
                    msg($scope, {
                        "title": "Error",
                        "text": 'Name need to be unique!',
                        "status": "error"
                    });
                }
                cfpLoadingBar.complete();
            }else{
                var index = $select[selectedElement].optionRef.indexOf(current);
                if(index >= 0 && index < $select[selectedElement].optionRef.length - 1){
                  var nextItem = $select[selectedElement].optionRef[index + 1];
                }
                clonePartHelper($scope, wholeDevice[current], selectedElement, selectedPart, nextItem, newCloneName);
            }
        }

    }
    return {

        /*
         *Generates Device schema from whole schema without dicomNetworkAE and dicomNetworkConnection
         *@return (Object) JSON-device schema
         */
        getDeviceSchema: function() {
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
        getDeviceForm: function() {
            var localShema = {};
            var endArray = [];
            try {
                angular.copy(schemas.device, localShema);
                angular.forEach($select, function(m, i) {
                    if (localShema.properties[i]) {
                        delete localShema.properties[i];
                    }
                });
                angular.forEach(localShema.properties, function(m, i) {
                    if (m && m.items && m.items.$ref) {
                        endArray.push({
                            "key": i,
                            "type": "ref",
                            "json": m.items.$ref
                        });
                    } else {

                        if (m.type != "array") {
                            if (i === "dicomInstalled") {
                            // if (m.type === "boolean") {
                                endArray.push({
                                    "key": "dicomInstalled",
                                    // "key": i,
                                    "type": "radios",
                                    "titleMap": [{
                                        "value": true,
                                        "name": "True"
                                    }, {
                                        "value": false,
                                        "name": "False"
                                    }]
                                });
                            } else {
                                endArray.push(i);
                            }
                        } else {
                            endArray.push({
                                "key": i,
                                "add": "Add",
                                "itmes": [
                                    i + "[]"
                                ]
                            });
                        }
                    }
                });
                return endArray;
            } catch (e) {
                $log.error("Error on splitting the device schema in factory DeviceService.js", e);
                return {};
            }
        },

        /*
         *Check if two json objects are equal. (External Library used "jsondiffpatch" see index.html for the link)
         *obj1 (Object) the first JSON-Object
         *obj2 (Object) the second JSON-Object
         */
        equalJSON: function(obj1, obj2) {
            if (obj1 && obj2) {
                var diff = jsondiffpatch.diff(JSON.parse(angular.toJson(obj1)), JSON.parse(angular.toJson(obj2)));
                var equal = true;

                angular.forEach(diff, function(m, k) {
                    if (Object.prototype.toString.call(m) === '[object Array]') {
                        if (m[0] != undefined && m[0][0] == null) {
                            equal = equal && true;
                        } else {
                            equal = false;
                        }
                    } else {
                        equal = false;
                    }
                });
                return equal;
            } else {
                return true;
            }
        },
        getDeviceModel: function(wholeDeviceModel) {
            var localModel = {};
            try {
                angular.copy(wholeDeviceModel, localModel);
                delete localModel.dicomNetworkAE;
                delete localModel.dicomNetworkConnection;
                return localModel;
            } catch (e) {
                $log.error("Error on splitting the device model in factory DeviceService.js", e);
                return {};
            }
        },
        clearJson: function($scope) {
            try {
                $scope.wholeDevice = removeEmptyArrays($scope.wholeDevice);
            } catch (e) {
                $log.error("Error on clearing json", e);
            }
        },
        clearReference: function(key, object) {
            if (object.dicomNetworkAE) {
                angular.forEach(object.dicomNetworkAE, function(m, k) {
                    if (m.dicomNetworkConnectionReference) {
                        angular.forEach(m.dicomNetworkConnectionReference, function(l, i) {
                            if (l) {
                                var exploded = l.split("/");
                                if (exploded[exploded.length - 1] > key) {
                                    object.dicomNetworkAE[k].dicomNetworkConnectionReference[i] = "/dicomNetworkConnection/" + (exploded[exploded.length - 1] - 1);
                                }
                            }
                        });
                        angular.forEach(m.dicomNetworkConnectionReference, function(l, i) {
                            if (l) {
                                var exploded = l.split("/");
                                if (key == exploded[exploded.length - 1]) {
                                    delete object.dicomNetworkAE[k].dicomNetworkConnectionReference.splice(i, 1);
                                }
                            }
                        });
                    }

                });
            }
            return object;
        },
        validateForm: function($scope) {
            var validForm = true;
            var message = "";
            var count = 0;

            if($scope.editMode && $scope.devicename === "CHANGE_ME" && $scope.wholeDevice.dicomDeviceName === "CHANGE_ME"){
            	validForm = false;
            	message += "- Change the name of the device first!";
            }
            if ($select[$scope.selectedElement] && $select[$scope.selectedElement].type === "array" && $scope.selectModel[$scope.selectedElement] && $scope.selectModel[$scope.selectedElement].length > 0) {
                angular.forEach($scope.selectModel[$scope.selectedElement], function(m, i) {
                    if (m[$select[$scope.selectedElement].optionValue] === $scope.dynamic_model[$select[$scope.selectedElement].optionValue]) {
                        count++;
                    }
                });
            }
            if (count > 1) {
                validForm = false;
                message += "- " + $select[$scope.selectedElement].required[$select[$scope.selectedElement].optionValue] + " have to be unique!<br/>";
            }
            if ($select[$scope.selectedElement] && $select[$scope.selectedElement].required) {
                angular.forEach($select[$scope.selectedElement].required, function(m, i) {
                	if(Object.prototype.toString.call($scope.dynamic_model[i]) === "[object Array]" && $scope.dynamic_model[i] && $scope.dynamic_model[i].length < 1){
                        validForm = false;
                        message += "- " + m + " is required!<br/>";
                	}
                    if((!$scope.dynamic_model[i] || $scope.dynamic_model[i] === "") && (Object.prototype.toString.call($scope.dynamic_model[i]) != "[object Boolean]")) {
                        validForm = false;
                        message += "- " + m + " is required!<br/>";
                    }
                });
            }
            return {
                "valid": validForm,
                "message": message
            };
        },
        checkValidProcess : function($scope, element){
        	var validProcess 	= true;
        	var message 		= "";
        	var missingDep   	= false;
  			if($select[element].requiredPart){

  				angular.forEach($select[element].requiredPart,function(i){
  					// //TODO
  					if($select[i].optionRef.length === 1){
  						if($select[i].type === "array"){
  							if(!$scope.wholeDevice[i] || $scope.wholeDevice[i].length < 1){
  								missingDep = true;
  							} 
  						}else{
  							if(!$scope.wholeDevice[i] || Object.getOwnPropertyNames($scope.wholeDevice[i]).length < 1){
  								missingDep = true;
  							} 
  						}
  					}
  					if(missingDep){
  						validProcess = false;
  						message += "- Create first a " + $select[i].title + "</br>";
  					}
  				});
  			}
  			return {"valid":validProcess,"message":message};
        },
        deleteDevice: function($scope) {
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
                    $scope.devicename	 	= "";
                    $scope.currentDevice	= "";
                    $scope.newDevice	 	= true;
                    $scope.middleBorder	 	= "";
                    $scope.lastBorder	 	= "";
                    $scope.editMode	 		= false;
                    $scope.deviceModel	 	= {};
                    $scope.wholeDevice	 	= {};
                    $scope.validForm	 	= true;
                    $scope.selectedElement	= "";
                    $scope.activeMenu	 	= "";
                    $scope.showCancel	 	= false;
                    $scope.showSave	 		= false;
                    $scope.lastBorder	 	= "";
                    msg($scope, {
                        "title": "Info",
                        "text": "Device deleted successfully!",
                        "status": "info"
                    });
                    angular.element("#add_dropdowns").html("");
                    angular.element("#editDevice").html("");
                    cfpLoadingBar.complete();
                    return true;
                })
                .error(function(data, status, headers, config) {
                    $log.error("Error deleting device", status);
                    msg($scope, {
                        "title": "error",
                        "text": "Error deleting device!",
                        "status": "error"
                    });
                    cfpLoadingBar.complete();
                    return false;
                });
        },
        save: function($scope) {
            if ($scope.wholeDevice.dicomDeviceName && $scope.wholeDevice.dicomDeviceName != undefined) {
                if($scope.newDevice){

                $http.post("../devices/" + $scope.wholeDevice.dicomDeviceName, $scope.wholeDevice)
                    .success(function(data, status, headers, config) {
                        $scope.saved = true;
                        cfpLoadingBar.complete();
                        $scope.showCancel = false;
                        addEmptyArrayFieldsPrivate($scope); //Add empty array fileds becouse there were cleared before save, if we don't then the array fields will be missing
                        msg($scope, {
                            "title": "Info",
                            "text": "Changes saved successfully!",
                            "status": "info"
                        });
                        $http.post("../ctrl/reload").then(function (res) {
                            msg($scope, {
                                "title": "Info",
                                "text": "Archive reloaded successfully!",
                                "status": "info"
                            });
                        });
                    })
                    .error(function(data, status, headers, config) {
                        $log.error("Error sending data on put!", status);
                        addEmptyArrayFieldsPrivate($scope);
                        msg($scope, {
                            "title": "error",
                            "text": "Error, changes could not be saved!",
                            "status": "error"
                        });
                        cfpLoadingBar.complete();
                    });
                }else{

                $http.put("../devices/" + $scope.wholeDevice.dicomDeviceName, $scope.wholeDevice)
                    .success(function(data, status, headers, config) {
                        $scope.saved = true;
                        cfpLoadingBar.complete();
                        $scope.showCancel = false;
                        addEmptyArrayFieldsPrivate($scope); //Add empty array fileds becouse there were cleared before save, if we don't then the array fields will be missing
                        msg($scope, {
                            "title": "Info",
                            "text": "Changes saved successfully!",
                            "status": "info"
                        });
                        $http.get("../ctrl/reload").then(function (res) {
                            msg($scope, {
                                "title": "Info",
                                "text": "Archive reloaded successfully!",
                                "status": "info"
                            });
                        });
                    })
                    .error(function(data, status, headers, config) {
                        $log.error("Error sending data on put!", status);
                        addEmptyArrayFieldsPrivate($scope);
                        msg($scope, {
                            "title": "error",
                            "text": "Error, changes could not be saved!",
                            "status": "error"
                        });
                        cfpLoadingBar.complete();
                    });
                }
            } else {
                msg($scope, {
                    "title": "error",
                    "text": "Error, changes could not be saved!",
                    "status": "error"
                });
            }
        },
        saveWithChangedName: function($scope) {
            if ($scope.wholeDevice.dicomDeviceName != undefined) {
                $http
                    .put("../devices/" + $scope.wholeDevice.dicomDeviceName, $scope.wholeDevice)
                    .success(function(data, status, headers, config) {
                        msg($scope, {
                            "title": "Info",
                            "text": "A new device with the name " + $scope.wholeDevice.dicomDeviceName + " created successfully!",
                            "status": "info"
                        });

                        addEmptyArrayFieldsPrivate($scope);

                        $http
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
                                msg($scope, {
                                    "title": "Info",
                                    "text": "Old device deleted successfully!",
                                    "status": "info"
                                });
                                $http.post("../ctrl/reload").then(function (res) {
                                    msg($scope, {
                                        "title": "Info",
                                        "text": "Archive reloaded successfully!",
                                        "status": "info"
                                    });
                                });
                            })
                            .error(function(data, status, headers, config) {
                                $log.error("Error deleting device", status);
                                msg($scope, {
                                    "title": "Error",
                                    "text": "Error deleting the old device!",
                                    "status": "error"
                                });
                                cfpLoadingBar.complete();
                                return false;
                            });
                    })
                    .error(function(data, status, headers, config) {
                        $log.error("Error sending data on put!", status);
                        msg($scope, {
                            "title": "Error",
                            "text": "Error creating new device with the name " + $scope.wholeDevice.dicomDeviceName + "!",
                            "status": "error"
                        });
                        cfpLoadingBar.complete();
                    });
            } else {
                msg($scope, {
                    "title": "Info",
                    "text": "Device name can not be undefined!",
                    "status": "info"
                });
            }
        },
        msg: function($scope, m) {
            return msg($scope, m);
        },
        addDirectiveToDom: function($scope, element, markup) {

            switch (element) {
                case "add_dropdowns":

                    $scope.showDropdownLoader = true;
                    $scope.middleBorder = "";
                    addDirective($scope, element, markup, true);

                    var watchDropdownLoader = setInterval(function() {
                        if (angular.element(document.getElementById('endLoadSelect')).length > 0) {
                            clearInterval(watchDropdownLoader);
                            $scope.middleBorder = "active_border";
                            $scope.showDropdownLoader = false;
                            angular.element(document.getElementById(element)).show();
                        }
                    }, 100);

                    break;
                case "add_edit_area":

                    $scope.showFormLoader = true;
                    $scope.lastBorder = "";
                    addDirective($scope, element, markup, true);
                    var elementLenght = angular.element(document.querySelectorAll('#editDevice bootstrap-decorator')).length;
                    var watchFormnLoader = setInterval(function() {
                        if (angular.element(document.querySelectorAll('#editDevice bootstrap-decorator')).length === elementLenght) {
                            clearInterval(watchFormnLoader);
                            $scope.lastBorder = "active_border";
                            $scope.showFormLoader = false;
                            angular.element(document.getElementById(element)).show();

                        } else {
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
        removeEmptyPart: function(element, key) {
            if (element) {
                angular.forEach(element, function(k, i) {
                    if (!k || (k && (!k[key] || k[key] === ""))) {
                        element.splice(i, 1);
                    }
                });
            }
        },
        /*
         *Add empty arrays to the wholeDevice, becouse without them sometimes the form doesn't show the array fields
         *$scope (Object) angularjs scope
         */
        addEmptyArrayFields: function($scope) {
            addEmptyArrayFieldsPrivate($scope);
        },
        getObjectFromString: function($scope, value, key) {
            // console.log("key",key);
            var partsArray = value.optionRef;
            if (partsArray.length === 2) {

                if ($select[partsArray[0]].type === "object" && $scope.wholeDevice[partsArray[0]] && $scope.wholeDevice[partsArray[0]][partsArray[1]]) {
                    $scope.selectModel[key] = $scope.wholeDevice[partsArray[0]][partsArray[1]];
                } else {
                    if ($scope.selectedPart && $scope.wholeDevice[partsArray[0]]) {
                        angular.forEach($scope.wholeDevice[partsArray[0]], function(m, i) {
                            if ((m && m[$select[partsArray[0]].optionValue]) && (m[$select[partsArray[0]].optionValue] === $scope.selectedPart[partsArray[0]])) {
                                $scope.selectModel[key] = $scope.wholeDevice[partsArray[0]][i][partsArray[1]];
                            }
                        });
                    }
                }
            } else {
                $log.warn("In TODO",partsArray);
                //TODO I don't know if we need it, we will see
            }
        },
        getForm: function(scope) {
                var timeout = 120;
                var waitforschema = setInterval(function() {
                    var checkItemsProperties = (
                        schemas[scope.selectedElement] &&
                        schemas[scope.selectedElement][scope.selectedElement] &&
                        schemas[scope.selectedElement][scope.selectedElement]["items"] &&
                        schemas[scope.selectedElement][scope.selectedElement]["items"]["properties"]
                    );
                    var checkItems = (
                        schemas[scope.selectedElement] &&
                        schemas[scope.selectedElement][scope.selectedElement] &&
                        schemas[scope.selectedElement][scope.selectedElement]["items"] &&
                        schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement] &&
                        schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement]["properties"]
                    );
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
                    if (checkItems || checkProp || checkPropShort || checkItemsProperties) {
                        clearInterval(waitforschema);
                        var endArray = [];
                        if (checkItems) {
                            var schema = schemas[scope.selectedElement][scope.selectedElement]["items"][scope.selectedElement]["properties"];
                        } else {
                        	if(checkPropShort){
                            	var schema = schemas[scope.selectedElement][scope.selectedElement]["properties"];
                        	}else{
                                if(checkItemsProperties){
                                    var schema = schemas[scope.selectedElement][scope.selectedElement]["items"]["properties"];
                                }else{
                                    var schema = schemas[scope.selectedElement][scope.selectedElement][scope.selectedElement]["properties"];
                                }
                        	}
                        }
                        angular.forEach(schema, function(m, i) {

                            if (i === "dicomNetworkConnectionReference") {
                                var connObject = [];
                                angular.forEach(scope.wholeDevice.dicomNetworkConnection, function(m, k) {
                                    var path = {
                                        value: "/dicomNetworkConnection/" + k,
                                        name: m.cn
                                    };
                                    // path["/dicomNetworkConnection/"+k] = m.cn;

                                    connObject.push(path);
                                });
                                endArray.push("select");
                                var temp = {
                                    "key": "dicomNetworkConnectionReference",
                                    "type": "checkboxes",
                                    "titleMap": angular.copy(connObject)
                                };
                                endArray.push(temp);
                            } else {
                                if(i === "dcmQueueName"){
                                    var queueObject = [];
                                    angular.forEach(scope.queues,function(m, i){
                                        queueObject.push({
                                            "value":m.name,
                                            "name":m.description
                                        });
                                    });
                                      var temp = {
                                                    "key": "dcmQueueName",
                                                    "type": "select",
                                                    "titleMap": queueObject          
                                                };
                                        endArray.push(temp);

                                }else{

                                    if (m.type === "array") {
                                        endArray.push({
                                            "key": i,
                                            "add": "Add",
                                            "itmes": [
                                                i + "[]"
                                            ]
                                        });
                                    } else {
                                        // if (i === "dicomInstalled") {
                                        if (m.type === "boolean") {
                                            endArray.push({
                                                // "key": "dicomInstalled",
                                                "key": i,
                                                "type": "radios",
                                                "titleMap": [{
                                                    "value": true,
                                                    "name": "True"
                                                }, {
                                                    "value": false,
                                                    "name": "False"
                                                }]
                                            });
                                        } else {
                                            endArray.push(i);
                                        }

                                    }
                                }
                            }
                        });
                        scope.form[scope.selectedElement] = scope.form[scope.selectedElement] || {};
                        scope.form[scope.selectedElement]["form"] = endArray;
                        // console.log("scope.form[scope.selectedElement]['form']",scope.form[scope.selectedElement]['form']);
                    }
                    if (timeout > 0) {
                        timeout--;
                    } else {
                        clearInterval(waitforschema);
                    }
                }, 10);
        },
        getSchema: function(selectedElement) {
            var localSchema = {};

            if ($select[selectedElement] && $select[selectedElement].optionRef.length > 1) {
                schemas[selectedElement] = schemas[selectedElement] || {};
                if (Object.keys(schemas[$select[selectedElement].optionRef[1]]).length < 1) {
                    var refs = $select[selectedElement].optionRef;
                    var localSchema2 = {};
                    angular.copy(schemas.device, localSchema);
                    if (!schemas[$select[selectedElement].optionRef[0]]) {
                        schemas[$select[selectedElement].optionRef[0]] = {};
                    }
                    traverse(localSchema, $select[selectedElement].optionRef[0], schemas[$select[selectedElement].optionRef[0]]);
                    replaceRef(schemas[$select[selectedElement].optionRef[0]], selectedElement, "", "");
                    var waitfirstlevel = setInterval(function() {
                        if ((
                                schemas[$select[selectedElement].optionRef[0]] &&
                                schemas[$select[selectedElement].optionRef[0]][$select[selectedElement].optionRef[0]] &&
                                schemas[$select[selectedElement].optionRef[0]][$select[selectedElement].optionRef[0]]["items"] &&
                                schemas[$select[selectedElement].optionRef[0]][$select[selectedElement].optionRef[0]]["items"][$select[selectedElement].optionRef[0]]
                            ) ||
                            (
                                schemas[$select[selectedElement].optionRef[0]] &&
                                schemas[$select[selectedElement].optionRef[0]][$select[selectedElement].optionRef[0]] &&
                                schemas[$select[selectedElement].optionRef[0]][$select[selectedElement].optionRef[0]][$select[selectedElement].optionRef[0]] &&
                                schemas[$select[selectedElement].optionRef[0]][$select[selectedElement].optionRef[0]][$select[selectedElement].optionRef[0]]["properties"]
                            )
                        ) {
                            clearInterval(waitfirstlevel);
                            angular.copy(schemas[$select[selectedElement].optionRef[0]], localSchema2);
                            traverse(localSchema2, $select[selectedElement].optionRef[1], schemas[$select[selectedElement].optionRef[1]]);
                            replaceRef(schemas[$select[selectedElement].optionRef[1]], selectedElement, "", "");
                           	removeChilde(selectedElement);
                            return schemas[selectedElement];
                        }
                    }, 10);
                } else {
                    removeChilde(selectedElement);
                    return schemas[selectedElement];
                }
            } else {
                schemas[selectedElement] = schemas[selectedElement] || {};
                if (Object.keys(schemas[selectedElement]).length < 1) {
                    angular.copy(schemas.device, localSchema);
                    traverse(localSchema, selectedElement, schemas[selectedElement]);
                    replaceRef(schemas[selectedElement], selectedElement, "", "");
                    console.log("schemas",schemas);
                    return schemas[selectedElement];
                } else {
                    return schemas[selectedElement];
                }

            }
        },
        getModelTest: function(scope){
            return getModelTestHelper(scope.wholeDevice, scope.selectedElement);
        },
        setFormModel: function(scope) {
            // console.log("angular.copy(getModelTest(scope))",angular.copy(getModelTestHelper(scope.wholeDevice, scope.selectedElement)));
            // scope.dynamic_model = 
            // console.log("scope.dynamic_model",scope.dynamic_model);
            // scope.form[scope.selectedElement] = scope.form[scope.selectedElement] || {};
            // scope.form[scope.selectedElement]["model"] = scope.form[scope.selectedElement]["model"] || {};
            // scope.form[scope.selectedElement]["model"] = getModelTestHelper(scope.wholeDevice, scope.selectedElement);
            // console.log("scope.wholeDevice",scope.wholeDevice);
            // console.log("scope.form",scope.form);
            if(scope.selectedElement){
                if ($select[scope.selectedElement] && $select[scope.selectedElement].type === "array") {
                    if ($select[scope.selectedElement].optionRef.length > 1) {
                        if (scope.wholeDevice[$select[scope.selectedElement].optionRef[0]]) {
                            if ($select[$select[scope.selectedElement].optionRef[0]].type === "object") {
                                angular.forEach(scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][$select[scope.selectedElement].optionRef[1]], function(k, j) {
                                    if (scope.selectedPart[$select[scope.selectedElement].optionRef[1]] === scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][$select[scope.selectedElement].optionRef[1]][j][$select[scope.selectedElement].optionValue]) {
                                        if (!scope.form[scope.selectedElement]) {
                                            scope.form[scope.selectedElement] = {};
                                        }
                                        scope.form[scope.selectedElement]["model"] = scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][$select[scope.selectedElement].optionRef[1]][j];
                                    }
                                });
                            } else {
                                angular.forEach(scope.wholeDevice[$select[scope.selectedElement].optionRef[0]], function(m, i) {
                                    if (scope.selectedPart[$select[scope.selectedElement].optionRef[0]] === scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i][$select[$select[scope.selectedElement].optionRef[0]].optionValue]) {
                                        angular.forEach(scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i][$select[scope.selectedElement].optionRef[1]], function(k, j) {
                                            if (scope.selectedPart[$select[scope.selectedElement].optionRef[1]] === scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i][$select[scope.selectedElement].optionRef[1]][j][$select[scope.selectedElement].optionValue]) {
                                                if (!scope.form[scope.selectedElement]) {
                                                    scope.form[scope.selectedElement] = {};
                                                }
                                                scope.form[scope.selectedElement]["model"] = scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i][$select[scope.selectedElement].optionRef[1]][j];
                                            }

                                        });
                                    }
                                });
                            }
                        }
                    } else {
                        if (scope.wholeDevice[$select[scope.selectedElement].optionRef[0]]) {
                            angular.forEach(scope.wholeDevice[$select[scope.selectedElement].optionRef[0]], function(m, i) {
                                if (scope.selectedPart[scope.selectedElement] === scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i][$select[scope.selectedElement].optionValue]) {
                                    if (!scope.form[scope.selectedElement]) {
                                        scope.form[scope.selectedElement] = {};
                                    }
                                    scope.form[scope.selectedElement]["model"] = scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i];
                                }
                            });
                        }
                    }
                } else {
                    if ($select[scope.selectedElement] && $select[scope.selectedElement].optionRef.length > 1) {
                        console.log("in if 2");
                        if (scope.wholeDevice[$select[scope.selectedElement].optionRef[0]]) {
                            if ($select[$select[scope.selectedElement].optionRef[0]].type === "object") {
                                angular.forEach(scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][$select[scope.selectedElement].optionRef[1]], function(k, j) {
                                    if (scope.selectedPart[$select[scope.selectedElement].optionRef[1]] === scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][$select[scope.selectedElement].optionRef[1]][j][$select[scope.selectedElement].optionValue]) {
                                        if (!scope.form[scope.selectedElement]) {
                                            scope.form[scope.selectedElement] = {};
                                        }
                                        scope.form[scope.selectedElement]["model"] = scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][$select[scope.selectedElement].optionRef[1]][j];
                                    }
                                });
                            } else {
                                angular.forEach(scope.wholeDevice[$select[scope.selectedElement].optionRef[0]], function(m, i) {
                                    if (scope.selectedPart[$select[scope.selectedElement].optionRef[0]] === scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i][$select[$select[scope.selectedElement].optionRef[0]].optionValue]) {
                                        if($select[$select[scope.selectedElement].optionRef[1]].type === "object"){
                                            if(!scope.form[scope.selectedElement]) {
                                                scope.form[scope.selectedElement] = {};
                                            }
                                            scope.form[scope.selectedElement]["model"] = scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i][$select[scope.selectedElement].optionRef[1]];
                                        }else{
                                            angular.forEach(scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i][$select[scope.selectedElement].optionRef[1]], function(k, j) {
                                                if (scope.selectedPart[$select[scope.selectedElement].optionRef[1]] && scope.selectedPart[$select[scope.selectedElement].optionRef[1]] === scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i][$select[scope.selectedElement].optionRef[1]][j][$select[scope.selectedElement].optionValue]) {
                                                    if (!scope.form[scope.selectedElement]) {
                                                        scope.form[scope.selectedElement] = {};
                                                    }
                                                    scope.form[scope.selectedElement]["model"] = scope.wholeDevice[$select[scope.selectedElement].optionRef[0]][i][$select[scope.selectedElement].optionRef[1]];
                                                }else{
                                                }
                                            });
                                        }
                                    }
                                });
                            }
                        }else{
                        }
                    } else {
                        if (!scope.form[scope.selectedElement]) {
                            scope.form[scope.selectedElement] = {};
                        }
                        if (scope.wholeDevice[$select[scope.selectedElement].optionRef[0]]) {

                                    console.log("scope.wholeDevice",scope.wholeDevice);
                                    console.log("scope.selectedElement",scope.selectedElement);
                                    console.log("$select",$select);
                                    console.log("$select[scope.selectedElement].optionRef[0]",$select[scope.selectedElement].optionRef[0]);
                                    // scope.dynamic_model = scope.wholeDevice[$select[scope.selectedElement].optionRef[0]];
                                    scope.form[scope.selectedElement]["model"] = scope.wholeDevice[$select[scope.selectedElement].optionRef[0]];
                                    // console.log("scope.dynamic_model",scope.dynamic_model);
                                    console.log("angular.copy(getModelTestHelper(scope.wholeDevice, scope.selectedElement))",angular.copy(getModelTestHelper(scope.wholeDevice, scope.selectedElement)));
                                    // scope.dynamic_model = angular.copy(getModelTestHelper(scope.wholeDevice, scope.selectedElement));
                                    console.log("1scope.dynamic_model",scope.dynamic_model);
                        }else{
                        	scope.wholeDevice[$select[scope.selectedElement].optionRef[0]] = {};
                            scope.form[scope.selectedElement]["model"] = scope.wholeDevice[$select[scope.selectedElement].optionRef[0]];
                        }
                    }
                }
            }
            // console.log("scope.wholeDevice",scope.wholeDevice);
            // console.log("scope.dynamic_model",scope.dynamic_model);
            // console.log("scope.form",scope.form);
        },
        createPart: function($scope) {
            if ($select[$scope.selectedElement].optionRef.length > 1) {
                $scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]] = $scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]] || {};
                if ($select[$select[$scope.selectedElement].optionRef[0]].type === "array") {
                    angular.forEach($scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]], function(k, i) {
                        if (k[$select[$select[$scope.selectedElement].optionRef[0]].optionValue] === $scope.selectedPart[$select[$scope.selectedElement].optionRef[0]]) {
                            if ($select[$select[$scope.selectedElement].optionRef[1]].type === "array") {
                                $scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]][i][$select[$scope.selectedElement].optionRef[1]] = $scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]][i][$select[$scope.selectedElement].optionRef[1]] || [];
                                $scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]][i][$select[$scope.selectedElement].optionRef[1]].push($scope.dynamic_model);
                            } else {
                                $scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]][i][$select[$scope.selectedElement].optionRef[1]] = $scope.dynamic_model;
                            }
                        }
                    });
                } else {
                    $scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]] = $scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]] || {};
                    if ($select[$select[$scope.selectedElement].optionRef[1]].type === "array") {
                        if (!$scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]][$select[$scope.selectedElement].optionRef[1]]) {
                            $scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]][$select[$scope.selectedElement].optionRef[1]] = [];
                        }
                        $scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]][$select[$scope.selectedElement].optionRef[1]].push($scope.dynamic_model);
                    } else {
                        if (!$scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]][$select[$scope.selectedElement].optionRef[1]]) {
                            $scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]][$select[$scope.selectedElement].optionRef[1]] = {};
                        }
                        $scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]][$select[$scope.selectedElement].optionRef[1]] = $scope.dynamic_model;
                    }
                }

            } else {
                if ($select[$scope.selectedElement].type === "array") {
                    $scope.wholeDevice[$scope.selectedElement] = $scope.wholeDevice[$scope.selectedElement] || [];
                    $scope.wholeDevice[$scope.selectedElement].push($scope.dynamic_model);
                } else {
                    $scope.wholeDevice[$scope.selectedElement] = $scope.dynamic_model;
                }
            }
            addEmptyArrayFieldsPrivate($scope);
        },
        cancle: function($scope) {
            if ($select[$scope.selectedElement].optionRef.length > 1) {
                if ($select[$select[$scope.selectedElement].optionRef[0]].type === "array") {
                    angular.forEach($scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]], function(k, i) {
                        if (k[$select[$select[$scope.selectedElement].optionRef[0]].optionValue] === $scope.selectedPart[$select[$scope.selectedElement].optionRef[0]]) {
                            if ($select[$select[$scope.selectedElement].optionRef[1]].type === "array") {
                                angular.forEach($scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]][i][$select[$scope.selectedElement].optionRef[1]], function(m, j) {
                                    if (m[$select[$select[$scope.selectedElement].optionRef[1]].optionValue] === $scope.dynamic_model[$select[$select[$scope.selectedElement].optionRef[1]].optionValue]) {
                                        $scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]][i][$select[$scope.selectedElement].optionRef[1]].splice(j, 1);
                                    }
                                });
                            }
                        }
                    });
                } else {
                    if ($select[$select[$scope.selectedElement].optionRef[1]].type === "array") {
                        angular.forEach($scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]][$select[$scope.selectedElement].optionRef[1]], function(m, j) {
                            if (m[$select[$select[$scope.selectedElement].optionRef[1]].optionValue] === $scope.dynamic_model[$select[$select[$scope.selectedElement].optionRef[1]].optionValue]) {
                                $scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]][$select[$scope.selectedElement].optionRef[1]].splice(j, 1);
                            }
                        });
                    }
                }
            } else {
                if ($select[$select[$scope.selectedElement].optionRef[0]].type === "array") {
                    angular.forEach($scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]], function(k, i) {
                        if (k[$select[$select[$scope.selectedElement].optionRef[0]].optionValue] === $scope.dynamic_model[$select[$select[$scope.selectedElement].optionRef[0]].optionValue]) {
                            $scope.wholeDevice[$select[$scope.selectedElement].optionRef[0]].splice(i, 1);
                        }
                    });
                }
            }
        },
        /*
        *Bind warn event to devicename, to warn the user if he wants to change the name of the device
        */
        warnEvent: function(scope) {
            if (scope.selectedElement === 'device') {
                var timeout = 300;
                var waitOfDeviceName = setInterval(function() {
                    if (angular.element('#dicomDeviceName').length > 0) {
                        clearInterval(waitOfDeviceName);
                        setTimeout(function() {
                            angular.element('#dicomDeviceName').unbind("click focus keydown").bind("click focus keydown", function() {
                                if (scope.currentDevice != "CHANGE_ME" && !scope.changeNameWarning) {
                                    angular.element('#dicomDeviceName').prop('disabled', true);
                                    vex.dialog.confirm({
                                        message: "If you change the name of the device, on save will try the system to create another copy of the device with the new name and delete the old one.\nDo you want to continue anyway?",
                                        callback: function(m) {
                                            angular.element('#dicomDeviceName').prop('disabled', true);
                                            if (m) {
                                                $('#dicomDeviceName').blur();
                                                scope.changeNameWarning = true;
                                            } else {
                                                $('#dicomDescription').blur();
                                            }
                                            angular.element('#dicomDeviceName').prop('disabled', false);
                                        }
                                    });
                                }
                            });
                        }, 400);
                    } else {
                        if (timeout > 0) {
                            timeout--;
                        } else {
                            clearInterval(waitOfDeviceName);
                        }
                    }
                }, 100);
            }
        },

        clonePart: function($scope, selectedElement, selectedPart){
            clonePartHelper($scope, $scope.wholeDevice , selectedElement, selectedPart, $select[selectedElement].optionRef[0], $scope.newCloneName);
        }

    }
});