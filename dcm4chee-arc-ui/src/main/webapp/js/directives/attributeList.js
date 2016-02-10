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