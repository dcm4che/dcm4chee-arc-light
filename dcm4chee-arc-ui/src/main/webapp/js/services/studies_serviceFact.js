"use strict";

myApp.factory('StudiesService', function() {

    return {
        getTodayDate: function() {
           	var today = new Date();
		    var dd = today.getDate();
		    var mm = today.getMonth()+1; //January is 0!
		    var yyyy = today.getFullYear();

		    if(dd<10) {
		        dd='0'+dd;
		    } 

		    if(mm<10) {
		        mm='0'+mm;
		    }
		   return yyyy+mm+dd;
        },
        convertTime : function(studyTime){
        	console.log("typeof from=",typeof(studyTime.fromObject));
        	if(studyTime.fromObject && typeof(studyTime.fromObject) != 'string'){
	            var timestampFrom 	= Date.parse(studyTime.fromObject);
	            var d1From 			= new Date(timestampFrom);
	            var hourFrom 		= d1From.getHours();
	            var minuteFrom 		= d1From.getMinutes();
	            if(hourFrom<10){
	            	hourFrom = '0'+hourFrom;
	            }
	            if(minuteFrom<10){
	            	minuteFrom = '0'+minuteFrom;
	            }
	            console.log("from will be set=",hourFrom+":"+minuteFrom);
	            studyTime.from 		= hourFrom+":"+minuteFrom;
	            console.log("from set=",studyTime.from);
        	}
        	if(studyTime.toObject && typeof(studyTime.toObject) != 'string'){
	            var timestampTo = Date.parse(studyTime.toObject);
	            var d1To = new Date(timestampTo);
	            var hourTo = d1To.getHours();
	            var minuteTo = d1To.getMinutes();
	            if(hourTo<10){
	            	hourTo = '0'+hourTo;
	            }
	            if(minuteTo<10){
	            	minuteTo = '0'+minuteTo;
	            }
	            studyTime.to = hourTo+":"+minuteTo;
			}
        }
    };
});

