import { Injectable } from '@angular/core';
import {Http, Headers} from '@angular/http';
import {DatePipe} from '@angular/common';
import * as _ from 'lodash';
import {Observable} from 'rxjs';
import {WindowRefService} from "../helpers/window-ref.service";
import {AppService} from "../app.service";
import {J4careHttpService} from "../helpers/j4care-http.service";
import {j4care} from "../helpers/j4care.service";
declare var DCM4CHE: any;
declare var window: any;

@Injectable()
export class StudiesService {
    private _patientIod: any;
    private _mwlIod: any;
    private _studyIod;
    integerVr = ['DS', 'FL', 'FD', 'IS', 'SL', 'SS', 'UL', 'US'];

    constructor(
        public $http: J4careHttpService,
        public datePipe: DatePipe,
        public mainservice:AppService,
    ) { }

    get studyIod() {
        return this._studyIod;
    }

    set studyIod(value) {
        this._studyIod = value;
    }

    get patientIod(): any {
        return this._patientIod;
    }
    get mwlIod(): any {
        return this._mwlIod;
    }

    set patientIod(value: any) {
        this._patientIod = value;
    }
    set mwlIod(value: any) {
        this._mwlIod = value;
    }


    getTodayDate() {
        let todayDate = new Date();
        return this.datePipe.transform(todayDate, 'yyyyMMdd');
    }
    getWindow(){
        return window;
    }
    getAes(user, aes){
        if (!user || !user.user || user.roles.length === 0){
            return aes;
        }else{
            let endAes = [];
            let valid;
            if(aes){
                aes.forEach((ae, i) => {
                    valid = false;
                    user.roles.forEach((user, i) => {
                        if(ae.dcmAcceptedUserRole){
                            ae.dcmAcceptedUserRole.forEach((aet, j) => {
                                if (user === aet){
                                    valid = true;
                                }
                            });
                        }
                    });
                    if (valid){
                        endAes.push(ae);
                    }
                });
                if(endAes.length === 0){
                    this.mainservice.setMessage({
                        'title': "Error",
                        'text': "Accepted User Roles in the AETs are missing, add at least one role per AET (ArchiveDevice -> AET -> Archive Network AE -> Accepted User Role)",
                        'status': "Error"
                    });
                }
                return endAes;
            }else{
                this.mainservice.setMessage({
                    'title': "Error",
                    'text': "No AETs found, please use the device-configurator or the LDAP-Browser to configure one!",
                    'status': "Error"
                });
            }
        }
    }
    getMsgFromResponse(res,defaultMsg = null){
        let msg;
        let endMsg = '';
        try{
            msg = res.json();
            if(_.hasIn(msg,"completed")){
                endMsg = `Completed: ${msg.completed}<br>`;
            }
            if(_.hasIn(msg,"warning")){
                endMsg = endMsg + `Warning: ${msg.warning}<br>`;
            }
            if(_.hasIn(msg,"failed")){
                endMsg = endMsg + `Failed: ${msg.failed}<br>`;
            }
            if(_.hasIn(msg,"errorMessage")){
                endMsg = endMsg + `${msg.errorMessage}<br>`;
            }
            if(_.hasIn(msg,"error")){
                endMsg = endMsg + `${msg.error}<br>`;
            }
            if(endMsg === ""){
                endMsg = defaultMsg;
            }
        }catch (e){
            if(defaultMsg){
                endMsg = defaultMsg;
            }else{
                endMsg = res.statusText;
            }
        }
        return endMsg;
    }
    _config = function(params) {
        // return '?' + decodeURIComponent(jQuery.param(params,true));
        return '?' + this.mainservice.param(params);
    };

    replaceKeyInJson(object, key, key2){
        let $this = this;
        _.forEach(object, function(m, k){
            if (m[key]){
                object[k][key2] = [object[k][key]];
                delete object[k][key];
            }
            if (m.vr && m.vr != 'SQ' && !m.Value){
                if (m.vr === 'PN'){
                    object[k]['Value'] = object[k]['Value'] || [{Alphabetic: ''}];
                    object[k]['Value'] = [{Alphabetic: ''}];
                }else{
                    object[k]['Value'] = [''];
                }
            }
            if ((Object.prototype.toString.call(m) === '[object Array]') || (object[k] !== null && typeof(object[k]) == 'object')) {
                $this.replaceKeyInJson(m, key, key2);
            }
        });
        return object;
    };
    initEmptyValue(object){
        // console.log(".", object);
        let $this = this;
        _.forEach(object, function(m, k){
            console.log('m', m);
            if (m && m.vr && m.vr === 'PN' && m.vr != 'SQ' && (!m.Value || m.Value[0] === null)){
                console.log('in pnvalue=', m);
                object[k]['Value'] = [{
                    Alphabetic: ''
                }];
            }
            if (m && m.vr && m.vr != 'SQ' && !m.Value){
                object[k]['Value'] = [''];
            }
            if (m && (_.isArray(m) || (m && _.isObject(m)))) {
                console.log('beforecall', m);
                $this.initEmptyValue(m);
            }
        });
        return object;
    };

    setExpiredDate(aet,studyUID, expiredDate){
        let url = `../aets/${aet}/rs/studies/${studyUID}/expire/${expiredDate}`
        return this.$http.put(url,{}).map(res => j4care.redirectOnAuthResponse(res));
    }

    queryPatients = function(url, params) {
        return this.$http.get(
            url + '/patients' + this._config(params),
            {
                headers:  new Headers({'Accept': 'application/dicom+json'})
            }).map(res => j4care.redirectOnAuthResponse(res));
    };
    queryDiffs = function(url, params) {
        params["missing"] = params["missing"] || true;
        return this.$http.get(
            url + this._config(params),
            {
                headers:  new Headers({'Accept': 'application/dicom+json'})
            }
        ).map(res => j4care.redirectOnAuthResponse(res));
    };

    getCount(url,mode,params) {
        return this.$http.get(
            `${url}/${mode}/count${this._config(params)}`,
            {
                headers:  new Headers({'Accept': 'application/json'})
            }
        ).map(res => j4care.redirectOnAuthResponse(res));
    };
    getSize(url,params) {
        return this.$http.get(
            `${url}/studies/size${this._config(params)}`,
            {
                headers:  new Headers({'Accept': 'application/json'})
            }
        ).map(res => j4care.redirectOnAuthResponse(res));
    };

    queryStudies = function(url, params) {
        return this.$http.get(
            url + '/studies' + this._config(params),
            {
                headers:  new Headers({'Accept': 'application/dicom+json'})
            }
        ).map(res => j4care.redirectOnAuthResponse(res));
    };
    otherAttributesButIDWasChanged(originalAttr,changedAttr){
        let firstObject = _.cloneDeep(originalAttr);
        let secondObject = _.cloneDeep(changedAttr);
        if (_.hasIn(firstObject, '["00100020"].Value[0]')){
            delete firstObject["00100020"];
        }
        if (_.hasIn(firstObject, '["00100021"].Value[0]')){
            delete firstObject["00100021"];
        }
        if (_.hasIn(firstObject, '["00100024"].Value[0]["00400032"].Value[0]')){
            delete firstObject['00100024'].Value[0]['00400032'];
        }
        if (_.hasIn(firstObject, '["00100024"].Value[0]["00400033"].Value[0]')){
            delete firstObject['00100024'].Value[0]['00400033'];
        }
        if (_.hasIn(secondObject, '["00100020"].Value[0]')){
            delete secondObject["00100020"];
        }
        if (_.hasIn(secondObject, '["00100021"].Value[0]')){
            delete secondObject["00100021"];
        }
        if (_.hasIn(secondObject, '["00100024"].Value[0]["00400032"].Value[0]')){
            delete secondObject['00100024'].Value[0]['00400032'];
        }
        if (_.hasIn(secondObject, '["00100024"].Value[0]["00400033"].Value[0]')){
            delete secondObject['00100024'].Value[0]['00400033'];
        }
        return !_.isEqual(firstObject, secondObject);
    }
    appendPatientIdTo(patient, obj){
        if (_.hasIn(patient, '00100020')){
            obj['00100020'] = obj['00100020'] || {};
            obj['00100020'] = patient['00100020'];
        }
        if (_.hasIn(patient, '00100021')){
            obj['00100021'] = obj['00100021'] || {};
            obj['00100021'] = patient['00100021'];
        }
        if (_.hasIn(patient, '00100024')){
            obj['00100024'] = obj['00100024'] || {};
            obj['00100024'] = patient['00100024'];
        }
    }
    queryMwl = function(url, params) {
        return this.$http.get(
            url + '/mwlitems' + this._config(params),
            {
                headers:  new Headers({'Accept': 'application/dicom+json'})
            }
        ).map(res => {let resjson; try{
            let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/");
            if(pattern.exec(res.url)){
                WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";
            }
            resjson = res.json(); }catch (e){resjson = {}; } return resjson; });
    };

    querySeries = function(url, studyIUID, params) {
        return this.$http.get(
            url + '/studies/' + studyIUID + '/series' + this._config(params),
            {
                headers:  new Headers({'Accept': 'application/dicom+json'})
            }
        ).map(res => j4care.redirectOnAuthResponse(res));
    };

    queryInstances = function(url, studyIUID, seriesIUID, params) {
        return this.$http.get(url
            + '/studies/' + studyIUID
            + '/series/' + seriesIUID
            + '/instances' +
            this._config(params),
            {
                headers:  new Headers({'Accept': 'application/dicom+json'})
            }
        ).map(res => j4care.redirectOnAuthResponse(res));
    };

    getPatientIod(){
        console.log('_patientIod', this._patientIod);
        if (this._patientIod) {
            return Observable.of(this._patientIod);
        } else {
            return this.$http.get('assets/iod/patient.iod.json').map(res => j4care.redirectOnAuthResponse(res));
        }
    };
    getStudyIod(){
        console.log('_patientIod', this._studyIod);
        if (this._studyIod) {
            return Observable.of(this._studyIod);
        } else {
            return this.$http.get('assets/iod/study.iod.json').map(res => j4care.redirectOnAuthResponse(res));
        }
    };
    getMwlIod(){
        console.log('_mwlIod', this._mwlIod);
        if (this._mwlIod) {
            return Observable.of(this._mwlIod);
        } else {
            return this.$http.get(
                'assets/iod/mwl.iod.json'
            ).map(res => j4care.redirectOnAuthResponse(res));
        }
    };

    getArrayFromIod(res){
        let dropdown = [];
        _.forEach(res, function(m, i){
            if (i === '00400100'){
                _.forEach(m.items, function(l, j){
                    dropdown.push({
                        'code': '00400100:' + j,
                        'codeComma': '>' + j.slice(0, 4) + ',' + j.slice(4),
                        'name': DCM4CHE.elementName.forTag(j)
                    });
                });
            }else{
                dropdown.push({
                    'code': i,
                    'codeComma': i.slice(0, 4) + ',' + i.slice(4),
                    'name': DCM4CHE.elementName.forTag(i)
                });
            }
        });
        return dropdown;
    };

    clearPatientObject(object){
        let $this = this;
        _.forEach(object, function(m, i){
            if (typeof(m) === 'object' && i != 'vr'){
                $this.clearPatientObject(m);
            }else{
                let check = typeof(i) === 'number' || i === 'vr' || i === 'Value' || i === 'Alphabetic' || i === 'Ideographic' || i === 'Phonetic' || i === 'items';
                if (!check){
                    delete object[i];
                }
            }
        });
    };
    convertStringToNumber(object){
        let $this = this;
        _.forEach(object, function(m, i){
            if (typeof(m) === 'object' && i != 'vr'){
                $this.convertStringToNumber(m);
            }else{
                if (i === 'vr'){
                    if (($this.integerVr.indexOf(object.vr) > -1 && object.Value && object.Value.length > 0)){
                        if (object.Value.length > 1){
                            _.forEach(object.Value, (k, j) => {
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
    clearSelection(patients){
        _.forEach(patients, function(patient, i){
            patient.selected = false;
            if (patient.studies){
                _.forEach(patient.studies, function(study, j){
                    study.selected = false;
                    if (study.series){
                        _.forEach(study.series, function(serie, j){
                            serie.selected = false;
                            if (serie.instances){
                                _.forEach(serie.instances, function(instance, j){
                                    instance.selected = false;
                                });
                            }
                        });
                    }
                });
            }
        });
    };
    MergeRecursive(clipboard, selected) {
        _.forEach(selected, function(study, studykey){
            clipboard[studykey] = clipboard[studykey] || selected[studykey];
            if (clipboard[studykey]){
                if (study['ReferencedSeriesSequence']){
                    clipboard[studykey]['ReferencedSeriesSequence'] = clipboard[studykey]['ReferencedSeriesSequence'] || study['ReferencedSeriesSequence'];
                    _.forEach(study['ReferencedSeriesSequence'] , function(selSeries, selSeriesKey){

                        let SeriesInstanceUIDInArray = false;
                        _.forEach(clipboard[studykey]['ReferencedSeriesSequence'] , function(clipSeries, clipSeriesKey){
                            if (clipSeries.SeriesInstanceUID === selSeries.SeriesInstanceUID){
                                SeriesInstanceUIDInArray = true;
                                if (selSeries.ReferencedSOPSequence){
                                    if (clipSeries.ReferencedSOPSequence){
                                        _.forEach(selSeries.ReferencedSOPSequence , function(selInstance, selSeriesKey){
                                            let sopClassInstanceUIDInArray = false;
                                            _.forEach(clipSeries.ReferencedSOPSequence , function(clipInstance, clipInstanceKey){
                                                if (clipInstance.ReferencedSOPClassUID && clipInstance.ReferencedSOPClassUID === selInstance.ReferencedSOPClassUID && clipInstance.ReferencedSOPInstanceUID && clipInstance.ReferencedSOPInstanceUID === selInstance.ReferencedSOPInstanceUID){
                                                    sopClassInstanceUIDInArray = true;
                                                }
                                            });
                                            if (!sopClassInstanceUIDInArray){
                                                clipSeries.ReferencedSOPSequence.push(selInstance);
                                            }
                                        });
                                    }
                                }
                            }
                        });
                        if (!SeriesInstanceUIDInArray){
                            clipboard[studykey]['ReferencedSeriesSequence'].push(selSeries);
                        }
                    });
                }
            }
        });
    }
    /*
    * Removing the element from clipboard, called from delete button on the clipboard or on copy-move dialog
    * @modus: what kind of object is the object that should be removed
    * @keys: the indexes where the object is in clipboard
    * @clipboard: the clipboard object
    * */
    removeClipboardElement(modus, keys, clipboard){
        switch (modus) {
            case 'patient':
                delete clipboard['patients'][keys.patientkey];
                delete clipboard['otherObjects'][keys.patientkey];
                break;
            case 'study':
                delete clipboard['otherObjects'][keys.studykey];
                break;
            case 'serie':
                delete clipboard['otherObjects'][keys.studykey].ReferencedSeriesSequence[keys.serieskey];
                break;
            case 'instance':
                clipboard['otherObjects'][keys.studykey].ReferencedSeriesSequence[keys.serieskey].ReferencedSOPSequence.splice(keys.instancekey, 1);
                break;
            default:
        }
        /*
        * Check if there are any patient in the clipboard anymore
        * */
        let haspatient = false;
        _.forEach(clipboard.otherObjects, (m, i) => {
            if (i != '' && (!m || _.size(m) === 0)){
                haspatient = true;
            }
        });
clipboard.hasPatient = haspatient || (_.size(clipboard.patient) > 0);
    }
    /*
    * return patientid - combination of patient id, issuer
    * */
    getPatientId(patient){
        console.log('patient', patient);
        let obj;
        if (_.hasIn(patient, '[0]')){
            obj = patient[0];
        }else{
            obj = patient;
        }
        let patientId = '';
        if(obj.PatientID || (_.hasIn(obj, '["00100020"].Value[0]') && obj["00100020"].Value[0] != '')){
            if (obj.PatientID){
                patientId = obj.PatientID;
            }
            if (obj.IssuerOfPatientID){
                patientId += '^^^' + obj.IssuerOfPatientID;
            }
            if(_.hasIn(obj,'IssuerOfPatientIDQualifiers.UniversalEntityID')){
                patientId += '&' + obj.IssuerOfPatientIDQualifiers.UniversalEntityID;
            }
            if(_.hasIn(obj,'IssuerOfPatientIDQualifiers.UniversalEntityIDType')){
                patientId += '&' + obj.IssuerOfPatientIDQualifiers.UniversalEntityIDType;
            }
            if (_.hasIn(obj, '["00100020"].Value[0]')){
                patientId += obj["00100020"].Value[0];
            }
            if (_.hasIn(obj, '["00100021"].Value[0]')){
                patientId += '^^^' + obj["00100021"].Value[0];
            }
            if (_.hasIn(obj, '["00100024"].Value[0]["00400032"].Value[0]')){
                patientId += '&' + obj['00100024'].Value[0]['00400032'].Value[0];
            }
            if (_.hasIn(obj, '["00100024"].Value[0]["00400033"].Value[0]')){
                patientId += '&' + obj['00100024'].Value[0]['00400033'].Value[0];
            }
            return patientId;
        }else{
            return undefined;
        }
    }
    preparePatientObjectForExternalPatiendIdChange(unchangedPatientObject, changedPatientObject){
        let endObject = _.cloneDeep(unchangedPatientObject);
        if(_.hasIn(changedPatientObject,'["00100020"].Value[0]')){
            _.set(endObject,'["00100020"].Value[0]', changedPatientObject["00100020"].Value[0]);
        }
        if(_.hasIn(changedPatientObject,'["00100021"].Value[0]')){
            _.set(endObject,'["00100021"].Value[0]', changedPatientObject["00100021"].Value[0]);
        }
        if(_.hasIn(changedPatientObject,'["00100024"].Value[0]["00400032"].Value[0]')){
            _.set(endObject,'["00100024"].Value[0]["00400032"].Value[0]', changedPatientObject["00100024"].Value[0]["00400032"].Value[0]);
        }
        if(_.hasIn(changedPatientObject,'["00100024"].Value[0]["00400033"].Value[0]')){
            _.set(endObject,'["00100024"].Value[0]["00400033"].Value[0]', changedPatientObject["00100024"].Value[0]["00400033"].Value[0]);
        }
        return endObject;
    }
    changeExternalPatientID(patient, internalAppName, externalAppName, oldPatientID){
        let url = `../hl7apps/${internalAppName}/hl7/${externalAppName}/patients/${oldPatientID}/changeid`;
        let headers = new Headers({ 'Content-Type': 'application/dicom+json' });
        let object;
        if(_.hasIn(patient,"attrs")){
            object = patient.attrs;
        }else{
            object = patient;
        }
        return {
            save:this.$http.post(
                url,
                object,
                {headers: headers}
            ).map(res => j4care.redirectOnAuthResponse(res))
            ,
            successMsg:'Patient ID changed successfully!'
        };
    }
    getHl7ApplicationNameFormAETtitle(aet, aes){
        for(let i = 0; i < aes.length; i++){
            if(aet === aes[i].dicomAETitle){
                return aes[i].hl7ApplicationName;
            }
        };
    }
    modifyPatient(patient, iod, oldPatientID, aet,internalAppName, externalAppName,  modifyMode, externalInternalAetMode){
        let url;
        if(externalInternalAetMode === 'external'){
            if(!internalAppName || !externalAppName){
                this.mainservice.setMessage({
                    'title': 'Error',
                    'text': 'Hl7Applications not found!',
                    'status': 'error'
                });
                return null;
            }else{
                url = `../hl7apps/${internalAppName}/hl7/${externalAppName}/patients`;
            }
        }else{
            url = `../aets/${aet}/rs/patients/`;
        }
        let headers = new Headers({ 'Content-Type': 'application/dicom+json' });
        this.clearPatientObject(patient.attrs);
        this.convertStringToNumber(patient.attrs);
        //Check if the patient.attrs object have PatientID
        if (_.hasIn(patient,'attrs[00100020].Value[0]') && patient.attrs['00100020'].Value[0] != ''){
            //Delete attrs that don't have values
            _.forEach(patient.attrs, function(m, i){
                if (iod && iod[i] && iod[i].vr != 'SQ' && m.Value && m.Value.length === 1 && m.Value[0] === ''){
                    delete patient.attrs[i];
                }
            });
            if (modifyMode === 'create' && _.hasIn(patient, 'attrs.00100021') && patient.attrs['00100021'] != undefined) {
                oldPatientID = this.getPatientId(patient.attrs);
            }
            if(externalInternalAetMode === 'internal'){
                url = url + (oldPatientID || patient.attrs['00100020'].Value[0]);
            }
            if((externalInternalAetMode === 'external' && modifyMode === 'edit') || externalInternalAetMode === 'internal'){
                    return {
                        save:this.$http.put(
                                url,
                                patient.attrs,
                                {headers: headers}
                            ).map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})
                        ,
                        successMsg:'Patient saved successfully!'
                    };
            }else{
               if(externalInternalAetMode === 'external' && modifyMode === 'create'){
                   return {
                       save:this.$http.post(
                           url,
                           patient.attrs,
                           {headers: headers}
                       ).map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})
                       ,
                       successMsg:'Patient created successfully!'
                   };
               }else{
                   this.mainservice.setMessage( {
                       'title': 'Error',
                       'text': 'Something went wrong, reload the page and try again!',
                       'status': 'error'
                   });
                   return null;
               }
            }
        }else{
            if (modifyMode === 'create'){
                return {
                    save:this.$http.post(
                        url,
                        patient.attrs,
                        {headers: headers}
                    ).map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})
                    ,
                        successMsg:'Patient created successfully!'
                };
            }else{
                this.mainservice.setMessage({
                    'title': 'Error',
                    'text': 'Patient ID is required!',
                    'status': 'error'
                });
                return null;
            }
        }
    }
    isTargetInClipboard(target, clipboard){
        let contains = false;
        _.forEach(clipboard.otherObjects, (m, i) => {
            if (_.hasIn(target, ['otherObjects', i]) && _.isEqual(m, target.otherObjects[i])){
               contains = true;
            }
        });
        _.forEach(clipboard.patients, (m, i) => {
            if (_.hasIn(target, ['patients', i]) &&  _.isEqual(m, target.patients[i])){
               contains = true;
            }
        });
        return contains;
    }

    rsURL(externalInternalAetMode,aet, aetTitle, externalInternalAetModelAETitle) {
        let url;
        if(externalInternalAetMode === "external"){
            url = `../aets/${aetTitle}/dimse/${externalInternalAetModelAETitle}`;
        }
        if(externalInternalAetMode === "internal"){
            url = `../aets/${aet}/rs`;
        }
        return url;
    }

}
