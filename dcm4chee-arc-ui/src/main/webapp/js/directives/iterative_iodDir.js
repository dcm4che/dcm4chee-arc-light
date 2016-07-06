
myApp.directive("iterativeIod", function ($window, $log, $compile) {

    return {
        restrict: "E", 
        templateUrl: 'templates/iterative_iod.html',
        link: function(scope,elm,attr) {
            // console.log("$parent.",scope.$parent[attr.object]);
            // console.log("attr.object",attr.object);
            // console.log("scope",scope);
            // if(attr.object.indexOf('{') >= 0){
            //     scope.obj = JSON.parse(attr.object);
            // }else{
            //     scope.obj = null;
            // }
            // console.log("scope.editmwl.attrs",scope.editmwl.attrs);
            // console.log("scope.editmwl.attrs",scope[attr.object]);
            // console.log("DCM4CHE",scope.$parent.DCM4CHE);
            // console.log("DCM4CHE",DCM4CHE);
            // scope.DCM4CHE = DCM4CHE;
            // // scope.test2 = JSON.parse(attr.object);
            // // if(typeof scope.object === "object"){
            // //     var dir = angular.element($compile('<iterative-iod object="obj" value="p" key="i"></iterative-iod>')(scope));
            // //     angular.element(elm).append(dir);
            // // }
            // console.log("typeof attr.object",typeof attr.object);
            // console.log("typeof scope.test2",typeof scope.test2);
            // console.log("elm",elm);
            // console.log("attr",attr);
        }
    };
});