"use strict";

myApp.directive("fileAttributeList", function($http, cfpLoadingBar) {
    function attrs2rows(level, attrs, rows) {
        var keys = Object.keys(attrs);
        keys.sort();
        keys.forEach(function (tag) {
            var el = attrs[tag];
            rows.push({ level: level, tag: tag, el: el });
            if (el.vr === 'SQ') {
                var itemLevel = level + ">";
                angular.forEach(el.Value, function (item, index) {
                    rows.push({ level: itemLevel, item: index });
                    attrs2rows(itemLevel, item, rows);
                });
            }
        });
    };
    return {
        restrict: 'E',
        scope: {
            studykey:'=', 
            serieskey: '=', 
            key: '='
        },
        templateUrl: 'templates/file_attribute_list.html',
        link: function(scope) {
            cfpLoadingBar.set(cfpLoadingBar.status()+(0.3));
            var url = "../aets/" + 
                        scope.$parent.aet + 
                        "/rs/studies/" + 
                        scope.$parent.studies[scope.studykey].series[scope.serieskey].instances[scope.key].wadoQueryParams.studyUID +
                        "/series/" +
                        scope.$parent.studies[scope.studykey].series[scope.serieskey].instances[scope.key].wadoQueryParams.seriesUID +
                        "/instances/" +
                        scope.$parent.studies[scope.studykey].series[scope.serieskey].instances[scope.key].wadoQueryParams.objectUID +
                        "/metadata";
            $http({
                method: 'GET',
                url: url
            }).then(function successCallback(response) {
                scope.attrs = response.data[0];
                scope.rows2 = [];
                attrs2rows("", scope.attrs, scope.rows2);
                cfpLoadingBar.complete();
            }, function errorCallback(response) {
                vex.dialog.alert("Error loading Attributes!");
            });
        }
    };
});
