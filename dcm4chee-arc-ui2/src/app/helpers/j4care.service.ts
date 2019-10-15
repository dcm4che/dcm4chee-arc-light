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
import {Router} from "@angular/router";
import {J4careDateTime, J4careDateTimeMode, RangeObject} from "./j4care";
import {TableSchemaElement} from "../models/dicom-table-schema-element";
import {DicomNetworkConnection} from "../interfaces";
import {DcmWebApp} from "../models/dcm-web-app";
import {HttpClient, HttpHeaders} from "@angular/common/http";
import * as uuid from  'uuid/v4';
declare const bigInt:Function;

@Injectable()
export class j4care {
    header = new HttpHeaders();
    dialogRef: MatDialogRef<any>;
    constructor(
        private $httpClient:HttpClient,
        public dialog: MatDialog,
        public config: MatDialogConfig,
        private router: Router
    ) {}
    static traverse(object,func, savedKeys?:string){
        if(savedKeys != undefined){
            savedKeys += `[${savedKeys}]`;
        }
        for(let key in object){
            if(object.hasOwnProperty(key)) {
                if(typeof object[key] === "object"){
                    this.traverse(object[key],func, key);
                }else{
                    object[key] = func.apply(object,[object[key],key,object, savedKeys]);
                }
            }
        }
        return object;
    }
    static getPath(obj, searchKey:string, value:string) {
        for(let key in obj) {
            if(obj[key] && typeof obj[key] === "object") {

                let result = this.getPath(obj[key], searchKey, value);
                if(result) {
                    result.unshift(key);
                    return result;
                }
            }else{
                if(searchKey){
                    if(key === searchKey && obj[key] === value){
                        return [key];
                    }
                }else{
                    if(obj[key] === value) {
                        return [key];
                    }
                }
            }
        }
    }
    static downloadFile(url, filename?){
        if(filename){
            try{
                let link = document.createElement('a');
                let linkText = document.createTextNode("&nbsp;");
                link.appendChild(linkText);
                link.href = url;
                link.download=filename;
                link.target='_blank';
                document.body.appendChild(link);
                link.click();
                document.body.removeChild(link);
            }catch (e) {
                this.log("On DownloadFile",e);
                WindowRefService.nativeWindow.open(url);
            }
        }else{
            WindowRefService.nativeWindow.open(url);
        }
    }
    static firstLetterToUpperCase(str){
        return str && str[0].toUpperCase() + str.slice(1);
    }
    static firstLetterToLowerCase(str) {
        return str && str[0].toLowerCase() + str.slice(1);
    }
    static isSet(value){
        if((value === undefined || value === null || (_.isObject(value) && _.isEmpty(value))) && (value != 0 && value != "") && !_.isDate(value) && !_.isBoolean(value)){
            return false;
        }
        return true;
    }
    static hasSet(obj, path){
        if(_.hasIn(obj,path) && j4care.isSet(_.get(obj,path)))
            return true;
        return false;
    }
    static isSetInObject(object:any, key:string){
        return _.hasIn(object,key) && this.isSet(object[key]);
    }
    navigateTo(url){
        this.router.navigateByUrl(url);
    }
    static promiseToObservable<T>(promise:Promise<T>):Observable<T>{
        return Observable.create(observer=>{
            promise.then(res=>{
                observer.next(res);
            }).catch(err=>{
                observer.error(err);
            })
        });
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
    static arrayHasIn(arr:any[], path:string, value?:any){
        let check:boolean = false;
        arr.forEach(el=>{
            if(_.hasIn(el,path)){
                if((value || value === false)){
                    if(_.get(el, path) === value){
                        check = true;
                    }
                }else{
                    check = true;
                }
            }
        });
        return check;
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

    static extendAetObjectWithAliasFromSameObject(aet){
        const aliasPath = "dcmNetworkAE.dcmOtherAETitle";
        let aetExtended = [];
        aet.forEach((a)=>{
            aetExtended.push(a)
            if(_.hasIn(a,aliasPath)){
                try{
                    (<string[]>_.get(a,aliasPath)).forEach(alias=>{
                        aetExtended.push({
                            dicomAETitle:alias
                        })
                    });
                }catch (e) {
                    this.log("Trying to get aliasis from same path",e);
                }
            }
        });
        return aetExtended
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
    static extractDateTimeFromString(str):{mode:J4careDateTimeMode,firstDateTime:J4careDateTime,secondDateTime:J4careDateTime}{
        const checkRegex = /^\d{14}-\d{14}$|^\d{8}-\d{8}$|^\d{6}-\d{6}$|^\d{14}-$|^-\d{14}$|^\d{14}$|^\d{8}-$|^-\d{8}$|^\d{8}$|^-\d{6}$|^\d{6}-$|^\d{6}$/m;
        const regex = /(-?)(\d{4})(\d{2})(\d{2})(\d{0,2})(\d{0,2})(\d{0,2})(-?)|(-?)(\d{0,4})(\d{0,2})(\d{0,2})(\d{2})(\d{2})(\d{2})(-?)/g;
        let matchString = checkRegex.exec(str);
        let match;
        let resultArray = [];
        let mode:J4careDateTimeMode;
        let firstDateTime:J4careDateTime;
        let secondDateTime:J4careDateTime;
        if (matchString !== null && matchString[0]) {
            while ((match = regex.exec(matchString[0])) !== null) {
                if (match.index === regex.lastIndex) {
                    regex.lastIndex++;
                }
                resultArray.push(match);
            }
            if(resultArray.length === 2){
                if(resultArray[0][8] ==='-' || resultArray[0][16] ==='-')
                    mode = "range";
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
            if(firstDateTime){
                firstDateTime["dateObject"] = new Date(`${
                        firstDateTime.FullYear
                    }-${
                        firstDateTime.Month
                    }-${
                        firstDateTime.Date
                    } ${
                        firstDateTime.Hours || '00'
                    }:${
                        firstDateTime.Minutes || '00'
                    }:${
                        firstDateTime.Seconds || '00'
                    }`);
            }
            if(secondDateTime){
                secondDateTime["dateObject"] = new Date(`${
                        secondDateTime.FullYear
                    }-${
                        secondDateTime.Month
                    }-${
                        secondDateTime.Date
                    } ${
                        secondDateTime.Hours || '00'
                    }:${
                        secondDateTime.Minutes || '00'
                    }:${
                        secondDateTime.Seconds || '00'
                    }`);
            }
            return {
                mode:mode,
                firstDateTime:firstDateTime,
                secondDateTime:secondDateTime
            }
        }
        return null;
    }
    static splitRange(range:string):any{
        let rangeObject:RangeObject = j4care.extractDateTimeFromString(range);
        let diff:number = rangeObject && rangeObject.mode === "range" ? rangeObject.secondDateTime.dateObject.getTime() - rangeObject.firstDateTime.dateObject.getTime() : 0;
        let block = diff/30;
        const DAY_IN_MSC = 86400000;
        if(diff > 0){
            if(DAY_IN_MSC > block){
                return  (j4care.splitRangeInBlocks(rangeObject,DAY_IN_MSC, undefined, false));
            }else{
                return  (j4care.splitRangeInBlocks(rangeObject,block, undefined, true));
            }
        }
    }

    static rangeObjectToString(range:RangeObject):string{
        let firstDateTime = '';
        let secondDateTime = '';
        let minus = range.mode != "single" ? '-': '';
        if(range.mode === "range" || range.mode === "rightOpen" || range.mode === "single"){
            firstDateTime = this.formatDate(range.firstDateTime.dateObject,"yyyyMMdd");
        }
        if(range.mode === "range" || range.mode === "leftOpen"){
            secondDateTime = this.formatDate(range.secondDateTime.dateObject,"yyyyMMdd");
        }
        return `${firstDateTime}${minus}${secondDateTime}`;
    }
    static splitRangeInBlocks(range:RangeObject, block:number, diff?:number, pare:boolean = false):string[]{
        let endDate = [];
        let endDatePare = [];
        if(!diff){
            diff = range && range.mode === "range" ? range.secondDateTime.dateObject.getTime() - range.firstDateTime.dateObject.getTime() : 0;
        }
        if(diff > block){
            endDate.push(this.formatDate(range.firstDateTime.dateObject,"yyyyMMdd"));
            let daysInDiff = diff/block;
            let dateStep = range.firstDateTime.dateObject.getTime();
            while(daysInDiff > 0){
                if(daysInDiff != diff/block){
                    let increasedDateStep = new Date(dateStep);
                    increasedDateStep.setDate(increasedDateStep.getDate() + 1);
                    endDatePare.push(this.convertToDatePareString(increasedDateStep,dateStep+block));
                }else {
                    endDatePare.push(this.convertToDatePareString(dateStep,dateStep+block));
                }
                dateStep = dateStep+block;
                endDate.push(this.convertToDateString(new Date(dateStep)));
                daysInDiff--;
            }
            if(pare){
                return endDatePare;
            }else{
                return endDate;
            }
        }else{
            return [this.rangeObjectToString(range)];
        }
    }
    static splitDate(object){
        let range;
        if(_.hasIn(object,'StudyDate'))
            range = object.StudyDate;
        else
            range = object;
            return j4care.splitRangeInBlocks(j4care.extractDateTimeFromString(range), 86400000);
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
        const regex = /(.*)([+-])(\d{2}:?\d{2})/;
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
/*            let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/");
            if(pattern.exec(res.url)){
                // WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";
                console.log("onredirectOnAuthResponse",res);
                location.reload(true);
            }*/
            // resjson = res.json();
            // resjson = res;
            resjson = res
        }catch (e){
            if(typeof res === "object"){
                resjson = res;
            }else{
                resjson = [];
            }
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

/*    download(url){
        this.httpJ4car.refreshToken().subscribe((res)=>{
            let token;
            let a = document.createElement('a');

            if(res.length && res.length > 0){
                this.httpJ4car.resetAuthenticationInfo(res);
                token = res.token;
            }else{
                token = this.mainservice.global.authentication.token;
            }
            this.header.append('Authorization', `Bearer ${token}`);
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
        });
    }*/
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
    static objToUrlParams(filter){
        try{
            let filterMaped = Object.keys(filter).map((key) => {
                if (filter[key] || filter[key] === false || filter[key] === 0){
                    return key + '=' + filter[key];
                }
            });
            let filterCleared = _.compact(filterMaped);
            return filterCleared.join('&');
        }catch (e) {
            return "";
        }
    }
    static param(filter){
        let paramString = j4care.objToUrlParams(filter);
        return paramString ? '?' + paramString : '';
    }
    get(url: string): Observable<any> {
        return new Observable((observer: Subscriber<any>) => {
            let objectUrl: string = null;
            this.$httpClient
                .get(url, {
                    headers:this.header,
                    responseType: "blob"
                })
                .subscribe(m => {
                    objectUrl = URL.createObjectURL(m);
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
        firstDate.setMonth(firstDate.getMonth()-1);
        firstDate.setDate(firstDate.getDate()+1);
        return this.convertToDatePareString(firstDate,new Date());
    }
    static convertToDatePareString(firstDate,secondDate):string{
        if(j4care.isSet(firstDate) && firstDate != "" && j4care.isSet(secondDate) && secondDate != ""){
            let firstDateConverted = new Date(firstDate);
            let secondDateConverted = new Date(secondDate);

            let firstDateString = `${firstDateConverted.getFullYear()}${(this.addZero(firstDateConverted.getMonth()+1))}${this.addZero(firstDateConverted.getDate())}`;
            if(new Date(firstDate).getTime() == new Date(secondDate).getTime()){
                return firstDateString;
            }else{
                if(new Date(firstDate).getTime() > new Date(secondDate).getTime()){
                    return firstDateString;
                }else{
                    let secondDateString = `${secondDateConverted.getFullYear()}${this.addZero(secondDateConverted.getMonth()+1)}${this.addZero(secondDateConverted.getDate())}`;
                    return firstDateString === secondDateString ? firstDateString : `${firstDateString}-${secondDateString}`;
                }
            }
        }
        return undefined;
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
    static calculateWidthOfTable(table:(TableSchemaElement[]|any)){
        let sum = 0;
        let pxWidths = 0;
        let check = 0;
        table.forEach((m)=>{
            if(m){
                if(_.hasIn(m,'pxWidth') && m.pxWidth){
                    pxWidths += m.pxWidth;
                }else{
                    sum += m.widthWeight;
                }
            }
        });
        table.forEach((m)=>{
            if(m){
                let procentualPart = (m.widthWeight * 100)/sum;
                if(pxWidths > 0){
                    if(_.hasIn(m, "pxWidth") && m.pxWidth){
                        m.calculatedWidth = `${m.pxWidth}px`;
                    }else{
                        let pxPart = (procentualPart * 0.01 * pxWidths);
                        m.calculatedWidth = `calc(${procentualPart}% - ${pxPart}px)`;
                        check += pxPart;
                    }
                }else{
                    m.calculatedWidth =  procentualPart + "%";
                }
            }
        });
        return table;
    };


    static valuesOf(attr) {
        return attr && attr.Value;
    }
    static valueOf(attr) {
        return attr && attr.Value && attr.Value[0];
    }

    static round(number:any, decimal?:number, asNumber?:boolean){
        decimal = decimal || 2;
        try{
            if(number && number != ""){
                if(typeof number === "number"){
                    if(asNumber)
                        return parseFloat(number.toFixed(decimal));
                    else
                        return number.toFixed(decimal);
                }else{
                    return _.round(number,decimal);
                }
            }
            return number;
        }catch (e) {
            this.log("Error on cutting the floating points, decimal()",e);
            return number;
        }
    }

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
    * Input
    * date:Date - javascript date
    * output:
    * timezone suffix like '+0200'
    * */
    static getTimezoneOffset(date:Date) {
        function z(n){return (n<10? '0' : '') + n}
        var offset = date.getTimezoneOffset();
        var sign = offset < 0? '+' : '-';
        offset = Math.abs(offset);
        return sign + z(offset/60 | 0) + z(offset%60);
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
    static formatDate(date:Date, format:string, appendTimezoneOffset?:boolean):string{
        try{
            format = format || 'yyyyMMdd';
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
            }) + (appendTimezoneOffset ? j4care.getTimezoneOffset(date) : '');
        }catch (e) {
            this.log(`Error on formatting date, date=${date}, format=${format}`,e);
            return "";
        }
    }
    /*
    * Input:
    * range:string - javascript date
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
    static formatRangeString(range:string, format?:string):string{
        let localFormatRange = "yyyy-MM-dd HH:mm:ss";
        let singleFormat = "yyyy-MM-dd";
        let rangeObject:RangeObject = this.extractDateTimeFromString(range);
        if(rangeObject){
            if(rangeObject.mode === "range"){
                return `${this.formatDate(rangeObject.firstDateTime.dateObject,format || localFormatRange)} - ${this.formatDate(rangeObject.secondDateTime.dateObject,format || localFormatRange)}`;
            }
            if(rangeObject.mode === "single"){
                return `${this.formatDate(rangeObject.firstDateTime.dateObject,format || singleFormat)}`;
            }
        }
        return range;
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
    * create new Date javascript object while ignoring zone information in the date-time string
    * */
    static newDate(dateString:string):Date{
        try{
            return new Date(this.splitTimeAndTimezone(dateString).time);
        }catch (e) {
            return new Date(dateString);
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


    // getDevices = ()=>this.httpJ4car.get('../devices').map(res => j4care.redirectOnAuthResponse(res));

    openDialog(parameters, width?, height?){
        this.dialogRef = this.dialog.open(ConfirmComponent, {
            height: height || 'auto',
            width: width || '500px'
        });
        this.dialogRef.componentInstance.parameters = parameters;
        return this.dialogRef.afterClosed();
    };
    static log(txt:string, e?:any){
        console.groupCollapsed(txt);
        console.trace();
        if(e)
            console.error(e);
        console.groupEnd();
    }

    static getDateFromString(dateString:string):Date|string{
        let date:RangeObject = j4care.extractDateTimeFromString(dateString);
        if(date.mode === "single" || date.mode === "rightOpen"){
            return date.firstDateTime.dateObject;
        }
        if(date.mode === "range"){
            return new Date((date.firstDateTime.dateObject.getTime() + (date.secondDateTime.dateObject.getTime() - date.firstDateTime.dateObject.getTime()) / 2))
        }

        if(date.mode === "leftOpen"){
            return date.secondDateTime.dateObject
        }
        return dateString;
    }
    static convertFilterValueToRegex(value){
        return value ? `^${value.replace(/(\*|\?)/g,(match, p1)=>{
            if(p1 === "*"){
                return ".*";
            }
            if(p1 === "?")
                return ".";
        })}$`: '';
    }


    /*
    * Extending Array.join function so you can add to the last element a different join string
    * example: ["test1","test2","test3"] => "test1, test2 and test3" by calling join(["test1","test2","test3"],', ', " and ")
    * */
    static join(array:string[],joinString:string, lastJoinString?:string){
        try{
            if(array.length > 1){
                if(lastJoinString){
                    return `${array.slice(0,-1).join(joinString)}${lastJoinString}${array.slice(-1)}`;
                }else{
                    return array.join(joinString);
                }
            }else{
                return array.toString();
            }
        }catch(e){
            this.log("Error on join",e);
            return "";
        }
    }

    /*
    * get DicomNetworkConnection from reference
    * input:reference:string ("/dicomNetworkConnection/1"), dicomNetworkConnections[] (dicomNetworkConnections of a device)
    * return one dicomNetworkConnection
    * */
    static getConnectionFromReference(reference:string, connections:DicomNetworkConnection[]):(DicomNetworkConnection|string){
        try{
            const regex = /\w+\/(\d*)/;
            let match;
            if(reference && connections && (match = regex.exec(reference)) !== null){
                return connections[match[1]];
            }
            return reference;
        }catch (e) {
            this.log("Something went wrong on getting the connection from references",e);
            return reference;
        }
    }

    /*
    * Return the whole url from passed DcmWebApp
    * */
    static getUrlFromDcmWebApplication(dcmWebApp:DcmWebApp):string{
        try{
            return `${this.getBaseUrlFromDicomNetworkConnection(dcmWebApp.dicomNetworkConnectionReference || dcmWebApp.dicomNetworkConnection) || ''}${dcmWebApp.dcmWebServicePath}`;
        }catch (e) {
            this.log("Error on getting Url from DcmWebApplication",e);
        }
    }

    /*
    *Select one connection from the array of dicomnetowrkconnections and generate base url from that (http://localhost:8080)
    * */
    static getBaseUrlFromDicomNetworkConnection(conns:DicomNetworkConnection[]){
        try{
            let selectedConnection:DicomNetworkConnection;
            let filteredConnections:DicomNetworkConnection[];

            //Get only connections with the protocol HTTP
            filteredConnections = conns.filter(conn=>{
                return this.getHTTPProtocolFromDicomNetworkConnection(conn) != '';
            });
            //If there are more than 1 than check if there is one with https protocol and return the first what you find.
            if(filteredConnections.length > 1){
                selectedConnection = filteredConnections.filter(conn=>{
                    return this.getHTTPProtocolFromDicomNetworkConnection(conn) === "https";
                })[0];
            }
            selectedConnection = selectedConnection || filteredConnections[0];
            if(selectedConnection){
                return `${this.getHTTPProtocolFromDicomNetworkConnection(selectedConnection)}://${selectedConnection.dicomHostname}:${selectedConnection.dicomPort}`;
            }else{
                return window.location.origin;
            }
        }catch (e) {
            this.log("Something went wrong on getting base url from a dicom network connections",e);
            return window.location.origin;
        }
    }

    /*
    * get Url from Dicom network Connection
    * */
    static getUrlFromDicomNetworkConnection(conns:DicomNetworkConnection){
        try{
            return `${this.getHTTPProtocolFromDicomNetworkConnection(conns)||'http'}://${conns.dicomHostname}:${conns.dicomPort}`;
        }catch (e) {
            return "";
        }
    }

    /*
    * If the passed connection has the protocol HTTP then return http or https otherwise return ''.
    * */
    static getHTTPProtocolFromDicomNetworkConnection(conn:DicomNetworkConnection):string{
        try{
            let pathToConn = '';
            if(_.hasIn(conn, "dcmNetworkConnection.dicomHostname")){
                pathToConn = "dcmNetworkConnection.";
            }
            if((_.hasIn(conn,`${pathToConn}dcmProtocol`) && _.get(conn,`${pathToConn}dcmProtocol`) === "HTTP") || !_.hasIn(conn,`${pathToConn}dcmProtocol`)){
                    if(_.hasIn(conn, `${pathToConn}dicomTLSCipherSuite`) && (<any[]>_.get(conn, `${pathToConn}dicomTLSCipherSuite`)).length > 0){
                        return "https";
                    }else{
                        return "http";
                    }
            }
            return '';
        }catch (e) {
            this.log("Something went wrong on getting the protocol from a connection",e);
        }
    }

    /*
    * get string with prefix and suffix if exist otherwise return empty string
    * */
    static meyGetString(object, path:string, prefix:string = "", suffix:string = "",showPrefixSuffixEvenIfEmpty?:boolean){
        if(_.hasIn(object, path)){
            return `${prefix}${_.get(object,path)}${suffix}`;
        }
        if(showPrefixSuffixEvenIfEmpty){
            return `${prefix}${suffix}`;
        }
        return "";
    }

    static changed(object, base, ignoreEmpty?:boolean) {
        function changes(object, base) {
            return _.transform(object, function(result, value, key) {
                if (!base || !_.isEqual(value, base[key])) {
                    if(ignoreEmpty){
                        if(_.isObject(value) && base && key && _.isObject(base[key])){
                            result[key] = changes(value, base[key])
                        }else{
                           if(!(_.isArray(value) && value.length === 0) && !(_.isObject(value) && Object.keys(value).length === 0) && value != undefined && value != "" && value != [""]){
                                result[key] = value;
                           }
                        }
                    }else{
                        result[key] = (_.isObject(value) && _.isObject(base[key])) ? changes(value, base[key]) : value;
                    }
                }
            });
        }
        return changes(object, base);
    }

    static diffObjects(object, base, ignoreEmpty?:boolean, splited?:boolean){
        if(splited){
            const first = j4care.changed(object,base,ignoreEmpty);
            const second = j4care.changed(base, object, ignoreEmpty);
            return {
                first: first,
                second: second,
                diff:{...first,...second}
            }
        }else{
            return _.mergeWith(j4care.changed(object,base,ignoreEmpty), j4care.changed(base, object, ignoreEmpty));
        }
    }

    static generateOIDFromUUID(){
        let guid = uuid();                            //Generate UUID
        let guidBytes = `0${guid.replace(/-/g, "")}`; //add prefix 0 and remove `-`
        return `2.25.${bigInt(guidBytes,16).toString()}`;       //Output the previous parsed integer as string by adding `2.25.` as prefix
    }
}
