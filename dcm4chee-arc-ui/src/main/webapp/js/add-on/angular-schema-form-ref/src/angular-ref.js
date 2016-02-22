"use strict";

angular
.module('schemaForm')
.value('schemas', '');



angular
.module('schemaForm')
.directive('loadSubSchema',function ($timeout) {
  return {
    restrict: 'A',
    scope: {
      jsonName: '='
    },
    templateUrl:'js/add-on/angular-schema-form-ref/src/form.html',
    link: function (scope, element, attrs) {
        console.log("in load schema part element=",attrs.jsonName);
    }
  };
});

angular
.module('schemaForm')
.directive('toggleButton', function ($compile) {

  return {
    restrict: 'A',
    scope: {
      name: '=',
      jsonName: '='
    },
    link: function (scope, element, attrs) {
    	
    	var json 		= attrs.jsonName.replace(/'/g,"");
    	var name 		= attrs.name.replace(/'/g,"");
    	var cssClass 	= attrs.name.replace(/'/g,"").replace(/.schema.json/g,"");

    	angular.element(element).bind("click", function(){
    		if(document.getElementsByClassName(cssClass).length<1){
    			// setTimeout(function(){
	    			angular
	    			.element(element)
	    			.after(
	    					$compile('<div class="'+cssClass+'" json-name="'+json+'" load-sub-schema >'+name+'</div>')(scope)
	    				);
    			// }, 3000);
    		}else{
    			angular.element(document.getElementsByClassName(cssClass)).toggle();
    		}
    	});
    }
  };
});
