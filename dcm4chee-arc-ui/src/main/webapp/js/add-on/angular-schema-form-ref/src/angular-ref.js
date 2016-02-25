"use strict";

angular
.module('schemaForm')
.value('schemas', '');



angular
.module('schemaForm')
.directive('loadSubSchema',function ($compile) {
  return {
    restrict: 'A',
    // scope: true,
    // scope: {
    //   jsonName: '=',
    //   partName: '=',
    //   formName: '@partName'
    // },
    templateUrl:'js/add-on/angular-schema-form-ref/src/form.html',
    link: function (scope, element, attrs) {
        console.log("element=",angular.element(element));
        console.log("in load schema part element=",attrs.jsonName, "partName=", attrs.partName, "attrs=",attrs);
        var formName    = attrs.partName+"Form";
        var schemaName  = attrs.partName+"Schema";
        var modelName   = attrs.partName+"Model";
        console.log("scope=",scope);
        console.log("scopehl=",scope.hl7ApplicationForm);
        console.log("before dynamicform=",scope.$parent.dynamicform);
        scope.dynamicform = scope.$parent.dynamicform || {};
        scope.$parent[schemaName]={
                            "type": "object",
                            "title": "Comment",
                            "properties": {
                              "name": {
                                "title": "Name",
                                "type": "string"
                              },
                              "email": {
                                "title": "Email",
                                "type": "string",
                                "pattern": "^\\S+@\\S+$",
                                "description": "Email will be used for evil."
                              },
                              "comment": {
                                "title": "Comment",
                                "type": "string",
                                "maxLength": 20,
                                "validationMessage": "Don't be greedy!"
                              }
                            },
                            "required": [
                              "name",
                              "email",
                              "comment"
                            ]
                          };
        scope.$parent[formName] = 
                          [
                            "name",
                            "email",
                            {
                              "key": "comment",
                              "type": "textarea",
                              "placeholder": "Make a comment"
                            },
                            {
                              "type": "submit",
                              "style": "btn-info",
                              "title": "OK"
                            }
                          ];
        angular.element(element).html(
          $compile('<div name="'+formName+'" class="ng-cloak" sf-schema="$parent[\''+schemaName+'\']" sf-form="$parent[\''+formName+'\']" sf-model="$parent[\''+modelName+'\']" ng-cloak>test:{{$parent[\''+formName+'\'] | json}}</div>')(scope)
        );
        console.log("dynamicform=",scope.dynamicform);
        // scope.$broadcast('schemaFormRedraw');
        //test:{{$parent[\''+formName+'\'] | json}}
       // scope.formName = "form"+attrs.partName;
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
	    					$compile('<div class="'+cssClass+'" json-name="'+json+'" part-name="'+name+'" load-sub-schema >'+name+'</div>')(scope)
	    				);
    			// }, 3000);
    		}else{
    			angular.element(document.getElementsByClassName(cssClass)).toggle();
    		}
    	});
    }
  };
});
