"use strict";

/*
*Show directive selectDicomNetworkConnection only if a device was selected
*/
myApp.directive('onDeviceChange', function($compile, $log, DeviceService) {
	return {
		restrict: 'A',
      	link: function(scope, elm, attrs) {    
           	elm.bind('change', function() {
           		//Print warning if something was changed, and the user want to select an other device
	      		if(scope.currentDevice != undefined && scope.saved != undefined && !scope.saved){		          
		          	vex.dialog.confirm({
			            message: 'You are about to change the device without saving changes?',
			            callback: function(value) {
			            	if(value){
			           			scope.currentDevice = scope.devicename;
			           			scope.saved = true;
			            	}else{
	      						$(elm).val(scope.currentDevice);
			            	}
			            }
			         });
	      		}else{
	           		scope.currentDevice = scope.devicename;
	      		}
           	});
       	}
   };        
});
