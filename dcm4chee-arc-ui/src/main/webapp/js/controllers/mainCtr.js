"use strict";

myApp.controller('MainController', function ($scope, $location, $http) {
  $scope.logoutUrl              = myApp.logoutUrl();
  $scope.showUserMenu           = false;
  $scope.msg                    = [];
  vex.defaultOptions.className  = 'vex-theme-os';
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

  var getPathName = function(){
    return $location.path().replace(/\//g, '');
  };
  $scope.isOnPage = function(page){
    console.log("getPathName()=",getPathName());
    if(page === getPathName()){
      return true;
    }else{
      return false;
    }
  }
  $scope.getPathName = function(){
    return getPathName();
  };
    /*
  *Close button for the messages
  *obj (Object) the message that need to bee closed
  */
  $scope.closeBox = function(obj){

    angular.forEach($scope.msg, function(m, k){
      if(m == obj){
        $(".msg_container li").eq(k).fadeOut("400",function(){
          $scope.msg.splice(k, 1);
        });
      }
    });
  };

  // console.log("$('.div-table .thead .tr_row')=",$('.div-table .thead .tr_row'));
  var headers = [
    ".div-table > .header1 > .tr_row",
    ".div-table > .header2 > .tr_row",
    ".div-table > .header3 > .tr_row",
    ".div-table > .header4 > .tr_row"
  ]
  var items = {};
  console.log("items=",items);
  angular.forEach(headers, function(m, i){
    console.log("i=",i);
    items[i] = items[i] || {};
    $(window).scroll(function(){
      console.log("m=",m);
      if($(m).length){

        items[i].itemOffset = $(m).offset().top;
        console.log("i",i);
        console.log("items[i].itemOffset",items[i].itemOffset);
        console.log("items[i].itemOffset2",items[i].itemOffset + i * 28);
        items[i].scrollTop = $(window).scrollTop() + i * 29;
        if(items[i].scrollTop >= items[i].itemOffset){
            items[i].itemOffsetOld = items[i].itemOffsetOld || $(m).offset().top;
            $(m).css({
                "position":"fixed",
                "width":"auto"
            });
        }
        if(items[i].itemOffsetOld  && (items[i].scrollTop < items[i].itemOffsetOld)){
            $(m).css({
                "position":"static",
                "width":"100%"
            });
        }
      }
    });
  });

});
