"use strict";

myApp.factory('MainService', function( cfpLoadingBar, $http, user)   {

	  return{
	  	user: function(){
	  		if(Object.keys(user).length < 1){
				$http({
				  method: 'GET',
				  url: 'rs/realm'
				}).then(function successCallback(response) {
				// console.log("response. user=",JSON.parse(response.data));
				// $scope.user           = {};
				user.username  = response.data.user;

				user.isRole = function(role){
				    if(response.data.user === null && response.data.roles.length === 0){
				      return true;
				    }else{ 
				      if(response.data.roles && response.data.roles.indexOf(role) > -1){
				        return true;
				      }else{
				        return false;
				      }
				    }
				};
				}, function errorCallback(response) {
				  	user.username = "user";
				    user.isRole = function(role){
				    	if(role === "admin"){
				    		return false;
				    	}else{
				    		return true;
				    	}
				    };
				}); 
	  		}
	  	}
	  }
  }); 