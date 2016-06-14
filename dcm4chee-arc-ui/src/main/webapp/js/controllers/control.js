"use strict";

myApp.controller('ArchiveCtrl', function ($scope, $http, DeviceService) {

    $scope.updaterate = 10;
    $scope.logoutUrl = myApp.logoutUrl();
    $scope.status = null;
    $scope.stopLoop = true;
    $scope.message = '';
    $scope.fetchStatus = function() {
        $http.get("../ctrl/status").then(function (res) {
            $scope.status = res.data.status;
            $scope.message = '';
        })
    };
    $scope.start = function() {
        $http.get("../ctrl/start").then(function (res) {
            $scope.status = 'STARTED';
            $scope.message = '';
        })
    };
    $scope.stop = function() {
        $http.get("../ctrl/stop").then(function (res) {
            $scope.status = 'STOPPED';
            $scope.message = '';
        })
    };
    $scope.reload = function() {
        $http.get("../ctrl/reload").then(function (res) {
            $scope.message = 'Reload successful';
        })
    };
    $scope.fetchStatus();


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

    $scope.propertyName = 'openSinceOrder';
    $scope.reverse = true;

    $scope.sortBy = function(propertyName) {
        $scope.reverse = ($scope.propertyName === propertyName) ? !$scope.reverse : false;
        $scope.propertyName = propertyName;
    };
    $scope.monitor = function(){
        $scope.stopLoop = false;
        // $http.get("../monitor/associations").then(function (res) {
        //     if(res.data && res.data[0] && res.data[0] != ""){
        //         res.data = timeCalculator(res.data);
        //         $scope.associationStatus = res.data;
        //     }
        // });
        $http({
          method: 'GET',
          url: "../monitor/associations"
        }).then(function successCallback(res) {
            if(res.data && res.data[0] && res.data[0] != ""){
                res.data = timeCalculator(res.data);
                $scope.associationStatus = res.data;
            }
        }, function errorCallback(response) {
                DeviceService.msg($scope, {
                        "title": "Error",
                        "text": "Error: "+response,
                        "status": "error"
                });
        });
        if($scope.updaterate && typeof $scope.updaterate === 'string' && $scope.updaterate.indexOf(",") > -1){
            $scope.updaterate = $scope.updaterate.replace(",", ".");
        }
        // if(!isNaN($scope.updaterate) && $scope.updaterate.toString().indexOf('.') != -1){
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
        // }
    };
    $scope.downloadAssocImmage = function(){
        var csv = "Local AE Title ⇆ Remote AE Title";
        csv += ",Invoked Ops.";
        csv += ",Performed Ops.";
        csv += ",Connection time (Server)";
        csv += ",Connection time (Local)";
        csv += ",Connection open for (hh:mm:ss)\n";
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
            csv += ","+m.openSince+"\n";
        });
        var file = new File([csv], "associacions.csv", {type: "text/csv;charset=utf-8"});
        saveAs(file);
    };
});
