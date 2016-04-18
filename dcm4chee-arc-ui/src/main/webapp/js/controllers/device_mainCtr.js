"use strict";

myApp.controller("DeviceController", function($scope, $http, $timeout, $log, cfpLoadingBar, $compile, DeviceService, $parse, schemas, $select) {

    $scope.activeMenu             = "device_menu";
    $scope.showSave               = false;
    $scope.middleBorder           = "";
    $scope.lastBorder             = "";
    vex.defaultOptions.className  = 'vex-theme-os';
    $scope.msg                    = [];
    $scope.loaderElement          = "";
    $scope.showDropdownLoader     = false;
    $scope.showFormLoader         = false;
    $scope.validForm              = true;
    $scope.showScrollButton       = false;
    $scope.dynamicform            = {};
    $scope.selectedPart           = {};
    $scope.selectObject           = $select;
    //Just for debuging
    $scope.schemas                = schemas;
    // var schemas = {};
    setTimeout(function(){
      $scope.$apply(function(){
        $scope.activeMenu         = "";
      });
    }, 2000);
    // console.log("TESTCHNAGES.....");
      //TEST
    // DeviceService.addMissingCheckboxes($scope);
      //TEST

    $scope.loadSchemaPart = function(href, name){
      $log.debug("laodschemaclicked href=",href);
      $log.debug("name=",name);
    }
    /*
    *Watch when the user trys to leave the page
    */
    window.addEventListener("beforeunload", function(e) {
        // if (!DeviceService.equalJSON($scope.wholeDevice, $scope.wholeDeviceCopy)) {
        if ($scope.saved === false) {

            var confirmationMessage = 'It looks like you have been editing something. ' + 'If you leave before saving, your changes will be lost.';

            (e || window.event).returnValue = confirmationMessage; //Gecko + IE
            return confirmationMessage; //Gecko + Webkit, Safari, Chrome etc.
        }
    });


    $http({
        method: 'GET',
        url: 'schema/device.schema.json'
        // url: '../devices'
    }).then(function successCallback(response) {
        // $log.debug("before schemas=",schemas);
        // $log.debug("new schemas=",response.data);
        schemas.device  = response.data;
        // schemas.whole   = response.data;
        $log.debug("after schemas=",schemas);
    }, function errorCallback(response) {
        $log.error("Error loading device names", response);
        vex.dialog.alert("Error loading device names, please reload the page and try again!");
    }); 

    //Warn if the user want to leav the page without saving the changes
    $scope.$on('$locationChangeStart', function(event) {
        // $log.debug("check changes=", DeviceService.equalJSON($scope.wholeDevice, $scope.wholeDeviceCopy));
        $log.debug("$scope.saved=",$scope.saved);
        if ($scope.saved === false) {
            var answer = confirm("Are you sure you want to leave this page without saving changes?")
            if (!answer) {
                event.preventDefault();
            }
        }
    });
    $scope.changeElement = function(element){
            $log.warn("in changeElement selectedPart=", $scope.selectedPart);
            $log.debug("element=",element);
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
                  //$scope.transfareCapModel  = {};
                }
                $log.debug("selectedElement=",$scope.selectedElement);
                $scope.selectedElement  = element;
                $log.debug("selectedElement2=",$scope.selectedElement);
                $scope.lastBorder       = "active_border";
                $scope.showSave         = true;
                console.log("schemas vor init in mainCtrl =",angular.copy(schemas));
                console.log("form model vor init = ",angular.copy($scope.form));
                // if(!schemas[$scope.selectedElement] || !schemas[$scope.selectedElement][$scope.selectedElement]){
                //                 console.log("getschema 2");

                //   DeviceService.getSchema($scope.selectedElement);
                // }
                // DeviceService.setFormModel($scope);

                // $scope.dynamic_model = null;
                // if($scope.form[$scope.selectedElement] && $scope.form[$scope.selectedElement].model){
                //   $scope.form[$scope.selectedElement].model = null;
                // }
                if($scope.selectedElement === "device"){
                    // $scope.dynamic_schema = DeviceService.getDeviceSchema();
                    $scope.dynamic_model  = $scope.wholeDevice;
                }else{

                    // if(!schemas[$scope.selectedElement] || !schemas[$scope.selectedElement][$scope.selectedElement]){
                    //   console.log("getschema 1");
                    //   DeviceService.getSchema($scope.selectedElement);
                    // }
                    DeviceService.setFormModel($scope);
                    console.log("$scope.form[$scope.selectedElement].model=",$scope.form[$scope.selectedElement].model);
                    $scope.dynamic_model = $scope.form[$scope.selectedElement].model;
                }
                console.log("form model nach init = ",$scope.form);
                console.log("schemas nach init in mainCtrl =",schemas);
            }
            // if ($scope.selectedPart.dicomNetworkAE != undefined && !$scope.selectedPart.dicomTransferCapability && $scope.devicename === "CHANGE_ME") {
            //     DeviceService
            //     .addDirectiveToDom(
            //         $scope, 
            //         "SelectDicomTransferCapability",
            //         "<div select-transfare-capability></div>"
            //     );
            // }
            if($scope.devicename === "CHANGE_ME"){
                // $timeout(function() {
                  DeviceService
                  .addDirectiveToDom(
                      $scope, 
                      "add_edit_area",
                      "<div edit-area></div>"
                  );
            }
            if ($scope.selectedPart.dicomNetworkConnection != undefined) {

              $scope.networkAeSchema   = DeviceService.getSchemaNetworkAe();
              // $scope.networkAeForm     = DeviceService.getFormNetworkAe($scope.wholeDevice.dicomNetworkConnection);
              $log.warn("scope.networkAeForm commented out");
            }  
            $log.debug("selectedElement2=",$scope.selectedElement);
            cfpLoadingBar.complete();
            $log.debug("form=",$scope.form);
            $log.debug("dynamic_model=",$scope.dynamic_model);
    };
    $scope.selectElement = function(element) {
        $log.warn("in selectElement");
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
              //$scope.transfareCapModel  = {};
            }
            $scope.selectedElement  = element;
            if($scope.selectedElement === "device"){
                $scope.dynamic_schema = DeviceService.getDeviceSchema();
                $scope.dynamic_model  = $scope.wholeDevice;
            }else{
                if(!schemas[$scope.selectedElement] || !schemas[$scope.selectedElement][$scope.selectedElement]){
                  console.log("getschema 1");
                  DeviceService.getSchema($scope.selectedElement);
                }
                DeviceService.setFormModel($scope);
                // console.log("$scope.form[$scope.selectedElement].model=",$scope.form[$scope.selectedElement].model);
                // if($scope.form[$scope.selectedElement] && $scope.form[$scope.selectedElement].model){

                //   $scope.dynamic_model = $scope.form[$scope.selectedElement].model;
                // }

            }
            // console.log("form model nach init = ",$scope.form);
            // console.log("schemas nach init in mainCtrl =",schemas);


            // if($select[$scope.selectedElement] && $select[$scope.selectedElement].type != "array"){
            //   DeviceService
            //         .addDirectiveToDom(
            //             $scope, 
            //             "add_edit_area",
            //             "<div edit-area></div>"
            //         );
            // }
            $scope.lastBorder       = "active_border";
            $scope.showSave         = true;
            cfpLoadingBar.complete();

                        $log.debug("dynamic_model=",$scope.dynamic_model);

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
            //cfpLoadingBar.inc();
            //Wait a little bit so the angularjs has time to render the first directive otherwise some input fields are not showing
            
            window.setTimeout(function() {

              DeviceService
              .addDirectiveToDom(
                  $scope, 
                  "add_edit_area",
                  "<div edit-area></div>"
              );
            cfpLoadingBar.set(cfpLoadingBar.status()+(0.1));
              // cfpLoadingBar.inc();
            }, 100);

        } else {
            cfpLoadingBar.complete();
            vex.dialog.alert("Select device"); //TODO add beautiful vex.dialog.alert
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
                      $log.debug("deleting canceled");
                    }
                  }
               });
        } else {
          vex.dialog.alert('Please select device first!');
        }
    };

    $scope.createDevice = function() {
        $scope.editMode         = true;
        $scope.showSave         = true;
        $scope.showCancel       = true;
          $scope.wholeDevice      = {
          "dicomDeviceName":"CHANGE_ME",
          "dicomInstalled":false
        };
        $scope.devicename       = $scope.wholeDevice.dicomDeviceName;
        $scope.currentDevice    = $scope.wholeDevice.dicomDeviceName;
        $scope.newDevice        = true;
        $scope.selectedElement  = "device";
        $scope.middleBorder     = "active_border";
        $scope.lastBorder       = "active_border";
        $scope.showSave         = true;

        // angular.element(document.getElementById('add_dropdowns'))
        //     .html($compile("<div select-device-part></div>")($scope));
        DeviceService
        .addDirectiveToDom(
                $scope,
                "add_dropdowns",
                "<div select-device-part></div>"
        );
        //Wait a little bit so the angularjs has time to render the first directive otherwise some input fields are not showing
        window.setTimeout(function() {
            // angular.element(document.getElementById('add_edit_area'))
            //     .html($compile("<div edit-area></div>")($scope));
            DeviceService
            .addDirectiveToDom(
                    $scope,
                    "add_edit_area",
                    "<div edit-area></div>"
            );

        }, 100);
    };

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
                        $log.warn("hier was $scope.networkAeForm");
                        // $scope.networkAeForm  = DeviceService.getFormNetworkAe($scope.selectedNetworkConnection);
                    }
                } else {
                    //TODO replace it with an bautiful messaging
                    $scope.activeMenu = "dicomNetworkConnection";
                    vex.dialog.alert("Please select first a network connection to delete");
                }
                break;


            case "dicomNetworkAE":
                $log.debug("$scope.selectedPart.dicomNetworkAE=",$scope.selectedPart.dicomNetworkAE);
                $log.debug("$scope.selectedPart.dicomTransferCapability=",$scope.selectedPart.dicomTransferCapability);
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
                        $log.debug("$scope.transfcap=",$scope.transfcap);
                        if($scope.transfcap[0]){
                            $log.debug("in if dicomTransferCapability[0]");
                            angular.forEach($scope.wholeDevice.dicomNetworkAE[toDeleteKey].dicomTransferCapability, function(k, i){
                              if(k.cn === $scope.transfcap[0].cn){
                                $scope.transfcap = null;
                              }
                            });
                        }
                        $log.debug("$scope.transfcap=",$scope.transfcap);
                        
                        $scope.wholeDevice.dicomNetworkAE.splice(toDeleteKey, 1);
                        // $scope.networkAeSchema      = {};
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
                            // $scope.networkAeModel     = {};
                            // $scope.networkAeForm        = [];
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
                //$log.debug("dicomTransferCapability delete trans", $scope.selectedPart.dicomTransferCapability);
                break;
        }
        $scope.editMode = false;
        $log.debug("editMode Set in deletePart to=",$scope.editMode);
    };
    $scope.createPart = function(element) {
                $scope.selectedElement = element;
                $scope.activeMenu      = element;
                $scope.form[$scope.selectedElement] = $scope.form[$scope.selectedElement] || {};
                $scope.dynamic_model   = {};
                if(!schemas[$scope.selectedElement]){
                  DeviceService.getSchema($scope.selectedElement);
                  var wait = setInterval(function(){
                        $log.debug("waiting");
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
                        if(checkItems || checkProp){
                          clearInterval(wait);
                          // DeviceService.setFormModel($scope);
                          if(checkItems){
                            $scope.form[$scope.selectedElement]["schema"] = schemas[$scope.selectedElement][$scope.selectedElement]["items"][$scope.selectedElement];
                          }else{
                            $scope.form[$scope.selectedElement]["schema"] = schemas[$scope.selectedElement][$scope.selectedElement][$scope.selectedElement];
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
        // switch (element) {
        //     case "dicomNetworkConnection":
                // var dicomNetConnSchema    = DeviceService.getSchemaDicomNetworkConn();
                // $scope.dicomNetConnSchema = dicomNetConnSchema.properties.dicomNetworkConnection.items;
                // $scope.selectedElement    = "dicomNetworkConnection";
                // $scope.activeMenu         = "dicomNetworkConnection";
                // if(!$scope.wholeDevice.dicomNetworkConnection){
                //   $scope.wholeDevice["dicomNetworkConnection"] = []
                // }
                // $scope.dicomNetConnModel  = {};
                // if($scope.wholeDevice.dicomNetworkConnection[0]===null){
                //   $scope.wholeDevice.dicomNetworkConnection[0] = $scope.dicomNetConnModel;
                // }else{
                //   $scope.wholeDevice.dicomNetworkConnection.push($scope.dicomNetConnModel);
                // }
                // $scope.networkAeForm      = DeviceService.getFormNetworkAe($scope.selectedNetworkConnection);
                // $log.debug("wholeDevice=",$scope.wholeDevice);
                // $scope.showCancel = true;
                // $scope.showSave   = true;
                // $scope.lastBorder = "active_border";
                // $scope.editMode   = true;
                // $scope.validForm  = false;
        //         break;
        //     case "dicomNetworkAE":
        //         if($scope.wholeDevice.dicomNetworkConnection && $scope.wholeDevice.dicomNetworkConnection[0]!=null){
        //           $log.debug("in if=",$scope.wholeDevice.dicomNetworkConnection);
        //           $scope.networkAeSchema  = DeviceService.getSchemaNetworkAe();
        //           $scope.networkAeForm    = DeviceService.getFormNetworkAe($scope.wholeDevice.dicomNetworkConnection);
        //           $scope.selectedElement  = "dicomNetworkAE";
        //           $scope.activeMenu       = "dicomNetworkAE";
        //           $scope.transfcap        = {};
        //           if(!$scope.wholeDevice.dicomNetworkAE) {
        //             $scope.wholeDevice["dicomNetworkAE"] = []
        //           }
        //           $scope.networkAeModel   = {};
        //           if($scope.wholeDevice.dicomNetworkAE[0]===null){
        //             $scope.wholeDevice.dicomNetworkAE[0]= $scope.networkAeModel;
        //           }else{
        //             $scope.wholeDevice.dicomNetworkAE.push($scope.networkAeModel);
        //           }
        //           $scope.selectedPart.dicomNetworkAE  = $scope.selectedElement.dicomAETitle;
        //           $scope.showCancel = true;
        //           $scope.showSave   = true;
        //           $scope.lastBorder = "active_border";
        //           $scope.editMode   = true;
        //           $scope.validForm  = false;
        //         }else{
        //           DeviceService.msg($scope, {
        //               "title": "Warning",
        //               "text": "Create first a dicom network connection",
        //               "status": "warning"
        //           });
        //         }
        //         break;

        //     case "dicomTransferCapability":
        //         if ($scope.selectedPart.dicomNetworkAE) {
        //           //TODO Create form element for the dicomTransferCapability
        //             $scope.transfareCapSchema = DeviceService.getShemaTransfareCap();
        //             $scope.transfareCapModel  = {};
        //             $scope.transfareCapForm   = DeviceService.getFormTransfareCap();
        //             $scope.selectedElement    = "dicomTransferCapability";
        //             var toEditKey;

        //             angular.forEach($scope.wholeDevice.dicomNetworkAE, function(value, key) {
        //                 if (value.dicomAETitle === $scope.selectedPart.dicomNetworkAE) {
        //                     toEditKey = key;
        //                 }
        //             });
        //             if ($scope.wholeDevice.dicomNetworkAE[toEditKey].dicomTransferCapability) {
        //                 $scope.wholeDevice.dicomNetworkAE[toEditKey].dicomTransferCapability.push($scope.transfareCapModel);
        //             } else {
        //                 $scope.wholeDevice.dicomNetworkAE[toEditKey]["dicomTransferCapability"] = [$scope.transfareCapModel];
        //             }
        //             $scope.transfcap = $scope.wholeDevice.dicomNetworkAE[toEditKey].dicomTransferCapability;
        //             $scope.showCancel = true;
        //             $scope.showSave   = true;
        //             $scope.lastBorder = "active_border";
        //             $scope.editMode = true;

        //         } else {
        //             $scope.activeMenu = "dicomTransferCapability";
        //             vex.dialog.alert("Select first a Network AE");
        //         }
        //         break;
        //         $scope.validForm = false;
        //         setTimeout(function(){ 
        //             scope.$apply();
        //         });
        // }

    };
    $scope.save = function() {
        cfpLoadingBar.start();
                //$log.debug("after return in save=",DeviceService.addMissingCheckboxes($scope));
        /*      $log.debug("selectedElement=",$scope.selectedElement);
      $log.debug("networkAeModel=",$scope.networkAeModel);*/
        // DeviceService.removeEmptyPart($scope.wholeDevice[$scope.selectedElement], [$select[$scope.selectedElement].optionValue]);
        $scope.validForm = DeviceService.validateForm($scope).valid;
        var message = DeviceService.validateForm($scope).message;

        // $log.debug("scope.currentDevice",$scope.currentDevice);
        // $log.debug("scope.devicename",$scope.devicename);
        // $log.debug("wholeDevice",$scope.wholeDevice);

        // $scope.$broadcast('schemaFormValidate');
        // if($scope.networkAeForm.$valid) {
        if ($scope.validForm) {
            $timeout(function() {
                // $log.debug("wholeDevice before clear",$scope.wholeDevice);
                DeviceService.clearJson($scope);
                //DeviceService.addMissingCheckboxes($scope);

                // $log.debug("wholeDevice after clear",$scope.wholeDevice);
                if($scope.devicename === "CHANGE_ME"){
                  $scope.devicename = $scope.wholeDevice.dicomDeviceName;
                  $scope.devices.push({
                    "dicomDeviceName":$scope.wholeDevice.dicomDeviceName
                  })
                }
                // $log.debug("$scope.currentDevice=",$scope.currentDevice);
                // $log.debug("$scope.wholeDevice.dicomDeviceName=",$scope.wholeDevice.dicomDeviceName);
                if($scope.currentDevice != $scope.wholeDevice.dicomDeviceName && $scope.currentDevice != "CHANGE_ME"){

                  DeviceService.saveWithChangedName($scope);

                }else{
                  $log.debug("before save");
                    DeviceService.save($scope);
                }
            });
        } else {
            cfpLoadingBar.complete();
            // $log.debug("message=",message);
           // vex.dialog.alert(message);
            $timeout(function() {
            $scope.$apply(function(){
              $scope.editMode = true;
              $scope.validForm = false;
            });
          });
          // $scope.showBlockLayer = true;
          DeviceService.msg($scope, {
              "title": "Warning",
              "text": message,
              "status": "warning"
          });
        }
        $scope.editMode = false;
        // }
        //$log.debug();
        // DeviceService.addEmptyArrayFields(scope);
        DeviceService.addEmptyArrayFields($scope);
        $log.debug("in wholeDevice=",$scope.wholeDevice);
        cfpLoadingBar.complete();
    };

    //Load device names on pageload
    $timeout(function() {
      
        $scope.$apply(function() {
            document.getElementById("init_select").focus(); //Focus the dropdown element
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
                // url: 'json/devices.json'
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
        //If the activeMenu and element are the same that meens that the user clicked again on the elment, so he wants to close it
        // $log.debug("validForm in toggle=",$scope.validForm);
        // $log.debug("validForm in activeMenu=",$scope.activeMenu);
        // $log.debug("element=",element);
        // if($scope.validForm){

          if ($scope.activeMenu && $scope.activeMenu === element) {
              $scope.activeMenu = "";
          } else {
              $scope.activeMenu = element;
          }
        // }
        // $log.debug("validForm in 2activeMenu=",$scope.activeMenu);
    };



    /*
    *Check if it was saved
    */
    $scope.savedCheck = function(e){
      // $log.debug("savecheck editMode=",$scope.editMode,"$scope.deletPartProcess=",$scope.deletPartProcess);
      $log.debug("in savecheck");
      if($scope.editMode && $(e.target).closest('.form_content').length<1 && !(e.target.className == "create "+$scope.selectedElement)){
        // $log.debug("validate");
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

    /*
    *Close button for the messages
    *obj (Object) the message that need to bee closed
    */
    $scope.closeBox = function(obj){

      angular.forEach($scope.msg, function(m, k){
        if(m == obj){
          $(".msg_container li").eq(k).fadeOut("400",function(){
            $scope.msg.splice(k, 1);
          });
        }
      });
    };

    /*
    *Implementation of the cancle button
    */
    $scope.cancel = function(){
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

                // $log.debug("");
                // if($scope.form[$scope.selectedElement].model[$select[$scope.selectedElement].optionValue]){
                //   angular.forEach($scope.wholeDevice[$scope.selectedElement], function(m,i){
                //     if(m[$select[$scope.selectedElement].optionValue] === $scope.form[$scope.selectedElement].model[$select[$scope.selectedElement].optionValue]){
                //       $scope.wholeDevice[$scope.selectedElement].splice(i,1);
                //     }
                //   });
                // }
                DeviceService.cancle($scope);
                $scope.form[$scope.selectedElement].model  = {};
                DeviceService.removeEmptyPart($scope.wholeDevice[$scope.selectedElement], [$select[$scope.selectedElement].optionValue]);
      }

      //With switch we check witch part ist selected so you can reset the right form and dropdown list
      // switch ($scope.selectedElement) {
      //       case "device":
      //           $scope.devicename       = "";
      //           $scope.currentDevice    = "";
      //           $scope.newDevice        = true;
      //           $scope.middleBorder     = "";
      //           $scope.lastBorder       = "";
      //           $scope.deviceModel      = {};
      //           $scope.wholeDevice      = {};
      //           $scope.showSave         = false;
      //           angular.element(document.getElementById("add_dropdowns")).html("");
      //           angular.element(document.getElementById("add_edit_area")).html("");
      //           break;
      //       case "dicomNetworkConnection":
      //           if($scope.dicomNetConnModel.cn){
      //             angular.forEach($scope.wholeDevice.dicomNetworkConnection, function(m,i){
      //               if(m.cn === $scope.dicomNetConnModel.cn){
      //                 $scope.wholeDevice.dicomNetworkConnection.splice(i,1);
      //               }
      //             });
      //           }
      //           $scope.dicomNetConnModel  = {};
      //           DeviceService.removeEmptyPart($scope.wholeDevice.dicomNetworkConnection, "cn");
      //           break;
      //       case "dicomNetworkAE":
      //             if($scope.networkAeModel.dicomAETitle){
      //               angular.forEach($scope.wholeDevice.dicomNetworkAE, function(m,i){
      //                 if(m.dicomAETitle === $scope.networkAeModel.dicomAETitle){
      //                   $scope.wholeDevice.dicomNetworkAE.splice(i,1);
      //                 }
      //               });
      //             }else{
      //               DeviceService.removeEmptyPart($scope.wholeDevice.dicomNetworkAE, "dicomAETitle");
      //             }
                  
      //             $scope.networkAeForm = [];
      //             $scope.networkAeModel = {};
      //             $scope.transfcap = {};
      //           break;

      //       case "dicomTransferCapability":
      //           if ($scope.selectedPart.dicomNetworkAE) {
      //               var toEditKey;
      //               angular.forEach($scope.wholeDevice.dicomNetworkAE, function(value, key) {
      //                   if (value.dicomAETitle === $scope.selectedPart.dicomNetworkAE) {
      //                       toEditKey = key;
      //                   }
      //               });
      //               if ($scope.wholeDevice.dicomNetworkAE[toEditKey].dicomTransferCapability) {

      //                   angular.forEach($scope.wholeDevice.dicomNetworkAE[toEditKey].dicomTransferCapability, function(m,i){
      //                     if(m.cn === $scope.transfareCapModel.cn){
      //                       $scope.wholeDevice.dicomNetworkAE[toEditKey].dicomTransferCapability.splice(i,1);
      //                     }
      //                   });
      //                   DeviceService.removeEmptyPart($scope.wholeDevice.dicomNetworkAE[toEditKey].dicomTransferCapability, "cn");
      //               }
      //           } else {
      //               $scope.activeMenu = "dicomTransferCapability";
      //               vex.dialog.alert("Select first a Network AE");
      //           }
      //           $scope.transfareCapModel  = {};
      //           break;
      //   }
        $scope.selectedElement  = "device";
        $scope.validForm        = true;
        // $scope.activeMenu       = "";
        $scope.showCancel       = false;
        // $scope.showSave         = false;
        // $scope.lastBorder       = "";
        $scope.editMode         = false;

    };

    /*
    *If the editMode is active and form is not valid show the block_layer (div element that over the rest of the app but the form)
    */
    $scope.showBlockLayer = function(){
      return ($scope.editMode && !$scope.validForm);
    };

    $scope.echo = function(){
      // $log.debug("selectedAet=",$scope.selectedAet);
      // $log.debug("selectedPart.dicomNetworkAE=",$scope.selectedPart.dicomNetworkAE);
      if($scope.selectedAet && $scope.selectedPart.dicomNetworkAE){

        $http({
            method: 'GET',
            // url: 'json/devices.json'
            url: '../aets/'+$scope.selectedAet+'/echo/'+$scope.selectedPart.dicomNetworkAE
        }).then(function successCallback(response) {
            // alert(response.data);
            try{
              if(response.data && response.data.result===0){
                $log.debug("in if");
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
                $log.debug("in else");
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
            }catch(e){
              // $log.error("e=",e);
              DeviceService.msg($scope, {
                  "title": "Error",
                  "text": "Something went wrong, echo didn't work!",
                  "status": "error"
              });
            }
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
    };

    $scope.showEcho = function(){
      if($scope.selectedElement == 'dicomNetworkAE' && !$scope.showCancel && $scope.devicenam != "CHANGE_ME"){
        return true;
      }else{
        return false;
      }
    };
    $scope.splitStringToObject = function(value,key){
      // $log.debug("in splitStringToObject, value=",value);
      // $log.debug("key=",key);
      $scope.selectModel = {};
      if(angular.isDefined($scope.wholeDevice)){
        if(value.optionRef.length > 1){
          DeviceService.getObjectFromString($scope, value, key);
        }else{
          $scope.selectModel[key] = $scope.wholeDevice[value.optionRef[0]];
        }
      }
    };
});

//http://localhost:8080/dcm4chee-arc/devices/dcm4chee-arc