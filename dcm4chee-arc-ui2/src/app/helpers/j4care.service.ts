import {Injectable} from '@angular/core';
import {AppService} from "../app.service";
import {J4careHttpService} from "./j4care-http.service";
import {Http, ResponseContentType, Headers} from "@angular/http";
import {Subscriber} from "rxjs/Subscriber";
import {Observable} from "rxjs/Observable";
declare var fetch;
declare var DCM4CHE: any;
import * as _ from 'lodash';
import {DatePipe} from "@angular/common";
import {WindowRefService} from "./window-ref.service";
import localeUs from '@angular/common/locales/es-US';
import {MatDialog, MatDialogConfig, MatDialogRef} from "@angular/material";
import {ConfirmComponent} from "../widgets/dialogs/confirm/confirm.component";
import {DevicesService} from "../configuration/devices/devices.service";


@Injectable()
export class j4care {
    header = new Headers();
    dialogRef: MatDialogRef<any>;
    constructor(
        public mainservice:AppService,
        public httpJ4car:J4careHttpService,
        public ngHttp:Http,
        public dialog: MatDialog,
        public config: MatDialogConfig
    ) {}
    static traverse(object,func){
        for(let key in object){
            if(object.hasOwnProperty(key)) {
                if(typeof object[key] === "object"){
                    this.traverse(object[key],func);
                }else{
                    object[key] = func.apply(object,[object[key],key,object]);
                }
            }
        }
        return object;
    }
    static firstLetterToUpperCase(str){
        return str && str[0].toUpperCase() + str.slice(1);
    }
    static firstLetterToLowerCase(str) {
        return str && str[0].toLowerCase() + str.slice(1);
    }
    static isSet(value){
        if((value === undefined || value === null) && (value != 0 && value != "")){
            return false;
        }
        return true;
    }
    static isSetInObject(object:any, key:string){
        return _.hasIn(object,key) && this.isSet(object[key]);
    }
    static prepareFlatFilterObject(array,lineLength?){
        if(!lineLength){
            lineLength = 3;
        }
        if(_.isArray(array) && array.length > 0){
            if(_.hasIn(array,"[0][0].firstChild")){
                return array
            }else{
                let endArray = [];
                let block = [];
                let line = [];
                array.forEach( formObject =>{
                    if(line.length < 2){
                        line.push(formObject);
                    }else{
                        if(block.length < lineLength){
                            block.push(line);
                            line = [];
                            line.push(formObject);
                        }else{
                            endArray.push(block)
                            block = [];
                            block.push(line);
                            line = [];
                            line.push(formObject);
                        }
                    }
                });
                if(line.length > 0){
                    if(block.length < lineLength){
                        block.push(line);
                    }else{
                        endArray.push(block);
                        block = [];
                        block.push(line);
                    }
                }
                if(block.length > 0){
                    endArray.push(block);
                }
                return endArray;
            }
        }else{
            return array;
        }
    }
    static stringifyArrayOrObject(properties, exceptions){
        Object.keys(properties).forEach(task=>{
            if(_.isArray(properties[task]) && exceptions.indexOf(task) === -1){
                if(properties[task].length === 2 && task.indexOf('Range') > -1)
                    properties[task] = properties[task].join(' - ');
                else{
                    if(_.isObject(properties[task][0])){
                        properties[task] = properties[task].map(t=>{
                            return Object.keys(t).map(key=>{
                                return `${key}=${t[key]}`
                            });
                        }).join('; ');
                    }else{
                        properties[task] = properties[task].join(', ');
                    }
                }
            }
            if(_.isObject(properties[task]) && exceptions.indexOf(task) === -1)
                properties[task] = Object.keys(properties[task]).map(taskKey=>`${taskKey}=${properties[task][taskKey]}`).join(', ');
        });
    }
    static extendAetObjectWithAlias(aet){
        let aliases = [];
        let usedAliasNames = [];
        aet.forEach((a)=>{
            if(_.hasIn(a,"dcmOtherAETitle")){
                let clone = _.cloneDeep(a);
                a.dcmOtherAETitle.forEach(alias=>{
                    clone.dicomAETitle = alias;
                    if(usedAliasNames.indexOf(alias) === -1){
                        aliases.push(_.cloneDeep(clone));
                        usedAliasNames.push(alias);
                    }
                });
            }
        });
        return [...aet,...aliases];
    }
    static convertDateRangeToString(rangeArray:Date[]){
        let datePipe = new DatePipe('en_US');

        if(rangeArray && rangeArray.length > 0){
            let stringArray:string[] = [];
            rangeArray.forEach(date =>{
                if(date){
                    stringArray.push(datePipe.transform(date,'yyyyMMdd'))
                }
            });
            return (stringArray.length > 1)?stringArray.join('-'):stringArray.join('');
        }else{
            if(_.isDate(rangeArray)){
                return datePipe.transform(rangeArray,'yyyyMMdd')
            }
            return '';
        }
    }
    static extractDurationFromValue(value:string){
        let match;
        const ptrn = /([P|p|T|t])|((\d*)(\w))/g;
        let year;
        let day;
        let month;
        let hour;
        let minute;
        let second;
        let week;
        let mode;
        try {
            while ((match = ptrn.exec(value)) != null) {
                if(match[1]){
                    mode = match[1];
                }
                switch(true) {
                    case (this.isEqual(match[4],'Y') || this.isEqual(match[4],'y')):
                        year = parseInt(match[3]);
                        break;
                    case (this.isEqual(match[4],'W') || this.isEqual(match[4],'w')):
                        week = parseInt(match[3]);
                        break;
                    case (this.isEqual(match[4],'M') || this.isEqual(match[4],'m')):
                        if(mode === "T" || mode === "t"){
                            minute = parseInt(match[3]);
                        }else{
                            month = parseInt(match[3]);
                        }
                        break;
                    case (this.isEqual(match[4],'D') || this.isEqual(match[4],'d')):
                        day= parseInt(match[3]);
                        break;
                    case (this.isEqual(match[4],'H') || this.isEqual(match[4],'h')):
                        hour = parseInt(match[3]);
                        break;
                    case (this.isEqual(match[4],'S') || this.isEqual(match[4],'s')):
                        second = parseInt(match[3]);
                        break;
                }
            }
            return {
                Week:week,
                FullYear:year,
                Date:day,
                Hours:hour,
                Minutes:minute,
                Month:month,
                Seconds:second
            }
        }catch (e){
            console.error("error parsing data!",e);
            return null;
        }
    }
    static getSingleDateTimeValueFromInt(value){
        if(value)
            if(value < 10)
                return `0${value}`;
            else
                return value.toString();
        else
            return '00';
    }
    static extractDateTimeFromString(str){
        const checkRegex = /^\d{14}-\d{14}$|^\d{8}-\d{8}$|^\d{6}-\d{6}$|^\d{14}-$|^-\d{14}$|^\d{14}$|^\d{8}-$|^-\d{8}$|^\d{8}$|^-\d{6}$|^\d{6}-$|^\d{6}$/m;
        const regex = /(-?)(\d{4})(\d{2})(\d{2})(\d{0,2})(\d{0,2})(\d{0,2})(-?)|(-?)(\d{0,4})(\d{0,2})(\d{0,2})(\d{2})(\d{2})(\d{2})(-?)/g;
        let matchString = checkRegex.exec(str);
        let match;
        let resultArray = [];
        let mode;
        let firstDateTime;
        let secondDateTime;
        if (matchString !== null && matchString[0]) {
            while ((match = regex.exec(matchString[0])) !== null) {
                if (match.index === regex.lastIndex) {
                    regex.lastIndex++;
                }
                resultArray.push(match);
            }
            if(resultArray.length === 2){
                if(resultArray[0][8] ==='-' || resultArray[0][16] ==='-')
                    mode = "range"
                firstDateTime = {
                    FullYear:resultArray[0][2],
                    Month:resultArray[0][3],
                    Date:resultArray[0][4],
                    Hours:resultArray[0][5] || resultArray[0][13],
                    Minutes:resultArray[0][6] || resultArray[0][14],
                    Seconds:resultArray[0][7] || resultArray[0][15]
                };
                secondDateTime = {
                    FullYear:resultArray[1][2],
                    Month:resultArray[1][3],
                    Date:resultArray[1][4],
                    Hours:resultArray[1][5] || resultArray[1][13],
                    Minutes:resultArray[1][6] || resultArray[1][14],
                    Seconds:resultArray[1][7] || resultArray[1][15]
                };
            }
            if(resultArray.length === 1){
                if(resultArray[0][1] ==='-' || resultArray[0][9] ==='-'){
                    mode = "leftOpen";
                    secondDateTime = {
                        FullYear:resultArray[0][2],
                        Month:resultArray[0][3],
                        Date:resultArray[0][4],
                        Hours:resultArray[0][5] || resultArray[0][13],
                        Minutes:resultArray[0][6] || resultArray[0][14],
                        Seconds:resultArray[0][7] || resultArray[0][15]
                    };
                }else{
                    if(resultArray[0][8] ==='-' || resultArray[0][16] ==='-')
                        mode = "rightOpen";
                    else
                        mode = "single";
                    firstDateTime = {
                        FullYear:resultArray[0][2],
                        Month:resultArray[0][3],
                        Date:resultArray[0][4],
                        Hours:resultArray[0][5] || resultArray[0][13],
                        Minutes:resultArray[0][6] || resultArray[0][14],
                        Seconds:resultArray[0][7] || resultArray[0][15]
                    };
                }
            }
            return {
                mode:mode,
                firstDateTime:firstDateTime,
                secondDateTime:secondDateTime
            }
        }
        return null;
    }
    static getMainAet(aets){
        try{
            return [aets.filter(aet => {
                return aet.dcmAcceptedUserRole && aet.dcmAcceptedUserRole.indexOf('user') > -1;
            })[0] || aets[0]];
        }catch (e) {
            console.groupCollapsed("j4care getMainAet(aets[])");
            console.error(e);
            console.groupEnd();
            return aets && aets.length > 0 ? [aets[0]] : [];
        }
    }
    static stringValidDate(string:string){
        if(Date.parse(string))
            return true;
        return false;
    }
    static validTimeObject(time:{Hours,Minutes,Seconds}){
        if(
            time.Hours && time.Hours < 25 &&
            time.Minutes && time.Minutes < 60 &&
            ((time.Seconds && time.Seconds < 60) || !time.Seconds || time.Seconds === "" || time.Seconds === "00")
        )
            return true;
        return false;
    }
    static isSetDateObject(date:{FullYear,Month,Date}){
        if(date && date.FullYear && date.Month && date.Date)
            return true;
        return false;
    }
    static isSetTimeObject(time:{Hours,Minutes,Seconds}){
        if(time && time.Hours && time.Minutes && time.Seconds)
            return true;
        return false;
    }
    static validDateObject(date:{FullYear,Month,Date}){
        if(this.stringValidDate(`${date.FullYear}-${date.Month}-${date.Date}`))
            return true;
        return false;
    }
    static splitTimeAndTimezone(string){
        const regex = /(.*)([+-])(\d{4})/;
        let m;
        if ((m = regex.exec(string)) !== null) {
            return {
                time:m[1],
                timeZone:`${m[2]||''}${m[3]||''}`
            }
        }
        if(string)
            return {
                time:string,
                timeZone:""
            };
        return string;
    }
    static redirectOnAuthResponse(res){
        let resjson;
        try{
            let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/");
            if(pattern.exec(res.url)){
                WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";
            }
            resjson = res.json();
        }catch (e){
            resjson = [];
        }
        return resjson;
    }
    static attrs2rows(level, attrs, rows) {
        function privateCreator(tag) {
            if ('02468ACE'.indexOf(tag.charAt(3)) < 0) {
                let block = tag.slice(4, 6);
                if (block !== '00') {
                    let el = attrs[tag.slice(0, 4) + '00' + block];
                    return el && el.Value && el.Value[0];
                }
            }
            return undefined;
        }
        let $this = this;
        Object.keys(attrs).sort().forEach(function (tag) {
            let el = attrs[tag];
            rows.push({ level: level, tag: tag, name: DCM4CHE.elementName.forTag(tag, privateCreator(tag)), el: el });
            if (el.vr === 'SQ') {
                let itemLevel = level + '>';
                _.forEach(el.Value, function (item, index) {
                    rows.push({ level: itemLevel, item: index });
                    $this.attrs2rows(itemLevel, item, rows);
                });
            }
        });
    };
    static dateToString(date:Date){
        return `${date.getFullYear()}${this.getSingleDateTimeValueFromInt(date.getMonth()+1)}${this.getSingleDateTimeValueFromInt(date.getDate())}`;
    }
    static fullDateToString(date:Date){
        return `${date.getFullYear()}.${this.getSingleDateTimeValueFromInt(date.getMonth()+1)}.${this.getSingleDateTimeValueFromInt(date.getDate())} ${this.getSingleDateTimeValueFromInt(date.getHours())}:${this.getSingleDateTimeValueFromInt(date.getMinutes())}:${this.getSingleDateTimeValueFromInt(date.getSeconds())}`;
    }
    static fullDateToStringFilter(date:Date){
        return `${date.getFullYear()}${this.getSingleDateTimeValueFromInt(date.getMonth()+1)}${this.getSingleDateTimeValueFromInt(date.getDate())}${this.getSingleDateTimeValueFromInt(date.getHours())}${this.getSingleDateTimeValueFromInt(date.getMinutes())}${this.getSingleDateTimeValueFromInt(date.getSeconds())}`;
    }
    static getTimeFromDate(date:Date){
        return `${j4care.getSingleDateTimeValueFromInt(date.getHours())}:${j4care.getSingleDateTimeValueFromInt(date.getMinutes())}:${j4care.getSingleDateTimeValueFromInt(date.getSeconds())}`;
    }
    static getDateFromObject(object:{FullYear,Month,Date}){
        if(object.FullYear && object.Month && object.Date)
            return `${object.FullYear}${object.Month}${object.Date}`
        return ''
    }
    static getTimeFromObject(object:{Hours,Minutes,Seconds}){
        if(object.Hours && object.Minutes && object.Seconds)
            return `${object.Hours}:${object.Minutes}:${object.Seconds}`
        return ''
    }
    static isEqual(a,b){
        if(a && b && a === b)
            return true;
        return false;
    }
    static convertDateToString(date:Date){
        let datePipe = new DatePipe('en_US');
        if(_.isDate(date)){
            return datePipe.transform(date,'yyyyMMdd')
        }
        return '';
    }
    static getValue(key, object, defaultVal?){
        if(object[key])
            return object[key];
        else
            return defaultVal || '';
    }
    download(url){
        this.httpJ4car.refreshToken().subscribe((res)=>{
            let token;
            let a = document.createElement('a');

            if(res.length && res.length > 0){
                this.httpJ4car.resetAuthenticationInfo(res);
                token = res.token;
            }else{
                token = this.mainservice.global.authentication.token;
            }
/*            let xhttp = new XMLHttpRequest();
            let filename = url.substring(url.lastIndexOf("/") + 1).split("?")[0];*/
/*            xhttp.onload = function() {
                let a = document.createElement('a');
                a.href = window.URL.createObjectURL(xhttp.response); // xhr.response is a blob
                xhttp.onreadystatechange = null;
                xhttp.abort();
                let attr = document.createAttribute("download");
                // attr.value = "true";
                a.setAttributeNode(attr);
                // a.download = filename; // Set the file name.
                a.style.display = 'none';
                document.body.appendChild(a);
                a.click();
                a.remove();
            };*/
/*            xhttp.onreadystatechange = ()=>{
                if (xhttp.readyState == 4){
                    if ((xhttp.status == 200) || (xhttp.status == 0)){
                        let a = document.createElement('a');
                        a.href = window.URL.createObjectURL(xhttp.response); // xhr.response is a blob
                        let attr = document.createAttribute("download");
                        xhttp.abort();
                        // attr.value = "true";
                        a.setAttributeNode(attr);
                        // a.download = filename; // Set the file name.
                        a.style.display = 'none';
                        document.body.appendChild(a);
                        a.click();
                        a.remove();

                    }
                }
            };
            xhttp.open("GET", url);
            xhttp.responseType = "blob";
            xhttp.setRequestHeader('Authorization', `Bearer ${token}`);
            xhttp.send();*/
            this.header.append('Authorization', `Bearer ${token}`);
/*            this.header.append('Authorization', `Bearer ${token}`);
            this.get(url).subscribe((res)=>{
                a.href = res;
                let attr = document.createAttribute("download");
                a.setAttributeNode(attr);
                // a.download = filename; // Set the file name.
                a.style.display = 'none';
                document.body.appendChild(a);
                a.click();
                a.remove();
            },(err)=>{

            })*/
            console.log("header",this.header);
            this.ngHttp.get(url,{
                        headers:this.header,
                        responseType: ResponseContentType.Blob
                    })
                .map(res => {
                        return new Blob([res['_body']], {
                            type: res.headers.get("Content-Type")
                        });
                })
                .subscribe((myBlob)=> {
                a.href = window.URL.createObjectURL(myBlob);
                let attr = document.createAttribute("download");
                a.setAttributeNode(attr);
                // a.download = filename; // Set the file name.
                a.style.display = 'none';
                document.body.appendChild(a);
                a.click();
                a.remove();
            });
/*            console.log("token",token);
            var myHeaders = new Headers();
            myHeaders.append('Authorization', `Bearer ${token}`);

            var myInit = { method: 'GET',
                headers: myHeaders,
                mode: 'cors',
                cache: 'default' };
            let a = document.createElement('a');;

            fetch(url,myInit)
                .then((response)=> {
                    console.log("response.url",response);
                    return response.blob();
                })
                .then((myBlob)=> {
                    a.href = window.URL.createObjectURL(myBlob);
                    let attr = document.createAttribute("download");
                    a.setAttributeNode(attr);
                    // a.download = filename; // Set the file name.
                    a.style.display = 'none';
                    document.body.appendChild(a);
                    a.click();
                    a.remove();
                });*/
        });
    }
    static convertBtoHumanReadable(value,mantissa?){
        let mantissaValue = 1000;
        if(mantissa == 0){
            mantissaValue = 1;
        }
        if(mantissa == 1){
            mantissaValue = 10;
        }
        if(mantissa == 2){
            mantissaValue = 100;
        }
        if(mantissa == 3){
            mantissaValue = 1000;
        }
        if (value > 2000000000){
            if(value > 1000000000000){
                return (Math.round((value / 1000 / 1000 / 1000 / 1000) * mantissaValue) / mantissaValue ) + ' TB';
            }else{
                return (Math.round((value / 1000 / 1000 / 1000) * mantissaValue) / mantissaValue ) + ' GB';
            }
        }else{
            return (Math.round((value / 1000 / 1000) * mantissaValue) / mantissaValue ) + ' MB';
        }
    }
    static clearEmptyObject(obj){
        _.forEach(obj,(m,i)=>{
            if((!m || m === "" || m === undefined) && m != 0){
                delete obj[i];
            }
        });
        return obj;
    };

    static getUrlParams(params){
        let paramString = jQuery.param(params);
        return paramString ? '?' + paramString : '';
    };

    get(url: string): Observable<any> {
        return new Observable((observer: Subscriber<any>) => {
            let objectUrl: string = null;

            this.ngHttp
                .get(url, {
                    headers:this.header,
                    responseType: ResponseContentType.Blob
                })
                .subscribe(m => {
                    objectUrl = URL.createObjectURL(m.blob());
                    observer.next(objectUrl);
                });

            return () => {
                if (objectUrl) {
                    URL.revokeObjectURL(objectUrl);
                    objectUrl = null;
                }
            };
        });
    }
    static splitDate(object){
        let endDate = [];
        let endDatePare = [];
        let m;
        let range;
        if(_.hasIn(object,'StudyDate'))
            range = object.StudyDate;
        else
            range = object;
        const regex = /((\d{4})(\d{2})(\d{2}))(?:\d{6})?-((\d{4})(\d{2})(\d{2}))(?:\d{6})?/;
        if ((m = regex.exec(range)) !== null) {
            let fromString = `${m[2]}-${m[3]}-${m[4]}`;
            let toString = `${m[6]}-${m[7]}-${m[8]}`;
            let from = new Date(fromString).getTime();
            let to = new Date(toString).getTime();
            let diff = to-from;
            let block = 86400000;
            if(diff > block){
                endDate.push(this.convertToDateString(fromString));
                let daysInDiff = diff/block;
                let dateStep = from;
                while(daysInDiff > 0){
                    endDatePare.push(this.convertToDatePareString(dateStep,dateStep+block));
                    dateStep = dateStep+block;
                    endDate.push(this.convertToDateString(new Date(dateStep)));
                    daysInDiff--;
                }
                return endDate;
            }else{
                return range;
            }
        }
        return null;
    }
    static addZero(nr){
        if(nr < 10){
            return `0${nr}`;
        }
        return nr;
    };
    static convertToDateString(date){
        if(date != undefined){
            let dateConverted = new Date(date);
            let dateObject =  {
                yyyy:dateConverted.getFullYear(),
                mm:this.addZero(dateConverted.getMonth()+1),
                dd:this.addZero(dateConverted.getDate())
            };
            return `${dateObject.yyyy}${(dateObject.mm)}${dateObject.dd}`;
        }
    }
    static getLastMonthRangeFromNow(){
        let firstDate = new Date();
        firstDate.setMonth(firstDate.getMonth()-1)
        return this.convertToDatePareString(firstDate,new Date());
    }
    static convertToDatePareString(firstDate,secondDate){
        if(firstDate === undefined && secondDate === undefined){
            return undefined;
        }
        let firstDateConverted = new Date(firstDate);
        let secondDateConverted = new Date(secondDate);
        let firstDateObject =  {
            yyyy:firstDateConverted.getFullYear(),
            mm:this.addZero(firstDateConverted.getMonth()+1),
            dd:this.addZero(firstDateConverted.getDate())
        };
        let firstDateString = `${firstDateObject.yyyy}${(firstDateObject.mm)}${firstDateObject.dd}`;
        if(new Date(firstDate).getTime() == new Date(secondDate).getTime()){
            return firstDateString;
        }else{
            let secondDateObject =  {
                yyyy:secondDateConverted.getFullYear(),
                mm:this.addZero(secondDateConverted.getMonth()+1),
                dd:this.addZero(secondDateConverted.getDate())
            };
            let secondDateString = `${secondDateObject.yyyy}${(secondDateObject.mm)}${secondDateObject.dd}`;
            return `${firstDateString}-${secondDateString}`;
        }
    }
    static flatten(data) {
        var result = {};

        function recurse(cur, prop) {
            if (Object(cur) !== cur) {
                result[prop] = cur;
            } else if (Array.isArray(cur)) {
                for (var i = 0, l = cur.length; i < l; i++)
                    recurse(cur[i], prop + "[" + i + "]");
                if (l == 0) result[prop] = [];
            } else {
                var isEmpty = true;
                for (var p in cur) {
                    isEmpty = false;
                    recurse(cur[p], prop ? prop + "." + p : p);
                }
                if (isEmpty && prop) result[prop] = {};
            }
        }
        recurse(data, "");
        return result;
    };
    static calculateWidthOfTable(table){
        let summ = 0;
        table.forEach((m)=>{
            summ += m.widthWeight;
        });
        table.forEach((m)=>{
            m.calculatedWidth =  ((m.widthWeight * 100)/summ)+"%";
        });
        return table;
    };

    static encode64(inputStr) {
        let b64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";
        let outputStr = "";
        let i = 0;

        while (i < inputStr.length) {
            let byte1 = inputStr.charCodeAt(i++) & 0xff;
            let byte2 = inputStr.charCodeAt(i++) & 0xff;
            let byte3 = inputStr.charCodeAt(i++) & 0xff;

            let enc1 = byte1 >> 2;
            let enc2 = ((byte1 & 3) << 4) | (byte2 >> 4);

            let enc3, enc4;
            if (isNaN(byte2)) {
                enc3 = enc4 = 64;
            } else {
                enc3 = ((byte2 & 15) << 2) | (byte3 >> 6);
                if (isNaN(byte3)) {
                    enc4 = 64;
                } else {
                    enc4 = byte3 & 63;
                }
            }
            outputStr += b64.charAt(enc1) + b64.charAt(enc2) + b64.charAt(enc3) + b64.charAt(enc4);
        }
        return outputStr;
    }
    /*
    * Input:
    * date:Date - javascript date
    * format:string - format as string
    * Output:
    * formatted date as string
    * defined format elements:
    * yyyy - 4 digit year
    * MM - month
    * dd - date
    * HH - Hour
    * mm - minute
    * ss - second
    * SSS - milliseconds
    * */
    static formatDate(date:Date, format:string):string{
        return format.replace(/(yyyy)|(MM)|(dd)|(HH)|(mm)|(ss)|(SSS)/g,(g1, g2, g3, g4, g5, g6, g7, g8)=>{
            if(g2)
                return `${date.getFullYear()}`;
            if(g3)
                return this.setZeroPrefix(`${date.getMonth() + 1}`);
            if(g4)
                return this.setZeroPrefix(`${date.getDate()}`);
            if(g5)
                return this.setZeroPrefix(`${date.getHours()}`);
            if(g6)
                return this.setZeroPrefix(`${date.getMinutes()}`);
            if(g7)
                return this.setZeroPrefix(`${date.getSeconds()}`);
            if(g8)
                return `${date.getMilliseconds()}`;
        });
    }
    /*
    *Adding 0 as prefix if the input is on  digit string for Example: 1 => 01
    */
    static setZeroPrefix(str){
        try{
            if(typeof str === "number"){
                str = str.toString();
            }
            return str.replace(/(\d*)(\d{1})/g,(g1, g2, g3)=>{
                if(!g2){
                    return `0${g3}`;
                }else{
                    return g1;
                }
            });
        }catch (e) {
            console.groupCollapsed("j4care setZeroPrefix(str)");
            console.error(e);
            console.groupEnd();
            return str;
        }
    }
    /*
    * Get difference of two date:Date, secondDate > firstDate return in the format HH:mm:ss:SSS
    * */
    static diff(firstDate:Date, secondDate:Date):string{
        try{
            let diff  = secondDate.getTime()  - firstDate.getTime();
            if(diff > -1){
                return `${
                    this.setZeroPrefix(parseInt(((diff/(1000*60*60))%24).toString()))
                }:${
                    this.setZeroPrefix(parseInt(((diff/(1000*60))%60).toString()))
                }:${
                    this.setZeroPrefix(parseInt(((diff/1000)%60).toString()))
                }.${
                    parseInt((diff % 1000).toString())
                }`;
            }
            return '';
        }catch (e) {
            console.groupCollapsed("j4care diff(date, date2)");
            console.error(e);
            console.groupEnd();
            return undefined;
        }
    }

    selectDevice(callBack, devices? ){
        let setParams = function(tempDevices){
            return {
                content: 'Select device if you wan\'t to reschedule to an other device',
                doNotSave:true,
                form_schema:[
                    [
                        [
                            {
                                tag:"label",
                                text:"Device"
                            },
                            {
                                tag:"select",
                                options:tempDevices,
                                showStar:true,
                                filterKey:"newDeviceName",
                                description:"Device",
                                placeholder:"Device"
                            }
                        ]
                    ]
                ],
                result: {
                    schema_model: {
                        newDeviceName:''
                    }
                },
                saveButton: 'SUBMIT'
            }
        };

        if(devices){
            this.openDialog(setParams(devices)).subscribe(callBack);
        }else{
            this.getDevices().subscribe((devices)=>{
                devices = devices.map(device=>{
                    return {
                        text:device.dicomDeviceName,
                        value:device.dicomDeviceName
                    }
                });
                this.openDialog(setParams(devices)).subscribe(callBack);
            },(err)=>{

            });
        }

    }
    getDevices = ()=>this.httpJ4car.get('../devices').map(res => j4care.redirectOnAuthResponse(res));

    openDialog(parameters, width?, height?){
        this.dialogRef = this.dialog.open(ConfirmComponent, {
            height: height || 'auto',
            width: width || '500px'
        });
        this.dialogRef.componentInstance.parameters = parameters;
        return this.dialogRef.afterClosed();
    };
}
