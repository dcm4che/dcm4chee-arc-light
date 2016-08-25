"use strict";

myApp.controller("DeviceController", function($scope, $http, $timeout, $log, cfpLoadingBar, $compile, DeviceService, $parse, schemas, $select) {

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

    $scope.echo = function(){
      if($scope.selectedAet && $scope.selectedPart.dicomNetworkAE){

        $http({
            method: 'GET',
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
                          $http.put("../devices/" + $scope.clonename, device)
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
                          "text": "Error, fealds required",
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
                          $http.put("../devices/" + $scope.clonename, device)
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
                          "text": "Error, fealds required",
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
    $scope.clearForm = function(){
        angular.forEach($scope.filter,function(m,i){
            $scope.filter[i] = "";
        });
        searchDevices();
    };
});

//http://localhost:8080/dcm4chee-arc/devices/dcm4chee-arc