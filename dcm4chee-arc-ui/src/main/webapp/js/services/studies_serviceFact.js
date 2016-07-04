"use strict";

myApp.factory('StudiesService', function(cfpLoadingBar, $compile) {

    var integerVr = ["DS","FL","FD","IS","SL","SS","UL", "US"];
    var getSchemaModelFromIodHelper = function(iod, patient, schema, patientedit){

            // $scope.patientedit = {};
            angular.forEach(iod, function(m,i){
                schema.properties  = schema.properties || {};
                schema.properties[i]  = schema.properties[i] || {};
                patientedit[i] = "";
                switch(m.vr) {
                    case "PN":
                            schema.properties[i]["properties"] = schema.properties[i]["properties"] || {};
                            schema.properties[i]["title"] = DCM4CHE.elementName.forTag(i);
                            schema.properties[i]["type"] = "object";
                            schema.properties[i]["properties"]["Alphabetic"] = schema.properties[i]["properties"]["Alphabetic"] || {};
                            schema.properties[i]["properties"]["Alphabetic"]["title"] = DCM4CHE.elementName.forTag(i) + " Alphabetic";
                            schema.properties[i]["properties"]["Alphabetic"]["type"] = "string";
                            schema.properties[i]["properties"]["Ideographic"] = schema.properties[i]["properties"]["Ideographic"] || {};
                            schema.properties[i]["properties"]["Ideographic"]["title"] = DCM4CHE.elementName.forTag(i) + " Ideographic";;
                            schema.properties[i]["properties"]["Ideographic"]["type"] = "string";
                            schema.properties[i]["properties"]["Phonetic"] = schema.properties[i]["properties"]["Phonetic"] || {};
                            schema.properties[i]["properties"]["Phonetic"]["title"] = DCM4CHE.elementName.forTag(i) + " Phonetic";
                            schema.properties[i]["properties"]["Phonetic"]["type"] = "string";
                        break;
                    case 'SQ':
                            schema.properties[i]["title"] = DCM4CHE.elementName.forTag(i);
                            schema.properties[i]["type"] = "object";
                            schema.properties[i] = getSchemaModelFromIodHelper(m.items, patient, schema.properties[i], patientedit);
                        break;
                    default:
                        schema.properties[i]["title"] = DCM4CHE.elementName.forTag(i);
                        if(m.multi === true){
                            schema.properties[i]["type"] = "array";
                            schema.properties[i]["items"] = {
                                                                    "type": "string",
                                                                    "title": DCM4CHE.elementName.forTag(i)
                                                                  };
                            patientedit[i] = [];
                        }else{
                            schema.properties[i]["type"] = "string";
                        }
                }
            });
            return schema;

    };
    Date.prototype.yyyymmdd = function() {
       var yyyy = this.getFullYear().toString();
       var mm = (this.getMonth()+1).toString(); // getMonth() is zero-based
       var dd  = this.getDate().toString();
       return yyyy + (mm[1]?mm:"0"+mm[0]) + (dd[1]?dd:"0"+dd[0]); // padding
    };

    var getArrayFromIodHelper = function(data, dropdown){
        angular.forEach(data, function(m, i){
            dropdown.push({
                "code":i,
                "codeComma": i.slice(0, 4)+","+i.slice(4),
                "name":DCM4CHE.elementName.forTag(i)
            });
        });
        return dropdown;
    };
    var replaceKeyInJsonHelper = function(object, key, key2){
        angular.forEach(object,function(m, k){
            if(m[key]){
                object[k][key2] = [object[k][key]];
                delete object[k][key];
            }
            if(m.vr && m.vr !="SQ" && !m.Value){
                object[k]["Value"] = [""];
            }
            if((Object.prototype.toString.call(m) === '[object Array]') || (object[k] !== null && typeof(object[k]) == "object")) {
                replaceKeyInJsonHelper(m, key, key2);
            }
        });
        return object;
    }
    var clearPatientObjectHelper = function(object, parent){
        angular.forEach(object, function(m,i){
            if(typeof(m) === "object" && i != "vr"){
                clearPatientObjectHelper(m,object);
            }else{
                var check = typeof(i) === "number" || i === "vr" || i === "Value" || i === "Alphabetic" || i === "Ideographic" || i === "Phonetic" || i === "items";
                if(!check){
                    delete object[i];
                }
            }
        });
    };
    var convertStringToNumberHelper = function(object, parent){
        angular.forEach(object, function(m,i){
            if(typeof(m) === "object" && i != "vr"){
                convertStringToNumberHelper(m,object);
            }else{
                if(i === "vr"){
                    if((integerVr.indexOf(object.vr) > -1 && object.Value && object.Value.length > 0)){
                        if(object.Value.length > 1){
                            angular.forEach(object.Value,function(k, j){
                                object.Value[j] = Number(object.Value[j]);
                            });
                        }else{
                            object.Value[0] = Number(object.Value[0]);
                        }
                    }

                }
            }
        });
    };

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
        updateTime : function(studyTime, scope){
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
                scope["ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime"].from = hourFrom+":"+minuteFrom;
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
                scope["ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime"].to = hourTo+":"+minuteTo;

            }
            cfpLoadingBar.complete();
        },
        updateFromDate : function(studyDate, scope){

            if(studyDate.fromObject){
                var timestampFrom   = Date.parse(studyDate.fromObject);
                var d1From          = new Date(timestampFrom);
                var yyyyFrom        = d1From.getFullYear();
                var MMFrom          = d1From.getMonth()+1;
                var ddFrom          = d1From.getDate();
                if(MMFrom<10){
                    MMFrom = '0'+MMFrom;
                }
                if(ddFrom<10){
                    ddFrom = '0'+ddFrom;
                }
                studyDate.from      = yyyyFrom+MMFrom+ddFrom;
                scope["ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate"].from = yyyyFrom+MMFrom+ddFrom;
            }else{
                studyDate.from      = "";
            }
            cfpLoadingBar.complete();
        },
        updateToDate : function(studyDate, scope){
            console.log("studyDate",studyDate);
            console.log("studyDate.toObject",studyDate.toObject);
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
                // console.log("in updateTo date studyDate.ScheduledProcedureStepEndDate",studyDate.ScheduledProcedureStepEndDate);
                scope["ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate"].to     = yyyyTo+MMTo+ddTo;
                // console.log("in updateTo date studyDate.ScheduledProcedureStepEndDate",ScheduledProcedureStepSequence.ScheduledProcedureStepEndDate);
            }else{
                studyDate.to      = "";
            }
            cfpLoadingBar.complete();
        },
        getParams : function($scope){
            var params = {};
            if($scope.rejectedBefore.date){
                var rejecteBeforeTimestamps     = Date.parse($scope.rejectedBefore.date);
                var rejecteBeforeDate           = new Date(rejecteBeforeTimestamps);
                var yyyy                        = rejecteBeforeDate.getFullYear();
                var MM                          = rejecteBeforeDate.getMonth()+1;
                var dd                          = rejecteBeforeDate.getDate();
                if(MM<10){
                    MM = '0'+MM;
                }
                if(dd<10){
                    dd = '0'+dd;
                }
                params["rejectedBefore"]        = yyyy+"-"+MM+"-"+dd;
            }
            if($scope.keepRejectionNote === true){
                params["keepRejectionNote"]     = true;
            }
            return params;
        },
        trim: function(scope){
            setTimeout(function(){ 
                  angular.forEach($('.txt'), function(m, i){
                    if ($(m)[0].scrollWidth >  $(m).innerWidth() || $(m)[0].scrollHeight >  $(m).innerHeight()+1) {
                            var tooltip = $(m).find(".tooltip_container");
                            var check1 = (Math.round($(m)[0].scrollWidth) >  Math.round($(m).innerWidth()) && Math.abs($(m)[0].scrollWidth - $(m).innerWidth()) > 1 );
                            var check2 = (Math.round($(m)[0].scrollHeight) >  Math.round($(m).innerHeight())&& Math.abs($(m)[0].scrollHeight - $(m).innerHeight()) > 1);
                            if ((check1 || check2) && $(m).text().length > 0) {
                                var fulltext = $(m).text();
                                if(check1){
                                    while($(m)[0].scrollWidth >  $(m).innerWidth() && Math.abs($(m)[0].scrollWidth - $(m).innerWidth()) > 1 ){
                                        var slice = Math.round(Math.abs($(m)[0].scrollWidth - $(m).innerWidth()) / 6);
                                        if(slice > 0){
                                            $(m).text(function (_,txt) {
                                                return txt.slice(0, -slice);
                                            });
                                        }else{
                                            $(m).text(function (_,txt) {
                                                return txt.slice(0, -1);
                                            });
                                        }
                                    }
                                }else{
                                        while($(m)[0].scrollHeight >  $(m).innerHeight() && Math.abs($(m)[0].scrollHeight - $(m).innerHeight()) > 1){
                                            var slice =  Math.round(Math.abs($(m)[0].scrollHeight - $(m).innerHeight()) / 2);
                                            if(slice > 0){
                                                $(m).text(function (_,txt) {
                                                    return txt.slice(0, -slice);
                                                });
                                            }else{
                                                $(m).text(function (_,txt) {
                                                    return txt.slice(0, -1);
                                                });
                                            }
                                        }
                                }
                                $(m).text(function (_,txt) {
                                    return txt.slice(0, -4)+"...";
                                });
                                $(m).prepend(tooltip);
                                $(m).css("overflow","visible");
                            }
                    }else{
                        $(m).find(".tooltip_container").remove();
                    }
                    $(m).removeClass('txt');
                  });
            }, 300);  
        },
        getSchemaModelFromIod: function(res, patient){
            var schema = {
                  "type": "object",
                  "title": "Comment",
                  "properties": {}
                };
            var patientedit = {};
            getSchemaModelFromIodHelper(res.data, patient, schema, patientedit);
            angular.forEach(patient.attrs,function(m, i){
                if(m.Value && m.Value[0]){
                    if(res.data[i].multi === true){
                        patientedit[i] = m.Value;
                    }else{
                        patientedit[i] = m.Value[0];
                    }
                }
            });
            return {
                    "schema":schema,
                    "patientedit":patientedit
                    };
        },
        getArrayFromIod :function(res){
            var dropdown = [];
            getArrayFromIodHelper(res.data, dropdown);
            return dropdown;
        },
        clearPatientObject : function(patient){
            clearPatientObjectHelper(patient);
        },
        convertStringToNumber : function(patient){
            convertStringToNumberHelper(patient,integerVr);

        },
        replaceKeyInJson : function(object, key, key2){
            replaceKeyInJsonHelper(object, key, key2);
            return object;
        },
        convertDateToString : function($scope, mode){
            console.log("mode",mode);
            angular.forEach($scope[mode].attrs,function(m, i){
                console.log("m",m);
                console.log("i",i);
                console.log("$scope[mode][i]",$scope[mode].attrs[i]);
                if(m.vr === "DA"){
                    // var string = value.Value[0];
                    // var yyyy = string.substring(0,4);
                    // var MM = string.substring(4,6);
                    // var dd = string.substring(6,8);
                    // console.log("yyyy",yyyy);
                    // console.log("MM",MM);
                    // console.log("dd",dd);
                    // var testDate = new Date(yyyy+"-"+MM+"-"+dd);
                    // console.log("testDate",testDate);
                    var d = new Date($scope.dateplaceholder[i]);
                    console.log("d",d);
                    d.yyyymmdd();
                    // console.log("d",d.yyyymmdd());
                    // var timestampDate   = Date.parse(m.Value[0]);
                    // var date          = new Date(timestampDate);
                    // console.log("date",date);
                    $scope[mode].attrs[i].Value[0] = d.yyyymmdd();
                }
            });
        }

    };
});

