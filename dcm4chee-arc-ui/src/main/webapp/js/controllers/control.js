"use strict";

myApp.controller('ArchiveCtrl', function ($scope, $http) {

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
    $scope.monitor = function(){
        $scope.stopLoop = false;
        var dummy = [
                        {
                            "serialNo":9,
                            "connectTime":"2016-06-10T09:56:52.431",
                            "initiated":false,
                            "localAETitle":"DCM4CHEE",
                            "remoteAETitle":"MOVESCU"
                        },
                        {
                            "serialNo":10,
                            "connectTime":"2016-06-10T09:56:52.503",
                            "initiated":true,
                            "localAETitle":"DCM4CHEE",
                            "remoteAETitle":"STORESCP"
                        }
                    ];
        // $http.get("../ctrl/reload").then(function (res) {
        //     $scope.message = 'Reload successful';
        // })
        $scope.associationStatus = dummy;
        var associationLoop = setInterval(function () {
            if ($scope.stopLoop){
                clearInterval(associationLoop);
                console.log("stoped");
            }else{
                $scope.$apply(function(){
                    $scope.associationStatus[0].connectTime = $scope.associationStatus[0].connectTime+".";
                });
                console.log("running");
            }
        }, $scope.updaterate * 1000);

    };
});
