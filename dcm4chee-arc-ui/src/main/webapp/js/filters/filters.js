"use strict";

myApp.filter("formatDA", function() {
    return function (da) {
        return (da && da.length == 8)
            ? da.substr(0, 4) + "-" + da.substr(4, 2) + "-" + da.substr(6)
            : da;
    };
});

myApp.filter("formatTM", function() {
    return function (tm) {
        if (!tm || tm.length < 3)
            return tm;
        if (tm.charAt(2) === ":")
            return tm.substr(0, 8);
        var hh_mm = tm.substr(0, 2) + ":" + tm.substr(2, 2);
        return tm.length < 5 ? hh_mm : hh_mm + ":" + tm.substr(4, 2);
    }
});

// myApp.filter("contentDescription", function() {
//     function valueOf(attr) {
//         return attr && attr.Value && attr.Value[0];
//     };
//     function valuesOf(attr) {
//         return attr && attr.Value && attr.Value.join();
//     };
//     function imageDescriptionOf(attrs) {
//         var cols = valueOf(attrs["00280011"]); // Columns
//         return cols && (cols + "x"
//             + valueOf(attrs["00280010"]) + " " // Rows
//             + valueOf(attrs["00280100"]) + " bit " // BitsAllocated
//             + valuesOf(attrs["00080008"])); // ImageType
//     };
//     function srDescriptionOf(attrs) {
//         var code = valueOf(attrs["0040A043"]); // ConceptNameCodeSequence
//         return code && [
//                 valueOf(attrs["0040A496"]), // PreliminaryFlag
//                 valueOf(attrs["0040A491"]), // CompletionFlag
//                 valueOf(attrs["0040A493"]), // VerificationFlag
//                 valueOf(code["00080104"])  // CodeMeaning
//             ].filter(function (obj) { return obj }).join(" ");
//     };
//     return function(attrs) {
//         try {
//             var instCheck = false;        
//             if((inst != undefined  || inst != null) && inst["00080016"] && valueOf(inst["00080016"])){
//                instCheck = true; 
//             }
//             return valueOf(attrs["00700081"]) // ContentDescription
//                 || imageDescriptionOf(attrs)
//                 || srDescriptionOf(attrs)
//                 || instCheck; // SOPClassUID

//         }
//         catch(err) {
//             return false;
//         }
//     };

// });
myApp.filter("contentDescription", function() {
    function valueOf(attrs, code) {
        try{
            return attrs[code].Value[0];
        }catch(e){
            return false;
        }
    };
    function valuesOf(attr) {
        return attr && attr.Value && attr.Value.join();
    };
    function imageDescriptionOf(attrs) {
        var cols = valueOf(attrs,"00280011"); // Columns
        return cols && (cols + "x"
            + valueOf(attrs,"00280010") + " " // Rows
            + valueOf(attrs,"00280100") + " bit " // BitsAllocated
            + valuesOf(attrs["00080008"])); // ImageType
    };
    function srDescriptionOf(attrs) {
        var code = valueOf(attrs,"0040A043"); // ConceptNameCodeSequence
        return code && [
                valueOf(attrs,"0040A496"), // PreliminaryFlag
                valueOf(attrs,"0040A491"), // CompletionFlag
                valueOf(attrs,"0040A493"), // VerificationFlag
                valueOf(code,"0080104")  // CodeMeaning
            ].filter(function (obj) { return obj }).join(" ");
    };
    return function(attrs) {
        return valueOf(attrs,"00700081") // ContentDescription
            || imageDescriptionOf(attrs)
            || srDescriptionOf(attrs)
            || valueOf(attrs,"00420010"); // Document Title
    };

});

myApp.filter("formatTag", function() {
    return function (tag) {
        return "(" + tag.slice(0,4) + "," + tag.slice(4, 8) + ")";
    };
});
myApp.filter("removedots", function() {
    return function (string) {
        if(string){
            return string.replace(/\./g, '');
        }else{
            return "";
        }
    };
});

myApp.filter("formatAttributeValue", function() {
    return function (el) {
        if (el.Value && el.Value.length) {
            switch (el.vr) {
                case 'SQ':
                    return el.Value.length + ' Item(s)';
                case 'PN':
                    if(el.Value && el.Value[0]){
                        return el.Value.map(function(value){
                            return value.Alphabetic;
                        }).join();
                    }else{
                        return "";
                    }
                default:
                    return el.Value.join();
            }
        }
        return el.BulkDataURI || "";
    };
});

myApp.filter("attributeNameOf", function() {
    return function (tag) {
        return DCM4CHE.elementName.forTag(tag);
    };
});

// myApp.filter("trim", function() {
//     return function (object,limit) {
//         if(object && object.length > limit){
//             return object.substr(0, limit)+"...";
//         }else{
//             return object;
//         }
//     };
// });


myApp.filter("testFilter", function($filter, $select){
    return function(object, selectedElement, selectedPart){
        var localObject = {};
        angular.forEach(object, function(m, i){
            if( (
                    $select[i].optionRef.length > 1 && 
                    $select[$select[i].optionRef[0]].type === "array" && 
                    selectedPart && 
                    selectedPart[$select[i].optionRef[0]]
                )
                ||
                (
                    $select[i].optionRef.length === 1
                )
                ||
                (
                    $select[i].optionRef.length > 1 && 
                    $select[$select[i].optionRef[0]].type === "object"
                )
                ){
                    localObject[i]=m;
                }
        });
        return localObject;
    };
});

myApp.filter("study", function(){
    return function(object, iod){
        var localObject = {};
        angular.forEach(object, function(m, i){
            if(iod && iod.study && iod.study[i]){
                localObject[i] = m;
            }

        });
        return localObject;
    };
});
myApp.filter("mwl", function(){
    return function(object, iod){
        var localObject = {};
        console.log("iod",iod);
        // console.log("object",object);
        angular.forEach(object, function(m, i){
            if(iod && iod.mwl && iod.mwl[i]){
                localObject[i] = m;
            }
        });
        return localObject;
    };
});