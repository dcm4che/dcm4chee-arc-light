"use strict";

myApp.directive("attributeList", function() {
    function attrs2rows(level, attrs, rows) {
        function privateCreator(tag) {
            if ("02468ACE".indexOf(tag.charAt(3)) < 0) {
                var block = tag.slice(4, 6);
                if (block !== "00") {
                    var el = attrs[tag.slice(0, 4) + "00" + block];
                    return el && el.Value && el.Value[0];
                }
            }
            return undefined;
        }

        Object.keys(attrs).sort().forEach(function (tag) {
            var el = attrs[tag];
            rows.push({ level: level, tag: tag, name: DCM4CHE.elementName.forTag(tag, privateCreator(tag)), el: el });
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
