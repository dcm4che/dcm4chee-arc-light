
myApp.directive("scroll", function ($window, $log) {

    return function(scope, element, attrs) {
        angular.element($window).bind("scroll", function() {
            if (this.pageYOffset >= 150) {
                scope.showScrollButton = true;
            } else {
                scope.showScrollButton = false;
            }
            setTimeout(function(){ 
                scope.$apply();
            });
        });
    };
});