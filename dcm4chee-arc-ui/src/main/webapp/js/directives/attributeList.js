"use strict";

myApp.directive("attributeList", function() {
    function attrs2rows(level, attrs, rows) {
        angular.forEach(attrs, function (el, tag) {
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
            attrs: '='
        },
        templateUrl: 'templates/attribute_list.html',
        link: function(scope) {
            scope.rows = [];
            attrs2rows("", scope.attrs, scope.rows);
        }
    };
});
myApp.directive("fileAttributeList", function($http) {
    function attrs2rows(level, attrs, rows) {

        angular.forEach(attrs, function (el, tag) {
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
            attrs: '=',
            instance: '=',
            aet: "="
        },
        templateUrl: 'templates/file_attribute_list.html',
        link: function(scope) {
            var url = "../aets/" + 
                        scope.aet + 
                        "/rs/studies/" + 
                        scope.instance.wadoQueryParams.studyUID +
                        "/series/" +
                        scope.instance.wadoQueryParams.seriesUID +
                        "/instances/" +
                        scope.instance.wadoQueryParams.objectUID +
                        "/metadata";
            $http({
                method: 'GET',
                url: url
            }).then(function successCallback(response) {
                scope.attrs = response.data[0];
                scope.rows2 = [];
                attrs2rows("", scope.attrs, scope.rows2);
            }, function errorCallback(response) {
                vex.dialog.alert("Error loading Attributes!");
            });
        }
    };
});
