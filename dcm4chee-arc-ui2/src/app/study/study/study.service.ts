import { Injectable } from '@angular/core';
import {AccessLocation, DicomLevel, DicomMode, DicomResponseType, FilterSchema, SelectDropdown} from "../../interfaces";
import {Globalvar} from "../../constants/globalvar";
import {Aet} from "../../models/aet";
import {AeListService} from "../../configuration/ae-list/ae-list.service";
import {j4care} from "../../helpers/j4care.service";
import {Headers, Http, RequestOptions} from "@angular/http";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {Observable} from "rxjs/Observable";
import * as _ from 'lodash'
import {GSPSQueryParams} from "../../models/gsps-query-params";
import {StorageSystemsService} from "../../monitoring/storage-systems/storage-systems.service";
import {DevicesService} from "../../configuration/devices/devices.service";
import {DcmWebApp} from "../../models/dcm-web-app";
import {HttpClient, HttpHeaders} from "@angular/common/http";
import {
    DicomTableSchema,
    DynamicPipe,
    StudySchemaOptions
} from "../../helpers/dicom-studies-table/dicom-studies-table.interfaces";
import {ContentDescriptionPipe} from "../../pipes/content-description.pipe";
import {TableSchemaElement} from "../../models/dicom-table-schema-element";
import {KeycloakService} from "../../helpers/keycloak-service/keycloak.service";
import {WebAppsListService} from "../../configuration/web-apps-list/web-apps-list.service";
import {RetrieveMonitoringService} from "../../monitoring/external-retrieve/retrieve-monitoring.service";
import {StudyWebService} from "./study-web-service.model";
declare var DCM4CHE: any;

@Injectable()
export class StudyService {

    private _patientIod;
    private _mwlIod;
    private _studyIod;
    integerVr = ['DS', 'FL', 'FD', 'IS', 'SL', 'SS', 'UL', 'US'];

    dicomHeader = new HttpHeaders({'Content-Type': 'application/dicom+json'});
    jsonHeader = new HttpHeaders({'Content-Type': 'application/json'});
    constructor(
      private aeListService:AeListService,
      private $http:J4careHttpService,
      private storageSystems:StorageSystemsService,
      private devicesService:DevicesService,
      private webAppListService:WebAppsListService,
      private retrieveMonitoringService:RetrieveMonitoringService
    ) { }

    get patientIod() {
        return this._patientIod;
    }

    set patientIod(value) {
        this._patientIod = value;
    }

    get mwlIod() {
        return this._mwlIod;
    }

    set mwlIod(value) {
        this._mwlIod = value;
    }

    get studyIod() {
        return this._studyIod;
    }

    set studyIod(value) {
        this._studyIod = value;
    }

    getWebApps(){
        return this.webAppListService.getWebApps();
    }

    getEntrySchema(devices, aetWebService):{schema:FilterSchema, lineLength:number}{
        return {
            schema: j4care.prepareFlatFilterObject(Globalvar.STUDY_FILTER_ENTRY_SCHEMA(devices,aetWebService),1),
            lineLength: 1
        }
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
            if (_.hasIn(obj, '["00100021"].Value[0]'))
                patientId += '^^^' + obj["00100021"].Value[0];
            else{
                if(_.hasIn(obj, '["00100024"].Value[0]["00400032"].Value[0]') || _.hasIn(obj, '["00100024"].Value[0]["00400033"].Value[0]'))
                    patientId += '^^^';
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
    initEmptyValue(object){
        _.forEach(object, (m, k)=>{
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
                this.initEmptyValue(m);
            }
        });
        return object;
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
    getArrayFromIod(res){
        let dropdown = [];
        _.forEach(res, function(m, i){
            if (i === '00400100'){
                _.forEach(m.items || m.Value[0], function(l, j){
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

    getFilterSchema(tab:DicomMode, aets:Aet[], quantityText:{count:string,size:string}, filterMode:('main'| 'expand'), webApps?:DcmWebApp[]){
        let schema:FilterSchema;
        let lineLength:number = 3;
        switch(tab){
            case "patient":
                schema = Globalvar.PATIENT_FILTER_SCHEMA(aets,filterMode === "expand");
                lineLength = filterMode === "expand" ? 1:2;
                break;
            case "mwl":
                schema = Globalvar.STUDY_FILTER_SCHEMA(aets,filterMode === "expand");
                lineLength = filterMode === "expand" ? 2:3;
                break;
            case "diff":
                schema = Globalvar.STUDY_FILTER_SCHEMA(aets,filterMode === "expand");
                lineLength = filterMode === "expand" ? 2:3;
                break;
            default:
                schema = Globalvar.STUDY_FILTER_SCHEMA(aets,false).filter(filter=>{
                    return filter.filterKey != "aet";
                });
                lineLength = filterMode === "expand" ? 2:3;
        }
        if(filterMode === "main"){
            if(tab != 'diff'){
                schema.push({
                    tag:"html-select",
                    options:Globalvar.ORDERBY_NEW
                        .filter(order=>order.mode === tab)
                        .map(order=>{
                            return new SelectDropdown(order.value, order.label,order.title,order.title,order.label);
                        }),
                    filterKey:'orderby',
                    text:"Order By",
                    title:"Order By",
                    placeholder:"Order By",
                    cssClass:'study_order'

                });
                schema.push({
                    tag:"html-select",
                    options:webApps
                        .map((webApps:DcmWebApp)=>{
                            return new SelectDropdown(webApps ,webApps.dcmWebAppName,webApps.dicomDescription);
                        }),
                    filterKey:'webApp',
                    text:"Web App Service",
                    title:"Web App Service",
                    placeholder:"Web App Service",
                    cssClass:'study_order'

                });
            }
            schema.push(
            {
                tag: "button",
                id: "submit",
                text: "SUBMIT",
                description: "Query Studies"
            });
            schema.push(
                {
                    tag:"dummy"
                },
                {
                    tag: "button",
                    id: "count",
                    text: quantityText.count,
                    description: "QUERY ONLY THE COUNT"
                },{
                    tag: "button",
                    id: "size",
                    text: quantityText.size,
                    description: "QUERY ONLY THE SIZE"
                });
        }
        return {
            lineLength:lineLength,
            schema:j4care.prepareFlatFilterObject(schema,lineLength)
        }
    }


    getStudies(filterModel, dcmWebApp:DcmWebApp, responseType?:DicomResponseType):Observable<any>{
        let header:HttpHeaders;
        if(!responseType || responseType === "object"){
            header =  this.dicomHeader
        }
        let params = j4care.objToUrlParams(filterModel);
        params = params ? `?${params}`:params;

        return this.$http.get(
            `${this.getDicomURL("study", dcmWebApp, responseType)}${params || ''}`,
                header,
                false,
                dcmWebApp
            ).map(res => j4care.redirectOnAuthResponse(res));
    }

    getSeries(studyInstanceUID:string, filterModel:any, dcmWebApp:DcmWebApp, responseType?:DicomResponseType):Observable<any>{
        let header;
        if(!responseType || responseType === "object"){
            header =  this.dicomHeader
        }
        let params = j4care.objToUrlParams(filterModel);
        params = params ? `?${params}`:params;

        return this.$http.get(
            `${this.getDicomURL("study", dcmWebApp, responseType)}/${studyInstanceUID}/series${params || ''}`,
                header,
            false,
            dcmWebApp
            ).map(res => j4care.redirectOnAuthResponse(res));
    }

    testAet( url, dcmWebApp:DcmWebApp){
        return this.$http.get(
            url,//`http://test-ng:8080/dcm4chee-arc/ui2/rs/aets`,
            this.jsonHeader,
            false,
            dcmWebApp
        ).map(res => j4care.redirectOnAuthResponse(res));
    }
    getInstances(studyInstanceUID:string, seriesInstanceUID:string, filterModel:any, dcmWebApp:DcmWebApp, responseType?:DicomResponseType):Observable<any>{
        let header:HttpHeaders;
        if(!responseType || responseType === "object"){
            header =  this.dicomHeader
        }
        let params = j4care.objToUrlParams(filterModel);
        params = params ? `?${params}`:params;

        return this.$http.get(
            `${this.getDicomURL("study", dcmWebApp, responseType)}/${studyInstanceUID}/series/${seriesInstanceUID}/instances${params || ''}`,
                header,
            false,
            dcmWebApp
            ).map(res => j4care.redirectOnAuthResponse(res));
    }
    getDicomURL(mode:DicomMode, dcmWebApp:DcmWebApp, responseType?:DicomResponseType):string{
        console.log("object",dcmWebApp);
        try{
            let url = j4care.getUrlFromDcmWebApplication(dcmWebApp);
            switch (mode) {
                case "patient":
                    url += '/patients';
                    break;
                case "mwl":
                    url += '/mwlitems';
                    break;
                case "export":
                    url += '/studies/export';
                    break;
                case "study":
                    url += '/studies';
                    break;
    /*            case "diff":
                    url = this.diffUrl(callingAet, externalAet, secondExternalAet, baseUrl);
                    break;*/
                default:
                    url;
            }
            if(mode != "diff" && responseType){
               if(responseType === "count")
                url += '/count';
               if(responseType === "size")
                url += '/size';
            }
            return url;
        }catch (e) {
            j4care.log("Error on getting dicomURL in study.service.ts",e);
        }
    }

    wadoURL(webService:StudyWebService,...args: any[]): any {
        let i, url = `${j4care.getUrlFromDcmWebApplication(this.getWebAppFromWebServiceClassAndSelectedWebApp(webService,"WADO_URI",  "WADO_URI"))}?requestType=WADO`;
        for (i = 1; i < arguments.length; i++) {
            _.forEach(arguments[i], (value, key) => {
                url += '&' + key.replace(/^(_){1}(\w*)/, (match,p1,p2)=>{
                    return p2;
                }) + '=' + value;
            });
        }
        return url;
    }

    private diffUrl(callingAet:Aet,  firstExternalAet?:Aet, secondExternalAet?:Aet, baseUrl?:string){

        return `${
                baseUrl || '..'
            }/aets/${
                callingAet.dicomAETitle
            }/dimse/${
                firstExternalAet.dicomAETitle
            }/diff/${
                secondExternalAet.dicomAETitle
            }/studies`;
    }

/*    private rsURL(callingAet:Aet, accessLocation?:AccessLocation,  externalAet?:Aet, baseUrl?:string) {
        if(accessLocation === "external" && externalAet){
            return `${baseUrl || '..'}/aets/${callingAet.dicomAETitle}/dims/${externalAet.dicomAETitle}`;
        }
        return `${baseUrl || '..'}/aets/${callingAet.dicomAETitle}/rs`;
    }*/

    getAttributeFilter(entity?:string, baseUrl?:string){
        return this.$http.get(
            `${baseUrl || '..'}/attribute-filter/${entity || "Patient"}`
        )
        .map(res => j4care.redirectOnAuthResponse(res))
        .map(res => {
            if((!entity || entity === "Patient") && res.dcmTag){
                let privateAttr = [parseInt('77770010', 16), parseInt('77771010', 16), parseInt('77771011', 16)];
                res.dcmTag.push(...privateAttr);
            }
            return res;
        });
    }
    getAets = ()=> this.aeListService.getAets();

    getAes = ()=> this.aeListService.getAes();

    equalsIgnoreSpecificCharacterSet(attrs, other) {
        return Object.keys(attrs).filter(tag => tag != '00080005')
                .every(tag => _.isEqual(attrs[tag],other[tag]))
            && Object.keys(other).filter(tag => tag != '00080005')
                .every(tag => attrs[tag]);
    }
    queryPatientDemographics(patientID:string, PDQServiceID:string,url?:string){
        return this.$http.get(`${url || '..'}/pdq/${PDQServiceID}/patients/${patientID}`).map(res => j4care.redirectOnAuthResponse(res));
    }
    extractAttrs(attrs, tags, extracted) {
        for (let tag in attrs){
            if (_.indexOf(tags, tag) > -1){
                extracted[tag] = attrs[tag];
            }
        }
    }

    createGSPSQueryParams(attrs):GSPSQueryParams[] {
        let sopClass = j4care.valueOf(attrs['00080016']),
            refSeries = j4care.valuesOf(attrs['00081115']),
            queryParams:GSPSQueryParams[] = [];
        if (sopClass === '1.2.840.10008.5.1.4.1.1.11.1' && refSeries) {
            refSeries.forEach((seriesRef) => {
                j4care.valuesOf(seriesRef['00081140']).forEach((objRef) => {
                    queryParams.push(
                        new GSPSQueryParams(
                            attrs['0020000D'].Value[0],
                            seriesRef['0020000E'].Value[0],
                            objRef['00081155'].Value[0],
                            'image/jpeg',
                            j4care.valueOf(objRef['00081160']) || 1,
                            attrs['0020000E'].Value[0],
                            attrs['00080018'].Value[0]
                        )
                    );
                });
            });
        }
        return queryParams;
    }

    studyURL(attrs, webApp:DcmWebApp){
        return `${this.getDicomURL("study", webApp)}/${attrs['0020000D'].Value[0]}`;
    }
    seriesURL(attrs, webApp:DcmWebApp) {
        return this.studyURL(attrs, webApp) + '/series/' + attrs['0020000E'].Value[0];
    }
    instanceURL(attrs, webApp:DcmWebApp) {
        return this.seriesURL(attrs, webApp) + '/instances/' + attrs['00080018'].Value[0];
    }

    getURL(attrs, webApp:DcmWebApp, dicomLevel:DicomLevel){
        if(dicomLevel === "series")
              return this.seriesURL(attrs, webApp);
        if(dicomLevel === "instance")
              return this.instanceURL(attrs, webApp);
        return this.studyURL(attrs, webApp);
    }

    studyFileName(attrs) {
        return attrs['0020000D'].Value[0];
    }
    seriesFileName(attrs) {
        return this.studyFileName(attrs) + '_' + attrs['0020000E'].Value[0];
    }
    instanceFileName(attrs) {
        return this.seriesFileName(attrs) + '_' + attrs['00080018'].Value[0];
    }
    isVideo(attrs):boolean {
        let sopClass = j4care.valueOf(attrs['00080016']);
        return [
            '1.2.840.10008.5.1.4.1.1.77.1.1.1',
            '1.2.840.10008.5.1.4.1.1.77.1.2.1',
            '1.2.840.10008.5.1.4.1.1.77.1.4.1']
            .indexOf(sopClass) != -1;
    }
    isImage(attrs):boolean{
        let sopClass = j4care.valueOf(attrs['00080016']);
        let bitsAllocated = j4care.valueOf(attrs['00280100']);
        return ((bitsAllocated && bitsAllocated != "") && (sopClass != '1.2.840.10008.5.1.4.1.1.481.2'));
    }

    createArray(n):any[] {
        let a = [];
        for (let i = 1; i <= n; i++)
            a.push(i);
        return a;
    }
    getStorageSystems(){
        return this.storageSystems.search({},0);
    }

    verifyStorage = (attrs, studyWebService:StudyWebService, level:DicomLevel, params:any) => {
        let url = `${this.getURL(attrs, studyWebService.selectedWebService, level)}/stgver`;

        return this.$http.post(url,{}, this.dicomHeader);
    };

    //TODO issiue is open to change/unify the URLs
    // scheduleStorageVerification = (param, studyWebService:StudyWebService) => this.$http.post(`../aets/${aet}/stgver/studies?${j4care.param(param)}`,{});

    getDevices(){
        return this.devicesService.getDevices();
    }
    PATIENT_STUDIES_TABLE_SCHEMA($this, actions, options:StudySchemaOptions):DicomTableSchema{
        return {
            patient:[
                new TableSchemaElement({
                    type:"actions",
                    header:"",
                    actions:[
                        {
                            icon:{
                                tag:'span',
                                cssClass:'glyphicon glyphicon-chevron-down',
                                text:''
                            },
                            click:(e)=>{
                                console.log("e",e);
                                actions.call($this, {
                                    event:"click",
                                    level:"patient",
                                    action:"toggle_studies"
                                },e);
                            },
                            title:"Hide Studies",
                            showIf:(e)=>{
                                return e.showStudies
                            }
                        },{
                            icon:{
                                tag:'span',
                                cssClass:'glyphicon glyphicon-chevron-right',
                                text:''
                            },
                            click:(e)=>{
                                console.log("e",e);
                                // e.showStudies = !e.showStudies;
                                actions.call($this, {
                                    event:"click",
                                    level:"patient",
                                    action:"toggle_studies"
                                },e);
                                // actions.call(this, 'study_arrow',e);
                            },
                            title:"Show Studies",
                            showIf:(e)=>{
                                return !e.showStudies
                            }
                        }
                    ],
                    headerDescription:"Show studies",
                    pxWidth:40
                }),
                new TableSchemaElement({
                    type:"index",
                    header:'',
                    pathToValue:'',
                    pxWidth:40
                }),
                new TableSchemaElement({
                    type:"actions-menu",
                    header:"",
                    menu:{
                            toggle:(e)=>{
                                console.log("e",e);
                                e.showMenu = !e.showMenu;
                            },
                            actions:[
                                {
                                    icon:{
                                        tag:'span',
                                        cssClass:'glyphicon glyphicon-pencil',
                                        text:''
                                    },
                                    click:(e)=>{
                                        actions.call($this, {
                                            event:"click",
                                            level:"patient",
                                            action:"edit_patient"
                                        },e);
                                    },
                                    title:'Edit this Patient',
                                    permission:{
                                        id:'action-studies-patient',
                                        param:'edit'
                                    }
                                },{
                                    icon:{
                                        tag:'span',
                                        cssClass:'glyphicon glyphicon-plus',
                                        text:''
                                    },
                                    click:(e)=>{
                                        actions.call($this, {
                                            event:"click",
                                            level:"patient",
                                            action:"create_mwl"
                                        },e);
                                    },
                                    title:'Add new MWL',
                                    permission:{
                                        id:'action-studies-mwl',
                                        param:'create'
                                    }
                                },{
                                    icon:{
                                        tag:'span',
                                        cssClass:'custom_icon csv_icon_black',
                                        text:''
                                    },
                                    click:(e)=>{
                                        actions.call($this, {
                                            event:"click",
                                            level:"study",
                                            action:"download_csv"
                                        },e);
                                    },
                                    title:'Download as CSV'
                                    //No permission, if you can see the study/patient than you should have the permission
                                    //to download the CSV if the user should not be allowed to see the study than he should not be allowed to call the page
                                }
                            ]
                    },
                    headerDescription:"Actions",
                    pxWidth:40
                }),
                new TableSchemaElement({
                    type:"actions",
                    header:"",
                    actions:[
                        {
                            icon:{
                                tag:'span',
                                cssClass:'glyphicon glyphicon-th-list',
                                text:''
                            },
                            click:(e)=>{
                                console.log("e",e);
                                e.showAttributes = !e.showAttributes;
                            },
                            title:"Toggle Attributes"
                        }
                    ],
                    headerDescription:"Actions",
                    pxWidth:40
                }),
                new TableSchemaElement({
                    type:"value",
                    header:"Patient's Name",
                    pathToValue:"00100010.Value[0].Alphabetic",
                    headerDescription:"Patient's Name",
                    widthWeight:1,
                    calculatedWidth:"20%"
                }),
                new TableSchemaElement({
                    type:"value",
                    header:"Patient ID",
                    pathToValue:"00100020.Value[0]",
                    headerDescription:"Patient ID",
                    widthWeight:1,
                    calculatedWidth:"20%"
                }),
                new TableSchemaElement({
                    type:"value",
                    header:"Issuer of Patient",
                    pathToValue:"00100021.Value[0]",
                    headerDescription:"Issuer of Patient ID",
                    widthWeight:1,
                    calculatedWidth:"20%"
                }),
                new TableSchemaElement({
                    type:"value",
                    header:"Birth Date",
                    pathToValue:"00100030.Value[0]",
                    headerDescription:"Patient's Birth Date",
                    widthWeight:0.5,
                    calculatedWidth:"20%"
                }),
                new TableSchemaElement({
                    type:"value",
                    header:"Sex",
                    pathToValue:"00100040.Value[0]",
                    headerDescription:"Patient's Sex",
                    widthWeight:0.2,
                    calculatedWidth:"20%"
                }),
                new TableSchemaElement({
                    type:"value",
                    header:"Patient Comments",
                    pathToValue:"00104000.Value[0]",
                    headerDescription:"Patient Comments",
                    widthWeight:3,
                    calculatedWidth:"20%"
                }),
                new TableSchemaElement({
                    type:"value",
                    header:"#S",
                    pathToValue:"00201200.Value[0]",
                    headerDescription:"Number of Patient Related Studies",
                    widthWeight:0.2,
                    calculatedWidth:"20%"
                })
            ],
            studies:[
                new TableSchemaElement({
                    type:"actions",
                    header:"",
                    actions:[
                        {
                            icon:{
                                tag:'span',
                                cssClass:'glyphicon glyphicon-chevron-down',
                                text:''
                            },
                            click:(e)=>{
                                actions.call($this, {
                                    event:"click",
                                    level:"study",
                                    action:"toggle_series"
                                },e);
                            },
                            title:"Hide Series",
                            showIf:(e)=>{
                                return e.showSeries
                            },
                            permission: {
                                id: 'action-studies-serie',
                                param: 'visible'
                            }
                        },{
                            icon:{
                                tag:'span',
                                cssClass:'glyphicon glyphicon-chevron-right',
                                text:''
                            },
                            click:(e)=>{
                                actions.call($this, {
                                    event:"click",
                                    level:"study",
                                    action:"toggle_series"
                                },e);
                            },
                            title:"Show Series",
                            showIf:(e)=>{
                                return !e.showSeries
                            },
                            permission: {
                                id: 'action-studies-serie',
                                param: 'visible'
                            }
                        }
                    ],
                    headerDescription:"Show studies",
                    widthWeight:0.3,
                    calculatedWidth:"6%"
                }),
                new TableSchemaElement({
                    type:"index",
                    header:'',
                    pathToValue:'',
                    pxWidth:40
                }),
                new TableSchemaElement({
                    type:"actions-menu",
                    header:"",
                    menu:{
                        toggle:(e)=>{
                            console.log("e",e);
                            e.showMenu = !e.showMenu;
                        },
                        actions:[
                            {
                                icon:{
                                    tag:'span',
                                    cssClass:'glyphicon glyphicon-pencil',
                                    text:''
                                },
                                click:(e)=>{
                                    actions.call($this, {
                                        event:"click",
                                        level:"study",
                                        action:"edit_study"
                                    },e);
                                },
                                title:'Edit this study',
                                permission:{
                                    id:'action-studies-study',
                                    param:'edit'
                                }
                            },{
                                icon:{
                                    tag:'span',
                                    cssClass:'glyphicon glyphicon-export',
                                    text:''
                                },
                                click:(e)=>{
                                    actions.call($this, {
                                        event:"click",
                                        level:"study",
                                        action:"export"
                                    },e);
                                },
                                title:'Export study',
                                permission:{
                                    id:'action-studies-study',
                                    param:'export'
                                }
                            },{
                                icon:{
                                    tag:'i',
                                    cssClass:'material-icons',
                                    text:'history'
                                },
                                click:(e)=>{
                                    actions.call($this, {
                                        event:"click",
                                        level:"study",
                                        action:"modify_expired_date"
                                    },e);
                                },
                                title:'Set/Change expired date',
                                permission:{
                                    id:'action-studies-study',
                                    param:'edit'
                                }
                            },{
                            //<i class="material-icons">file_upload</i>
                                icon:{
                                    tag:'i',
                                    cssClass:'material-icons',
                                    text:'file_upload'
                                },
                                click:(e)=>{
                                    actions.call($this, {
                                        event:"click",
                                        level:"study",
                                        action:"upload_file"
                                    },e);
                                },
                                title:'Upload file',
                                permission:{
                                    id:'action-studies-study',
                                    param:'upload'
                                }
                            },{
                                icon:{
                                    tag:'span',
                                    cssClass:'glyphicon glyphicon-save',
                                    text:''
                                },
                                click:(e)=>{
                                    actions.call($this, {
                                        event:"click",
                                        level:"study",
                                        action:"download",
                                        mode:"uncompressed"
                                    },e);
                                },
                                title:'Retrieve Study uncompressed',
                                permission:{
                                    id:'action-studies-download',
                                    param:'visible'
                                }
                            },{
                                icon:{
                                    tag:'span',
                                    cssClass:'glyphicon glyphicon-download-alt',
                                    text:''
                                },
                                click:(e)=>{
                                    actions.call($this, {
                                        event:"click",
                                        level:"study",
                                        action:"download",
                                        mode:"compressed",
                                    },e);
                                },
                                title:'Retrieve Study as stored at the archive',
                                permission:{
                                    id:'action-studies-download',
                                    param:'visible'
                                }
                            },{
                                icon:{
                                    tag:'span',
                                    cssClass: options.trash.active ? 'glyphicon glyphicon-repeat' : 'glyphicon glyphicon-trash',
                                    text:''
                                },
                                click:(e)=>{
                                    actions.call($this, {
                                        event:"click",
                                        level:"study",
                                        action:"reject"
                                    },e);
                                },
                                title:options.trash.active ? 'Restore study' : 'Reject study',
                                permission:{
                                    id:'action-studies-study',
                                    param:options.trash.active ? 'restore': 'reject'
                                }
                            },{
                                icon:{
                                    tag:'span',
                                    cssClass: 'glyphicon glyphicon-remove',
                                    text:''
                                },
                                click:(e)=>{
                                    actions.call($this, {
                                        event:"click",
                                        level:"study",
                                        action:"delete"
                                    },e);
                                },
                                title:'Delete study permanently',
                                showIf:(e)=>{
                                    return options.trash.active ||
                                        (
                                            options.selectedWebService &&
                                            options.selectedWebService.dicomAETitleObject &&
                                            options.selectedWebService.dicomAETitleObject.dcmAllowDeleteStudyPermanently === "ALWAYS"
                                        )
                                },
                                permission: {
                                    id: 'action-studies-study',
                                    param: 'delete'
                                }
                            },{
                                icon:{
                                    tag:'span',
                                    cssClass:'glyphicon glyphicon-ok',
                                    text:''
                                },
                                click:(e)=>{
                                    actions.call($this, {
                                        event:"click",
                                        level:"study",
                                        action:"verify_storage"
                                    },e);
                                },
                                title:'Verify storage commitment',
                                permission: {
                                    id: 'action-studies-verify_storage_commitment',
                                    param: 'visible'
                                }
                            },{
                                icon:{
                                    tag:'span',
                                    cssClass:'custom_icon csv_icon_black',
                                    text:''
                                },
                                click:(e)=>{
                                    actions.call($this, {
                                        event:"click",
                                        level:"series",
                                        action:"download_csv"
                                    },e);
                                },
                                title:"Download as CSV"
                            }
                        ]
                    },
                    headerDescription:"Actions",
                    pxWidth:40
                }),
                new TableSchemaElement({
                    type:"actions",
                    header:"",
                    actions:[
                        {
                            icon:{
                                tag:'span',
                                cssClass:'glyphicon glyphicon-th-list',
                                text:''
                            },
                            click:(e)=>{
                                console.log("e",e);
                                e.showAttributes = !e.showAttributes;
                            },
                            title:"Toggle Attributes"
                        }
                    ],
                    headerDescription:"Actions",
                    pxWidth:40
                }),
                new TableSchemaElement({
                    type:"value",
                    header:"Study ID",
                    pathToValue:"[00200010].Value[0]",
                    headerDescription:"Study ID",
                    widthWeight:0.9,
                    calculatedWidth:"20%"
                }),new TableSchemaElement({
                    type:"value",
                    header:"Study Instance UID",
                    pathToValue:"[0020000D].Value[0]",
                    headerDescription:"Study Instance UID",
                    widthWeight:3,
                    calculatedWidth:"20%"
                }),
                new TableSchemaElement({
                    type:"value",
                    header:"Study Date",
                    pathToValue:"[00080020].Value[0]",
                    headerDescription:"Study Date",
                    widthWeight:0.6,
                    calculatedWidth:"20%"
                }),
                new TableSchemaElement({
                    type:"value",
                    header:"Study Time",
                    pathToValue:"[00080030].Value[0]",
                    headerDescription:"Study Time",
                    widthWeight:0.6,
                    calculatedWidth:"20%"
                }),
                new TableSchemaElement({
                    type:"value",
                    header:"R. Physician's Name",
                    pathToValue:"[00080090].Value[0].Alphabetic",
                    headerDescription:"Referring Physician's Name",
                    widthWeight:1,
                    calculatedWidth:"20%"
                }),
                new TableSchemaElement({
                    type:"value",
                    header:"Accession Number",
                    pathToValue:"[00080050].Value[0]",
                    headerDescription:"Accession Number",
                    widthWeight:1,
                    calculatedWidth:"20%"
                }),
                new TableSchemaElement({
                    type:"value",
                    header:"Modalities",
                    pathToValue:"[00080061].Value[0]",
                    headerDescription:"Modalities in Study",
                    widthWeight:0.5,
                    calculatedWidth:"20%"
                }),
                new TableSchemaElement({
                    type:"value",
                    header:"Study Description",
                    pathToValue:"[00081030].Value[0]",
                    headerDescription:"Study Description",
                    widthWeight:2,
                    calculatedWidth:"20%"
                }),
                new TableSchemaElement({
                    type:"value",
                    header:"#S",
                    pathToValue:"[00201206].Value[0]",
                    headerDescription:"Number of Study Related Series",
                    widthWeight:0.2,
                    calculatedWidth:"20%"
                }),
                new TableSchemaElement({
                    type:"value",
                    header:"#I",
                    pathToValue:"[00201208].Value[0]",
                    headerDescription:"Number of Study Related Instances",
                    widthWeight:0.2,
                    calculatedWidth:"20%"
                })
            ],
            series:[
                new TableSchemaElement({
                    type:"actions",
                    header:"",
                    actions:[
                        {
                            icon:{
                                tag:'span',
                                cssClass:'glyphicon glyphicon-chevron-down',
                                text:''
                            },
                            click:(e)=>{
                                actions.call($this, {
                                    event:"click",
                                    level:"series",
                                    action:"toggle_instances"
                                },e);
                            },
                            title:"Hide Instances",
                            showIf:(e)=>{
                                return e.showInstances
                            }
                        },{
                            icon:{
                                tag:'span',
                                cssClass:'glyphicon glyphicon-chevron-right',
                                text:''
                            },
                            click:(e)=>{
                                actions.call($this, {
                                    event:"click",
                                    level:"series",
                                    action:"toggle_instances"
                                },e);
                            },
                            title:"Show Instaces",
                            showIf:(e)=>{
                                return !e.showInstances
                            }
                        }
                    ],
                    headerDescription:"Show Instances",
                    widthWeight:0.2,
                    calculatedWidth:"6%"
                }),
                new TableSchemaElement({
                    type:"index",
                    header:'',
                    pathToValue:'',
                    pxWidth:40
                }),
                new TableSchemaElement({
                    type:"actions-menu",
                    header:"",
                    menu:{
                        toggle:(e)=>{
                            e.showMenu = !e.showMenu;
                        },
                        actions:[
                            {
                                icon:{
                                    tag:'span',
                                    cssClass:'custom_icon csv_icon_black',
                                    text:''
                                },
                                click:(e)=>{
                                    console.log("e",e);
                                    actions.call($this, {
                                        event:"click",
                                        level:"instance",
                                        action:"download_csv"
                                    },e);
                                },
                                title:'Download as CSV'
                            },{
                                icon:{
                                    tag:'span',
                                    cssClass: options.trash.active ? 'glyphicon glyphicon-repeat' : 'glyphicon glyphicon-trash',
                                    text:''
                                },
                                click:(e)=>{
                                    actions.call($this, {
                                        event:"click",
                                        level:"series",
                                        action:"reject"
                                    },e);
                                },
                                title:options.trash.active ? 'Restore series' : 'Reject series',
                                permission: {
                                    id: 'action-studies-serie',
                                    param: options.trash.active ? 'restore' : 'reject'
                                }
                            },{
                                icon:{
                                    tag:'span',
                                    cssClass:'glyphicon glyphicon-ok',
                                    text:''
                                },
                                click:(e)=>{
                                    actions.call($this, {
                                        event:"click",
                                        level:"series",
                                        action:"verify_storage"
                                    },e);
                                },
                                title:'Verify storage commitment',
                            }
                            ,{
                                icon:{
                                    tag:'span',
                                    cssClass:'glyphicon glyphicon-export',
                                    text:''
                                },
                                click:(e)=>{
                                    actions.call($this, {
                                        event:"click",
                                        level:"series",
                                        action:"export"
                                    },e);
                                },
                                title:'Export series',
                            }
                        ]
                    },
                    headerDescription:"Actions",
                    pxWidth:40
                }),
                new TableSchemaElement({
                    type:"actions",
                    header:"",
                    actions:[
                        {
                            icon:{
                                tag:'span',
                                cssClass:'glyphicon glyphicon-th-list',
                                text:''
                            },
                            click:(e)=>{
                                e.showAttributes = !e.showAttributes;
                            },
                            title:"Show attributes"
                        }
                    ],
                    headerDescription:"Actions",
                    pxWidth:40
                }),
                new TableSchemaElement({
                    type:"value",
                    header:"Station Name",
                    pathToValue:"00081010.Value[0]",
                    headerDescription:"Station Name",
                    widthWeight:0.9,
                    calculatedWidth:"20%"
                }),
                new TableSchemaElement({
                    type:"value",
                    header:"Series Number",
                    pathToValue:"00200011.Value[0]",
                    headerDescription:"Series Number",
                    widthWeight:0.9,
                    calculatedWidth:"20%"
                }),
                new TableSchemaElement({
                    type:"value",
                    header:"PPS Start Date",
                    pathToValue:"00400244.Value[0]",
                    headerDescription:"Performed Procedure Step Start Date",
                    widthWeight:0.9,
                    calculatedWidth:"20%"
                }),
                new TableSchemaElement({
                    type:"value",
                    header:"PPS Start Time",
                    pathToValue:"00400245.Value[0]",
                    headerDescription:"Performed Procedure Step Start Time",
                    widthWeight:0.9,
                    calculatedWidth:"20%"
                }),
                new TableSchemaElement({
                    type:"value",
                    header:"Body Part",
                    pathToValue:"00180015.Value[0]",
                    headerDescription:"Body Part Examined",
                    widthWeight:0.9,
                    calculatedWidth:"20%"
                }),
                new TableSchemaElement({
                    type:"value",
                    header:"Modality",
                    pathToValue:"00080060.Value[0]",
                    headerDescription:"Modality",
                    widthWeight:0.9,
                    calculatedWidth:"20%"
                }),
                new TableSchemaElement({
                    type:"value",
                    header:"Series Description",
                    pathToValue:"0008103E.Value[0]",
                    headerDescription:"Series Description",
                    widthWeight:0.9,
                    calculatedWidth:"20%"
                }),
                new TableSchemaElement({
                    type:"value",
                    header:"#I",
                    pathToValue:"00201209.Value[0]",
                    headerDescription:"Number of Series Related Instances",
                    widthWeight:0.9,
                    calculatedWidth:"20%"
                })
            ],
            instance:[
                new TableSchemaElement({
                    type:"index",
                    header:'',
                    pathToValue:'',
                    pxWidth:40
                }),
                new TableSchemaElement({
                    type:"actions-menu",
                    header:"",
                    menu:{
                        toggle:(e)=>{
                            console.log("e",e);
                            e.showMenu = !e.showMenu;
                        },
                        actions:[
                            {
                                icon:{
                                    tag:'span',
                                    cssClass:'glyphicon glyphicon-export',
                                    text:''
                                },
                                click:(e)=>{
                                    actions.call($this, {
                                        event:"click",
                                        level:"instance",
                                        action:"export"
                                    },e);
                                },
                                title:'Export instance',
                            },
                            {
                                icon:{
                                    tag:'span',
                                    cssClass: options.trash.active ? 'glyphicon glyphicon-repeat' : 'glyphicon glyphicon-trash',
                                    text:''
                                },
                                click:(e)=>{
                                    actions.call($this, {
                                        event:"click",
                                        level:"instance",
                                        action:"reject"
                                    },e);
                                },
                                title:options.trash.active ? 'Restore instance' : 'Reject instance',
                            },{
                                icon:{
                                    tag:'span',
                                    cssClass:'glyphicon glyphicon-ok',
                                    text:''
                                },
                                click:(e)=>{
                                    actions.call($this, {
                                        event:"click",
                                        level:"instance",
                                        action:"verify_storage"
                                    },e);
                                },
                                title:'Verify storage commitment',
                            },
                            {
                                icon:{
                                    tag:'span',
                                    cssClass:'glyphicon glyphicon-save',
                                    text:''
                                },
                                click:(e)=>{
                                    actions.call($this, {
                                        event:"click",
                                        level:"instance",
                                        action:"download",
                                        mode:"uncompressed"
                                    },e);
                                },
                                title:'Download Uncompressed DICOM Object'
                            },
                            {
                                icon:{
                                    tag:'span',
                                    cssClass:'glyphicon glyphicon-download-alt',
                                    text:'',
                                },
                                click:(e)=>{
                                    actions.call($this, {
                                        event:"click",
                                        level:"instance",
                                        action:"download",
                                        mode:"compressed",
                                    },e);
                                },
                                title:'Download DICOM Object'
                            }
                        ]
                    },
                    headerDescription:"Actions",
                    pxWidth:40
                }),new TableSchemaElement({
                    type:"actions",
                    header:"",
                    actions:[
                        {
                            icon:{
                                tag:'span',
                                cssClass:'glyphicon glyphicon-th-list',
                                text:''
                            },
                            click:(e)=>{
                                console.log("e",e);
                                e.showAttributes = !e.showAttributes;
                            },
                            title:'Show attributes'
                        }
                    ],
                    headerDescription:"Actions",
                    pxWidth:40
                }),new TableSchemaElement({
                    type:"actions",
                    header:"",
                    actions:[
                        {
                            icon:{
                                tag:'span',
                                cssClass:'glyphicon glyphicon-list',
                                text:''
                            },
                            click:(e)=>{
                                console.log("e",e);
                                e.showFileAttributes = !e.showFileAttributes;
                            },
                            title:'Show attributes from file'
                        }
                    ],
                    headerDescription:"Actions",
                    pxWidth:40
                }),
                new TableSchemaElement({
                    type:"value",
                    header:"SOP Class UID",
                    pathToValue:"00080016.Value[0]",
                    headerDescription:"SOP Class UID",
                    widthWeight:0.9,
                    calculatedWidth:"20%"
                }),
                new TableSchemaElement({
                    type:"value",
                    header:"Instance Number",
                    pathToValue:"00200013.Value[0]",
                    headerDescription:"Instance Number",
                    widthWeight:0.9,
                    calculatedWidth:"20%"
                }),
                new TableSchemaElement({
                    type:"value",
                    header:"Content Date",
                    pathToValue:"00080023.Value[0]",
                    headerDescription:"Content Date",
                    widthWeight:0.9,
                    calculatedWidth:"20%"
                }),
                new TableSchemaElement({
                    type:"value",
                    header:"Content Time",
                    pathToValue:"00080033.Value[0]",
                    headerDescription:"Content Time",
                    widthWeight:0.9,
                    calculatedWidth:"20%"
                }),
                new TableSchemaElement({
                    type:"pipe",
                    header:"Content Description",
                    headerDescription:"Content Description",
                    widthWeight:1.5,
                    calculatedWidth:"20%",
                    pipe:new DynamicPipe(ContentDescriptionPipe,undefined)
                }),
                new TableSchemaElement({
                    type:"value",
                    header:"#F",
                    pathToValue:"00280008.Value[0]",
                    headerDescription:"Number of Frames",
                    widthWeight:0.3,
                    calculatedWidth:"20%"
                })
            ]
        }
    }
    modifyStudy(study,deviceWebservice:StudyWebService, header:HttpHeaders){
        const url = this.getModifyStudyUrl(deviceWebservice);
        if(url){
            return this.$http.post(url,study, header);
        }
        return Observable.throw({error:"Error on getting the WebApp URL"});
    }
    getModifyStudyUrl(deviceWebservice:StudyWebService){
        return this.getDicomURL("study", this.getModifyStudyWebApp(deviceWebservice));
    }
    getModifyStudyWebApp(deviceWebservice:StudyWebService):DcmWebApp{
        if(deviceWebservice.selectedWebService.dcmWebServiceClass.indexOf("DCM4CHEE_ARC_AET") > -1){
            return deviceWebservice.selectedWebService;
        }else{
            return undefined;
        }
    }

    modifyMWL(mwl,deviceWebservice:StudyWebService, header:HttpHeaders){
        const url = this.getModifyMWLUrl(deviceWebservice);
        if(url){
            return this.$http.post(url,mwl, header);
        }
        return Observable.throw({error:"Error on getting the WebApp URL"});
    }
    getModifyMWLUrl(deviceWebservice:StudyWebService){
        return this.getDicomURL("mwl", this.getModifyMWLWebApp(deviceWebservice));
    }
    getModifyMWLWebApp(deviceWebservice:StudyWebService):DcmWebApp{
        if(deviceWebservice.selectedWebService.dcmWebServiceClass.indexOf("DCM4CHEE_ARC_AET") > -1){
            return deviceWebservice.selectedWebService;
        }else{
            return undefined;
        }
    }

    modifyPatient(patientId:string,patientObject,deviceWebservice:StudyWebService){
        const url = this.getModifyPatientUrl(deviceWebservice);
        if(url){
            if(patientId){
                //Change patient;
                return this.$http.put(`${url}/${patientId}`,patientObject);
            }else{
                //Create new patient
                return this.$http.post(url,patientObject);
            }
        }
        return Observable.throw({error:"Error on getting the WebApp URL"});
    }

    getModifyPatientUrl(deviceWebService:StudyWebService){
        return this.getDicomURLFromWebService(deviceWebService, "patient");
    }
    getModifyPatientWebApp(deviceWebService:StudyWebService):DcmWebApp{
        return this.getWebAppFromWebServiceClassAndSelectedWebApp(deviceWebService,"DCM4CHEE_ARC_AET",  "PAM_RS");
    }

    getDicomURLFromWebService(deviceWebService:StudyWebService, mode:("patient"|"study")){
        return this.getDicomURL(mode, this.getModifyPatientWebApp(deviceWebService));
    }
    getWebAppFromWebServiceClassAndSelectedWebApp(deviceWebService:StudyWebService, neededWebServiceClass:string, alternativeWebServiceClass:string){
        if(deviceWebService.selectedWebService.dcmWebServiceClass.indexOf(neededWebServiceClass) > -1){
            return deviceWebService.selectedWebService;
        }else{
            try{
                return deviceWebService.webServices.filter((webService:DcmWebApp)=>{
                    if(webService.dcmWebServiceClass.indexOf(alternativeWebServiceClass) > -1 && webService.dicomAETitle === deviceWebService.selectedWebService.dicomAETitle){
                        return true;
                    }
                    return false;
                })[0];
            }catch (e) {
                j4care.log(`Error on getting the ${alternativeWebServiceClass} WebApp getModifyPatientUrl`,e);
                return undefined;
            }
        }
    }

    getUploadFileWebApp(deviceWebService:StudyWebService){
        return this.getWebAppFromWebServiceClassAndSelectedWebApp(deviceWebService,"STOW_RS",  "STOW_RS");
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
    getPatientIod(){
        if (this._patientIod) {
            return Observable.of(this._patientIod);
        } else {
            return this.$http.get('assets/iod/patient.iod.json')
        }
    };
    getStudyIod(){
        if (this._studyIod) {
            return Observable.of(this._studyIod);
        } else {
            return this.$http.get('assets/iod/study.iod.json')
        }
    };

    getMwlIod(){
        if (this._mwlIod) {
            return Observable.of(this._mwlIod);
        } else {
            return this.$http.get(
                'assets/iod/mwl.iod.json'
            )
        }
    };

    getPrepareParameterForExpiriationDialog(study, exporters, infinit){
        let expiredDate:Date;
        let title = "Set expired date for the study.";
        let schema:any = [
            [
                [
                    {
                        tag:"label",
                        text:"Expired date"
                    },
                    {
                        tag:"p-calendar",
                        filterKey:"expiredDate",
                        description:"Expired Date"
                    }
                ]
            ]
        ];
        let schemaModel = {};
        if(infinit){
            if(_.hasIn(study,"7777102B.Value[0]") && study["7777102B"].Value[0] === "FROZEN"){
                schemaModel = {
                    setExpirationDateToNever:false,
                    FreezeExpirationDate:false
                };
                title = "Unfreeze/Unprotect Expiration Date of the Study";
                schema = [
                    [
                        [
                            {
                                tag:"label",
                                text:"Expired Date"
                            },
                            {
                                tag:"p-calendar",
                                filterKey:"expiredDate",
                                description:"Expired Date"
                            }
                        ]
                    ]
                ];
            }else{
                title = "Freeze/Protect Expiration Date of the Study";
                schemaModel = {
                    setExpirationDateToNever:true,
                    FreezeExpirationDate:true
                };
                schema = [
                    [
                        [
                            {
                                tag:"label",
                                text:"Expired date",
                                showIf:(model)=>{
                                    return !model.setExpirationDateToNever
                                }
                            },
                            {
                                tag:"p-calendar",
                                filterKey:"expiredDate",
                                description:"Expired Date",
                                showIf:(model)=>{
                                    return !model.setExpirationDateToNever
                                }
                            }
                        ],[
                        {
                            tag:"dummy"
                        },
                        {
                            tag:"checkbox",
                            filterKey:"setExpirationDateToNever",
                            description:"Set Expiration Date to 'never' if you want also to protect the study",
                            text:"Set Expiration Date to 'never' if you want also to protect the study"
                        }
                    ],[
                        {
                            tag:"dummy"
                        },
                        {
                            tag:"checkbox",
                            filterKey:"FreezeExpirationDate",
                            description:"Freeze Expiration Date",
                            text:"Freeze Expiration Date"
                        }
                    ]
                    ]
                ];
            }
        }else{
            if(_.hasIn(study,"77771023.Value.0") && study["77771023"].Value[0] != ""){
                let expiredDateString = study["77771023"].Value[0];
                expiredDate = new Date(expiredDateString.substring(0, 4)+ '.' + expiredDateString.substring(4, 6) + '.' + expiredDateString.substring(6, 8));
            }else{
                expiredDate = new Date();
            }
            schemaModel = {
                expiredDate:j4care.formatDate(expiredDate,'yyyyMMdd')
            };
            title += "<p>Set exporter if you wan't to export on expiration date too.";
            schema[0].push([
                {
                    tag:"label",
                    text:"Exporter"
                },
                {
                    tag:"select",
                    filterKey:"exporter",
                    description:"Exporter",
                    options:exporters.map(exporter=> new SelectDropdown(exporter.id, exporter.description || exporter.id))
                }])
        }
        return {
            content: title,
            form_schema:schema,
            result: {
                schema_model: schemaModel
            },
            saveButton: 'SAVE'
        };
    }
    setExpiredDate(deviceWebservice:StudyWebService,studyUID, expiredDate, exporter, params?:any){
        const url = this.getModifyStudyUrl(deviceWebservice);
        let localParams = "";
        if(exporter){
            localParams = `?ExporterID=${exporter}`
        }
        if(params && Object.keys(params).length > 0){
            if(localParams){
                localParams += j4care.objToUrlParams(params);
            }else{
                localParams = `?${j4care.objToUrlParams(params)}`
            }
        }
        return this.$http.put(`${url}/${studyUID}/expire/${expiredDate}${localParams}`,{})
    }

    getExporters = () => this.$http.get('../export');

    deleteStudy = (studyInstanceUID:string, dcmWebApp:DcmWebApp) => this.$http.delete(`${this.getDicomURL("study", dcmWebApp)}/${studyInstanceUID}`);

    deleteRejectedInstances = (reject, params) => this.$http.delete(`../reject/${reject}${j4care.param(params)}`);

    rejectStudy(studyAttr, webApp:DcmWebApp, rejectionCode){
        return this.$http.post(
            `${this.studyURL(studyAttr, webApp)}/reject/${rejectionCode}`,
            {},
            this.jsonHeader
        )
    }
    rejectSeries(studyAttr, webApp:DcmWebApp, rejectionCode){
        return this.$http.post(
            `${this.seriesURL(studyAttr, webApp)}/reject/${rejectionCode}`,
            {},
            this.jsonHeader
        )
    }
    rejectInstance(studyAttr, webApp:DcmWebApp, rejectionCode){
        return this.$http.post(
            `${this.instanceURL(studyAttr, webApp)}/reject/${rejectionCode}`,
            {},
            this.jsonHeader
        )
    }
    mapCode(m,i,newObject,mapCodes){
        if(_.hasIn(mapCodes,i)){
            if(_.isArray(mapCodes[i])){
                _.forEach(mapCodes[i],(seq,j)=>{
                    newObject[seq.code] = _.get(m,seq.map);
                    newObject[seq.code].vr = seq.vr;
                });
            }else{
                newObject[mapCodes[i].code] = m;
                newObject[mapCodes[i].code].vr = mapCodes[i].vr;
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

    export = (url) => this.$http.post(url,{}, this.jsonHeader);

    getQueueNames = () => this.retrieveMonitoringService.getQueueNames();

    getRejectNotes = (params?:any) => this.$http.get(`../reject/${j4care.param(params)}`);

}
