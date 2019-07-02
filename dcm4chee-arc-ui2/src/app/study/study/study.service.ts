import { Injectable } from '@angular/core';
import {AccessLocation, DicomMode, DicomResponseType, FilterSchema, SelectDropdown} from "../../interfaces";
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
import {StudyDeviceWebserviceModel} from "./study-device-webservice.model";
import {DcmWebApp} from "../../models/dcm-web-app";
import {HttpClient, HttpHeaders} from "@angular/common/http";
import {DicomTableSchema, DynamicPipe} from "../../helpers/dicom-studies-table/dicom-studies-table.interfaces";
import {ContentDescriptionPipe} from "../../pipes/content-description.pipe";
import {TableSchemaElement} from "../../models/dicom-table-schema-element";
import {KeycloakService} from "../../helpers/keycloak-service/keycloak.service";
declare var DCM4CHE: any;

@Injectable()
export class StudyService {
    private _patientIod;

    constructor(
      private aeListService:AeListService,
      private $http:J4careHttpService,
      private storageSystems:StorageSystemsService,
      private devicesService:DevicesService
    ) { }

    get patientIod() {
        return this._patientIod;
    }

    set patientIod(value) {
        this._patientIod = value;
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

    getFilterSchema(tab:DicomMode, aets:Aet[], quantityText:{count:string,size:string}, filterMode:('main'| 'expand')){
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
                    placeholder:"Order By",
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
            header =  new HttpHeaders({'Accept': 'application/dicom+json'});
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
            header =  new HttpHeaders({'Accept': 'application/dicom+json'});
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
        let header:HttpHeaders;
            header =  new HttpHeaders({'Accept': 'application/json'});
        return this.$http.get(
            url,//`http://test-ng:8080/dcm4chee-arc/ui2/rs/aets`,
            header,
            false,
            dcmWebApp
        ).map(res => j4care.redirectOnAuthResponse(res));
    }
    getInstances(studyInstanceUID:string, seriesInstanceUID:string, filterModel:any, dcmWebApp:DcmWebApp, responseType?:DicomResponseType):Observable<any>{
        let header:HttpHeaders;
        if(!responseType || responseType === "object"){
            header =  new HttpHeaders({'Accept': 'application/dicom+json'});
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
    /*            case "diff":
                    url = this.diffUrl(callingAet, externalAet, secondExternalAet, baseUrl);
                    break;*/
                default:
                    url += '/studies';
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

    private rsURL(callingAet:Aet, accessLocation?:AccessLocation,  externalAet?:Aet, baseUrl?:string) {
        if(accessLocation === "external" && externalAet){
            return `${baseUrl || '..'}/aets/${callingAet.dicomAETitle}/dims/${externalAet.dicomAETitle}`;
        }
        return `${baseUrl || '..'}/aets/${callingAet.dicomAETitle}/rs`;
    }

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

    getDevices(){
        return this.devicesService.getDevices();
    }
    PATIENT_STUDIES_TABLE_SCHEMA($this, actions):DicomTableSchema{
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
                                text:'',
                                showIf:(e)=>{
                                    return e.showStudies
                                }
                            },
                            click:(e)=>{
                                console.log("e",e);
                                actions.call($this, {
                                    event:"click",
                                    level:"patient",
                                    action:"toggle_studies"
                                },e);
                                // e.showStudies = !e.showStudies;
                            }
                        },{
                            icon:{
                                tag:'span',
                                cssClass:'glyphicon glyphicon-chevron-right',
                                text:'',
                                showIf:(e)=>{
                                    return !e.showStudies
                                }
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
                                        text:'',
                                        description:'Edit this Patient',
                                    },
                                    click:(e)=>{
                                        console.log("e",e);
                                        //TODO edit patient
                                        actions.call($this, {
                                            event:"click",
                                            level:"patient",
                                            action:"edit_patient"
                                        },e);
                                    }
                                },{
                                    icon:{
                                        tag:'span',
                                        cssClass:'glyphicon glyphicon-plus',
                                        text:'',
                                        description:'Add new MWL',
                                    },
                                    click:(e)=>{
                                        console.log("e",e);
                                        //TODO create mwl
                                        actions.call($this, {
                                            event:"click",
                                            level:"patient",
                                            action:"create_mwl"
                                        },e);
                                    }
                                },{
                                    icon:{
                                        tag:'span',
                                        cssClass:'custom_icon csv_icon_black',
                                        text:'',
                                        description:'Download as CSV',
                                    },
                                    click:(e)=>{
                                        console.log("e",e);
                                        //TODO download csv
                                        actions.call($this, {
                                            event:"click",
                                            level:"patient",
                                            action:"download_csv"
                                        },e);
                                    }
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
                            }
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
                                text:'',
                                showIf:(e)=>{
                                    return e.showSeries
                                }
                            },
                            click:(e)=>{
                                actions.call($this, {
                                    event:"click",
                                    level:"studies",
                                    action:"toggle_series"
                                },e);
                            }
                        },{
                            icon:{
                                tag:'span',
                                cssClass:'glyphicon glyphicon-chevron-right',
                                text:'',
                                showIf:(e)=>{
                                    return !e.showSeries
                                }
                            },
                            click:(e)=>{
                                actions.call($this, {
                                    event:"click",
                                    level:"studies",
                                    action:"toggle_series"
                                },e);
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
                    type:"actions",
                    header:"",
                    actions:[
                        {
                            icon:{
                                tag:'span',
                                cssClass:'glyphicon glyphicon-option-vertical',
                                text:''
                            },
                            click:(e)=>{
                                console.log("e",e);
                                /*                                actions.call($this, {
                                                                    event:"click",
                                                                    level:"patient",
                                                                    action:"toggle_studies"
                                                                },e);*/
                                // e.showAttributes = !e.showAttributes;
                            }
                        },
                        {
                            icon:{
                                tag:'span',
                                cssClass:'glyphicon glyphicon-th-list',
                                text:''
                            },
                            click:(e)=>{
                                console.log("e",e);
                                e.showAttributes = !e.showAttributes;
                            }
                        }
                    ],
                    headerDescription:"Actions",
                    pxWidth:65
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
                                text:'',
                                showIf:(e)=>{
                                    return e.showInstances
                                }
                            },
                            click:(e)=>{
                                actions.call($this, {
                                    event:"click",
                                    level:"series",
                                    action:"toggle_instances"
                                },e);
                            }
                        },{
                            icon:{
                                tag:'span',
                                cssClass:'glyphicon glyphicon-chevron-right',
                                text:'',
                                showIf:(e)=>{
                                    return !e.showInstances
                                }
                            },
                            click:(e)=>{
                                actions.call($this, {
                                    event:"click",
                                    level:"series",
                                    action:"toggle_instances"
                                },e);
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
                    type:"actions",
                    header:"",
                    actions:[                        {
                        icon:{
                            tag:'span',
                            cssClass:'glyphicon glyphicon-option-vertical',
                            text:''
                        },
                        click:(e)=>{
                            console.log("e",e);
                            /*                                actions.call($this, {
                                                                event:"click",
                                                                level:"patient",
                                                                action:"toggle_studies"
                                                            },e);*/
                            // e.showAttributes = !e.showAttributes;
                        }
                    },
                        {
                            icon:{
                                tag:'span',
                                cssClass:'glyphicon glyphicon-th-list',
                                text:''
                            },
                            click:(e)=>{
                                console.log("e",e);
                                e.showAttributes = !e.showAttributes;
                            }
                        }
                    ],
                    headerDescription:"Actions",
                    pxWidth:65
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
                    type:"actions",
                    header:"",
                    actions:[
                        {
                            icon:{
                                tag:'span',
                                cssClass:'glyphicon glyphicon-option-vertical',
                                text:''
                            },
                            click:(e)=>{
                                console.log("e",e);
                                /*                                actions.call($this, {
                                                                    event:"click",
                                                                    level:"patient",
                                                                    action:"toggle_studies"
                                                                },e);*/
                                // e.showAttributes = !e.showAttributes;
                            }
                        },
                        {
                            icon:{
                                tag:'span',
                                cssClass:'glyphicon glyphicon-th-list',
                                text:''
                            },
                            click:(e)=>{
                                console.log("e",e);
                                e.showAttributes = !e.showAttributes;
                            }
                        }
                    ],
                    headerDescription:"Actions",
                    pxWidth:65
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

    getPatientIod(){
        if (this._patientIod) {
            return Observable.of(this._patientIod);
        } else {
            return this.$http.get('assets/iod/patient.iod.json')
        }
    };
}
