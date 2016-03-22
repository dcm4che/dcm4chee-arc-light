"use strict";

myApp.controller('MainController', function ($scope, $location, $http) {
  $scope.logoutUrl = myApp.logoutUrl();
  $scope.showUserMenu = false;
	$scope.getClass = function (path) {
		if($location.path().substr(0, path.length) === path) {
		    return 'active';
		} else {
		    return '';
		}
			  
	};
  $http({
      method: 'GET',
      url: 'rs/realm'
  }).then(function successCallback(response) {
    // console.log("response. user=",JSON.parse(response.data));
    $scope.user = {};
    $scope.user.username = response.data.user;
    if(response.data.roles.indexOf("admin") > 0){
      $scope.user.role = "admin";
    }else{
      $scope.user.role = response.data.roles[0];
    }
  }, function errorCallback(response) {
      // vex.dialog.alert("Error loading device names, please reload the page and try again!");
  }); 
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
