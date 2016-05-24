"use strict";

myApp.factory('StudiesService', function(cfpLoadingBar, $compile) {
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
                    if ($(m)[0].scrollWidth >  $(m).innerWidth() || $(m)[0].scrollHeight >  $(m).innerHeight()) {
                            var tooltip = $(m).find(".tooltip_container");
                            $(m).text(function (_,txt) {
                                return txt.trim();
                            });
                            var check1 = (Math.round($(m)[0].scrollWidth) >  Math.round($(m).innerWidth()) && Math.abs($(m)[0].scrollWidth - $(m).innerWidth()) > 1 );
                            var check2 = (Math.round($(m)[0].scrollHeight) >  Math.round($(m).innerHeight())&& Math.abs($(m)[0].scrollHeight - $(m).innerHeight()) > 1);
                            if ((check1 || check2) && $(m).text().length > 0) {
                                var fulltext = $(m).text();
                                $(m).attr("tooltip",fulltext);
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
            console.log("2res=",res.data);

            getSchemaModelFromIodHelper(res.data, patient, schema, patientedit);
            console.log("afterhelper patientedit",angular.copy(patientedit));
            angular.forEach(patient.attrs,function(m, i){
                console.log("m=",m);
                if(m.Value && m.Value[0]){
                    if(res.data[i].multi === true){
                        patientedit[i] = m.Value;
                    }else{
                        patientedit[i] = m.Value[0];
                    }
                    // console.log("in if value=",value);
                }
            });
            console.log("schema in service =",schema);
            console.log("schema in patientedit =",patientedit);
            return {
                    "schema":schema,
                    "patientedit":patientedit
                    };
        },
        getArrayFromIod :function(res){
            var dropdown = [];
            getArrayFromIodHelper(res.data, dropdown);
            return dropdown;
        }

    };
});

