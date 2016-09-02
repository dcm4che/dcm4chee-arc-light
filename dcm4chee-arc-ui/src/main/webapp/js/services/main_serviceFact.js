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
				// if(!response.data.user && response.data.roles.length === 0){

				// }else{
					user.username  = response.data.user;
					user.roles = response.data.roles;
				// }

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
	  	},
	  	getAes: function(user, aes){
			if(!user.data || !user.data.user || user.data.roles.length === 0){
				return aes;
			}else{
		  		var endAes = [];
		  		var valid;

		  		console.log("user",user);
		  		console.log("aes",aes);
		        angular.forEach(aes, function(ae, i){
		  			valid = false;
			        angular.forEach(user.roles, function(user, i){
			            angular.forEach(ae.dcmAcceptedUserRole, function(aet, j){
			                if(user === aet){
			                    valid = true;
			                }
			            })
			        });
			        if(valid){
			        	endAes.push(ae);
			        }
		        });
		        console.log("endAes",endAes);
		        return endAes;
			}
	  	}
	  }
  }); 