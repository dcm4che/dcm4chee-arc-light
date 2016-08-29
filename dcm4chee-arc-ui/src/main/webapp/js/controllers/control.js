"use strict";

myApp.controller('ArchiveCtrl', function (cfpLoadingBar, $scope, $http, DeviceService) {

    $scope.updaterate = 3;
    // $scope.logoutUrl = myApp.logoutUrl();
    $scope.status = null;
    $scope.stopLoop = true;
    $scope.message = '';
    $scope.others   = false;
    $scope.fetchStatus = function() {
        $http.get("../ctrl/status").then(function (res) {
            $scope.status = res.data.status;
            $scope.message = '';
        })
    };
    $scope.start = function() {
        $http.post("../ctrl/start").then(function (res) {
            $scope.status = 'STARTED';
            $scope.message = '';
        })
    };
    $scope.stop = function() {
        $http.post("../ctrl/stop").then(function (res) {
            $scope.status = 'STOPPED';
            $scope.message = '';
        })
    };
    $scope.reload = function() {
        $http.post("../ctrl/reload").then(function (res) {
            $scope.message = 'Reload successful';
        })
    };
    $scope.fetchStatus();

        var modifyObject = function(obj){
        var local = [];
        var definedFields = [
                        "serialNo",
                        "connectTime",
                        "initiated",
                        "localAETitle",
                        "remoteAETitle",
                        "performedOps",
                        "invokedOps"
                    ];
        // obj = [{
        //             "serialNo":5,
        //             "connectTime":"2016-06-16T10:25:19.844+02:00",
        //             "initiated":false,
        //             "localAETitle":"DCM4CHEE",
        //             "remoteAETitle":"MOVESCU",
        //             "performedOps":{
        //                 "C-MOVE":{
        //                     "RQ":1,
        //                     "RSP":0
        //                 }
        //             },
        //             "invokedOps":{}
        //         },
        //         {
        //             "serialNo":6,
        //             "connectTime":"2016-06-16T10:25:19.912+02:00",
        //             "initiated":true,
        //             "localAETitle":"MOVESCU",
        //             "remoteAETitle":"ARCACT1TLN",
        //             "performedOps":{},
        //             "invokedOps":{
        //                 "C-MOVE":{
        //                     "RQ":1,
        //                     "RSP":0
        //                 }
        //             }
        //             ,
        //             "forward-C-MOVE-RQ-for-Study":"1.2.840.113619.2.55.1.1762927524.2188.1148396481.727",
        //             "testwert":"hallo value"
        //         },
        //         {
        //             "serialNo":7,
        //             "connectTime":"2016-06-16T10:25:20.145+02:00",
        //             "initiated":false,
        //             "localAETitle":"DCM4CHEE",
        //             "remoteAETitle":"ARCACT1TLN",
        //             "performedOps":{
        //                 "C-STORE":{
        //                     "RQ":2,
        //                     "RSP":1
        //                 }
        //             },
        //             "invokedOps":{

        //             }
        //         },
        //         {
        //             "serialNo":8,
        //             "connectTime":"2016-06-16T10:25:20.302+02:00",
        //             "initiated":true,
        //             "localAETitle":"DCM4CHEE",
        //             "remoteAETitle":"STORESCP",
        //             "performedOps":{

        //             },
        //             "invokedOps":{
        //                 "C-STORE":{
        //                     "RQ":1,
        //                     "RSP":1
        //                 }
        //             }
        //         }];
                angular.forEach(obj,function(j, l){
                    angular.forEach(j,function(m, i){
                        // console.log("m",m);
                        // console.log("i",i);
                        // console.log("obj[i]",obj[i]);
                        // console.log("definedFields.indexOf(i)",definedFields.indexOf(i));
                        local[l] = local[l] || {};
                        if(definedFields.indexOf(i) > -1){
                            local[l][i] = m;
                        }else{
                            
                            local[l]["others"] = local[l]["others"] || {};
                            local[l]["othersFile"] = local[l]["othersFile"] || {};
                            if(Object.keys(local[l]["others"]).length === 0){
                                local[l]["others"] = "<table><tr><td>"+i+"</td><td>"+m+"</td></tr>";
                                local[l]["othersFile"] = i+"="+m;
                            }else{
                                local[l]["others"] += "<tr><td>"+i+"</td><td>"+m+"</td></tr>";
                                local[l]["othersFile"] += " | "+i+"="+m;
                            }
                            $scope.others = true;
                        }
                    });
                    if(local[l]["others"] && Object.keys(local[l]["others"]).length > 0){
                        local[l]["others"] += "<table>";
                    }
                });
            return local;
    };
    var timeCalculator = function(data){
        angular.forEach(data, function(m, i){
            var date    = new Date(m.connectTime);
            var today   = new Date();
            var diff    = Math.round((today-date)/1000);
            var sec     = "00";
            var min     = "00";
            var h       = "00";
            var milsec       = Math.round((((today-date) / 1000)-Math.floor((today-date) / 1000))*1000);

            if(diff < 60){
                sec = diff;
            }else{
                sec = Math.round(((diff / 60)-Math.floor(diff / 60))*60);

                if(Math.floor(diff / 60) < 60){
                    min = Math.round(Math.floor(diff / 60));
                    if(min < 10){
                        min = "0"+min;
                    }
                }else{
                    min = Math.round(((Math.floor(diff / 60) / 60) - Math.floor(Math.floor(diff / 60) / 60))*60);
                    h   = Math.round(Math.floor(Math.floor(diff / 60) / 60));
                }
            }
            if(sec<10 != "00"){
                sec = "0"+sec;
            }
            if(min < 10 && min != "00"){
                min = "0"+min;
            }
            if(h < 10 && h != "00"){
                h = "0"+h;
            }
            var dYear  = date.getFullYear();
            var dMonth = date.getMonth()+1;
            if(dMonth < 10){
                dMonth = "0"+dMonth;
            }
            var dDate = date.getDate();
            if(dDate < 10){
                dDate = "0"+dDate;
            }
            var dHours = date.getHours();
            if(dHours < 10 && dHours != "00"){
                dHours = "0"+dHours;
            }
            var dMinutes = date.getMinutes();
            if(dMinutes < 10 && dMinutes != "00"){
                dMinutes = "0"+dMinutes;
            }
            var dSeconds = date.getSeconds();
            if(dSeconds < 10 && dSeconds != "00"){
                dSeconds = "0"+dSeconds;
            }
            data[i]["browserTime"] = dYear +"-"+ dMonth +"-"+ dDate +"  "+ dHours +":"+ dMinutes +":"+ dSeconds;
            data[i]["openSince"]   = h+":"+min+":"+sec+"."+milsec;
            data[i]["openSinceOrder"]   = (today-date);
        });
        return data;
    }
    $scope.abort = function(serialnr){
        cfpLoadingBar.start();
        $http({
          method: 'DELETE',
          url: "../monitor/associations/"+serialnr
        }).then(function successCallback(res) {
            refresh();
            cfpLoadingBar.complete();
        }, function errorCallback(response) {
                console.error("response=",response);
                DeviceService.msg($scope, {
                        "title": "Error",
                        "text": "Error: "+response,
                        "status": "error"
                });
                cfpLoadingBar.complete();
        });
    }

    $scope.propertyName = 'openSinceOrder';
    $scope.reverse = true;

    $scope.sortBy = function(propertyName) {
        $scope.reverse = ($scope.propertyName === propertyName) ? !$scope.reverse : false;
        $scope.propertyName = propertyName;
    };
    var refresh = function(){
        cfpLoadingBar.start();
        $http({
          method: 'GET',
          url: "../monitor/associations"
        }).then(function successCallback(res) {
            if(res.data && res.data[0] && res.data[0] != ""){
                res.data = modifyObject(res.data);
                res.data = timeCalculator(res.data);
                $scope.associationStatus = res.data;
            }else{
                $scope.associationStatus = null;
            }
            cfpLoadingBar.complete();
        }, function errorCallback(response) {
                console.error("response=",response);
                DeviceService.msg($scope, {
                        "title": "Error",
                        "text": "Error: "+response,
                        "status": "error"
                });
                cfpLoadingBar.complete();
        });
    }
    $scope.refresh = function(){
        refresh();
    }
    $scope.monitor = function(){
        cfpLoadingBar.start();
        $scope.stopLoop = false;
        $http({
          method: 'GET',
          url: "../monitor/associations"
        }).then(function successCallback(res) {
            if(res.data && res.data[0] && res.data[0] != ""){
                res.data = modifyObject(res.data);
                res.data = timeCalculator(res.data);
                $scope.associationStatus = res.data;
            }else{
                $scope.associationStatus = null;
            }
            cfpLoadingBar.complete();
        }, function errorCallback(response) {
                console.error("response=",response);
                DeviceService.msg($scope, {
                        "title": "Error",
                        "text": "Error: "+response,
                        "status": "error"
                });
                cfpLoadingBar.complete();
        });
        if($scope.updaterate && typeof $scope.updaterate === 'string' && $scope.updaterate.indexOf(",") > -1){
            $scope.updaterate = $scope.updaterate.replace(",", ".");
        }
        var associationLoop = setInterval(function () {
            if ($scope.stopLoop){
                clearInterval(associationLoop);
            }else{
                $scope.$apply(function(){
                    $http({
                      method: 'GET',
                      url: "../monitor/associations"
                    }).then(function successCallback(res) {
                        if(res.data && res.data[0] && res.data[0] != ""){
                            res.data = modifyObject(res.data);
                            res.data = timeCalculator(res.data);
                            $scope.associationStatus = res.data;
                        }else{
                            $scope.associationStatus = null;
                        }
                    }, function errorCallback(response) {
                            DeviceService.msg($scope, {
                                    "title": "Error",
                                    "text": "Connection error!",
                                    "status": "error"
                            });
                    });
                });
            }
        }, $scope.updaterate * 1000);
    };
    $scope.downloadAssocImmage = function(){
        var csv = "Local AE Title ⇆ Remote AE Title";
        csv += ",Invoked Ops.";
        csv += ",Performed Ops.";
        csv += ",Connection time (Server)";
        csv += ",Connection time (Browser)";
        csv += ",Connection open for (hh:mm:ss)";

        if($scope.others){
            csv += ",Other attributes\n";
        }else{
            csv += "\n";
        }
        angular.forEach($scope.associationStatus,function(m, i){
            if(m.initiated){
                csv += m.localAETitle +"→"+ m.remoteAETitle;
            }else{
                csv += m.localAETitle +"←"+ m.remoteAETitle;
            }
            if(m.invokedOps){
                csv += ","
                angular.forEach(m.invokedOps, function(l, j){
                    csv = csv + "   "+ j + "- RQ/RSP : " + l.RQ + "/" + l.RSP;
                });
            }else{
                csv += ",";
            }
            if(m.performedOps){
                csv += ","
                angular.forEach(m.performedOps, function(l, j){
                    csv = csv + "   "+ j + "- RQ/RSP : " + l.RQ + "/" + l.RSP;
                });
            }else{
                csv += ","
            }
            csv += ","+m.connectTime;
            csv += ","+m.browserTime;
            csv += ","+m.openSince;
            if(m.othersFile){
                csv += ","+m.othersFile+"\n";
            }else{
                csv += "\n";
            }
        });
        var file = new File([csv], "associacions.csv", {type: "text/csv;charset=utf-8"});
        saveAs(file);
    };
});
