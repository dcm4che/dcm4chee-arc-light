"use strict";

myApp.controller("DeviceController", function($scope, $http, $timeout, $log, cfpLoadingBar, $compile, DeviceService, $parse) {

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

    setTimeout(function(){ 
      $scope.$apply(function(){
        $scope.activeMenu             = "";
      });
    }, 2000);
    
      //TEST
    // DeviceService.addMissingCheckboxes($scope);
      //TEST

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

    //Warn if the user want to leav the page without saving the changes
    $scope.$on('$locationChangeStart', function(event) {
        $log.debug("check changes=", DeviceService.equalJSON($scope.wholeDevice, $scope.wholeDeviceCopy));
        $log.debug("$scope.saved=",$scope.saved);
        if ($scope.saved === false) {
            var answer = confirm("Are you sure you want to leave this page without saving changes?")
            if (!answer) {
                event.preventDefault();
            }
        }
    });
    $scope.changeElement = function(element){
              if ($scope.selectedNetworkAE != undefined && !$scope.selectedTransfCap && $scope.devicename == "CHANGE_ME") {
                // $log.debug("in if transfare put in dome");
                  DeviceService
                  .addDirectiveToDom(
                      $scope, 
                      "SelectDicomTransferCapability",
                      "<div select-transfare-capability></div>"
                  );
              }
              if($scope.devicename == "CHANGE_ME"){
                  // $timeout(function() {
                    DeviceService
                    .addDirectiveToDom(
                        $scope, 
                        "add_edit_area",
                        "<div edit-area></div>"
                    );
              }
              if ($scope.selectedDicomNetworkConnection != undefined) {

                $scope.networkAeSchema   = DeviceService.getSchemaNetworkAe();
                $scope.networkAeForm = DeviceService.getFormNetworkAe($scope.wholeDevice.dicomNetworkConnection);
              }  
    };
    $scope.selectElement = function(element) {
        // $log.debug("in selectElement, $scope.devicename=",$scope.devicename);
        // $log.debug("selectedTransfCap=",$scope.selectedTransfCap);
        if(
            (
                element === "device"        ||
                element === "connection"    && $scope.selectedDicomNetworkConnection  != undefined ||
                element === "networkae"     && $scope.selectedNetworkAE               != undefined ||
                element === 'transfarecap'  && $scope.selectedTransfCap               != undefined
            ) 
              &&
            (
                element != $scope.selectedElement ||
                $scope.devicename == "CHANGE_ME"
            )&&
                $scope.validForm
        ) {
            cfpLoadingBar.start();
            if(element === 'networkae'){
              $scope.selectedTransfCap  = null;
              //$scope.transfareCapModel  = {};
            }
            $scope.selectedElement  = element;
            $scope.lastBorder       = "active_border";
            $scope.showSave         = true;


//
        }
        cfpLoadingBar.complete();
    };

    //Edit selected device
    $scope.edit = function() {



        cfpLoadingBar.start();
        if ($scope.devicename) {
            cfpLoadingBar.set(cfpLoadingBar.status()+(0.1));
          setTimeout(function(){ 
              $scope.showDropdownLoader = true;
              $scope.showFormLoader   = true;
          });
            cfpLoadingBar.set(cfpLoadingBar.status()+(0.1));
            $scope.selectedElement = "device";
            // $scope.middleBorder = "active_border";
            // $scope.lastBorder = "active_border";
            cfpLoadingBar.set(cfpLoadingBar.status()+(0.1));
            DeviceService
            .addDirectiveToDom(
                $scope, 
                "add_dropdowns",
                "<div select-device-part></div>"
            );
            cfpLoadingBar.set(cfpLoadingBar.status()+(0.1));
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
            case "connection":
                if ($scope.selectedDicomNetworkConnection) {
                    var m = confirm("Are you sure you want to delete the network connection: " + $scope.selectedDicomNetworkConnection + "?");
                    
                    if (m) {
                        var toDeleteKey;

                        angular.forEach($scope.wholeDevice.dicomNetworkConnection, function(value, key) {
                            if (value.cn == $scope.selectedDicomNetworkConnection) {
                                toDeleteKey = key;
                            }
                        });

                        $scope.wholeDevice.dicomNetworkConnection.splice(toDeleteKey, 1);
                        $scope.dicomNetConnModel              = {};
                        $scope.selectedElement                = "";
                        $scope.selectedDicomNetworkConnection = null;
                        $scope.lastBorder                     = "";

                        //TODO Check references and delete them to
                        $scope.wholeDevice    = DeviceService.clearReference(toDeleteKey, $scope.wholeDevice);
                        $scope.networkAeForm  = DeviceService.getFormNetworkAe($scope.selectedNetworkConnection);
                    }
                } else {
                    //TODO replace it with an bautiful messaging
                    $scope.activeMenu = "connection";
                    vex.dialog.alert("Please select first a network connection to delete");
                }
                break;


            case "networkae":
                $log.debug("$scope.selectedNetworkAE=",$scope.selectedNetworkAE);
                $log.debug("$scope.selectedTransfCap=",$scope.selectedTransfCap);
                if($scope.selectedNetworkAE) {

                    var m = confirm("Are you sure you want to delete the Network AE: " + $scope.selectedNetworkAE + "?");
                  
                    if(m){

                        var toDeleteKey;
                        var shouldDeleteTransfare = false;
                        angular.forEach($scope.wholeDevice.dicomNetworkAE, function(value, key) {
                            if (value.dicomAETitle == $scope.selectedNetworkAE) {
                                toDeleteKey = key;
                            }
                        });
                        $log.debug("$scope.transfcap=",$scope.transfcap);
                        if($scope.transfcap[0]){
                            $log.debug("in if transfarecap[0]");
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
                        $scope.selectedNetworkAE    = null;
                        $scope.lastBorder           = "";
                    }

                }else{
                    $scope.activeMenu = "networkae";
                    //TODO replace it with an bautiful messaging
                    vex.dialog.alert("Please select first a network AE to delete");
                }
                break;

            case "transfarecap":

                if($scope.selectedTransfCap){

                    // var m = confirm("Are you sure you want to delete the Network AE: " + $scope.selectedTransfCap + "?");
                  vex.dialog.confirm({
                      message: "Are you sure you want to delete the Network AE: " + $scope.selectedTransfCap + "?",
                      callback: function(m) {
                        if (m) {
                            var networkAEKey;
                            var toDeleteKey;

                            angular.forEach($scope.wholeDevice.dicomNetworkAE, function(value, key) {
                                if (value.dicomAETitle == $scope.selectedNetworkAE) {

                                    networkAEKey = key;

                                    angular.forEach(value.dicomTransferCapability, function(m, k) {
                                        if (m.cn == $scope.selectedTransfCap) {
                                            toDeleteKey = k;
                                        }
                                    });
                                }
                            });

                            $scope.wholeDevice.dicomNetworkAE[networkAEKey].dicomTransferCapability.splice(toDeleteKey, 1);
                            $scope.networkAeModel     = {};
                            $scope.selectedElement    = "";
                            $scope.selectedTransfCap  = null;
                            $scope.lastBorder         = "";
                            $scope.showSave           = false;
                        }
                      }
                   });
                }else{
                    $scope.activeMenu = "transfarecap";
                    vex.dialog.alert("Please select first a network connection to delete");
                }
                //$log.debug("transfarecap delete trans", $scope.selectedTransfCap);
                break;
        }
        $scope.editMode = false;
        $log.debug("editMode Set in deletePart to=",$scope.editMode);
    };
    $scope.createPart = function(element) {

        switch (element) {
            case "connection":
                var dicomNetConnSchema    = DeviceService.getSchemaDicomNetworkConn();
                $scope.dicomNetConnSchema = dicomNetConnSchema.properties.dicomNetworkConnection.items;
                $scope.selectedElement    = "connection";
                $scope.activeMenu         = "connection";
                if(!$scope.wholeDevice.dicomNetworkConnection){
                  $scope.wholeDevice["dicomNetworkConnection"] = []
                }
                $scope.dicomNetConnModel  = {};
                if($scope.wholeDevice.dicomNetworkConnection[0]===null){
                  $scope.wholeDevice.dicomNetworkConnection[0] = $scope.dicomNetConnModel;
                }else{
                  $scope.wholeDevice.dicomNetworkConnection.push($scope.dicomNetConnModel);
                }
                $scope.networkAeForm      = DeviceService.getFormNetworkAe($scope.selectedNetworkConnection);
                // $log.debug("wholeDevice=",$scope.wholeDevice);
                $scope.showCancel = true;
                $scope.showSave   = true;
                $scope.lastBorder = "active_border";
                 $scope.editMode = true;
                break;
            case "networkae":
                if($scope.wholeDevice.dicomNetworkConnection){

                  $scope.networkAeSchema  = DeviceService.getSchemaNetworkAe();
                  $scope.networkAeForm    = DeviceService.getFormNetworkAe($scope.wholeDevice.dicomNetworkConnection);
                  $scope.selectedElement  = "networkae";
                  $scope.activeMenu       = "networkae";
                  $scope.transfcap        = {};

                  if(!$scope.wholeDevice.dicomNetworkAE) {
                    $scope.wholeDevice["dicomNetworkAE"] = []
                  }
                  $scope.networkAeModel   = {};
                  if($scope.wholeDevice.dicomNetworkAE[0]===null){
                    $scope.wholeDevice.dicomNetworkAE[0]= $scope.networkAeModel;
                  }else{
                    $scope.wholeDevice.dicomNetworkAE.push($scope.networkAeModel);
                  }



                  $scope.selectedNetworkAE  = $scope.selectedElement.dicomAETitle;
                  $scope.showCancel = true;
                  $scope.showSave   = true;
                  $scope.lastBorder = "active_border";
                   $scope.editMode = true;
                }else{
                  DeviceService.msg($scope, {
                      "title": "Warning",
                      "text": "Create first a dicom network connection",
                      "status": "warning"
                  });
                }
                break;

            case "transfarecap":
                if ($scope.selectedNetworkAE) {
                  //TODO Create form element for the transfarecap
                    $scope.transfareCapSchema = DeviceService.getShemaTransfareCap();
                    $scope.transfareCapModel  = {};
                    $scope.transfareCapForm   = DeviceService.getFormTransfareCap();
                    $scope.selectedElement    = "transfarecap";
                    var toEditKey;

                    angular.forEach($scope.wholeDevice.dicomNetworkAE, function(value, key) {
                        if (value.dicomAETitle === $scope.selectedNetworkAE) {
                            toEditKey = key;
                        }
                    });
                    if ($scope.wholeDevice.dicomNetworkAE[toEditKey].dicomTransferCapability) {
                        $scope.wholeDevice.dicomNetworkAE[toEditKey].dicomTransferCapability.push($scope.transfareCapModel);
                    } else {
                        $scope.wholeDevice.dicomNetworkAE[toEditKey]["dicomTransferCapability"] = [$scope.transfareCapModel];
                    }
                    $scope.transfcap = $scope.wholeDevice.dicomNetworkAE[toEditKey].dicomTransferCapability;
                    $scope.showCancel = true;
                    $scope.showSave   = true;
                    $scope.lastBorder = "active_border";
                    $scope.editMode = true;

                } else {
                    $scope.activeMenu = "transfarecap";
                    vex.dialog.alert("Select first a Network AE");
                }
                break;
        }

    };
    $scope.save = function() {
        cfpLoadingBar.start();
                //$log.debug("after return in save=",DeviceService.addMissingCheckboxes($scope));
        /*      $log.debug("selectedElement=",$scope.selectedElement);
      $log.debug("networkAeModel=",$scope.networkAeModel);*/
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
                DeviceService.addMissingCheckboxes($scope);
                // $log.debug("wholeDevice after clear",$scope.wholeDevice);
                if($scope.devicename == "CHANGE_ME"){
                  $scope.devicename = $scope.wholeDevice.dicomDeviceName;
                }
                // $log.debug("$scope.currentDevice=",$scope.currentDevice);
                // $log.debug("$scope.wholeDevice.dicomDeviceName=",$scope.wholeDevice.dicomDeviceName);
                if($scope.currentDevice != $scope.wholeDevice.dicomDeviceName && $scope.currentDevice != "CHANGE_ME"){

                  DeviceService.saveWithChangedName($scope);

                }else{
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
      $log.debug("savecheck editMode=",$scope.editMode,"$scope.deletPartProcess=",$scope.deletPartProcess);

      if($scope.editMode && $(e.target).closest('.form_content').length<1 && !(e.target.className == "create "+$scope.selectedElement)){
        $log.debug("validate");
        $scope.validForm = DeviceService.validateForm($scope).valid;
        var message = DeviceService.validateForm($scope).message;
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
    
    $scope.$watchCollection('[dicomNetConnModel, networkAeModel, transfareCapModel]', function(newValue, oldValue) {
      if(!$scope.deletPartProcess){
        if(!DeviceService.equalJSON(oldValue,newValue)){
          $scope.editMode = true;
        }
      }else{
        $scope.deletPartProcess = false;
      }
    });

    /*
    *Watch wholeDevice json-object to see if it was changet so you can set the saved wariable to false
    */
    $scope.$watchCollection('wholeDevice', function(newValue, oldValue){
      if(!DeviceService.equalJSON(oldValue,newValue) &&  newValue.dicomDeviceName == oldValue.dicomDeviceName){
        $scope.saved = false;
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

      //With switch we check witch part ist selected so you can reset the right form and dropdown list
      switch ($scope.selectedElement) {
            case "device":
                $scope.devicename       = "";
                $scope.currentDevice    = "";
                $scope.newDevice        = true;
                $scope.middleBorder     = "";
                $scope.lastBorder       = "";
                $scope.deviceModel      = {};
                $scope.wholeDevice      = {};
                $scope.validForm        = true;
                $scope.showSave         = false;
                angular.element(document.getElementById("add_dropdowns")).html("");
                angular.element(document.getElementById("add_edit_area")).html("");
                break;
            case "connection":
                if($scope.dicomNetConnModel.cn){
                  angular.forEach($scope.wholeDevice.dicomNetworkConnection, function(m,i){
                    if(m.cn === $scope.dicomNetConnModel.cn){
                      $scope.wholeDevice.dicomNetworkConnection.splice(i,1);
                    }
                  });
                }
                $scope.dicomNetConnModel  = {};
                DeviceService.removeEmptyPart($scope.wholeDevice.dicomNetworkConnection, "cn");
                break;
            case "networkae":
                  if($scope.networkAeModel.dicomAETitle){
                    angular.forEach($scope.wholeDevice.dicomNetworkAE, function(m,i){
                      if(m.dicomAETitle === $scope.networkAeModel.dicomAETitle){
                        $scope.wholeDevice.dicomNetworkAE.splice(i,1);
                      }
                    });
                  }else{
                    DeviceService.removeEmptyPart($scope.wholeDevice.dicomNetworkAE, "dicomAETitle");
                  }
                  
                  $scope.networkAeForm = [];
                  $scope.networkAeModel = {};
                  $scope.transfcap = {};
                break;

            case "transfarecap":
                if ($scope.selectedNetworkAE) {
                    var toEditKey;
                    angular.forEach($scope.wholeDevice.dicomNetworkAE, function(value, key) {
                        if (value.dicomAETitle === $scope.selectedNetworkAE) {
                            toEditKey = key;
                        }
                    });
                    if ($scope.wholeDevice.dicomNetworkAE[toEditKey].dicomTransferCapability) {

                        angular.forEach($scope.wholeDevice.dicomNetworkAE[toEditKey].dicomTransferCapability, function(m,i){
                          if(m.cn === $scope.transfareCapModel.cn){
                            $scope.wholeDevice.dicomNetworkAE[toEditKey].dicomTransferCapability.splice(i,1);
                          }
                        });
                        DeviceService.removeEmptyPart($scope.wholeDevice.dicomNetworkAE[toEditKey].dicomTransferCapability, "cn");
                    }
                } else {
                    $scope.activeMenu = "transfarecap";
                    vex.dialog.alert("Select first a Network AE");
                }
                $scope.transfareCapModel  = {};
                break;
        }
        $scope.selectedElement  = "device";
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
      // $log.debug("selectedNetworkAE=",$scope.selectedNetworkAE);
      if($scope.selectedAet && $scope.selectedNetworkAE){

        $http({
            method: 'GET',
            // url: 'json/devices.json'
            url: '../aets/'+$scope.selectedAet+'/echo/'+$scope.selectedNetworkAE
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
      if($scope.selectedElement == 'networkae' && !$scope.showCancel && $scope.devicenam != "CHANGE_ME"){
        return true;
      }else{
        return false;
      }
    };
});

//http://localhost:8080/dcm4chee-arc/devices/dcm4chee-arc