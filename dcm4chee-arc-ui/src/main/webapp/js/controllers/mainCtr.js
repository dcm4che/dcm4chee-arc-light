"use strict";

myApp.controller('MainController', function ($scope,$location) {
  $scope.logoutUrl = myApp.logoutUrl();
	$scope.getClass = function (path) {
		if($location.path().substr(0, path.length) === path) {
		    return 'active';
		} else {
		    return '';
		}
			  
	};
    $scope.toggleMenu = function(){
      if($scope.showMenu){
        	$scope.showMenu = false;
      }else{
        $scope.showMenu = true;
      }
    };
    $scope.scrollUp = function(){
        $("html, body").animate({
            scrollTop: 0
        }, 300);
    };
	$scope.getPathName = function(){
    return $location.path().replace(/\//g, '');;
  };
});
