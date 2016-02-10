"use strict";

myApp.controller('MainController', function ($scope,$location) {
	$scope.getClass = function (path) {
		if($location.path().substr(0, path.length) === path) {
		    return 'active';
		} else {
		    return '';
		}
			  
	};

    $scope.toggleMenu = function(){
      console.log("showMenu=",$scope.showMenu);
      if($scope.showMenu){
        $scope.showMenu = false;
      }else{
        $scope.showMenu = true;
      }
    };
	
});
