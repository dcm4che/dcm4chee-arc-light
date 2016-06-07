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
    // ".headerblock> .header_block"
    // ".headerblock"
    ".main_content"
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
            // $(m).css({
            //     "position":"fixed",
            //     "width":"auto",
            //     "background":"white"
            //     // "box-shadow":"0px 7px 12px rgba(58, 58, 58, 0.61)"
            // });
            // $(".headerblock").css({
            //   "top":"-25px",
            //   "padding-top":"100px",
            //   "background":"rgb(225, 231, 236)"
            // });
            $(".headerblock").addClass('fixed');
        }
        if(items[i].itemOffsetOld  && (items[i].scrollTop < items[i].itemOffsetOld)){
            // $(m).css({
            //     "position":"static",
            //     "width":"100%",
            //     "background":"transparent"
            //     // "box-shadow":"none"
            // });
            // $(".headerblock").css({
            //   "top":"75px",
            //   "padding-top":"0px",
            //   "background":"transparent"
            // });
            $(".headerblock").removeClass('fixed');
        }
      }
    });
  });
  // var hoverdic = {
  //   ".repeat0 .thead .tr_row":".div-table > .hader_block > .header1",
  //   ".repeat1_hover":".div-table > .hader_block > .header2",
  //   ".repeat2_hover":".div-table > .hader_block > .header3",
  //   ".repeat3_hover":".div-table > .hader_block > .header4"
  // }  
  var hoverdic = [
    ".repeat0 .thead .tr_row",
    ".repeat1_hover",
    ".repeat2_hover",
    ".repeat3_hover"
  ]
  angular.forEach(hoverdic, function(m, i){
    // console.log("m",m);
    // console.log("i",i);
    $(document.body).on("mouseover mouseleave",m,function(e){
      // console.log("e",e);
      // console.log("hover2");
      // console.log("e.relatedTarget",e.relatedTarget);
      // console.log("$e.relatedTarget",$(e.relatedTarget));
      // console.log("this index",$(this).children(".th").index(e.relatedTarget));
          console.log("i=",i);
          console.log("m=",m);
      // $(".header1").removeClass('hover');
      if(e.type === "mouseover" && $scope.visibleHeaderIndex != i){
        $(this).addClass('hover');
        $(m).addClass('hover');
        // console.log("this",this);
        // console.log("i",i); 
        // console.log("m",m);
        console.log("theads", $(".headerblock.hader_block .thead"));
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
        console.log("$scope.visibleHeaderIndex",$scope.visibleHeaderIndex);
        // $(".header1").removeClass('gray');
        // $(".header1.gray .cellhover").removeClass('cellhover');
      }
      // else{
      //   $(m).removeClass('hover');
      //   $(this).removeClass('hover');
      //   $scope.$apply(function() {
      //     $scope.visibleHeaderIndex = 0;
      //   });
      // }
      // if(!$(".hader_block .hover").length){
      //   // setTimeout(function(){
      //     // if(!$(".hader_block .hover").length){
      //       $(".header1").addClass('hover gray');
      //       $(".header1.gray .cellhover").removeClass('cellhover');

      //   //   }
      //   // }, 2000);
      // }
    });
  });
  $(document.body).on("mouseover mouseleave",".hover_cell",function(e){
    // console.log("hovercell e",e);
    // console.log("index",$(this).index());
    var $this = this;
    if(e.type === "mouseover"){
      angular.forEach($(".headerblock > .header_block > .thead"),function(m, i){
        $(m).find(".cellhover").removeClass("cellhover");
        $(m).find(".th:eq("+$($this).index()+")").addClass('cellhover');
        // console.log("m=",$(m));
        // console.log("thisindex=",$($this).index());
        // console.log("index=",$(m).find(".th:eq("+$($this).index()+")"));
      });
      // $(".div-table > .header_block > .thead > .tr_row > .th").removeClass("cellhover");
      // console.log("selectedelemten",$(".div-table > .header_block > .thead.hover > .tr_row > .th:eq("+$(this).index()+")"));
      // $(".div-table > .header_block > .thead > .tr_row > .th:eq("+$(this).index()+")").addClass('cellhover');
    }else{
      // console.log("cellhover0s",$(".div-table > .header_block > .thead > .tr_row > .cellhover"));
      $(".headerblock > .header_block > .thead > .tr_row > .cellhover").removeClass("cellhover");
    }
  });



});
