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
    // $scope.user           = {};
    $scope.username  = response.data.user;

    $scope.isRole = function(role){
        if(response.data.roles && response.data.roles.indexOf(role) > -1){
          return true;
        }else{
          return false;
        }
    };
  }, function errorCallback(response) {
      // vex.dialog.alert("Error loading device names, please reload the page and try again!");
  }); 
  // $scope.isRole = function(role){
  //   console.log("role=",role);
  //   console.log("$scope.user.roles=",$scope.user.roles);
  //   if($scope.user && $scope.user.roles && $scope.user.roles.indexOf(role) > 0){
  //     return true;
  //   }else{
  //     return false;
  //   }
  // };
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
