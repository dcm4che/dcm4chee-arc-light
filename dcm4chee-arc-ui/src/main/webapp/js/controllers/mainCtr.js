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

  // console.log("$('.div-table .thead .tr_row')=",$('.div-table .thead .tr_row'));
  var headers = [
    ".div-table > .hader_block > .thead"
    // ,
    // ".div-table > .hader_block > .header2 > .tr_row",
    // ".div-table > .hader_block > .header3 > .tr_row",
    // ".div-table > .hader_block > .header4 > .tr_row"
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
            $(m).css({
                "position":"fixed",
                "width":"auto",
                "box-shadow":"0px 7px 12px rgba(58, 58, 58, 0.61)"
            });
        }
        if(items[i].itemOffsetOld  && (items[i].scrollTop < items[i].itemOffsetOld)){
            $(m).css({
                "position":"static",
                "width":"100%",
                "box-shadow":"none"
            });
        }
      }
    });
  });
  var hoverdic = {
    ".repeat0 .thead .tr_row":".div-table > .hader_block > .header1",
    ".repeat1_hover":".div-table > .hader_block > .header2",
    ".repeat2_hover":".div-table > .hader_block > .header3",
    ".repeat3_hover":".div-table > .hader_block > .header4"
  }
  angular.forEach(hoverdic, function(m, i){
    // console.log("m",m);
    // console.log("i",i);
    $(document.body).on("mouseover mouseleave",i,function(e){
      // console.log("e",e);
      // console.log("hover2");
      // console.log("e.relatedTarget",e.relatedTarget);
      // console.log("$e.relatedTarget",$(e.relatedTarget));
      // console.log("this index",$(this).children(".th").index(e.relatedTarget));
      $(".header1").removeClass('hover');
      if(e.type === "mouseover"){
        $(this).addClass('hover');
        $(m).addClass('hover');
        $(".header1").removeClass('gray');
      }else{
        $(m).removeClass('hover');
        $(this).removeClass('hover');
      }
      if(!$(".hader_block .hover").length){
        // setTimeout(function(){
          // if(!$(".hader_block .hover").length){
            $(".header1").addClass('hover gray');
        //   }
        // }, 2000);
      }
    });
  });
  $(document.body).on("mouseover mouseleave",".hover_cell",function(e){
    console.log("hovercell e",e);
    console.log("index",$(this).index());
    $(".div-table > .hader_block > .thead.hover > .tr_row > .th").removeClass("cellhover");
    console.log("selectedelemten",$(".div-table > .hader_block > .thead.hover > .tr_row > .th:eq("+$(this).index()+")"));
    $(".div-table > .hader_block > .thead.hover > .tr_row > .th:eq("+$(this).index()+")").addClass('cellhover');
  });



});
