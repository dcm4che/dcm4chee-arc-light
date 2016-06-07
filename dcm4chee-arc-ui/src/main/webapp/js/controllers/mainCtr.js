"use strict";

myApp.controller('MainController', function ($scope, $location, $http) {
  $scope.logoutUrl              = myApp.logoutUrl();
  $scope.showUserMenu           = false;
  $scope.msg                    = [];
  $scope.visibleHeaderIndex     = 0;
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
  /*
  * Add the class fixed to the main_content when the user starts to scroll (reacht the main_content position) 
  * so we can user that as layer so the user can't see the table above the filters when he scrolls.
  */
  var headers = [
    ".main_content"
  ]
  var items = {};
  angular.forEach(headers, function(m, i){
    items[i] = items[i] || {};
    $(window).scroll(function(){
      if($(m).length){
        items[i].itemOffset = $(m).offset().top;
        items[i].scrollTop = $(window).scrollTop();
        if(items[i].scrollTop >= items[i].itemOffset){
            items[i].itemOffsetOld = items[i].itemOffsetOld || $(m).offset().top;
            $(".headerblock").addClass('fixed');
        }
        if(items[i].itemOffsetOld  && (items[i].scrollTop < items[i].itemOffsetOld)){
            $(".headerblock").removeClass('fixed');
        }
      }
    });
  });

  //Detecht witch header shuld be shown.
  var hoverdic = [
    ".repeat0 .thead .tr_row",
    ".repeat1_hover",
    ".repeat2_hover",
    ".repeat3_hover"
  ]
  angular.forEach(hoverdic, function(m, i){
    $(document.body).on("mouseover mouseleave",m,function(e){

      if(e.type === "mouseover" && $scope.visibleHeaderIndex != i){
        $(this).addClass('hover');
        $(m).addClass('hover');
        $(".headerblock .header_block .thead").addClass('animated fadeOut');
        setTimeout(function(){
          $scope.$apply(function() {
            $scope.visibleHeaderIndex = i;
          });
          $(".div-table .header_block .thead").removeClass('fadeOut').addClass('fadeIn');
        }, 200);
        setTimeout(function(){
          $(".headerblock .header_block .thead").removeClass('animated');
        },200);
      }
    });
  });
  
  //Detect in witch column is the mouse position and select the header.
  $(document.body).on("mouseover mouseleave",".hover_cell",function(e){
    var $this = this;
    if(e.type === "mouseover"){
      angular.forEach($(".headerblock > .header_block > .thead"),function(m, i){
        $(m).find(".cellhover").removeClass("cellhover");
        $(m).find(".th:eq("+$($this).index()+")").addClass('cellhover');
      });
    }else{
      $(".headerblock > .header_block > .thead > .tr_row > .cellhover").removeClass("cellhover");
    }
  });



});
