myApp.directive("input", function ($window, $log, $compile) {
    var getScopeModelFromString = function(scope, ngModel){
        if(ngModel){
            var ngmodel = ngModel.split(".");
            var object = scope;
            ngmodel.forEach(function(m, i){
                m = m.replace(/'/g, '');
                m = m.replace(/\[/g, '');
                m = m.replace(/\]/g, '');
                if(object[m]){
                    object = object[m];
                }else{
                    object = "";
                }
            });
            return object;
        }else{
            return "";
        }
    }
    return{
        restrict: 'E',
        link: function(scope, element, attrs) {
            if(!(attrs.class && attrs.class.indexOf('no-close-button') > -1)){
                angular.element(element).bind("keydown keyup change click",function(){
                    if(angular.element(this).val() != ""){
                        angular.element(this).siblings("span.x").show();
                    }    
                });
                var ngmodel = getScopeModelFromString(scope, attrs.ngModel);
                if(attrs.type==="text"){
                    var div  = angular.element('<div class="input"></div>');
                    var x    = angular.element('<span class="x glyphicon single_clear '+attrs.id+' glyphicon-remove-sign" ng-hide="'+attrs.ngModel+'===\'\'" ng-click="'+attrs.ngModel+'=\'\'"></span>');
                    div.insertAfter(element);
                    div.append(element);
                    div.append(x);
                    if(ngmodel === ""){
                        x.hide();
                    }
                }
                $compile(x)(scope);
            }
        }
    }
});