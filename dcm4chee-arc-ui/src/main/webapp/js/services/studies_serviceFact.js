"use strict";

myApp.factory('StudiesService', function(cfpLoadingBar) {

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
        updateTime : function(studyTime){
            if(studyTime.fromObject){
                var timestampFrom   = Date.parse(studyTime.fromObject);
                var d1From          = new Date(timestampFrom);
                var hourFrom        = d1From.getHours();
                var minuteFrom      = d1From.getMinutes();
                if(hourFrom<10){
                    hourFrom = '0'+hourFrom;
                }
                if(minuteFrom<10){
                    minuteFrom = '0'+minuteFrom;
                }
                studyTime.from      = hourFrom+":"+minuteFrom;
            }
            if(studyTime.toObject){
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
            cfpLoadingBar.complete();
        },
        updateFromDate : function(studyDate){

            if(studyDate.fromObject){
                var timestampFrom   = Date.parse(studyDate.fromObject);
                var d1From          = new Date(timestampFrom);
                var yyyyFrom        = d1From.getFullYear();
                var MMFrom          = d1From.getMonth()+1;
                var ddFrom          = d1From.getDate();
                if(MMFrom<10){
                    MMFrom = '0'+MMFrom;
                }
                console.log("MMFrom",MMFrom);
                if(ddFrom<10){
                    ddFrom = '0'+ddFrom;
                }
                studyDate.from      = yyyyFrom+MMFrom+ddFrom;
            }else{
                studyDate.from      = "";
            }
            cfpLoadingBar.complete();
        },
        updateToDate : function(studyDate){
        	if(studyDate.toObject){
                var timestampTo   = Date.parse(studyDate.toObject);
                var d1To          = new Date(timestampTo);
                var yyyyTo        = d1To.getFullYear();
                var MMTo          = d1To.getMonth()+1;
                var ddTo          = d1To.getDate();
                if(MMTo<10){
                    MMTo = '0'+MMTo;
                }
                if(ddTo<10){
                    ddTo = '0'+ddTo;
                }
                studyDate.to      = yyyyTo+MMTo+ddTo;
            }else{
                studyDate.to      = "";
            }
            cfpLoadingBar.complete();
        }
    };
});

