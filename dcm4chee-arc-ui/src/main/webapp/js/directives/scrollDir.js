
myApp.directive("scroll", function ($window, $log) {

    return function(scope, element, attrs) {
        angular.element($window).bind("scroll", function() {
             if (this.pageYOffset >= 200) {
                 scope.showScrollButton = true;
             } else {
             	scope.showScrollButton = false;
             }
            scope.$apply();
        });
    };
});