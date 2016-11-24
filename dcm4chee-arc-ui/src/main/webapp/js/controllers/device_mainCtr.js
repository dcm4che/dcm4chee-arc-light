// "use strict";

myApp.controller("DeviceController", function($scope, $http, $timeout, $log, cfpLoadingBar, $compile, DeviceService, $parse, schemas, $select, $templateRequest, MainService, user) {

    $scope.activeMenu             = "device_menu";
    $scope.showSave               = false;
    $scope.middleBorder           = "";
    $scope.lastBorder             = "";
    $scope.loaderElement          = "";
    $scope.showDropdownLoader     = false;
    $scope.showFormLoader         = false;
    $scope.validForm              = true;
    $scope.showScrollButton       = false;
    $scope.dynamicform            = {};
    $scope.selectedPart           = {};
    $scope.selectObject           = $select;
    $scope.schemas                = schemas;
    $scope.dicomconn              = [];
    $scope.aeSelected             = {};
    $scope.aeSelected.mode        = "new";
    $scope.saved                  = true;
    setTimeout(function(){
      $scope.$apply(function(){
        $scope.activeMenu         = "";
      });
    }, 2000);

    $http.get("../queue").then(function (res) {
        $scope.queues = res.data;
    })
    /*
    *Watch when the user trys to leave the page
    */
    window.addEventListener("beforeunload", function(e) {
        if ($scope.saved === false) {
            var confirmationMessage = 'It looks like you have been editing something. ' + 'If you leave before saving, your changes will be lost.';
            (e || window.event).returnValue = confirmationMessage; //Gecko + IE
            return confirmationMessage; //Gecko + Webkit, Safari, Chrome etc.
        }
    });

    /*
    *Get device schema
    */
    $http({
        method: 'GET',
        url: 'schema/device.schema.json'
    }).then(function successCallback(response) {
        schemas.device  = response.data;
    }, function errorCallback(response) {
        $log.error("Error loading device names", response);
        vex.dialog.alert("Error loading device names, please reload the page and try again!");
    }); 
    $http({
        method: 'GET',
        // url: 'json/devices.json'
        url: '../aes'
    }).then(function successCallback(response) {
        $scope.aes = response.data;
    }, function errorCallback(response) {
        $log.error("Error loading device names", response);
        vex.dialog.alert("Error loading device names, please reload the page and try again!");
    });    
    //Warn if the user want to leav the page without saving the changes
    $scope.$on('$locationChangeStart', function(event) {
        if ($scope.saved === false) {
            var answer = confirm("Are you sure you want to leave this page without saving changes?")
            if (!answer) {
                event.preventDefault();
            }
        }
    });
    $scope.changeElement = function(element){
            console.log("on changelement element",element);
            var checkDevice = element === "device";
            angular.forEach($select, function(m, j){
              if(element === j && $scope.selectedPart[j]  != undefined ){
                checkDevice = true;
              }
            });

            cfpLoadingBar.start();
            if(
                (
                    checkDevice
                ) 
                  &&
                (
                    element != $scope.selectedElement ||
                    $scope.devicename === "CHANGE_ME"
                )&&
                    $scope.validForm
            ){
                cfpLoadingBar.start();
                if(element === 'dicomNetworkAE'){
                  $scope.selectedPart.dicomTransferCapability  = null;
                }
                $scope.selectedElement  = element;
                $scope.lastBorder       = "active_border";
                $scope.showSave         = true;

                if($scope.selectedElement === "device"){
                    $scope.dynamic_model  = $scope.wholeDevice;
                }else{

                    DeviceService.setFormModel($scope);
                    $scope.dynamic_model = $scope.form[$scope.selectedElement].model;
                }
            }
            if($scope.devicename === "CHANGE_ME"){
                  DeviceService
                  .addDirectiveToDom(
                      $scope, 
                      "add_edit_area",
                      "<div edit-area></div>"
                  );
            }
                        console.log("$scope.selectedPart",$scope.selectedPart);

            cfpLoadingBar.complete();
    };
    $scope.selectElement = function(element) {
        console.log("on selectelement element",element);
        var checkDevice = element === "device";
        angular.forEach($select, function(m, j){
            //Differentiate between array elements and not array elements becouse just the array elements (Select element) has selectedPart model
            if(m.type==="array"){
              if(element === j && $scope.selectedPart[j]  != undefined ){
                checkDevice = true;
              }
            }else{
              if(element === j){
                checkDevice = true;
              }
            }
        });
        
        if(
            (
              checkDevice
            ) 
              &&
            (
                element != $scope.selectedElement ||
                $scope.devicename == "CHANGE_ME"
            )&&
                $scope.validForm
        ) {
            cfpLoadingBar.start();
            //TODO Make this generic
            if(element === 'dicomNetworkAE'){
              $scope.selectedPart.dicomTransferCapability  = null;
            }
            $scope.selectedElement  = element;
            if($scope.selectedElement === "device"){
                $scope.dynamic_schema = DeviceService.getDeviceSchema();
                console.log("$scope.dynamic_schema",$scope.dynamic_schema);
                $scope.dynamic_model  = $scope.wholeDevice;
            }else{
                if(!schemas[$scope.selectedElement] || !schemas[$scope.selectedElement][$scope.selectedElement]){
                  DeviceService.getSchema($scope.selectedElement);
                }
                DeviceService.setFormModel($scope);
            }
            $scope.lastBorder       = "active_border";
            $scope.showSave         = true;
            cfpLoadingBar.complete();
        }
    };

    //Edit selected device
    $scope.edit = function() {
        $scope.form                      = {};
        cfpLoadingBar.start();
        if ($scope.devicename) {
            cfpLoadingBar.set(cfpLoadingBar.status()+(0.1));
            setTimeout(function(){ 
                $scope.showDropdownLoader = true;
                $scope.showFormLoader   = true;
            });
            $scope.selectedElement = "device";
            cfpLoadingBar.set(cfpLoadingBar.status()+(0.1));
            DeviceService
            .addDirectiveToDom(
                $scope, 
                "add_dropdowns",
                "<div select-device-part></div>"
            );
            cfpLoadingBar.set(cfpLoadingBar.status()+(0.2));
            //Wait a little bit so the angularjs has time to render the first directive otherwise some input fields are not showing
            window.setTimeout(function() {

              DeviceService
              .addDirectiveToDom(
                  $scope, 
                  "add_edit_area",
                  "<div edit-area></div>"
              );
            cfpLoadingBar.set(cfpLoadingBar.status()+(0.1));
            }, 100);

        } else {
            cfpLoadingBar.complete();
            vex.dialog.alert("Select device");
        }
        setTimeout(function(){
            DeviceService.warnEvent($scope);
        },1000);
    };

    //Deleting device
    $scope.delete = function() {
        if ($scope.devicename) {
            vex.dialog.confirm({
                  message: 'Are you sure you want to delete the device ' + $scope.devicename + '?',
                  callback: function(value) {
                    if(value){
                      DeviceService.deleteDevice($scope);
                    }else{
                      $log.log("deleting canceled");
                    }
                  }
               });
        } else {
          vex.dialog.alert('Please select device first!');
        }
    };
    $scope.toggleValue = function(array, string){
        for (var i = array.length - 1; i >= 0; i--) {
            console.log("array[i]",array[i]);
            // array[i]
            if(array[i] === "" || array[i] === undefined || array[i] === null){
                array.splice(i,1);
            }
        }
    };
    var setReferencesFromDevice = function(){

        var dicomconn = [];
        angular.forEach($scope.selctedDeviceObject.dicomNetworkConnection, function(l, i) {
            console.log("l",l);
            dicomconn.push({
                "value":"/dicomNetworkConnection/" + i,
                "name":l.cn
            });
        });
        $timeout(function() {
            $scope.$apply(function(){
                $scope.netAESchema = {            
                    type: "object",
                      "required": [
                        "dicomAETitle",
                        "dicomNetworkConnectionReference",
                        "dicomAssociationInitiator",
                        "dicomAssociationAcceptor"
                      ],
                        properties: {
                            "dicomAETitle": {
                                "title": "AE Title",
                                "description": "Unique AE title for this Network AE",
                                "type": "string"
                            },    
                            "dicomNetworkConnectionReference": {
                              "title": "Network Connection Reference",
                              "description": "JSON Pointers to the Network Connection objects for this AE",
                              "type": "array",
                              "default":["/dicomNetworkConnection/0"],
                              "items": {
                                "type": "string"
                              }
                            },    
                            "dicomDescription": {
                              "title": "AE Description",
                              "description": "Unconstrained text description of the application entity",
                              "type": "string"
                            }           
                            // "dicomAssociationInitiator": {
                            //     "title": "Association Initiator",
                            //     "description": "True if the Network AE can initiate associations, false otherwise",
                            //     "type": "boolean"
                            // },
                            // "dicomAssociationAcceptor": {
                            //     "title": "Association Acceptor",
                            //     "description": "True if the Network AE can accept associations, false otherwise",
                            //     "type": "boolean"
                            // }
                        }
                };
                $scope.netAEForm = [
                    {
                        "key":"dicomAETitle",
                        "onChange":"setNamesOfDevice()"
                    },
                    {
                        "type": "conditional",
                        "condition": "selctedDeviceObject.dicomNetworkConnection",
                        "key":"dicomNetworkConnectionReference",
                        "type": "checkboxes",
                        "titleMap": dicomconn,
                        "required": true

                    },
                    {
                    "type": "help",
                    "condition": "!selctedDeviceObject.dicomNetworkConnection",
                    "helpvalue": "To be able to select the reference create first a network connection, the selected device doesn't have any connections!",
                    "required": true
                    },   
                    {
                        "key":"dicomDescription",
                        "type":"textarea",
                        "onChange":"copyDescriptionToDevice()"
                    }     
                    // {
                    // "key": "dicomAssociationInitiator",
                    // // "key": i,
                    // "type": "radios",
                    // "titleMap": [{
                    //     "value": true,
                    //     "name": "True"
                    // }, {
                    //     "value": false,
                    //     "name": "False"
                    // }]
                    // },
                    // {
                    // "key": "dicomAssociationAcceptor",
                    // // "key": i,
                    // "type": "radios",
                    // "titleMap": [{
                    //     "value": true,
                    //     "name": "True"
                    // }, {
                    //     "value": false,
                    //     "name": "False"
                    // }]
                    //}
                ];
            });
        });
        // $scope.newAetModel.dicomNetworkAE[0].dicomNetworkConnectionReference = ["/dicomNetworkConnection/0"];
    }
    $scope.getDevice = function(){

        if($scope.selectedDevice){
            if($scope.selctedDeviceObject && $scope.selctedDeviceObject.dicomDeviceName === $scope.selectedDevice){
                setReferencesFromDevice();
            }else{
                $http({
                    method: 'GET',
                    url: '../devices/'+$scope.selectedDevice
                }).then(function successCallback(response) {
                    console.log("response",response);
                    $scope.selctedDeviceObject = response.data;
                    // $scope.selctedDeviceObject.dicomNetworkConnection;
                    // $scope.selctedDeviceObject.dicomNetworkConnection.push($scope.netConnModelDevice);
                    console.log("$scope.selctedDeviceObject",$scope.selctedDeviceObject);
                    setReferencesFromDevice();

                }, function errorCallback(response) {
                  DeviceService.msg($scope, {
                      "title": "Error",
                      "text": response.status+":"+response.statusText,
                      "status": "error"
                  });
                });
            }
        }
    };
    $scope.addNewConnectionToDevice = function(){
        console.log("in addNewConnectionToDevice");
        console.log("$scope.selctedDeviceObject",$scope.selctedDeviceObject);
        console.log("$scope.netConnModelDevice",$scope.netConnModelDevice);
        // $scope.netConnModelDevice = {};
        var inModel = false;
        angular.forEach($scope.selctedDeviceObject.dicomNetworkConnection, function(m, i){
            if(m.cn === $scope.netConnModelDevice.cn){
                inModel = true;
            }
        });
        if(!inModel){
            $scope.selctedDeviceObject.dicomNetworkConnection = $scope.selctedDeviceObject.dicomNetworkConnection || [];
            $scope.selctedDeviceObject.dicomNetworkConnection.push($scope.netConnModelDevice);
            console.log("$scope.selctedDeviceObject.dicomNetworkConnection",$scope.selctedDeviceObject.dicomNetworkConnection);
        }else{
            vex.dialog.alert("Network connection  with that name already exists!");
        }
        $scope.getDevice();
    };
    $scope.removeNewConnectionFromDevice = function(){
        var inModel = false;
        var index;
        angular.forEach($scope.selctedDeviceObject.dicomNetworkConnection, function(m, i){
            if(m.cn === $scope.netConnModelDevice.cn){
                index = i;
                inModel = true;
            }
        });
        if(inModel){
            console.log("$scope.selctedDeviceObject.dicomNetworkConnection",$scope.selctedDeviceObject.dicomNetworkConnection);
            $scope.selctedDeviceObject.dicomNetworkConnection.splice(index, 1);
            console.log("$scope.selctedDeviceObject.dicomNetworkConnection",$scope.selctedDeviceObject.dicomNetworkConnection);
        }
        $scope.getDevice(); // Refresh the references
    };
    $scope.getConn = function(){
        console.log("getconn called",$scope.activetab);
        console.log("1$scope.newAetModel.dicomNetworkConnection",$scope.newAetModel.dicomNetworkConnection);

        if($scope.newAetModel && $scope.activetab === "createdevice" && $scope.newAetModel.dicomNetworkConnection && $scope.newAetModel.dicomNetworkConnection[0] && $scope.newAetModel.dicomNetworkConnection[0].cn && $scope.newAetModel.dicomNetworkConnection[0].cn != ""){
            console.log("if");
            var dicomconn = [];
            // if($scope.newAetModel && $scope.newAetModel.dicomNetworkConnection){
            dicomconn.push({
                "value":"/dicomNetworkConnection/" + 0,
                "name":$scope.newAetModel.dicomNetworkConnection[0].cn
            });
            // }
            $scope.netAEForm = [
                {
                    "key":"dicomAETitle",
                    "onChange":"setNamesOfDevice()"
                },
                {
                "type": "conditional",
                "condition": "newAetModel.dicomNetworkConnection[0].cn",
                "key":"dicomNetworkConnectionReference",
                "type": "checkboxes",
                "titleMap": dicomconn,
                "required": true
                },
                {
                "type": "help",
                "condition": "!newAetModel.dicomNetworkConnection[0].cn",
                "helpvalue": "To be able to select the reference create or select first a network connection!",
                "required": true
                }, 
                {
                    "key":"dicomDescription",
                    "type":"textarea",
                    "onChange":"copyDescriptionToDevice()"
                }            
                // {
                //     "key": "dicomAssociationInitiator",
                //     // "key": i,
                //     "type": "radios",
                //     "titleMap": [{
                //         "value": true,
                //         "name": "True"
                //     }, {
                //         "value": false,
                //         "name": "False"
                //     }]
                // },
                // {
                //     "key": "dicomAssociationAcceptor",
                //     // "key": i,
                //     "type": "radios",
                //     "titleMap": [{
                //         "value": true,
                //         "name": "True"
                //     }, {
                //         "value": false,
                //         "name": "False"
                //     }]
                // }
                ];
        }
    }
    $scope.changeTabAERegister = function(tabname){
        $scope.activetab = tabname;
        if(tabname==='createdevice'){
            $scope.getConn();
        }else{
            $scope.getDevice(); 
        }
    };
    var validateAeRegisterForms = function(){
        
        console.log("$scope.activetab",$scope.activetab);
        $scope.$broadcast('schemaFormValidate');
        console.log("$scopenetAEForm.$valid", $scope.netAEForm);
        var valid = true;
        var msg = "";

        if($scope.activetab === "createdevice"){
            if(!$scope.newAetModel.dicomDeviceName || $scope.newAetModel.dicomDeviceName === ""){
                valid = false;
                msg += "Device name is required!<br>";
            }
        }
        if($scope.activetab === "selectdevice"){
            if(!$scope.selectedDevice ||  $scope.selectedDevice === ""){
                valid = false;
                msg += "Selecting one device is required!<br>";
            }else{
                if(!$scope.selctedDeviceObject || $scope.selctedDeviceObject.dicomNetworkConnection.length === 0){
                    valid = false;
                    msg += "Seleted device doesn't have any network connections!<br>";
                }
            }

        }
        if(!$scope.newAetModel.dicomNetworkAE[0] || !$scope.newAetModel.dicomNetworkAE[0].dicomAETitle || $scope.newAetModel.dicomNetworkAE[0].dicomAETitle === ""){
            valid = false;
            msg += "Ae Title is required!<br>";
        }
        if(!$scope.newAetModel.dicomNetworkAE[0] || $scope.newAetModel.dicomNetworkAE[0].dicomNetworkConnectionReference.length === 0){
            valid = false;
            msg += "Reference is required!<br>";
        }
        if(valid === false){
            $scope.$apply(function(){
                DeviceService.msg($scope, {
                    "title": "Error",
                    "text": msg,
                    "status": "error"
                });
            });
        }
        return valid;
    }
    // $scope.validate = function(){
    //     console.log("validate");
    //      $scope.$broadcast('schemaFormValidate');
    //      // console.log("$scope.testname2.$valid",$scope.testname2.$valid);
    // }
    $scope.cancleForm = function(){
        return vex.close($scope.$vexAe.id);
    };
    $scope.setNamesOfDevice = function(){
       if($scope.activetab === "createdevice"){
            // console.log("dicomAETitle=",$scope.newAetModel.dicomNetworkAE[0].dicomAETitle);
            // console.log("devicename=",$scope.newAetModel.dicomDeviceName);
            if( 
                $scope.newAetModel.dicomNetworkAE[0] && 
                $scope.newAetModel.dicomNetworkAE[0].dicomAETitle &&
                (   $scope.newAetModel.dicomDeviceName === undefined || 
                    $scope.newAetModel.dicomDeviceName === "" || 
                    $scope.newAetModel.dicomNetworkAE[0].dicomAETitle.slice(0, -1).toLowerCase() === $scope.newAetModel.dicomDeviceName.toLowerCase() || 
                    $scope.newAetModel.dicomNetworkAE[0].dicomAETitle.toLowerCase() === $scope.newAetModel.dicomDeviceName.slice(0, -1).toLowerCase()
                )
            ){
                $scope.newAetModel.dicomDeviceName = $scope.newAetModel.dicomNetworkAE[0].dicomAETitle.toLowerCase();
            }
       }
    };
    $scope.copyDescriptionToDevice = function(){
       if($scope.activetab === "createdevice"){
            if( 
                $scope.newAetModel.dicomNetworkAE[0] && 
                $scope.newAetModel.dicomNetworkAE[0].dicomDescription &&
                (   $scope.newAetModel.dicomDescription === undefined || 
                    $scope.newAetModel.dicomDescription === "" || 
                    $scope.newAetModel.dicomNetworkAE[0].dicomDescription.slice(0, -1) === $scope.newAetModel.dicomDescription || 
                    $scope.newAetModel.dicomNetworkAE[0].dicomDescription === $scope.newAetModel.dicomDescription.slice(0, -1) ||
                    $scope.newAetModel.dicomNetworkAE[0].dicomDescription.slice(0, -1) === $scope.newAetModel.dicomDescription+" " ||
                    $scope.newAetModel.dicomNetworkAE[0].dicomDescription.slice(0, -1) === $scope.newAetModel.dicomDescription+"\n" ||
                    $scope.newAetModel.dicomNetworkAE[0].dicomDescription.slice(0, -1) === $scope.newAetModel.dicomDescription+" \n" 
                )
            ){
                $scope.newAetModel.dicomDescription = $scope.newAetModel.dicomNetworkAE[0].dicomDescription;
            }
       }
    };
    $scope.deleteAE = function(device, ae){
        $scope.deleteDeviceTo = false;
            var html = $compile('<label><input type="checkbox" ng-model="deleteDeviceTo" /> Delete also the device : '+device+'</label>')($scope);
            vex.dialog.open({
              message: 'Are you sure you want to unregister and delete from device the AE <b>'+ae+'</b>',
              input: html,
              buttons: [
                $.extend({}, vex.dialog.buttons.YES, {
                  text: 'Yes'
                }), $.extend({}, vex.dialog.buttons.NO, {
                  text: 'Cancel'
                })
              ],
              callback: function(data) {
                if (data === false) {
                  return console.log('Cancelled');
                }else{
                    // console.log("$scope.deleteDeviceTo",$scope.deleteDeviceTo);
                    // console.log("data",data);
                    // console.log("in else deleteDeviceto=",deleteDeviceTo);
                    // $http.post(studyURL(study.attrs) + '/export/' + $scope.exporterID);
                    $http.delete(
                        "../unique/aets/"+ae
                    ).then(function successCallback(response) {
                        if($scope.deleteDeviceTo === true){
                            $http
                            .delete("../devices/" + device)
                                .success(function(data, status, headers, config) {
                                    DeviceService.msg($scope, {
                                        "title": "Info",
                                        "text": "Device deleted successfully!",
                                        "status": "info"
                                    });
                                    $http.post("../ctrl/reload").then(function (res) {
                                        DeviceService.msg($scope, {
                                            "title": "Info",
                                            "text": "Archive reloaded successfully!",
                                            "status": "info"
                                        });
                                    });
                                    $scope.searchAes();
                                })
                                .error(function(data, status, headers, config) {
                                    $log.error("Error deleting device", status);
                                    DeviceService.msg($scope, {
                                        "title": "Error",
                                        "text": "Error deleting the device!",
                                        "status": "error"
                                    });
                                    cfpLoadingBar.complete();
                                    return false;
                                });
                        }else{

                            $http({
                                method: 'GET',
                                url: '../devices/'+device
                            }).then(function successCallback(response) {
                                console.log("response",response);
                                var deviceObject = response.data;
                                //Remove ae from device and save it back
                                angular.forEach(deviceObject.dicomNetworkAE ,function(m, i){
                                    if(m.dicomAETitle === ae){
                                        deviceObject.dicomNetworkAE.splice(i, 1);
                                    }
                                });
                                $http.put("../devices/" + device, deviceObject)
                                    .success(function(data, status, headers, config) {
                                        DeviceService.msg($scope, {
                                            "title": "Info",
                                            "text": "Ae removed from device successfully!",
                                            "status": "info"
                                        });
                                        $http.post("../ctrl/reload").then(function (res) {
                                            DeviceService.msg($scope, {
                                                "title": "Info",
                                                "text": "Archive reloaded successfully!",
                                                "status": "info"
                                            });
                                        });
                                        $scope.searchAes();
                                    })
                                    .error(function(data, status, headers, config) {
                                        $log.error("Error sending data on put!", status);
                                        addEmptyArrayFieldsPrivate($scope);
                                        DeviceService.msg($scope, {
                                            "title": "error",
                                            "text": "Error, the AE was not removed from device!",
                                            "status": "error"
                                        });
                                        cfpLoadingBar.complete();
                                    });
                            }, function errorCallback(response) {
                              DeviceService.msg($scope, {
                                  "title": "Error",
                                  "text": response.status+":"+response.statusText,
                                  "status": "error"
                              });
                            });
                        }
                        DeviceService.msg($scope, {
                            "title": "Info",
                            "text": "Aet unregistered successfully!",
                            "status": "info"
                        });
                    // });
                    },function errorCallback(response) {
                        DeviceService.msg($scope, {
                            "title": "Error",
                            "text": "Aet couldn't be unregistered!",
                            "status": "error"
                        });
                    });
                }
                $scope.searchAes();
              }
            });
    }
    $scope.editAe = function(device, ae){
        console.log("editae");
        $scope.selectedElement = "dicomNetworkAE";
        $scope.selectedPart["dicomNetworkAE"] = ae.toString();

            $scope.devicename       = device;
            $scope.currentDevice    = $scope.devicename;
            $scope.form             = {};
            cfpLoadingBar.start();
            if($scope.devicename){
                cfpLoadingBar.set(cfpLoadingBar.status()+(0.1));
                setTimeout(function(){ 
                    $scope.showDropdownLoader = true;
                    $scope.showFormLoader   = true;
                });
                // $scope.selectedElement = "device";
                cfpLoadingBar.set(cfpLoadingBar.status()+(0.1));
                DeviceService
                .addDirectiveToDom(
                    $scope, 
                    "add_dropdowns",
                    "<div select-device-part></div>"
                );
                cfpLoadingBar.set(cfpLoadingBar.status()+(0.2));
                //Wait a little bit so the angularjs has time to render the first directive otherwise some input fields are not showing
                window.setTimeout(function() {
                DeviceService
                .addDirectiveToDom(
                  $scope, 
                  "add_edit_area",
                  "<div edit-area></div>"
                );
                cfpLoadingBar.set(cfpLoadingBar.status()+(0.1));
                }, 100);
            }else{
                cfpLoadingBar.complete();
                vex.dialog.alert("Select device");
            }
            setTimeout(function(){
                DeviceService.warnEvent($scope);
                addEffect("right",".devicelist_block", "hide");
                setTimeout(function(){
                    addEffect("left",".deviceedit_block", "show");
                    $scope.changeElement(ae);
                    // console.log("$scope.selectedPart",$scope.selectedPart);
                    // console.log("$('#dicomNetworkAE option[value='+ae+']')",$('#dicomNetworkAE option[value='+ae+']'));
                    $('#dicomNetworkAE option[value='+ae+']').prop('selected', true);
                    // $scope.selectModel["dicomNetworkAE"] = ae.toString();
                },301);
            },1000);

    };
    $scope.createAe = function(){
        $scope.dicomconn = [];
        if($scope.aeSelected.mode === "edit" && $scope.newAetModel.dicomNetworkConnection){
            angular.forEach($scope.newAetModel.dicomNetworkConnection, function(l, i) {
                $scope.dicomconn.push({
                    "value":"/dicomNetworkConnection/" + i,
                    "name":l.cn
                });
            });
            angular.forEach($scope.newAetModel.dicomNetworkAE, function(m, j) {
                if(m.dicomAETitle === $scope.aeSelected.ae){
                    $scope.netAEModel = $scope.newAetModel.dicomNetworkAE[j];
                }
            });
        }else{
            $scope.newAetModel = {};
            $scope.newAetModel.dicomNetworkConnection = [];
            $scope.newAetModel.dicomNetworkConnection[0] = {};
            $scope.newAetModel.dicomNetworkAE = [];
            $scope.newAetModel.dicomNetworkAE[0] = {};
            $scope.netAEModel = $scope.newAetModel.dicomNetworkAE[0];
            $scope.dicomconn.push({
                "value":"/dicomNetworkConnection/" + 0,
                "name":"dicom"
            });
        }
        $templateRequest('templates/device_aet.html').then(function(tpl) {
            $scope.newDeviceAESchema = {
                "title": "Device",
                "description": "DICOM Device related information",
                "type": "object",
                "required": ["dicomDeviceName", "dicomInstalled"],
                "properties": {
                    "dicomDeviceName": {
                      "title": "Device Name",
                      "description": "A unique name for this device",
                      "type": "string"
                    },    
                    "dicomDescription": {
                      "title": "Device Description",
                      "description": "Unconstrained text description of the device",
                      "type": "string"
                    },
                    "dicomInstalled": {
                      "title": "installed",
                      "description": "Boolean to indicate whether this device is presently installed on the network",
                      "type": "boolean",
                      "default": true
                    },
                } 
            }
            $scope.newDeviceAEForm = [
                "dicomDeviceName",
                {
                    "key":"dicomDescription",
                    "type": "textarea"
                },
                {
                    "key": "dicomInstalled",
                    "type": "radios",
                    "titleMap": [{
                        "value": true,
                        "name": "True"
                    }, {
                        "value": false,
                        "name": "False"
                    }]
                }
            ]
            // $scope.newAetModel.devicemodel = {};
           $scope.netConnSchema = {
            type: "object",
            properties: {
                "cn": {
                  "title": "Name",
                  "description": "Arbitrary/Meaningful name for the Network Connection object",
                  "type": "string",
                  "default":"dicom"
                },    
                "dicomHostname": {
                  "title": "Hostname",
                  "description": "DNS name for this particular connection",
                  "type": "string",
                  "default":"localhost"
                },   
                "dicomPort": {
                  "title": "Port",
                  "description": "TCP/UDP port that a service is listening on. May be missing if this network connection is only used for outbound connections",
                  "type": "integer",
                  "minimum": 0,
                  "maximum":99999,
                  "exclusiveMinimum": true,
                  "default":104
                }
                // ,
                // "dicomTLSCipherSuite": {
                //   "title": "TLS CipherSuites",
                //   "description": "The TLS CipherSuites that are supported on this particular connection. If not present TLS is disabled",
                //   "type": "array",
                //   "items": {
                //     "enum": [
                //       "SSL_RSA_WITH_NULL_SHA",
                //       "TLS_RSA_WITH_AES_128_CBC_SHA",
                //       "SSL_RSA_WITH_3DES_EDE_CBC_SHA"
                //     ]
                //   }
                // },
                // "dicomInstalled": {
                //   "title": "installed",
                //   "description": "True if the Network Connection is installed on the network. If not present, information about the installed status of the Network Connection is inherited from the device",
                //   "type": "boolean",
                //   "default": true
                // },
            }
            };
            $scope.sfOptions = { validationMessage: { 302: 'Required field!' }};
            $scope.netConnForm = [
                {
                    "key":"cn",
                    "onChange":"getConn()"
                },
                "dicomHostname",
                "dicomPort",
                // "dicomTLSCipherSuite",
                // {
                //     "key": "dicomInstalled",
                //     // "key": i,
                //     "type": "radios",
                //     "titleMap": [{
                //         "value": true,
                //         "name": "True"
                //     }, {
                //         "value": false,
                //         "name": "False"
                //     }]
                // }
            ];
            $scope.netAESchema = {
                type: "object",
                "required": [
                        "dicomAETitle",
                        "dicomNetworkConnectionReference",
                        "dicomAssociationInitiator",
                        "dicomAssociationAcceptor"
                ],
                properties: {
                    "dicomAETitle": {
                      "title": "AE Title",
                      "description": "Unique AE title for this Network AE",
                      "type": "string"
                    },
                    "dicomNetworkConnectionReference": {
                      "title": "Network Connection Reference",
                      "description": "JSON Pointers to the Network Connection objects for this AE",
                      "default":["/dicomNetworkConnection/0"],
                      "type": "array",
                      "items": {
                        "type": "string"
                      }                    
                    },
                    "dicomDescription": {
                      "title": "AE Description",
                      "description": "Unconstrained text description of the application entity",
                      "type": "string"
                    }
                    // ,
                    // "dicomAssociationInitiator": {
                    //   "title": "Association Initiator",
                    //   "description": "True if the Network AE can initiate associations, false otherwise",
                    //   "type": "boolean",
                    //   "default": false
                    // },
                    // "dicomAssociationAcceptor": {
                    //   "title": "Association Acceptor",
                    //   "description": "True if the Network AE can accept associations, false otherwise",
                    //   "type": "boolean",
                    //   "default": false
                    // }
                }
            };
            
            $scope.dicomNetworkConnectionReference = "dicomNetworkConnectionReference";
            // console.log("$scope.dicomconn",$scope.dicomconn);

            $scope.netAEForm = [
                {
                    "key":"dicomAETitle",
                    "onChange":"setNamesOfDevice()"
                },
                {
                "type": "conditional",
                "condition": "newAetModel.dicomNetworkConnection[0].cn",
                "key":"dicomNetworkConnectionReference",
                "type": "checkboxes",
                "titleMap": $scope.dicomconn,
                "required": true
                },
                {
                "type": "help",
                "condition": "!newAetModel.dicomNetworkConnection[0].cn",
                "helpvalue": "To be able to select the reference create or select first a network connection!",
                "required": true
                },
                {
                    "key":"dicomDescription",
                    "onChange":"copyDescriptionToDevice()",
                    "type": "textarea"
                }
                // , 
                // "dicomTLSCipherSuite",
                // {
                //     "key": "dicomAssociationInitiator",
                //     // "key": i,
                //     "type": "radios",
                //     "titleMap": [{
                //         "value": true,
                //         "name": "True"
                //     }, {
                //         "value": false,
                //         "name": "False"
                //     }]
                // },
                // {
                //     "key": "dicomAssociationAcceptor",
                //     // "key": i,
                //     "type": "radios",
                //     "titleMap": [{
                //         "value": true,
                //         "name": "True"
                //     }, {
                //         "value": false,
                //         "name": "False"
                //     }]
                // }
            ];
            

            $scope.netConnModel = $scope.newAetModel.dicomNetworkConnection[0];


            if($scope.selectedDevice && $scope.selectedDevice.dicomNetworkConnection){
                $scope.netConnModelDevice = $scope.selectedDevice.dicomNetworkConnection.push({});
            }else{
                $scope.netConnModelDevice = {};
            }
            var html = $compile(tpl)($scope);
            // var html = $compile('<select id="exporter" ng-model="exporterID" class="col-md-12"><option ng-repeat="exporter in exporters" title="{{exporter.description}}">{{exporter.id}}</option></select>')($scope);
            $scope.$vexAe = vex.dialog.open({
                message: 'Register new Application Entity',
                input: html,
                form:"testname",
                className:"vex-theme-os registernewaet",
                buttons: [
                $.extend({}, vex.dialog.buttons.YES, {
                    text: 'Register',
                    className: "defaultbutton"
                }), $.extend({}, vex.dialog.buttons.NO, {
                    text: 'Cancel',
                    className: "defaultbutton"
                })
                ],
                onSubmit: function(e) {

                    console.log("onsubmit");
                    if(validateAeRegisterForms()){
                        console.log("validation");
                        $scope.$vexAe.data().vex.callback();
                    }
                    e.preventDefault();
                    e.stopPropagation();
                },
                callback: function(data) {
                    console.log("callback");

                    if (data === false) {
                      console.log('1Cancelled');
                      return false;
                    }else{
                        console.log("not cancelled +$scope.newAet=",$scope.newAet);
                        $http.post(
                            "../unique/aets/"+$scope.netAEModel.dicomAETitle
                        ).then(function successCallback(response) {
                            console.log("success response",response);
                            if($scope.activetab === "createdevice"){
                                //Create device
                                 //            console.log("$scope.netAEModel",$scope.netAEModel);
                                 // console.log("$scope.newAetModel",$scope.newAetModel);
                                $scope.newAetModel.dicomNetworkAE[0].dicomAssociationInitiator = true;
                                $scope.newAetModel.dicomNetworkAE[0].dicomAssociationAcceptor = true;
                                $http.post("../devices/" + $scope.newAetModel.dicomDeviceName, $scope.newAetModel)
                                .success(function(data, status, headers, config) {
                                    DeviceService.msg($scope, {
                                        "title": "Info",
                                        "text": "Aet registered successfully!<br>Device created successfully!",
                                        "status": "info"
                                    });
                                    $http.post("../ctrl/reload").then(function (res) {
                                        DeviceService.msg($scope, {
                                            "title": "Info",
                                            "text": "Archive reloaded successfully!",
                                            "status": "info"
                                        });
                                    });
                                    $scope.searchAes();
                                })
                                .error(function(data, status, headers, config) {
                                    cfpLoadingBar.complete();
                                    $http.delete(
                                        "../unique/aets/"+$scope.netAEModel.dicomAETitle
                                    ).then(function successCallback(response) {
                                        DeviceService.msg($scope, {
                                            "title": "Error",
                                            "text": "Aet couldn't be registered!",
                                            "status": "error"
                                        });
                                    });
                                });
                            }else{
                                console.log("in else post");
                                $scope.selctedDeviceObject.dicomNetworkAE =  $scope.selctedDeviceObject.dicomNetworkAE || [];
                                $scope.netAEModel.dicomAssociationInitiator = true;
                                $scope.netAEModel.dicomAssociationAcceptor = true;
                                $scope.selctedDeviceObject.dicomNetworkAE.push($scope.netAEModel);
                                $http.put("../devices/" + $scope.selctedDeviceObject.dicomDeviceName, $scope.selctedDeviceObject)
                                .success(function(data, status, headers, config) {
                                    DeviceService.msg($scope, {
                                        "title": "Info",
                                        "text": "Aet registered and added to device successfully!",
                                        "status": "info"
                                    });
                                    $http.post("../ctrl/reload").then(function (res) {
                                        DeviceService.msg($scope, {
                                            "title": "Info",
                                            "text": "Archive reloaded successfully!",
                                            "status": "info"
                                        });
                                    });
                                    $scope.searchAes();
                                })
                                .error(function(data, status, headers, config) {
                                    cfpLoadingBar.complete();
                                    $http.delete(
                                        "../unique/aets/"+$scope.netAEModel.dicomAETitle
                                    ).then(function successCallback(response) {
                                        DeviceService.msg($scope, {
                                            "title": "Error",
                                            "text": "Aet couldn't be registered!",
                                            "status": "error"
                                        });
                                    });
                                });
                            }
                            // DeviceService.msg($scope, {
                            //     "title": "Info",
                            //     "text": "Aet registered successfully!",
                            //     "status": "info"
                            // });
                            vex.close($scope.$vexAe.id);
                        }, function errorCallback(response) {
                            console.log("errorcallback response",response);
                            if(response.status === 409){
                                DeviceService.msg($scope, {
                                    "title": "Error "+response.status,
                                    "text": "AET already exists, try with an other name",
                                    "status": "error"
                                });
                            }else{
                                DeviceService.msg($scope, {
                                    "title": "Error"+ response.status,
                                    "text": "Something went wrong please try again later",
                                    "status": "error"
                                });
                                vex.close($scope.$vexAe.id);
                            }
                        });
                        return true;
                    }
                    
                }
            });
        });
    }

    /*
    *Create new Device
    */
    $scope.createDevice = function() {
        $scope.showSave         = true;
        $scope.showCancel       = true;
        $scope.form             = {};
          $scope.wholeDevice      = {
          "dicomDeviceName":"CHANGE_ME",
          "dicomInstalled":true
        };
        $scope.devicename       = $scope.wholeDevice.dicomDeviceName;
        $scope.currentDevice    = $scope.wholeDevice.dicomDeviceName;
        $scope.newDevice        = true;
        $scope.selectedElement  = "device";
        $scope.middleBorder     = "active_border";
        $scope.lastBorder       = "active_border";
        $scope.showSave         = true;
        DeviceService
        .addDirectiveToDom(
                $scope,
                "add_dropdowns",
                "<div select-device-part></div>"
        );
        //Wait a little bit so the angularjs has time to render the first directive otherwise some input fields are not showing
        window.setTimeout(function() {
            DeviceService
            .addDirectiveToDom(
                    $scope,
                    "add_edit_area",
                    "<div edit-area></div>"
            );

            addEffect("right",".devicelist_block", "hide");
            setTimeout(function(){
                addEffect("left",".deviceedit_block", "show");
            },301);
        }, 100);
        
    };
    /*
    *Delete part
    */
    $scope.deletePart = function(element) {
        $scope.deletPartProcess = true;
        //TODO Make service for this
        switch (element) {
            case "dicomNetworkConnection":
                if ($scope.selectedPart.dicomNetworkConnection) {
                    var m = confirm("Are you sure you want to delete the network connection: " + $scope.selectedPart.dicomNetworkConnection + "?");
                    
                    if (m) {
                        var toDeleteKey;

                        angular.forEach($scope.wholeDevice.dicomNetworkConnection, function(value, key) {
                            if (value.cn == $scope.selectedPart.dicomNetworkConnection) {
                                toDeleteKey = key;
                            }
                        });

                        $scope.wholeDevice.dicomNetworkConnection.splice(toDeleteKey, 1);
                        $scope.dicomNetConnModel              = {};
                        $scope.selectedElement                = "";
                        $scope.selectedPart.dicomNetworkConnection = null;
                        $scope.lastBorder                     = "";

                        //TODO Check references and delete them to
                        $scope.wholeDevice    = DeviceService.clearReference(toDeleteKey, $scope.wholeDevice);
                        // $scope.networkAeForm  = DeviceService.getFormNetworkAe($scope.selectedNetworkConnection);
                    }
                } else {
                    //TODO replace it with an bautiful messaging
                    $scope.activeMenu = "dicomNetworkConnection";
                    vex.dialog.alert("Please select first a network connection to delete");
                }
                break;


            case "dicomNetworkAE":
                if($scope.selectedPart.dicomNetworkAE) {

                    var m = confirm("Are you sure you want to delete the Network AE: " + $scope.selectedPart.dicomNetworkAE + "?");
                  
                    if(m){

                        var toDeleteKey;
                        var shouldDeleteTransfare = false;
                        angular.forEach($scope.wholeDevice.dicomNetworkAE, function(value, key) {
                            if (value.dicomAETitle == $scope.selectedPart.dicomNetworkAE) {
                                toDeleteKey = key;
                            }
                        });
                        if($scope.transfcap[0]){
                            angular.forEach($scope.wholeDevice.dicomNetworkAE[toDeleteKey].dicomTransferCapability, function(k, i){
                              if(k.cn === $scope.transfcap[0].cn){
                                $scope.transfcap = null;
                              }
                            });
                        }
                        
                        $scope.wholeDevice.dicomNetworkAE.splice(toDeleteKey, 1);
                        $scope.networkAeModel       = {};
                        $scope.networkAeForm        = [];
                        $scope.selectedElement      = "";
                        $scope.selectedPart.dicomNetworkAE    = null;
                        $scope.lastBorder           = "";
                    }

                }else{
                    $scope.activeMenu = "dicomNetworkAE";
                    //TODO replace it with an bautiful messaging
                    vex.dialog.alert("Please select first a network AE to delete");
                }
                break;

            case "dicomTransferCapability":

                if($scope.selectedPart.dicomTransferCapability){

                    // var m = confirm("Are you sure you want to delete the Network AE: " + $scope.selectedPart.dicomTransferCapability + "?");
                  vex.dialog.confirm({
                      message: "Are you sure you want to delete the Network AE: " + $scope.selectedPart.dicomTransferCapability + "?",
                      callback: function(m) {
                        if (m) {
                            var networkAEKey;
                            var toDeleteKey;
                            angular.forEach($scope.wholeDevice.dicomNetworkAE, function(value, key) {
                                if (value.dicomAETitle == $scope.selectedPart.dicomNetworkAE) {
                                    networkAEKey = key;
                                    angular.forEach(value.dicomTransferCapability, function(m, k) {
                                        if (m.cn == $scope.selectedPart.dicomTransferCapability) {
                                            toDeleteKey = k;
                                        }
                                    });
                                }
                            });

                            $scope.wholeDevice.dicomNetworkAE[networkAEKey].dicomTransferCapability.splice(toDeleteKey, 1);
                            $scope.selectedElement    = "";
                            $scope.selectedPart.dicomTransferCapability  = null;
                            $scope.lastBorder         = "";
                            $scope.showSave           = false;
                        }
                      }
                   });
                }else{
                    $scope.activeMenu = "dicomTransferCapability";
                    vex.dialog.alert("Please select first a network connection to delete");
                }
                break;
        }
        $scope.editMode = false;
    };
    $scope.createPart = function(element) {
                $scope.dynamic_model = {};
                var validProcess = DeviceService.checkValidProcess($scope, element);
                if(validProcess.valid){
                  $scope.selectedElement = element;
                  $scope.activeMenu      = element;
                  $scope.form[$scope.selectedElement] = $scope.form[$scope.selectedElement] || {};
                  $scope.dynamic_model   = {};
                  if(!schemas[$scope.selectedElement]){
                    DeviceService.getSchema($scope.selectedElement);
                    var wait = setInterval(function(){
                          var checkItemsProperties = (
                                            schemas[$scope.selectedElement] && 
                                            schemas[$scope.selectedElement][$scope.selectedElement] && 
                                            schemas[$scope.selectedElement][$scope.selectedElement]["items"] && 
                                            schemas[$scope.selectedElement][$scope.selectedElement]["items"]["properties"]
                                            );
                          var checkItems = (
                                            schemas[$scope.selectedElement] && 
                                            schemas[$scope.selectedElement][$scope.selectedElement] && 
                                            schemas[$scope.selectedElement][$scope.selectedElement]["items"] && 
                                            schemas[$scope.selectedElement][$scope.selectedElement]["items"][$scope.selectedElement]
                                            );
                          var checkProp = (
                                            schemas[$scope.selectedElement] && 
                                            schemas[$scope.selectedElement][$scope.selectedElement] && 
                                            schemas[$scope.selectedElement][$scope.selectedElement][$scope.selectedElement]
                                          );
                          if(checkItems || checkProp || checkItemsProperties){
                            clearInterval(wait);
                            // DeviceService.setFormModel($scope);
                            if(checkItems){
                              $scope.form[$scope.selectedElement]["schema"] = schemas[$scope.selectedElement][$scope.selectedElement]["items"][$scope.selectedElement];
                            }else{
                              if(checkProp){
                                $scope.form[$scope.selectedElement]["schema"] = schemas[$scope.selectedElement][$scope.selectedElement][$scope.selectedElement];
                              }else{
                                $scope.form[$scope.selectedElement]["schema"] = schemas[$scope.selectedElement][$scope.selectedElement]["items"]["properties"];
                              }
                            }
                            if($select[$scope.selectedElement].parentOf){
                                angular.forEach($select[$scope.selectedElement].parentOf,function(m,i){
                                    delete $scope.form[$scope.selectedElement]["schema"].properties[$select[$scope.selectedElement].parentOf[i]];
                                });
                            }
                            DeviceService.createPart($scope);
                        }
                    },100);
                  }else{
                      DeviceService.createPart($scope);
                  }
                  $scope.showCancel = true;
                  $scope.showSave   = true;
                  $scope.lastBorder = "active_border";
                  $scope.editMode   = true;
                  $scope.validForm  = false;
                  setTimeout(function(){ 
                      $scope.$apply();
                  });
              }else{
                DeviceService.msg($scope, {
                    "title": "Warning",
                    "text": validProcess.message,
                    "status": "warning"
                });
              }
       

    };
    $scope.save = function() {
        cfpLoadingBar.start();
        $scope.validForm = DeviceService.validateForm($scope).valid;
        var message = DeviceService.validateForm($scope).message;
        if ($scope.validForm) {
            $timeout(function() {
                DeviceService.clearJson($scope);
                if($scope.currentDevice === "CHANGE_ME" || $scope.devicename === "CHANGE_ME"){
                    $scope.newDevice = true;
                }else{
                    $scope.newDevice = false;
                }
                if($scope.devicename === "CHANGE_ME"){
                  $scope.devicename = $scope.wholeDevice.dicomDeviceName;
                  var deviceIsInArray = false;
                  angular.forEach($scope.devices, function(m, i){
                    if(m.dicomDeviceName === $scope.wholeDevice.dicomDeviceName){
                        deviceIsInArray = true;
                    }
                  });
                  if(!deviceIsInArray){
                      $scope.devices.push({
                        "dicomDeviceName":$scope.wholeDevice.dicomDeviceName
                      })
                  }
                }
                if($scope.currentDevice != $scope.wholeDevice.dicomDeviceName && $scope.currentDevice != "CHANGE_ME"){

                  DeviceService.saveWithChangedName($scope);

                }else{
                    DeviceService.save($scope);
                }
            });
        } else {
            cfpLoadingBar.complete();
            $timeout(function() {
            $scope.$apply(function(){
              $scope.editMode = true;
              $scope.validForm = false;
            });
          });
          DeviceService.msg($scope, {
              "title": "Warning",
              "text": message,
              "status": "warning"
          });
        }
        $scope.editMode = false;
        DeviceService.addEmptyArrayFields($scope);
        cfpLoadingBar.complete();
    };

    //Load device names on pageload
    $timeout(function() {
      
        $scope.$apply(function() {
            if(document.getElementById("init_select")){
                document.getElementById("init_select").focus(); //Focus the dropdown element
            }
            $http({
                method: 'GET',
                // url: 'json/devices.json'
                url: '../devices'
            }).then(function successCallback(response) {
                $scope.devices = response.data;
            }, function errorCallback(response) {
                $log.error("Error loading device names", response);
                vex.dialog.alert("Error loading device names, please reload the page and try again!");
            });            

            $http({
                method: 'GET',
                url: '../aets'
            }).then(function successCallback(response) {
                $scope.aets = response.data;
            }, function errorCallback(response) {
                $log.error("Error loading aets ", response);
                // vex.dialog.alert("Error loading device names, please reload the page and try again!");
            });
        });
    });

    //Toggle function to show / hide the delete and create buttons for sub parts of device
    $scope.toggle = function(element) {
          if ($scope.activeMenu && $scope.activeMenu === element) {
              $scope.activeMenu = "";
          } else {
              $scope.activeMenu = element;
          }
    };



    /*
    *Check if it was saved
    */
    $scope.savedCheck = function(e){
      if($scope.editMode && $(e.target).closest('.form_content').length<1 && !(e.target.className == "create "+$scope.selectedElement)){
        var validateForm = DeviceService.validateForm($scope);
        $scope.validForm = validateForm.valid;
        var message = validateForm.message;
        if(!$scope.validForm){
          e.preventDefault();
          // vex.dialog.alert(message);
          DeviceService.msg($scope, {
              "title": "Warning",
              "text": message,
              "status": "warning"
          });

        }
      }
    };

    
    /*
    *Watch the form models to see if something was changed so you can set the editmode to true;
    */
    angular.forEach($select, function(m, i){

      $scope.$watchCollection('[dicomNetConnModel, networkAeModel, transfareCapModel, form['+i+'].model]', function(newValue, oldValue) {
        if(!$scope.deletPartProcess){
          if(!DeviceService.equalJSON(oldValue,newValue)){
            $scope.editMode = true;
          }
        }else{
          $scope.deletPartProcess = false;
        }
      });
    });

    /*
    *Watch wholeDevice json-object to see if it was changet so you can set the saved wariable to false
    */
    $scope.$watchCollection('wholeDevice', function(newValue, oldValue){
      if(!DeviceService.equalJSON(oldValue,newValue) &&  newValue.dicomDeviceName == oldValue.dicomDeviceName){
        $scope.saved = false;
      }
    });  

    $scope.$watchCollection('[selectedElement]', function(newValue, oldValue){
      if(newValue[0]!=oldValue[0] && newValue[0] === "device"){
          DeviceService.warnEvent($scope);
      }
    });

    var cancel = function(){
        $scope.deletPartProcess = true;
        if($scope.selectedElement === "device"){
                $scope.devicename       = "";
                $scope.currentDevice    = "";
                $scope.newDevice        = true;
                $scope.middleBorder     = "";
                $scope.lastBorder       = "";
                $scope.deviceModel      = {};
                $scope.wholeDevice      = {};
                $scope.showSave         = false;
                angular.element(document.getElementById("add_dropdowns")).html("");
                angular.element(document.getElementById("add_edit_area")).html("");
        }else{
                DeviceService.cancle($scope);
                $scope.form[$scope.selectedElement].model  = {};
                DeviceService.removeEmptyPart($scope.wholeDevice[$scope.selectedElement], [$select[$scope.selectedElement].optionValue]);
        }

        $scope.selectedElement  = "device";
        $scope.validForm        = true;
        // $scope.activeMenu       = "";
        $scope.showCancel       = false;
        // $scope.showSave         = false;
        // $scope.lastBorder       = "";
        $scope.editMode         = false;
    };
    /*
    *Implementation of the cancel button
    */
    $scope.cancel = function(){
        cancel();
    };

    /*
    *If the editMode is active and form is not valid show the block_layer (div element that over the rest of the app but the form)
    */
    $scope.showBlockLayer = function(){
      return ($scope.editMode && !$scope.validForm);
    };
    $scope.echoAe = function(ae){

        var html =  '<div class="col-md-6">'+
                        '<span>Select one AET:</span>'+
                        '<select ng-options="obj.title as obj.title for obj in aets track by obj.title" ng-model="selectedAet" name="aet"></select>'+
                    '</div>'+ 
                    '<div class="col-md-6">'+
                        '<span>remote AET:<br>'+ae+'</span>'+
                    '</div>';
        html = $compile(html)($scope);
        $scope.selectedPart.dicomNetworkAE = ae;
        $scope.$vexAe = vex.dialog.open({
            message: 'Send echo to Network AE',
            input: html,
            className:"vex-theme-os echoae",
            buttons: [
              $.extend({}, vex.dialog.buttons.YES, {
                text: 'Echo'
              }), $.extend({}, vex.dialog.buttons.NO, {
                text: 'Close'
              })
            ],
            callback: function(data) {
              cfpLoadingBar.start();
              if (data === false) {
                cfpLoadingBar.complete();
                return console.log('Cancelled');
              }else{
                $scope.echo();
              }
            }
        });
    };
    $scope.echo = function(){
      if($scope.selectedAet && $scope.selectedPart.dicomNetworkAE){
        $http({
            method: 'POST',
            // url: 'json/devices.json'
            url: '../aets/'+$scope.selectedAet+'/echo/'+$scope.selectedPart.dicomNetworkAE
        }).then(function successCallback(response) {
            // alert(response.data);
            try{
              if(response.data && response.data.result===0){
                DeviceService.msg($scope, {
                    "title": "Info",
                    "text": "Echo successfully accomplished!<br>- Connection time: "+
                            response.data.connectionTime+
                            " ms<br/>- Echo time: "+
                            response.data.echoTime+
                            " ms<br/>- Release time: "+
                            response.data.releaseTime+" ms",
                    "status": "info"
                });
              }else{
                  if(response.data.errorMessage){
                    DeviceService.msg($scope, {
                        "title": "Error",
                        "text": response.data.errorMessage,
                        "status": "error"
                    });
                  }else{
                    DeviceService.msg($scope, {
                        "title": "Error",
                        "text": "Something went wrong, echo didn't work!",
                        "status": "error"
                    });
                  }
              }
              $scope.selectedPart =  {};
            }catch(e){
              // $log.error("e=",e);
              DeviceService.msg($scope, {
                  "title": "Error",
                  "text": "Something went wrong, echo didn't work!",
                  "status": "error"
              });
            }
            $scope.selectedAet = "";
        }, function errorCallback(response) {
            $log.error("Error loading aets ", response);
            DeviceService.msg($scope, {
                        "title": "Error",
                        "text": "Something went wrong, echo didn't work!",
                        "status": "error"
            });
            // vex.dialog.alert("Error loading device names, please reload the page and try again!");
        });
      }else{
        if(!$scope.selectedAet){
            DeviceService.msg($scope, {
                "title": "Error",
                "text": "Select first one AET!",
                "status": "error"
            });
        }else{
            DeviceService.msg($scope, {
                "title": "Error",
                "text": "Remote AET is missing, refresh the page and try again!",
                "status": "error"
            });
        }
      }
      cfpLoadingBar.complete();
    };

    $scope.showEcho = function(){
      if($scope.selectedElement == 'dicomNetworkAE' && !$scope.showCancel && $scope.devicenam != "CHANGE_ME"){
        return true;
      }else{
        return false;
      }
    };
    $scope.splitStringToObject = function(value,key){
      $scope.selectModel = {};
      if(angular.isDefined($scope.wholeDevice)){
        if(value.optionRef.length > 1){
          DeviceService.getObjectFromString($scope, value, key);
        }else{
          $scope.selectModel[key] = $scope.wholeDevice[value.optionRef[0]];
        }
      }
    };

    $scope.cloneDevice = function(){
      cfpLoadingBar.start();
      var html =$compile(
        '<lable>Select device to clone:</label>'+
        '<select tabindex="1"'+
            'id="init_select"'+
            'class="form-control"'+
            'name="device"'+
            'ng-model="devicename"'+
            'ng-options="obj.dicomDeviceName as obj.dicomDeviceName for obj in devices"'+
            'on-device-change required>'+
        '</select>'+
        '<label>set the name for the new device</label>'+
        '<input type="text" ng-model="clonename" required/>'
      )($scope);
      vex.dialog.open({
        message: 'Clone device',
        input: html,
        buttons: [
          $.extend({}, vex.dialog.buttons.YES, {
            text: 'Clone'
          }), $.extend({}, vex.dialog.buttons.NO, {
            text: 'Cancel'
          })
        ],
        callback: function(data) {
          if (data === false) {
            cfpLoadingBar.complete();
            return console.log('Cancelled');
          }else{
              var isAlreadyThere = false;
              angular.forEach($scope.devices, function(m){
                  if(m.dicomDeviceName === $scope.clonename){
                      isAlreadyThere = true;
                  }
              });
              if(!isAlreadyThere && $scope.devicename != undefined && $scope.devicename != "" && $scope.clonename != undefined && $scope.clonename != ""){
                $http({
                        method: 'GET',
                        url: '../devices/'+$scope.devicename
                        }).then(function successCallback(response) {
                          cfpLoadingBar.set(cfpLoadingBar.status()+(0.5));
                          var device = response.data;
                          device.dicomDeviceName = $scope.clonename;
                          $http.post("../devices/" + $scope.clonename, device)
                              .success(function(data, status, headers, config) {
                                  DeviceService.msg($scope, {
                                      "title": "Info",
                                      "text": "Clone created successfully!",
                                      "status": "info"
                                  });
                                  $scope.devices.push({
                                    dicomDeviceName : $scope.clonename,
                                    dicomInstalled: true
                                  });
                                  $scope.devicename = "";
                                  $scope.clonename = "";
                                  cfpLoadingBar.complete();
                              })
                              .error(function(data, status, headers, config) {
                                  $log.error("Error sending data on put!", status);
                                  addEmptyArrayFieldsPrivate($scope);
                                  DeviceService.msg($scope, {
                                      "title": "error",
                                      "text": "Error, clone could not be created!",
                                      "status": "error"
                                  });
                                  cfpLoadingBar.complete();
                              });
                      }, function errorCallback(response) {
                          DeviceService.msg($scope, {
                              "title": "Error",
                              "text": response.status+":"+response.statusText,
                              "status": "error"
                          });
                          $log.error("Error",response);
                          cfpLoadingBar.complete();
                      });
              }else{
                $scope.$apply(function() {
                  if(isAlreadyThere){
                      DeviceService.msg($scope, {
                          "title": "Error",
                          "text": "Name need to be unique!",
                          "status": "error"
                      });
                  }else{
                    
                      DeviceService.msg($scope, {
                          "title": "Error",
                          "text": "Error, fields required",
                          "status": "error"
                      });
                  }
                  cfpLoadingBar.complete();
                });
              }
          }
        }
      });
    };

    $scope.clonePart = function(part){
      cfpLoadingBar.start();
      if($scope.selectedPart[part]){
        var html = $compile(
                      '<input type="text" ng-model="newCloneName" required/>'
                    )($scope);
        vex.dialog.open({
          message: 'Set the new name to clone "'+$scope.selectedPart[part]+'"!',
          input: html,
          buttons: [
            $.extend({}, vex.dialog.buttons.YES, {
              text: 'Clone'
            }), $.extend({}, vex.dialog.buttons.NO, {
              text: 'Cancel'
            })
          ],
          callback: function(data) {
            if (data === false) {
              cfpLoadingBar.complete();
              return console.log('Cancelled');
            }else{
              DeviceService.clonePart($scope, part, $scope.selectedPart);
            }
          }
        });
      }else{
          DeviceService.msg($scope, {
              "title": "Error",
              "text": "Select first a "+$select[part].title,
              "status": "error"
          });
          cfpLoadingBar.complete();
      }
    };


    //Device list


    //Deleting device
    $scope.deleteDeviceList = function(devicename) {
        $scope.devicename = devicename;
        if ($scope.devicename) {
            vex.dialog.confirm({
                  message: 'Are you sure you want to delete the device ' + $scope.devicename + '?',
                  callback: function(value) {
                    if(value){
                      DeviceService.deleteDevice($scope);
                    }else{
                      $log.log("deleting canceled");
                    }
                  }
               });
        } else {
          vex.dialog.alert('Please select device first!');
        }
    };
    $scope.cloneDeviceDeviceList = function(devicename){
      cfpLoadingBar.start();
        $scope.devicename = devicename;
      var html =$compile(
        '<label>set the name for the new device</label>'+
        '<input type="text" ng-model="clonename" required/>'
      )($scope);
      vex.dialog.open({
        message: 'Clone device: '+devicename,
        input: html,
        buttons: [
          $.extend({}, vex.dialog.buttons.YES, {
            text: 'Clone'
          }), $.extend({}, vex.dialog.buttons.NO, {
            text: 'Cancel'
          })
        ],
        callback: function(data) {
          if (data === false) {
            cfpLoadingBar.complete();
            return console.log('Cancelled');
          }else{
              var isAlreadyThere = false;
              angular.forEach($scope.devices, function(m){
                  if(m.dicomDeviceName === $scope.clonename){
                      isAlreadyThere = true;
                  }
              });
              if(!isAlreadyThere && $scope.devicename != undefined && $scope.devicename != "" && $scope.clonename != undefined && $scope.clonename != ""){
                $http({
                        method: 'GET',
                        url: '../devices/'+$scope.devicename
                        }).then(function successCallback(response) {
                          cfpLoadingBar.set(cfpLoadingBar.status()+(0.5));
                          var device = response.data;
                          device.dicomDeviceName = $scope.clonename;
                          $http.post("../devices/" + $scope.clonename, device)
                              .success(function(data, status, headers, config) {
                                  DeviceService.msg($scope, {
                                      "title": "Info",
                                      "text": "Clone created successfully!",
                                      "status": "info"
                                  });
                                  $scope.devices.push({
                                    dicomDeviceName : $scope.clonename,
                                    dicomInstalled: true
                                  });
                                  $scope.devicename = "";
                                  $scope.clonename = "";
                                  cfpLoadingBar.complete();
                              })
                              .error(function(data, status, headers, config) {
                                  $log.error("Error sending data on put!", status);
                                  addEmptyArrayFieldsPrivate($scope);
                                  DeviceService.msg($scope, {
                                      "title": "error",
                                      "text": "Error, clone could not be created!",
                                      "status": "error"
                                  });
                                  cfpLoadingBar.complete();
                              });
                      }, function errorCallback(response) {
                          DeviceService.msg($scope, {
                              "title": "Error",
                              "text": response.status+":"+response.statusText,
                              "status": "error"
                          });
                          $log.error("Error",response);
                          cfpLoadingBar.complete();
                      });
              }else{
                $scope.$apply(function() {
                  if(isAlreadyThere){
                      DeviceService.msg($scope, {
                          "title": "Error",
                          "text": "Name need to be unique!",
                          "status": "error"
                      });
                  }else{
                    
                      DeviceService.msg($scope, {
                          "title": "Error",
                          "text": "Error, fields required",
                          "status": "error"
                      });
                  }
                  cfpLoadingBar.complete();
                });
              }
          }
        }
      });
    };
    $scope.editDeviceList = function(devicename) {
            $scope.devicename       = devicename;
            $scope.currentDevice    = $scope.devicename;
            $scope.form             = {};
            cfpLoadingBar.start();
            if($scope.devicename){
                cfpLoadingBar.set(cfpLoadingBar.status()+(0.1));
                setTimeout(function(){ 
                    $scope.showDropdownLoader = true;
                    $scope.showFormLoader   = true;
                });
                $scope.selectedElement = "device";
                cfpLoadingBar.set(cfpLoadingBar.status()+(0.1));
                DeviceService
                .addDirectiveToDom(
                    $scope, 
                    "add_dropdowns",
                    "<div select-device-part></div>"
                );
                cfpLoadingBar.set(cfpLoadingBar.status()+(0.2));
                //Wait a little bit so the angularjs has time to render the first directive otherwise some input fields are not showing
                window.setTimeout(function() {
                DeviceService
                .addDirectiveToDom(
                  $scope, 
                  "add_edit_area",
                  "<div edit-area></div>"
                );
                cfpLoadingBar.set(cfpLoadingBar.status()+(0.1));
                }, 100);
            }else{
                cfpLoadingBar.complete();
                vex.dialog.alert("Select device");
            }
            setTimeout(function(){
                DeviceService.warnEvent($scope);
                addEffect("right",".devicelist_block", "hide");
                setTimeout(function(){
                    addEffect("left",".deviceedit_block", "show");
                },301);
            },1000);
    };
    var addEffect = function(direction, selector, display){
        var element = angular.element(selector);
            element.removeClass('fadeInRight').removeClass('fadeInLeft');
            if(display === "hide"){
                setTimeout(function(){
                    if(direction === "left"){
                        element.addClass('animated').addClass("fadeOutRight");
                    }
                    if(direction === "right"){
                        element.addClass('animated').addClass("fadeOutLeft");
                    }
                },1);
                setTimeout(function(){
                    element.hide();
                },301);
            }else{
                setTimeout(function(){
                    element.removeClass('fadeOutRight').removeClass('fadeOutLeft');
                    if(direction === "left"){
                        element.addClass("fadeInLeft").removeClass('animated');
                    }
                    if(direction === "right"){
                        element.addClass("fadeInRight").removeClass('animated');
                    }
                },1);
                setTimeout(function(){
                    element.show();
                },301);
            }
    };
    $scope.goBackToDeviceList = function(){
        addEffect("left",".deviceedit_block", "hide");
        setTimeout(function(){
            addEffect("right",".devicelist_block", "show");
            cancel();
        },301);
    };
    $scope.filter = {};
    $scope.$watchCollection('filter', function(newValue, oldValue){
        console.log("newValue",newValue);
    });  
    $scope.searchDevices = function(){
        searchDevices();
    }
    var searchDevices = function(){
         cfpLoadingBar.start();
        var urlParam = Object.keys($scope.filter).map(function(key){
            if($scope.filter[key]){
                return encodeURIComponent(key) + '=' + encodeURIComponent($scope.filter[key]); 
            }
        }).join('&');
        if(urlParam){
            urlParam = "?"+urlParam;
        }
        $http({
                method: 'GET',
                // url: 'json/devices.json'
                url: '../devices'+urlParam
            }).then(function successCallback(response) {
            $scope.devices = response.data;
            cfpLoadingBar.complete();
        }, function errorCallback(response) {
            $log.error("Error loading device names", response);
            vex.dialog.alert("Error loading device names, please reload the page and try again!");
        });   
    };
    $scope.searchAes = function(){
         cfpLoadingBar.start();
        var urlParam = Object.keys($scope.filter).map(function(key){
            if($scope.filter[key]){
                return encodeURIComponent(key) + '=' + encodeURIComponent($scope.filter[key]); 
            }
        }).join('&');
        if(urlParam){
            urlParam = "?"+urlParam;
        }
        $http({
                method: 'GET',
                // url: 'json/devices.json'
                url: '../aes'+urlParam
            }).then(function successCallback(response) {
            $scope.aes = response.data;
            cfpLoadingBar.complete();
        }, function errorCallback(response) {
            vex.dialog.alert("Error loading aes, please reload the page and try again!");
        });   
    };
    $scope.clearForm = function(){
        angular.forEach($scope.filter,function(m,i){
            $scope.filter[i] = "";
        });
        searchDevices();
    };
});

//http://localhost:8080/dcm4chee-arc/devices/dcm4chee-arc