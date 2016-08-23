"use strict";

myApp.controller('MainController', function ($scope, $location, $http, MainService, user) {
  // $scope.logoutUrl              = myApp.logoutUrl();
  MainService.user();
  $scope.user = user;
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
  // $scope.logoutUrl = function() {
  //     // var host = location.protocol + "//" + location.host
  //     var host = $scope.url;
  //     return host + "/realms/dcm4che/protocol/openid-connect/logout?redirect_uri="
  //         + encodeURIComponent(host + location.pathname);
  // }
  $http({
      method: 'GET',
      url: '../auth'
  }).then(function successCallback(response) {
    $scope.url  = response.data.url;
    var host    = location.protocol + "//" + location.host

    $scope.logoutUrl = response.data.url + "/realms/dcm4che/protocol/openid-connect/logout?redirect_uri="
          + encodeURIComponent(host + location.pathname);
  }, function errorCallback(response) {
      // vex.dialog.alert("Error loading device names, please reload the page and try again!");
      $scope.url = "/auth";
      var host = location.protocol + "//" + location.host
      $scope.logoutUrl =  host + "/auth/realms/dcm4che/protocol/openid-connect/logout?redirect_uri="
          + encodeURIComponent(host + location.pathname);
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
    ".repeat3_hover",
    ".repeat4_hover"
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

  //Hide mainmenu and user-config menu when the user clicks outside of them
  angular.element("html").bind("click",function(e){
        if(!(e.target.id === "mainmenu" || $(e.target).hasClass('toggle-button'))){
          $scope.showMenu = false;
        }
        if(!(e.target.id === "usermenu"  || $(e.target).hasClass('username') || $(e.target).hasClass('config'))){
          $scope.showUserMenu = false;
        }
  });
  $(".logo").unbind("click").bind("click",function(){
      var html =  '<div class="info-block">'
          html +=         '<div class="head">'
          html +=             '<h1>J4Care</h1>'
          html +=             '<h3>SMooTH Archive</h3>'
          html +=             '<h4>Version 5.5.0</h4>'
          html +=         '</div>'
          html +=         '<div class="content">'
          html +=             '<p><b>J4Care GmbH</b><br/>Enzersdorfer Strasse 7<br/>A-2340 Mödling</p>'
          html +=         '</div>'
          html +=         '<div class="pre_footer">'
          html +=             '<span>2009</span>'
          html +=         '</div>'
          html +=         '<div class="footer">'
          html +=             '<div class="footer_left col-sm-6">'
          html +=             '</div>'
          html +=             '<div class="footer_right col-sm-6">'
          html +=             '<span>0408</span>'
          html +=             '</div>'
          html +=         '</div>'
          html +=     '</div>'
      vex.dialog.alert({
          // input:'<img src="img/kenn.jpg">',
          input:html,
          className:"vex-theme-os info-dialog"
      });
  });

});
