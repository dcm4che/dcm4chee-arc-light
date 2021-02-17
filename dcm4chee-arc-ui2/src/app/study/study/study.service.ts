import { Injectable } from '@angular/core';
import {
    AccessControlIDMode,
    AccessLocation,
    DicomLevel,
    DicomMode,
    DicomResponseType, DiffAttributeSet,
    FilterSchema, FilterSchemaElement,
    SelectDropdown, SelectedDetailObject, SelectionAction, StudyFilterConfig,
    UniqueSelectIdObject, UPSModifyMode
} from "../../interfaces";
import {Globalvar} from "../../constants/globalvar";
import {Aet} from "../../models/aet";
import {AeListService} from "../../configuration/ae-list/ae-list.service";
import {j4care} from "../../helpers/j4care.service";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {Observable, forkJoin, of, throwError} from "rxjs";
import * as _ from 'lodash-es'
import {GSPSQueryParams} from "../../models/gsps-query-params";
import {StorageSystemsService} from "../../monitoring/storage-systems/storage-systems.service";
import {DevicesService} from "../../configuration/devices/devices.service";
import {DcmWebApp} from "../../models/dcm-web-app";
import {HttpClient, HttpHeaders} from "@angular/common/http";
import {
    DicomTableSchema,
    DynamicPipe,
    StudySchemaOptions, TableAction
} from "../../helpers/dicom-studies-table/dicom-studies-table.interfaces";
import {ContentDescriptionPipe} from "../../pipes/content-description.pipe";
import {PatientIssuerPipe} from "../../pipes/patient-issuer.pipe";
import {TableSchemaElement} from "../../models/dicom-table-schema-element";
import {KeycloakService} from "../../helpers/keycloak-service/keycloak.service";
import {WebAppsListService} from "../../configuration/web-apps-list/web-apps-list.service";
import {StudyWebService} from "./study-web-service.model";
import {PermissionService} from "../../helpers/permissions/permission.service";
import {SelectionActionElement} from "./selection-action-element.models";
declare var DCM4CHE: any;
import {catchError, map, switchMap} from "rxjs/operators";
import {FormatTMPipe} from "../../pipes/format-tm.pipe";
import {FormatDAPipe} from "../../pipes/format-da.pipe";
import {FormatAttributeValuePipe} from "../../pipes/format-attribute-value.pipe";
import {AppService} from "../../app.service";
import {MwlDicom} from "../../models/mwl-dicom";
import {DynamicPipePipe} from "../../pipes/dynamic-pipe.pipe";

@Injectable()
export class StudyService {

    iod = {};
    integerVr = ['DS', 'FL', 'FD', 'IS', 'SL', 'SS', 'UL', 'US'];

    dicomHeader = new HttpHeaders({'Content-Type': 'application/dicom+json'});
    jsonHeader = new HttpHeaders({'Content-Type': 'application/json'});

    selectedElements:SelectionActionElement;
    constructor(
        private aeListService: AeListService,
        private $http: J4careHttpService,
        private storageSystems: StorageSystemsService,
        private devicesService: DevicesService,
        private webAppListService: WebAppsListService,
        private permissionService: PermissionService,
        private _keycloakService:KeycloakService,
        private appService:AppService
    ) {}

    getWebApps(filter?:any) {
        return this.webAppListService.getWebApps(filter)
            .pipe(map((webApp:any)=> this.webAppHasPermission(webApp)));
    }

    getEntrySchema(devices, aetWebService): { schema: FilterSchema, lineLength: number } {
        return {
            schema: j4care.prepareFlatFilterObject(Globalvar.STUDY_FILTER_ENTRY_SCHEMA(devices, aetWebService), 1),
            lineLength: 1
        }
    }
    getTokenService(studyWebService:StudyWebService){
        if(studyWebService && studyWebService.selectedWebService && _.hasIn(studyWebService.selectedWebService, "dcmKeycloakClientID")){
            return this.$http.getRealm(studyWebService.selectedWebService);
        }else{
            return this._keycloakService.getToken();
        }
    }

    /*
    * return patientid - combination of patient id, issuer
    * */
    getPatientId(patient) {
        console.log('patient', patient);
        let obj;
        if (_.hasIn(patient, '[0]')) {
            obj = patient[0];
        } else {
            obj = patient;
        }
        let patientId = '';
        if (obj.PatientID || (_.hasIn(obj, '["00100020"].Value[0]') && obj["00100020"].Value[0] != '')) {
            if (obj.PatientID) {
                patientId = obj.PatientID;
            }
            if (obj.IssuerOfPatientID) {
                patientId += '^^^' + obj.IssuerOfPatientID;
            }
            if (_.hasIn(obj, 'IssuerOfPatientIDQualifiers.UniversalEntityID')) {
                patientId += '&' + obj.IssuerOfPatientIDQualifiers.UniversalEntityID;
            }
            if (_.hasIn(obj, 'IssuerOfPatientIDQualifiers.UniversalEntityIDType')) {
                patientId += '&' + obj.IssuerOfPatientIDQualifiers.UniversalEntityIDType;
            }
            if (_.hasIn(obj, '["00100020"].Value[0]')) {
                patientId += obj["00100020"].Value[0];
            }
            if (_.hasIn(obj, '["00100021"].Value[0]'))
                patientId += '^^^' + obj["00100021"].Value[0];
            else {
                if (_.hasIn(obj, '["00100024"].Value[0]["00400032"].Value[0]') || _.hasIn(obj, '["00100024"].Value[0]["00400033"].Value[0]'))
                    patientId += '^^^';
            }
            if (_.hasIn(obj, '["00100024"].Value[0]["00400032"].Value[0]')) {
                patientId += '&' + obj['00100024'].Value[0]['00400032'].Value[0];
            }
            if (_.hasIn(obj, '["00100024"].Value[0]["00400033"].Value[0]')) {
                patientId += '&' + obj['00100024'].Value[0]['00400033'].Value[0];
            }
            return _.replace(patientId,"/","%2F");
        } else {
            return undefined;
        }
    }
    getUpsWorkitemUID(attr){
        return _.get(attr, "00080018.Value[0]")
    }

    removeUpsWorkitemUID(attr){
        //SOP Instance UID === WorkitemUID
        _.hasIn(attr, "00080018")
            delete attr["00080018"];
        return attr;
    }

    clearPatientObject(object) {
        let $this = this;
        _.forEach(object, function (m, i) {
            if (typeof(m) === 'object' && i != 'vr') {
                $this.clearPatientObject(m);
            } else {
                let check = typeof(i) === 'number' || i === 'vr' || i === 'Value' || i === 'Alphabetic' || i === 'Ideographic' || i === 'Phonetic' || i === 'items';
                if (!check) {
                    delete object[i];
                }
            }
        });
    };

    convertStringToNumber(object) {
        let $this = this;
        _.forEach(object, function (m, i) {
            if (typeof(m) === 'object' && i != 'vr') {
                $this.convertStringToNumber(m);
            } else {
                if (i === 'vr') {
                    if (($this.integerVr.indexOf(object.vr) > -1 && object.Value && object.Value.length > 0)) {
                        if (object.Value.length > 1) {
                            _.forEach(object.Value, (k, j) => {
                                object.Value[j] = Number(object.Value[j]);
                            });
                        } else {
                            object.Value[0] = Number(object.Value[0]);
                        }
                    }

                }
            }
        });
    };

    initEmptyValue(object) {
        _.forEach(object, (m, k) => {
            console.log('m', m);
            if (m && m.vr && m.vr === 'PN' && m.vr != 'SQ' && (!m.Value || m.Value[0] === null)) {
                console.log('in pnvalue=', m);
                object[k]['Value'] = [{
                    Alphabetic: ''
                }];
            }
            if (m && m.vr && m.vr != 'SQ' && !m.Value) {
                object[k]['Value'] = [''];
            }
            if (m && (_.isArray(m) || (m && _.isObject(m)))) {
                console.log('beforecall', m);
                this.initEmptyValue(m);
            }
        });
        return object;
    };

    replaceKeyInJson(object, key, key2) {
        let $this = this;
        _.forEach(object, function (m, k) {
            if (m[key]) {
                object[k][key2] = [object[k][key]];
                delete object[k][key];
            }
            if (m.vr && m.vr != 'SQ' && !m.Value) {
                if (m.vr === 'PN') {
                    object[k]['Value'] = object[k]['Value'] || [{Alphabetic: ''}];
                    object[k]['Value'] = [{Alphabetic: ''}];
                } else {
                    object[k]['Value'] = [''];
                }
            }
            if ((Object.prototype.toString.call(m) === '[object Array]') || (object[k] !== null && typeof(object[k]) == 'object')) {
                $this.replaceKeyInJson(m, key, key2);
            }
        });
        return object;
    };

    getArrayFromIod(res) {
        let dropdown = [];
        _.forEach(res, function (m, i) {
            if (i === '00400100') {
                _.forEach(m.items || m.Value[0], function (l, j) {
                    dropdown.push({
                        'code': '00400100:' + j,
                        'codeComma': '>' + j.slice(0, 4) + ',' + j.slice(4),
                        'name': DCM4CHE.elementName.forTag(j)
                    });
                });
            } else {
                dropdown.push({
                    'code': i,
                    'codeComma': i.slice(0, 4) + ',' + i.slice(4),
                    'name': DCM4CHE.elementName.forTag(i)
                });
            }
        });
        return dropdown;
    };

    clearFilterObject(tab: DicomMode, filterObject:StudyFilterConfig){
        const keys = this.getFilterKeysFromTab(tab);
        Object.keys(filterObject.filterModel).forEach(filterKey=>{
           if(keys.indexOf(filterKey) === -1){
               delete filterObject.filterModel[filterKey];
           }
        });
    }

    getFilterKeysFromTab(tab:DicomMode){
        if(tab){
            return (()=>{
                   switch (tab) {
                    case "patient":
                        return [
                            ...Globalvar.PATIENT_FILTER_SCHEMA([], false),
                            ...Globalvar.PATIENT_FILTER_SCHEMA([], true)
                        ].filter(filter => {
                            return filter.filterKey != "aet";
                        });
                        break;
                    case "mwl":
                        return [
                            ...Globalvar.MWL_FILTER_SCHEMA(false),
                            ...Globalvar.MWL_FILTER_SCHEMA(true)
                        ];
                        break;
                    case "uwl":
                        return [
                            ...Globalvar.UWL_FILTER_SCHEMA(false),
                            ...Globalvar.UWL_FILTER_SCHEMA(true)
                        ];
                        break;
                    case "diff":
                        return [
                            ...Globalvar.DIFF_FILTER_SCHEMA([],[],false),
                            ...Globalvar.DIFF_FILTER_SCHEMA([],[],true)
                        ].filter(filter => {
                            return filter.filterKey != "aet";
                        });
                        break;
                    default:
                        return [
                            ...Globalvar.STUDY_FILTER_SCHEMA([],false),
                            ...Globalvar.STUDY_FILTER_SCHEMA([],true)
                        ].filter(filter => {
                            return filter.filterKey != "aet";
                        });
                }
            })().map((filterSchemaElement:FilterSchemaElement)=>{
                return filterSchemaElement.filterKey;
            })
        }
        return [];
    }

    getFilterSchema(tab: DicomMode, aets: Aet[], quantityText: { count: string, size: string }, filterMode: ('main' | 'expand'), studyWebService?: StudyWebService, attributeSet?:SelectDropdown<DiffAttributeSet>[],showCount?:boolean, filter?:StudyFilterConfig) {
        let schema: FilterSchema;
        let lineLength: number = 3;
        switch (tab) {
            case "patient":
                schema = Globalvar.PATIENT_FILTER_SCHEMA(aets, filterMode === "expand").filter(filter => {
                    return filter.filterKey != "aet";
                });
                lineLength = filterMode === "expand" ? 1 : 3;
                break;
            case "mwl":
                schema = Globalvar.MWL_FILTER_SCHEMA( filterMode === "expand");
                lineLength = filterMode === "expand" ? 1 : 3;
                break;
            case "uwl":
                schema = Globalvar.UWL_FILTER_SCHEMA( filterMode === "expand");
                lineLength = filterMode === "expand" ? 1 : 3;
                break;
            case "diff":
                schema = Globalvar.DIFF_FILTER_SCHEMA(aets,attributeSet, filterMode === "expand").filter(filter => {
                    return filter.filterKey != "aet";
                });
                // lineLength = filterMode === "expand" ? 2 : 3;
                break;
            default:
                schema = Globalvar.STUDY_FILTER_SCHEMA(aets, filterMode === "expand").filter(filter => {
                    return filter.filterKey != "aet";
                });
                lineLength = 3;
        }
        if (filterMode === "main") {
            if (tab != 'diff') {
                let orderby;
                if(tab === "uwl"){
/*                    schema.push({
                        tag: "dummy"
                    });*/
                    orderby = [
                        new SelectDropdown('00741200', $localize `:@@asc_scheduled_procedure_step_priority:(asc)  Scheduled Procedure Step Priority`),
                        new SelectDropdown('-00741200', $localize `:@@desc_scheduled_procedure_step_priority:(desc) Scheduled Procedure Step Priority`),
                        new SelectDropdown('00404005', $localize `:@@asc_scheduled_procedure_step_start_date_and_time:(asc)  Scheduled Procedure Step Start Date and Time`),
                        new SelectDropdown('-00404005', $localize `:@@desc_scheduled_procedure_step_start_date_and_time:(desc) Scheduled Procedure Step Start Date and Time`),
                        new SelectDropdown('00404011', $localize `:@@asc_expected_completion_date_and_time:(asc)  Expected Completion Date and Time`),
                        new SelectDropdown('-00404011', $localize `:@@desc_expected_completion_date_and_time:(desc) Expected Completion Date and Time`)
                    ]
                }else{
                    orderby = Globalvar.ORDERBY_NEW
                        .filter(order => order.mode === tab)
                        .map(order => {
                            return new SelectDropdown(order.value, order.label, order.title, order.title, order.label);
                        });
                }
                schema.push({
                    tag: "html-select",
                    options: orderby,
                    filterKey: 'orderby',
                    text: $localize `:@@study.order_by:Order By`,
                    title: $localize `:@@study.order_by:Order By`,
                    placeholder: $localize `:@@study.order_by:Order By`,
                    cssClass: 'study_order'

                });
            }
            schema.push({
                tag: "html-select",
                options: studyWebService.webServices.map((webApp: DcmWebApp) => {
                    return new SelectDropdown(
                        webApp,
                        webApp.dcmWebAppName,
                        webApp.dicomDescription,
                        undefined,
                        undefined,
                        webApp,
                        (studyWebService.selectedWebService && studyWebService.selectedWebService.dcmWebAppName === webApp.dcmWebAppName)
                    );
                }),
                filterKey: 'webApp',
                text: $localize `:@@web_app_service:Web App Service`,
                title: $localize `:@@web_app_service:Web App Service`,
                showStar:tab === "diff",
                placeholder: $localize `:@@web_app_service:Web App Service`,
                cssClass: 'study_order',
                showSearchField: true
            });
            schema.push({
                tag: "button",
                id: "submit",
                text: $localize `:@@SUBMIT:SUBMIT`,
                description: tab === "diff" ? $localize `:@@study.show_diffs:Show DIFFs` : $localize `:@@query_studies:Query Studies`
            });
            if(tab != "diff" && tab != "uwl"){
                schema.push({
                    tag: "dummy"
                })
            }else{
/*                schema.push({
                    tag: "button",
                    id: "trigger_diff",
                    text: $localize `:@@trigger_diff:Trigger Diff`,
                    description: $localize `:@@study.trigger_diffs:Trigger DIFFs`
                });*/
            }
            if(tab != "diff"){
                if(showCount){
                    schema.push({
                        tag: "button",
                        id: "count",
                        text: quantityText.count,
                        showRefreshIcon: true,
                        showDynamicLoader: false,
                        description: $localize `:@@query_only_the_count:QUERY ONLY THE COUNT`
                    });
                }else{
                    schema.push({
                        tag: "dummy"
                    });
                }
            }
            if(tab === "study"){
                schema.push({
                    tag: "button",
                    id: "size",
                    showRefreshIcon: true,
                    showDynamicLoader: false,
                    text: quantityText.size,
                    description: $localize `:@@query_only_the_size:QUERY ONLY THE SIZE`
                })
            }
        }
        return {
            lineLength: lineLength,
            schema: j4care.prepareFlatFilterObject(schema, lineLength)
        }
    }


    getMWL(filterModel, dcmWebApp: DcmWebApp, responseType?: DicomResponseType): Observable<any> {
        let header: HttpHeaders;
        if (!responseType || responseType === "object") {
            header = this.dicomHeader
        }
        let params = j4care.objToUrlParams(filterModel);
        params = params ? `?${params}` : params;

        return this.$http.get(
            `${this.getDicomURL("mwl", dcmWebApp, responseType)}${params || ''}`,
            header,
            false,
            dcmWebApp
        )
    }

    getUWL(filterModel, dcmWebApp: DcmWebApp, responseType?: DicomResponseType): Observable<any> {
        let header: HttpHeaders;
        if (!responseType || responseType === "object") {
            header = this.dicomHeader
        }
        let params = j4care.objToUrlParams(filterModel);
        params = params ? `?${params}` : params;

        return this.$http.get(
            `${this.getDicomURL("uwl", dcmWebApp, responseType)}${params || ''}`,
            header,
            false,
            dcmWebApp
        )
    }

    triggerDiff(filterModel, studyWebService:StudyWebService, mode: DicomMode, dicomResponseType:DicomResponseType, file?:File, fileField?:number){
        if(_.get(studyWebService, "selectedWebService.dcmWebServiceClass") && studyWebService.selectedWebService.dcmWebServiceClass.indexOf("DCM4CHEE_ARC_AET_DIFF") > -1){
            if(dicomResponseType === "csv"){
                return this.$http.post(this.getDicomURL(mode,studyWebService.selectedWebService, dicomResponseType, fileField) + j4care.objToUrlParams(filterModel, true), file);
            }else{
                return this.$http.get(this.getDicomURL(mode,studyWebService.selectedWebService, dicomResponseType) + j4care.objToUrlParams(filterModel, true))
            }
        }else{
            return throwError({error:$localize `:@@webapp_with_service_class_not_found:Web Application Service with the web service class ${'DCM4CHEE_ARC_AET_DIFF'}:@@webServiceClass: not found!`})
        }
    }
    getDiff(filterModel, studyWebService: StudyWebService, responseType?: DicomResponseType): Observable<any> {
        //http://shefki-lifebook:8080/dcm4chee-arc/monitor/diff/batch/testnew34/studies
        let header: HttpHeaders;
        if (!responseType || responseType === "object") {
            header = this.dicomHeader
        }
        let batchID;
        let taskPK;
        let url;
        if((_.hasIn(filterModel,"batchID") && _.get(filterModel,"batchID") != "") || (_.hasIn(filterModel,"taskPK") && _.get(filterModel,"taskPK") != "")){
            if(_.hasIn(filterModel,"batchID") && _.get(filterModel,"batchID") != ""){
                batchID = _.get(filterModel,"batchID");
                url = `../monitor/diff/batch/${batchID}/studies${j4care.param(filterModel)}`
            }else{
                taskPK = _.get(filterModel,"taskPK");
                url = `../monitor/diff/${taskPK}/studies${j4care.param(filterModel)}`
            }
            delete filterModel["batchID"];
            delete filterModel["taskPK"];
        }
        if(batchID || taskPK){
            return this.$http.get(
                url,
                header
            )
        }else{
            return this.getWebAppFromWebServiceClassAndSelectedWebApp(studyWebService, "DCM4CHEE_ARC_AET_DIFF", "DCM4CHEE_ARC_AET_DIFF")
                .pipe(map(webApp=>{
                        return `${j4care.getUrlFromDcmWebApplication(webApp)}`;
                })).pipe(switchMap(url=>{
                return this.$http.get(
                    `${url}${j4care.param(filterModel) || ''}`,
                    header
                )
            }));
        }
    }

    getDiffHeader(study,code){
        let value;
        let sqValue;
        if(_.hasIn(study,[code,"Value",0])){
            if(study[code].vr === "PN"){
                if(_.hasIn(study,["04000561","Value",0,"04000550","Value",0,code,"Value",0,"Alphabetic"])){
                    value =  _.get(study,[code,"Value",0,"Alphabetic"]);
                    sqValue = _.get(study,["04000561","Value",0,"04000550","Value",0,code,"Value",0,"Alphabetic"]);
                    if(value === sqValue){
                        return {
                            Value: [value],
                            showBorder:false
                        }
                    }else{
                        return {
                            Value: [value + "/" + sqValue],
                            showBorder:true
                        }
                    }
                }else{
                    return {
                        Value: [study[code].Value[0].Alphabetic],
                        showBorder:false
                    }
                }
            }else{
                //00200010
                switch(code) {
                    case "00080061":
                        value = new FormatAttributeValuePipe().transform(study[code]);
                        // value = _.get(study,[code,"Value", 0]);
                        if(_.hasIn(study,["04000561","Value",0,"04000550","Value",0,code,"Value",0])){
                            sqValue = new FormatAttributeValuePipe().transform(_.get(study,["04000561","Value",0,"04000550","Value",0,code]));
                            // sqValue = _.get(study,["04000561","Value",0,"04000550","Value",0,code, "Value",0]);
                            if(value === sqValue){
                                return {
                                    Value: [value],
                                    showBorder:false
                                }
                            }else{
                                return {
                                    Value: [value + "/" + sqValue],
                                    showBorder:true
                                }
                            }
                        }
                        break;
                    case "00080020":
                        value = new FormatDAPipe().transform(_.get(study,[code,"Value",0]));
                        // value = _.get(study,[code,"Value",0]);
                        if(_.hasIn(study,["04000561","Value",0,"04000550","Value",0,code,"Value",0])){
                            sqValue = new FormatDAPipe().transform(_.get(study,["04000561","Value",0,"04000550","Value",0,code,"Value",0]));
                            // sqValue = _.get(study,["04000561","Value",0,"04000550","Value",0,code,"Value",0]);
                            if(value === sqValue){
                                return {
                                    Value: [value],
                                    showBorder:false
                                }
                            }else{
                                return {
                                    Value: [value + "/" + sqValue],
                                    showBorder:true
                                }
                            }
                        }
                        break;
                    case "00080030":
                        value = new FormatTMPipe().transform(_.get(study,[code,"Value",0]));
                        // value = _.get(study,[code,"Value",0]);
                        if(_.hasIn(study,["04000561","Value",0,"04000550","Value",0,code,"Value",0])){
                            sqValue = new FormatTMPipe().transform(_.get(study,["04000561","Value",0,"04000550","Value",0,code,"Value",0]));
                            // sqValue = _.get(study,["04000561","Value",0,"04000550","Value",0,code,"Value",0]);
                            if(value === sqValue){
                                return {
                                    Value: [value],
                                    showBorder:false
                                }
                            }else{
                                return {
                                    Value: [value + "/" + sqValue],
                                    showBorder:true
                                }
                            }
                        }
                        break;
                    default:
                        if(_.hasIn(study,["04000561","Value",0,"04000550","Value",0,code,"Value",0])){
                            value = _.get(study,[code,"Value",0]);
                            sqValue = _.get(study,["04000561","Value",0,"04000550","Value",0,code,"Value",0]);
                            if(value === sqValue){
                                return {
                                    Value: [value],
                                    showBorder:false
                                }
                            }else{
                                return {
                                    Value: [value + "/" + sqValue],
                                    showBorder:true
                                }
                            }
                        }
                }
            }
            return {
                Value: [study[code].Value[0]],
                showBorder:false
            }
        }else{
            return {
                Value: [""],
                showBorder:false
            }
        }
    }

    deletePatient(dcmWebApp: DcmWebApp, patientId:string){
        return this.$http.delete(`${this.getDicomURL("patient", dcmWebApp)}/${patientId}`, undefined, true);
    }

    deleteMWL(dcmWebApp: DcmWebApp, studyInstanceUID:string, scheduledProcedureStepID:string,  responseType?: DicomResponseType){
        return this.$http.delete(`${this.getDicomURL("mwl", dcmWebApp, responseType)}/${studyInstanceUID}/${scheduledProcedureStepID}`);
    }

    getPatients(filterModel, dcmWebApp: DcmWebApp, responseType?: DicomResponseType): Observable<any> {
        let header: HttpHeaders;
        if (!responseType || responseType === "object") {
            header = this.dicomHeader
        }
        let params = j4care.objToUrlParams(filterModel);
        params = params ? `?${params}` : params;

        return this.$http.get(
            `${this.getDicomURL("patient", dcmWebApp, responseType)}${params || ''}`,
            header,
            false,
            dcmWebApp
        )
    }

    getStudies(filterModel, dcmWebApp: DcmWebApp, responseType?: DicomResponseType): Observable<any> {
        let header: HttpHeaders;
        if (!responseType || responseType === "object") {
            header = this.dicomHeader
        }
        let params = j4care.objToUrlParams(filterModel);
        params = params ? `?${params}` : params;

        return this.$http.get(
            `${this.getDicomURL("study", dcmWebApp, responseType)}${params || ''}`,
            header,
            false,
            dcmWebApp
        );
    }

    getSeries(studyInstanceUID: string, filterModel: any, dcmWebApp: DcmWebApp, responseType?: DicomResponseType): Observable<any> {
        let header;
        if (!responseType || responseType === "object") {
            header = this.dicomHeader
        }
        let params = j4care.objToUrlParams(filterModel);
        params = params ? `?${params}` : params;

        return this.$http.get(
            `${this.getDicomURL("study", dcmWebApp, responseType)}/${studyInstanceUID}/series${params || ''}`,
            header,
            false,
            dcmWebApp
        );
    }

    testAet(url, dcmWebApp: DcmWebApp) {
        return this.$http.get(
            url,//`http://test-ng:8080/dcm4chee-arc/ui2/rs/aets`,
            this.jsonHeader,
            false,
            dcmWebApp
        );
    }

    getInstances(studyInstanceUID: string, seriesInstanceUID: string, filterModel: any, dcmWebApp: DcmWebApp, responseType?: DicomResponseType): Observable<any> {
        let header: HttpHeaders;
        if (!responseType || responseType === "object") {
            header = this.dicomHeader
        }
        let params = j4care.objToUrlParams(filterModel);
        params = params ? `?${params}` : params;

        return this.$http.get(
            `${this.getDicomURL("study", dcmWebApp, responseType)}/${studyInstanceUID}/series/${seriesInstanceUID}/instances${params || ''}`,
            header,
            false,
            dcmWebApp
        );
    }

    getStudyInstanceUID(model){
        try{
            return _.get(model, "0020000D.Value[0]");
        }catch (e) {
            return undefined;
        }
    }

    getDicomURL(mode: DicomMode, dcmWebApp: DcmWebApp, responseType?: DicomResponseType, csvField?:number): string {
        console.log("object", dcmWebApp);
        if(dcmWebApp){
            try {
                let url = j4care.getUrlFromDcmWebApplication(dcmWebApp);
                if(url){
                    switch (mode) {
                        case "patient":
                            url += '/patients';
                            break;
                        case "mwl":
                            url += '/mwlitems';
                            break;
                        case "uwl":
                            url += '/workitems';
                            break;
                        case "export":
                            url += '/studies/export';
                            break;
                        case "study":
                            url += '/studies';
                            break;
                        case "diff":
                            // url = this.diffUrl(callingAet, externalAet, secondExternalAet, baseUrl);
                            //TODO
                            break;
                        default:
                            url;
                    }
                    if (mode != "diff" && responseType) {
                        if (responseType === "count")
                            url += '/count';
                        if (responseType === "size")
                            url += '/size';
                    }
                    if (responseType && responseType === "csv")
                        url += `/csv:${csvField}`;
                    return url;
                }else{
                    j4care.log('Url is undefined');
                }
            } catch (e) {
                j4care.log("Error on getting dicomURL in study.service.ts", e);
            }
        }else{
            j4care.log("WebApp is undefined");
        }
    }

    wadoURL(webService: StudyWebService, ...args: any[]): Observable<string> {
        let arg = arguments;
        return this.getWebAppFromWebServiceClassAndSelectedWebApp(webService, "WADO_URI", "WADO_URI").pipe(map(webApp=>{
            let i,
                url = `${j4care.getUrlFromDcmWebApplication(webApp)}?requestType=WADO`;
            for (i = 1; i < arg.length; i++) {
                _.forEach(arg[i], (value, key) => {
                    url += '&' + key.replace(/^(_){1}(\w*)/, (match, p1, p2) => {
                        return p2;
                    }) + '=' + value;
                });
            }
            return url;
        }));
    }

    renderURL(webService: StudyWebService,inst):Observable<string> {
        if (inst.video)
            return this.wadoURL(webService, inst.wadoQueryParams, {contentType: 'video/*'});
        if (inst.numberOfFrames)
            return this.wadoURL(webService, inst.wadoQueryParams, {contentType: 'image/jpeg', frameNumber: inst.view});
        if (inst.gspsQueryParams.length)
            return this.wadoURL(webService, inst.gspsQueryParams[inst.view - 1]);
        return this.wadoURL(webService, inst.wadoQueryParams);
    }

    private diffUrl(callingAet: Aet, firstExternalAet?: Aet, secondExternalAet?: Aet, baseUrl?: string) {

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

    getAttributeFilter(entity?: string, baseUrl?: string) {
        return this.$http.get(
            `${baseUrl || '..'}/attribute-filter/${entity || "Patient"}`
        )
        .pipe(map(res => {
            if ((!entity || entity === "Patient") && res["dcmTag"]) {
                let privateAttr = [parseInt('77770010', 16), parseInt('77771010', 16), parseInt('77771011', 16)];
                res["dcmTag"].push(...privateAttr);
            }
            return res;
        }));
    }

    getDiffAttributeSet = (baseUrl?: string) => this.$http.get(`${baseUrl || '..'}/attribute-set/DIFF_RS`);

    getAets = () => this.aeListService.getAets();

    getAes = () => this.aeListService.getAes();

    equalsIgnoreSpecificCharacterSet(attrs, other) {
        return Object.keys(attrs).filter(tag => tag != '00080005')
                .every(tag => _.isEqual(attrs[tag], other[tag]))
            && Object.keys(other).filter(tag => tag != '00080005')
                .every(tag => attrs[tag]);
    }

    queryPatientDemographics(patientID: string, PDQServiceID: string, url?: string) {
        return this.$http.get(`${url || '..'}/pdq/${PDQServiceID}/patients/${patientID}`);
    }
    queryNationalPatientRegister(patientID){
        return this.$http.get(`../xroad/RR441/${patientID}`)
    }

    extractAttrs(attrs, tags, extracted) {
        for (let tag in attrs) {
            if (_.indexOf(tags, tag) > -1) {
                extracted[tag] = attrs[tag];
            }
        }
    }

    createGSPSQueryParams(attrs): GSPSQueryParams[] {
        let sopClass = j4care.valueOf(attrs['00080016']),
            refSeries = j4care.valuesOf(attrs['00081115']),
            queryParams: GSPSQueryParams[] = [];
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

    studyURL(attrs, webApp: DcmWebApp) {
        return `${this.getDicomURL("study", webApp)}/${attrs['0020000D'].Value[0]}`;
    }

    seriesURL(attrs, webApp: DcmWebApp) {
        return this.studyURL(attrs, webApp) + '/series/' + attrs['0020000E'].Value[0];
    }

    instanceURL(attrs, webApp: DcmWebApp) {
        return this.seriesURL(attrs, webApp) + '/instances/' + attrs['00080018'].Value[0];
    }

    getObjectUniqueId(attrs: any[], dicomLevel: DicomLevel): UniqueSelectIdObject {
        let idObject = {
            id: this.getPatientId(attrs),
            idParts: [this.getPatientId(attrs)]
        };
        if (dicomLevel != "patient") {
            idObject.id += `_${attrs['0020000D'].Value[0]}`;
            idObject.idParts.push(attrs['0020000D'].Value[0]);
        }
        if (dicomLevel === "series" || dicomLevel === "instance") {
            idObject.id += `_${attrs['0020000E'].Value[0]}`;
            idObject.idParts.push(attrs['0020000E'].Value[0]);
        }
        if (dicomLevel === "instance") {
            idObject.id += `_${attrs['00080018'].Value[0]}`;
            idObject.idParts.push(attrs['00080018'].Value[0]);
        }
        if (dicomLevel === "mwl" && _.hasIn(attrs,"[00400100].Value[0][00400009].Value[0]")) {
            idObject.id += `_${_.get(attrs,"[00400100].Value[0][00400009].Value[0]")}`;
            idObject.idParts.push(_.get(attrs,"[00400100].Value[0][00400009].Value[0]"));
        }
        return idObject;
    }

    getURL(attrs, webApp: DcmWebApp, dicomLevel: DicomLevel) {
        if (dicomLevel === "series")
            return this.seriesURL(attrs, webApp);
        if (dicomLevel === "instance")
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

    isVideo(attrs): boolean {
        let sopClass = j4care.valueOf(attrs['00080016']);
        return [
            '1.2.840.10008.5.1.4.1.1.77.1.1.1',
            '1.2.840.10008.5.1.4.1.1.77.1.2.1',
            '1.2.840.10008.5.1.4.1.1.77.1.4.1']
            .indexOf(sopClass) != -1;
    }

    isImage(attrs): boolean {
        let sopClass = j4care.valueOf(attrs['00080016']);
        let bitsAllocated = j4care.valueOf(attrs['00280100']);
        return ((bitsAllocated && bitsAllocated != "") && (sopClass != '1.2.840.10008.5.1.4.1.1.481.2'));
    }

    createArray(n): any[] {
        let a = [];
        for (let i = 1; i <= n; i++)
            a.push(i);
        return a;
    }

    getStorageSystems() {
        return this.storageSystems.search({}, 0);
    }

    verifyStorage = (attrs, studyWebService: StudyWebService, level: DicomLevel, param) => {
        let url = `${this.getURL(attrs, studyWebService.selectedWebService, level)}/stgver${j4care.param(param)}`;

        return this.$http.post(url, {}, this.dicomHeader);
    };

    scheduleStorageVerification = (param, studyWebService: StudyWebService) => this.$http.post(`${this.getDicomURL("study", studyWebService.selectedWebService)}/stgver${j4care.param(param)}`, {});

    supplementIssuer = (issuer:string, testSupplement:string, param, studyWebService: StudyWebService) => {
        let paramString = `${j4care.param(param)}`;
        paramString = testSupplement
                    ? paramString == ''
                        ? '?test=' + testSupplement
                        : paramString + '&test=' + testSupplement
                    : paramString;
        return this.$http.post(
            `${this.getDicomURL("patient", studyWebService.selectedWebService)}/issuer/${issuer}${paramString}`,
            {});
    };

    storageVerificationForSelected(multipleObjects: SelectionActionElement, studyWebService: StudyWebService, param){
        return forkJoin((<any[]> multipleObjects.getAllAsArray().filter((element: SelectedDetailObject) =>
            (element.dicomLevel === "study" || element.dicomLevel === "instance" || element.dicomLevel === "series"))
            .map((element: SelectedDetailObject) => {
            return this.$http.post(
                `${this.getURL(element.object.attrs, studyWebService.selectedWebService, element.dicomLevel)}/stgver${j4care.param(param)}`,
                {}
            );
        })));
    }

    sendStorageCommitmentRequestForMatching(studyWebService: StudyWebService,stgCmtSCP:string, filters:any){
        return this.$http.post(
            `${this.getDicomURL("study", studyWebService.selectedWebService)}/stgcmt/${stgCmtSCP}${j4care.param(filters)}`,
            {}
        );
    }
    sendStorageCommitmentRequestForSelected(multipleObjects: SelectionActionElement, studyWebService: StudyWebService, stgCmtSCP:string){
        return forkJoin((<any[]> multipleObjects.getAllAsArray().filter((element: SelectedDetailObject) => (element.dicomLevel === "study" || element.dicomLevel === "instance" || element.dicomLevel === "series")).map((element: SelectedDetailObject) => {
            return this.$http.post(
                `${this.getURL(element.object.attrs, studyWebService.selectedWebService, element.dicomLevel)}/stgcmt/dicom:${stgCmtSCP}`,
                {}
            );
        })));
    }
    sendStorageCommitmentRequestForSingle(attrs,studyWebService: StudyWebService, level: DicomLevel, stgCmtSCP:string){
        let url = `${this.getURL(attrs, studyWebService.selectedWebService, level)}/stgcmt/dicom:${stgCmtSCP}`;
        return this.$http.post(url, {});
    }
    sendInstanceAvailabilityNotificationForMatching(studyWebService: StudyWebService,ianscp:string, filters:any){
        return this.$http.post(
            `${this.getDicomURL("study", studyWebService.selectedWebService)}/ian/${ianscp}${j4care.param(filters)}`,
            {}
        );
    }
    sendInstanceAvailabilityNotificationForSelected(multipleObjects: SelectionActionElement, studyWebService: StudyWebService, ianscp:string){
        return forkJoin((<any[]> multipleObjects.getAllAsArray().filter((element: SelectedDetailObject) => (element.dicomLevel === "study" || element.dicomLevel === "instance" || element.dicomLevel === "series")).map((element: SelectedDetailObject) => {
            return this.$http.post(
                `${this.getURL(element.object.attrs, studyWebService.selectedWebService, element.dicomLevel)}/ian/dicom:${ianscp}`,
                {}
            );
        })));
    }
    sendInstanceAvailabilityNotificationForSingle(attrs,studyWebService: StudyWebService, level: DicomLevel, ianscp:string){
        let url = `${this.getURL(attrs, studyWebService.selectedWebService, level)}/ian/dicom:${ianscp}`;
        return this.$http.post(url, {});
    }

    getDevices() {
        return this.devicesService.getDevices();
    }

    checkSchemaPermission(schema: DicomTableSchema): DicomTableSchema {
        Object.keys(schema).forEach(levelKey => {
            schema[levelKey].forEach((element: TableSchemaElement) => {
                if (element && element.type) {
                    if (element.type === "actions" || element.type === "actions-menu") {
                        let key = "actions";
                        if (_.hasIn(element, "menu") && element.menu) {
                            key = "menu.actions";
                        }
                        if (_.get(element, key) && (<any[]>_.get(element, key)).length > 0) {
                            let result = (<any[]>_.get(element, key)).filter((menu: TableAction) => {
                                if (menu.permission) {
                                    return this.permissionService.checkVisibility(menu.permission);
                                }
                                return true
                            });
                            _.set(element, key, result);
                        }
                    }
                } else {
                    return false;
                }
            })
        });
        console.log("schema", schema);
        return schema;
    }

    selectedWebServiceHasClass(selectedWebService:DcmWebApp, serviceClass:string):boolean{
        if(selectedWebService && serviceClass && serviceClass != ""){
            return _.hasIn(selectedWebService,"dcmWebServiceClass") && (<string[]>_.get(selectedWebService,"dcmWebServiceClass")).indexOf(serviceClass) > -1;
        }
        return false;
    }

    PATIENT_STUDIES_TABLE_SCHEMA($this, actions, options: StudySchemaOptions): DicomTableSchema {
        let schema: DicomTableSchema = {
            patient: [
                new TableSchemaElement({
                    type: "dummy",
                    pxWidth: 35,
                }),
                new TableSchemaElement({
                    type: "actions-menu",
                    header: "",
                    menu: {
                        toggle: (e) => {
                            console.log("e", e);
                            e.showMenu = !e.showMenu;
                        },
                        actions: [
                            {
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-unchecked',
                                    text: ''
                                },
                                click: (e) => {
                                    e.selected = !e.selected;
                                },
                                title: $localize `:@@select:Select`,
                                showIf: (e, config) => {
                                    return !config.showCheckboxes && !e.selected;
                                }
                            }, {
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-check',
                                    text: ''
                                },
                                click: (e) => {
                                    console.log("e", e);
                                    e.selected = !e.selected;
                                },
                                title: $localize `:@@unselect:Unselect`,
                                showIf: (e, config) => {
                                    return !config.showCheckboxes && e.selected;
                                }
                            },{
                                icon: {
                                    tag: 'span',
                                    cssClass: 'xroad_icon',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "patient",
                                        action: "pdq_patient"
                                    }, e);
                                },
                                title: $localize `:@@study.query_patient_demographics_service:Query Patient Demographics Service`,
                                showIf: (e, config) => {
                                    return options.appService['xRoad'] || (options.appService.global['PDQs'] && options.appService.global['PDQs'].length > 0);
                                }
                            },
                            {
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-pencil',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "patient",
                                        action: "edit_patient"
                                    }, e);
                                },
                                showIf:(e,config)=>{
                                    return  this.selectedWebServiceHasClass(options.selectedWebService,"DCM4CHEE_ARC_AET")
                                },
                                title: $localize `:@@study.edit_this_patient:Edit this Patient`,
                                permission: {
                                    id: 'action-studies-patient',
                                    param: 'edit'
                                }
                            },
                            {
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-remove',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "patient",
                                        action: "delete_patient"
                                    }, e);
                                },
                                title: $localize `:@@study.delete_this_patient:Delete this Patient`,
                                permission: {
                                    id: 'action-studies-patient',
                                    param: 'delete'
                                },
                                showIf: (e, config) => {
                                    return (
                                        (
                                            _.hasIn(e,'attrs.00201200.Value[0]') &&
                                            e.attrs['00201200'].Value[0] == "0" &&
                                            !(_.hasIn(options,"selectedWebService.dicomAETitleObject.dcmAllowDeletePatient") && _.get(options,"selectedWebService.dicomAETitleObject.dcmAllowDeletePatient") === "NEVER")
                                        ) ||
                                        (_.hasIn(options,"selectedWebService.dicomAETitleObject.dcmAllowDeletePatient") && _.get(options,"selectedWebService.dicomAETitleObject.dcmAllowDeletePatient") === "ALWAYS")
                                    ) && this.selectedWebServiceHasClass(options.selectedWebService,"DCM4CHEE_ARC_AET");
                                }
                            },{
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-plus',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "patient",
                                        action: "create_mwl"
                                    }, e);
                                },showIf:(e,config)=>{
                                    return  this.selectedWebServiceHasClass(options.selectedWebService,"DCM4CHEE_ARC_AET")
                                },
                                title: $localize `:@@study.add_new_mwl:Add new MWL`,
                                permission: {
                                    id: 'action-studies-mwl',
                                    param: 'create'
                                }
                            },
                            {
                                icon: {
                                    tag: 'i',
                                    cssClass: 'material-icons',
                                    text: 'file_upload'
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "patient",
                                        action: "upload_file"
                                    }, e);
                                },
                                title: $localize `:@@upload_file:Upload file`,
                                permission: {
                                    id: 'action-studies-study',
                                    param: 'upload'
                                }
                            }, {
                                icon: {
                                    tag: 'span',
                                    cssClass: `custom_icon csv_icon_black`,
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "study",
                                        action: "download_csv"
                                    }, e);
                                },showIf:(e,config)=>{
                                    return  this.selectedWebServiceHasClass(options.selectedWebService,"DCM4CHEE_ARC_AET")
                                },
                                title: $localize `:@@study.download_as_csv:Download as CSV`,
                                permission: {
                                    id: 'action-studies-download',
                                    param: 'visible'
                                }
                            }, {
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-eye-open',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "patient",
                                        action: "open_viewer"
                                    }, e);
                                },
                                title: $localize `:@@study.open_patient_in_the_viewer:Open patient in the viewer`,
                                permission: {
                                    id: 'action-studies-viewer',
                                    param: 'visible'
                                },
                                showIf: (e, config) => {
                                    return _.hasIn(options,"selectedWebService.IID_PATIENT_URL");
                                }
                            }
                        ]
                    },
                    headerDescription: $localize `:@@actions:Actions`,
                    pxWidth: 35
                }),
                new TableSchemaElement({
                    type: "actions",
                    header: "",
                    actions: [
                        {
                            icon: {
                                tag: 'span',
                                cssClass: 'glyphicon glyphicon-th-list',
                                text: ''
                            },
                            click: (e) => {
                                console.log("e", e);
                                e.showAttributes = !e.showAttributes;
                            },
                            title: $localize `:@@study.toggle_attributes:Toggle Attributes`
                        }
                    ],
                    headerDescription: $localize `:@@actions:Actions`,
                    pxWidth: 40
                }),
                new TableSchemaElement({
                    type: "actions",
                    header: "",
                    actions: [
                        {
                            icon: {
                                tag: 'span',
                                cssClass: 'glyphicon glyphicon-chevron-down',
                                text: ''
                            },
                            click: (e) => {
                                console.log("e", e);
/*                                if(options.studyConfig.tab === "mwl") {
                                    e.showMwls = !e.showMwls;
                                }else{
                                    if(options.studyConfig.tab === "diff") {
                                        e.showDiffs = !e.showDiffs;
                                    }else{
                                        actions.call($this, {
                                            event: "click",
                                            level: "patient",
                                            action: "toggle_studies"
                                        }, e);
                                    }
                                }*/
                                switch (options.studyConfig.tab) {
                                    case "mwl":
                                        e.showMwls = !e.showMwls;
                                        break;
                                    case "diff":
                                        e.showDiffs = !e.showDiffs;
                                        break;
                                    case "uwl":
                                        e.showUwls = !e.showUwls;
                                        break;
                                    default:
                                        actions.call($this, {
                                            event: "click",
                                            level: "patient",
                                            action: "toggle_studies"
                                        }, e);
                                }
                            },
                            title:((string,...keys)=> {
                                let msg = "Studies";
                                switch (options.studyConfig.tab) {
                                    case "mwl":
                                        msg = "MWLs";
                                    case "diff":
                                        msg = "DIFFs";
                                    case "uwl":
                                        msg = "UWLs";
                                }
                                return string[0] + msg;
                            })`Hide ${''}`,
                            showIf: (e) => {
                                switch (options.studyConfig.tab) {
                                    case "mwl":
                                        return e.showMwls;
                                    case "diff":
                                        return e.showDiffs;
                                    case "uwl":
                                        return e.showUwls;
                                    default:
                                        return e.showStudies;
                                }
                            }
                        }, {
                            icon: {
                                tag: 'span',
                                cssClass: 'glyphicon glyphicon-chevron-right',
                                text: ''
                            },
                            click: (e) => {
                                console.log("e", e);
                                // e.showStudies = !e.showStudies;
                                switch (options.studyConfig.tab) {
                                    case "mwl":
                                        e.showMwls = !e.showMwls;
                                        break;
                                    case "diff":
                                        e.showDiffs = !e.showDiffs;
                                        break;
                                    case "uwl":
                                        e.showUwls = !e.showUwls;
                                        break;
                                    default:
                                        actions.call($this, {
                                            event: "click",
                                            level: "patient",
                                            action: "toggle_studies"
                                        }, e);
                                }
                                // actions.call(this, 'study_arrow',e);
                            },
                            title: ((string,...keys) => {  //TODO change the code so you can use $localize
                                let msg = "Studies";
                                switch (options.studyConfig.tab) {
                                    case "mwl":
                                        msg = "MWLs";
                                    case "diff":
                                        msg = "DIFFs";
                                    case "uwl":
                                        msg = "UWLs";
                                }
                                return string[0] + msg;
                            })`Show ${''}`
                            ,
                            showIf: (e) => {
                                switch (options.studyConfig.tab) {
                                    case "mwl":
                                        return !e.showMwls;
                                    case "diff":
                                        return !e.showDiffs;
                                    case "uwl":
                                        return !e.showUwls;
                                    default:
                                        return !e.showStudies;
                                }
                            }
                        }
                    ],
                    headerDescription: ((string,...keys) => { //TODO change the code so you can use $localize
                        let msg = "Studies";
                        switch (options.studyConfig.tab) {
                            case "mwl":
                                msg = "MWLs";
                            case "diff":
                                msg = "DIFFs";
                            case "uwl":
                                msg = "UWLs";
                        }
                        return string[0] + msg;
                    })`Toggle ${''}`,
                    pxWidth: 40
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@patients_name:Patient's Name`,
                    pathToValue: "00100010.Value[0].Alphabetic",
                    headerDescription: $localize `:@@patients_name:Patient's Name`,
                    widthWeight: 1,
                    calculatedWidth: "20%",
                    cssClass:"border-left"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@patient_id:Patient ID`,
                    pathToValue: "00100020.Value[0]",
                    headerDescription: $localize `:@@patient_id:Patient ID`,
                    widthWeight: 1,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "pipe",
                    header: $localize `:@@issuer_of_patient:Issuer of Patient`,
                    headerDescription: $localize `:@@issuer_of_patient:Issuer of Patient`,
                    widthWeight: 1.5,
                    calculatedWidth: "20%",
                    pipe: new DynamicPipe(PatientIssuerPipe, undefined)
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@birth_date:Birth Date`,
                    pathToValue: "00100030.Value[0]",
                    headerDescription: $localize `:@@patients_birth_date:Patient's Birth Date`,
                    widthWeight: 0.5,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@sex:Sex`,
                    pathToValue: "00100040.Value[0]",
                    headerDescription: $localize `:@@patients_sex:Patient's Sex`,
                    widthWeight: 0.2,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@study.patient_comments:Patient Comments`,
                    pathToValue: "00104000.Value[0]",
                    headerDescription: $localize `:@@study.patient_comments:Patient Comments`,
                    widthWeight: 3,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@number_of_related_studies:#S`,
                    pathToValue: "00201200.Value[0]",
                    headerDescription: $localize `:@@number_of_patient_related_studies:Number of Patient Related Studies`,
                    widthWeight: 0.2,
                    calculatedWidth: "20%"
                })
            ],
            studies: [
                new TableSchemaElement({
                    type: "index",
                    header: '',
                    pathToValue: '',
                    pxWidth: 40
                }),
                new TableSchemaElement({
                    type: "actions-menu",
                    header: "",
                    menu: {
                        toggle: (e) => {
                            console.log("e", e);
                            e.showMenu = !e.showMenu;
                        },
                        actions: [
                            {
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-unchecked',
                                    text: ''
                                },
                                click: (e) => {
                                    e.selected = !e.selected;
                                    actions.call($this, {
                                        event: "click",
                                        level: "study",
                                        action: "select"
                                    }, e);
                                },
                                title: $localize `:@@select:Select`,
                                showIf: (e, config) => {
                                    return !config.showCheckboxes && !e.selected;
                                }
                            },{
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-check',
                                    text: ''
                                },
                                click: (e) => {
                                    console.log("e", e);
                                    e.selected = !e.selected;
                                    actions.call($this, {
                                        event: "click",
                                        level: "study",
                                        action: "select"
                                    }, e);
                                },
                                title: $localize `:@@unselect:Unselect`,
                                showIf: (e, config) => {
                                    return !config.showCheckboxes && e.selected;
                                }
                            },
                            {
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-pencil',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "study",
                                        action: "edit_study"
                                    }, e);
                                },
                                title: $localize `:@@study.edit_this_study:Edit this study`,
                                showIf:(e,config)=>{
                                    return  this.selectedWebServiceHasClass(options.selectedWebService,"DCM4CHEE_ARC_AET")
                                },
                                permission: {
                                    id: 'action-studies-study',
                                    param: 'edit'
                                }
                            }, {
                                icon: {
                                    tag: 'i',
                                    cssClass: 'material-icons',
                                    text: 'history'
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "study",
                                        action: "modify_expired_date"
                                    }, e);
                                },
                                title: $localize `:@@set_change_expired_date:Set/Change expired date`,
                                showIf:(e,config)=>{
                                    return  this.selectedWebServiceHasClass(options.selectedWebService,"DCM4CHEE_ARC_AET")
                                },
                                permission: {
                                    id: 'action-studies-study',
                                    param: 'edit'
                                }
                            }, {
                                icon: {
                                    tag: 'span',
                                    cssClass: options.trash.active ? 'glyphicon glyphicon-repeat' : 'glyphicon glyphicon-trash',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "study",
                                        action: "reject"
                                    }, e);
                                },
                                title: options.trash.active ? $localize `:@@study.restore_study:Restore study` : $localize `:@@study.reject_study:Reject study`,
                                permission: {
                                    id: 'action-studies-study',
                                    param: options.trash.active ? 'restore' : 'reject'
                                }
                            }, {
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-ok',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "study",
                                        action: "verify_storage"
                                    }, e);
                                },
                                title: $localize `:@@study.verify_storage_commitment:Verify storage commitment`,
                                showIf:(e,config)=>{
                                    return  this.selectedWebServiceHasClass(options.selectedWebService,"DCM4CHEE_ARC_AET")
                                },
                                permission: {
                                    id: 'action-studies-verify_storage_commitment',
                                    param: 'visible'
                                }
                            }, {
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-save',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "study",
                                        action: "download"
                                    }, e);
                                },
                                title: $localize `:@@study.retrieve_study:Retrieve Study`,
                                permission: {
                                    id: 'action-studies-download',
                                    param: 'visible'
                                }
                            },{
                                icon: {
                                    tag: 'i',
                                    cssClass: 'material-icons',
                                    text: 'file_upload'
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "study",
                                        action: "upload_file"
                                    }, e);
                                },
                                title: $localize `:@@upload_file:Upload file`,
                                permission: {
                                    id: 'action-studies-study',
                                    param: 'upload'
                                }
                            }, {
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-export',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "study",
                                        action: "export"
                                    }, e);
                                },
                                title: $localize `:@@study.export_study:Export study`,
                                permission: {
                                    id: 'action-studies-study',
                                    param: 'export'
                                }
                            }, {
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-remove',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "study",
                                        action: "delete"
                                    }, e);
                                },
                                title: $localize `:@@study.delete_study_permanently:Delete study permanently`,
                                showIf: (e) => {
                                    return (options.trash.active ||
                                        (
                                            options.selectedWebService &&
                                            options.selectedWebService.dicomAETitleObject &&
                                            options.selectedWebService.dicomAETitleObject.dcmAllowDeleteStudyPermanently === "ALWAYS"
                                        )
                                    ) && this.selectedWebServiceHasClass(options.selectedWebService,"DCM4CHEE_ARC_AET");
                                },
                                permission: {
                                    id: 'action-studies-study',
                                    param: 'delete'
                                }
                            },{
                                icon: {
                                    tag: 'span',
                                    cssClass: `custom_icon csv_icon_black`,
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "series",
                                        action: "download_csv"
                                    }, e);
                                },
                                title: $localize `:@@study.download_as_csv:Download as CSV`,
                                showIf:(e,config)=>{
                                    return  this.selectedWebServiceHasClass(options.selectedWebService,"DCM4CHEE_ARC_AET")
                                },
                                permission: {
                                    id: 'action-studies-download',
                                    param: 'visible'
                                }
                            },{
                                icon: {
                                    tag: 'i',
                                    cssClass: 'material-icons',
                                    text: 'vpn_key'
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "study",
                                        action: "update_access_control_id"
                                    }, e);
                                },
                                title: $localize `:@@study.update_study_access_control_id:Update Study Access Control ID`,
                                showIf:(e,config)=>{
                                    return  this.selectedWebServiceHasClass(options.selectedWebService,"DCM4CHEE_ARC_AET")
                                },
                                permission: {
                                    id: 'action-studies-study',
                                    param: 'edit'
                                }
                            },{
                                icon: {
                                    tag: 'span',
                                    cssClass: `custom_icon hand_shake_black`,
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "study",
                                        action: "send_storage_commit"
                                    }, e);
                                },
                                title: $localize `:@@send_storage_commitment_request_for_study:Send Storage Commitment Request for this study`,
                                showIf:(e,config)=>{
                                    return  this.selectedWebServiceHasClass(options.selectedWebService,"DCM4CHEE_ARC_AET")
                                },
                                permission: {
                                    id: 'action-studies-verify_storage_commitment',
                                    param: 'visible'
                                }
                            },{
                                icon: {
                                    tag: 'span',
                                    cssClass: `custom_icon ticker_export_black`,
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "study",
                                        action: "send_instance_availability_notification"
                                    }, e);
                                },
                                title: $localize `:@@send_instance_availability_notification_for_this_study:Send Instance Availability Notification for this study`,
                                showIf:(e,config)=>{
                                    return  this.selectedWebServiceHasClass(options.selectedWebService,"DCM4CHEE_ARC_AET")
                                },
                                permission: {
                                    id: 'action-studies-verify_storage_commitment',
                                    param: 'visible'
                                }
                            },{
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-eye-open',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "study",
                                        action: "open_viewer"
                                    }, e);
                                },
                                title: $localize `:@@study.open_study_in_the_viewer:Open study in the viewer`,
                                permission: {
                                    id: 'action-studies-viewer',
                                    param: 'visible'
                                },
                                showIf: (e, config) => {
                                    return _.hasIn(options,"selectedWebService.IID_STUDY_URL");
                                }
                            }
                        ]
                    },
                    headerDescription: $localize `:@@actions:Actions`,
                    pxWidth: 35
                }),
                new TableSchemaElement({
                    type: "actions",
                    header: "",
                    actions: [
                        {
                            icon: {
                                tag: 'span',
                                cssClass: 'glyphicon glyphicon-th-list',
                                text: ''
                            },
                            click: (e) => {
                                console.log("e", e);
                                e.showAttributes = !e.showAttributes;
                            },
                            title: $localize `:@@study.toggle_attributes:Toggle Attributes`
                        }
                    ],
                    headerDescription: $localize `:@@actions:Actions`,
                    pxWidth: 40
                }),new TableSchemaElement({
                    type: "actions",
                    header: "",
                    actions: [
                        {
                            icon: {
                                tag: 'span',
                                cssClass: 'glyphicon glyphicon-chevron-down',
                                text: ''
                            },
                            click: (e) => {
                                actions.call($this, {
                                    event: "click",
                                    level: "study",
                                    action: "toggle_series"
                                }, e);
                            },
                            title: $localize `:@@study.hide_series:Hide Series`,
                            showIf: (e) => {
                                return e.showSeries
                            },
                            permission: {
                                id: 'action-studies-serie',
                                param: 'visible'
                            }
                        }, {
                            icon: {
                                tag: 'span',
                                cssClass: 'glyphicon glyphicon-chevron-right',
                                text: ''
                            },
                            click: (e) => {
                                actions.call($this, {
                                    event: "click",
                                    level: "study",
                                    action: "toggle_series"
                                }, e);
                            },
                            title: $localize `:@@study.show_series:Show Series`,
                            showIf: (e) => {
                                return !e.showSeries
                            },
                            permission: {
                                id: 'action-studies-serie',
                                param: 'visible'
                            }
                        }
                    ],
                    headerDescription: $localize `:@@study.show_studies:Show studies`,
                    widthWeight: 0.3,
                    calculatedWidth: "6%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@study_id:Study ID`,
                    pathToValue: "[00200010].Value[0]",
                    headerDescription: $localize `:@@study_id:Study ID`,
                    widthWeight: 0.9,
                    calculatedWidth: "20%",
                    cssClass:"border-left"
                }), new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@study_instance_uid:Study Instance UID`,
                    pathToValue: "[0020000D].Value[0]",
                    headerDescription: $localize `:@@study_instance_uid:Study Instance UID`,
                    widthWeight: 2.5,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@study_date:Study Date`,
                    pathToValue: "[00080020].Value[0]",
                    headerDescription: $localize `:@@study_date:Study Date`,
                    widthWeight: 0.6,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@study.study_time:Study Time`,
                    pathToValue: "[00080030].Value[0]",
                    headerDescription: $localize `:@@study.study_time:Study Time`,
                    widthWeight: 0.6,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@study.r._physicians_name:R. Physician's Name`,
                    pathToValue: "[00080090].Value[0].Alphabetic",
                    headerDescription: $localize `:@@referring_physician_name:Referring physician name`,
                    widthWeight: 1,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@accession_number:Accession Number`,
                    pathToValue: "[00080050].Value[0]",
                    headerDescription: $localize `:@@accession_number:Accession Number`,
                    widthWeight: 1,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@admission_id:Admission ID`,
                    pathToValue: "[00380010].Value[0]",
                    headerDescription: $localize `:@@admission_id:Admission ID`,
                    widthWeight: 1,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@modalities:Modalities`,
                    pathToValue: "[00080061].Value",
                    headerDescription: $localize `:@@modalities_in_study:Modalities in Study`,
                    widthWeight: 0.5,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@study_description:Study Description`,
                    pathToValue: "[00081030].Value[0]",
                    headerDescription: $localize `:@@study_description:Study Description`,
                    widthWeight: 2,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@number_of_related_series:#S`,
                    pathToValue: "[00201206].Value[0]",
                    headerDescription: $localize `:@@number_of_study_related_series:Number of Study Related Series`,
                    widthWeight: 0.3,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@number_of_instances:#I`,
                    pathToValue: "[00201208].Value[0]",
                    headerDescription: $localize `:@@number_of_study_related_instances:Number of Study Related Instances`,
                    widthWeight: 0.3,
                    calculatedWidth: "20%"
                })
            ],
            series: [

                new TableSchemaElement({
                    type: "index",
                    header: '',
                    pathToValue: '',
                    pxWidth: 40
                }),
                new TableSchemaElement({
                    type: "actions-menu",
                    header: "",
                    menu: {
                        toggle: (e) => {
                            e.showMenu = !e.showMenu;
                        },
                        actions: [
                             {
                                icon: {
                                    tag: 'span',
                                    cssClass: options.trash.active ? 'glyphicon glyphicon-repeat' : 'glyphicon glyphicon-trash',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "series",
                                        action: "reject"
                                    }, e);
                                },
                                title: options.trash.active ? $localize `:@@study.restore_series:Restore series` : $localize `:@@study.reject_series:Reject series`,
                                permission: {
                                    id: 'action-studies-serie',
                                    param: options.trash.active ? 'restore' : 'reject'
                                }
                            }, {
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-ok',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "series",
                                        action: "verify_storage"
                                    }, e);
                                },
                                title: $localize `:@@study.verify_storage_commitment:Verify storage commitment`,
                                permission: {
                                    id: 'action-studies-verify_storage_commitment',
                                    param: 'visible'
                                }
                            },{
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-save',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "series",
                                        action: "download"
                                    }, e);
                                },
                                title: $localize `:@@study.retrieve_series:Retrieve Series`,
                                permission: {
                                    id: 'action-studies-download',
                                    param: 'visible'
                                }
                            },{
                                icon: {
                                    tag: 'i',
                                    cssClass: 'material-icons',
                                    text: 'file_upload'
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "series",
                                        action: "upload_file"
                                    }, e);
                                },
                                title: $localize `:@@upload_file:Upload file`,
                                permission: {
                                    id: 'action-studies-download',
                                    param: 'visible'
                                }
                            }
                            , {
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-export',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "series",
                                        action: "export"
                                    }, e);
                                },
                                title: $localize `:@@export_series:Export series`,
                                permission: {
                                    id: 'action-studies-serie',
                                    param: 'export'
                                }
                            },{
                                icon: {
                                    tag: 'span',
                                    cssClass: `custom_icon hand_shake_black`,
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "series",
                                        action: "send_storage_commit"
                                    }, e);
                                },
                                title: $localize `:@@study.send_storage_commitment_request_for_series:Send Storage Commitment Request for this series`,
                                showIf:(e,config)=>{
                                    return  this.selectedWebServiceHasClass(options.selectedWebService,"DCM4CHEE_ARC_AET")
                                },
                                permission: {
                                    id: 'action-studies-verify_storage_commitment',
                                    param: 'visible'
                                }
                            },{
                                icon: {
                                    tag: 'span',
                                    cssClass: `custom_icon ticker_export_black`,
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "series",
                                        action: "send_instance_availability_notification"
                                    }, e);
                                },
                                title: $localize `:@@send_instance_availability_notification_for_this_study:Send Instance Availability Notification for this Series`,
                                showIf:(e,config)=>{
                                    return  this.selectedWebServiceHasClass(options.selectedWebService,"DCM4CHEE_ARC_AET")
                                },
                                permission: {
                                    id: 'action-studies-verify_storage_commitment',
                                    param: 'visible'
                                }
                            },{
                                icon: {
                                    tag: 'span',
                                    cssClass: `custom_icon csv_icon_black`,
                                    text: ''
                                },
                                click: (e) => {
                                    console.log("e", e);
                                    actions.call($this, {
                                        event: "click",
                                        level: "instance",
                                        action: "download_csv"
                                    }, e);
                                },
                                title: $localize `:@@study.download_as_csv:Download as CSV`,
                                permission: {
                                    id: 'action-studies-download',
                                    param: 'visible'
                                }
                            }
                        ]
                    },
                    headerDescription: $localize `:@@actions:Actions`,
                    pxWidth: 35
                }),
                new TableSchemaElement({
                    type: "actions",
                    header: "",
                    actions: [
                        {
                            icon: {
                                tag: 'span',
                                cssClass: 'glyphicon glyphicon-th-list',
                                text: ''
                            },
                            click: (e) => {
                                e.showAttributes = !e.showAttributes;
                            },
                            title: $localize `:@@study.show_attributes:Show attributes`
                        }
                    ],
                    headerDescription: $localize `:@@actions:Actions`,
                    pxWidth: 40
                }),new TableSchemaElement({
                    type: "actions",
                    header: "",
                    actions: [
                        {
                            icon: {
                                tag: 'span',
                                cssClass: 'glyphicon glyphicon-chevron-down',
                                text: ''
                            },
                            click: (e) => {
                                actions.call($this, {
                                    event: "click",
                                    level: "series",
                                    action: "toggle_instances"
                                }, e);
                            },
                            title: $localize `:@@study.hide_instances:Hide Instances`,
                            showIf: (e) => {
                                return e.showInstances
                            },
                            permission: {
                                id: 'action-studies-serie',
                                param: 'visible'
                            }
                        }, {
                            icon: {
                                tag: 'span',
                                cssClass: 'glyphicon glyphicon-chevron-right',
                                text: ''
                            },
                            click: (e) => {
                                actions.call($this, {
                                    event: "click",
                                    level: "series",
                                    action: "toggle_instances"
                                }, e);
                            },
                            title: $localize `:@@study.show_instances:Show Instances`,
                            showIf: (e) => {
                                return !e.showInstances
                            },
                            permission: {
                                id: 'action-studies-serie',
                                param: 'visible'
                            }
                        }
                    ],
                    headerDescription: $localize `:@@study.show_instances:Show Instances`,
                    widthWeight: 0.2,
                    calculatedWidth: "6%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@station_name:Station Name`,
                    pathToValue: "00081010.Value[0]",
                    headerDescription: $localize `:@@station_name:Station Name`,
                    widthWeight: 0.9,
                    calculatedWidth: "20%",
                    cssClass:"border-left"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@series_number:Series Number`,
                    pathToValue: "00200011.Value[0]",
                    headerDescription: $localize `:@@series_number:Series Number`,
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@study.pps_start_date:PPS Start Date`,
                    pathToValue: "00400244.Value[0]",
                    headerDescription: $localize `:@@study.performed_procedure_step_start_date:Performed Procedure Step Start Date`,
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@study.pps_start_time:PPS Start Time`,
                    pathToValue: "00400245.Value[0]",
                    headerDescription: $localize `:@@study.performed_procedure_step_start_time:Performed Procedure Step Start Time`,
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@body_part:Body Part`,
                    pathToValue: "00180015.Value[0]",
                    headerDescription: $localize `:@@body_part_examined:Body Part Examined`,
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@modality:Modality`,
                    pathToValue: "00080060.Value[0]",
                    headerDescription: $localize `:@@modality:Modality`,
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@series_description:Series Description`,
                    pathToValue: "0008103E.Value[0]",
                    headerDescription: $localize `:@@series_description:Series Description`,
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@number_of_instances:#I`,
                    pathToValue: "00201209.Value[0]",
                    headerDescription: $localize `:@@number_of_series_related_instances:Number of Series Related Instances`,
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                })
            ],
            instance: [
                new TableSchemaElement({
                    type: "index",
                    header: '',
                    pathToValue: '',
                    pxWidth: 40
                }),
                new TableSchemaElement({
                    type: "actions-menu",
                    header: "",
                    menu: {
                        toggle: (e) => {
                            console.log("e", e);
                            e.showMenu = !e.showMenu;
                        },
                        actions: [
                            {
                                icon: {
                                    tag: 'span',
                                    cssClass: options.trash.active ? 'glyphicon glyphicon-repeat' : 'glyphicon glyphicon-trash',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "instance",
                                        action: "reject"
                                    }, e);
                                },
                                title: options.trash.active ? $localize `:@@study.restore_instance:Restore instance` : $localize `:@@study.reject_instance:Reject instance`,
                                permission: {
                                    id: 'action-studies-instance',
                                    param: options.trash.active ? 'restore' : 'reject'
                                }
                            }, {
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-ok',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "instance",
                                        action: "verify_storage"
                                    }, e);
                                },
                                title: $localize `:@@study.verify_storage_commitment:Verify storage commitment`,
                                permission: {
                                    id: 'action-studies-verify_storage_commitment',
                                    param: 'visible'
                                }
                            },
                            {
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-save',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "instance",
                                        action: "download"
                                    }, e);
                                },
                                title: $localize `:@@study.download_dicom_object:Download DICOM Object`,
                                permission: {
                                    id: 'action-studies-download',
                                    param: 'visible'
                                }
                            },{
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-export',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "instance",
                                        action: "export"
                                    }, e);
                                },
                                title: $localize `:@@export_instance:Export instance`,
                                permission: {
                                    id: 'action-studies-instance',
                                    param: 'export'
                                }
                            },
                            {
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-picture',
                                    text: '',
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "instance",
                                        action: "view"
                                    }, e);
                                },
                                title: $localize `:@@study.view_dicom_object:View DICOM Object`,
                                permission: {
                                    id: 'action-studies-download',
                                    param: 'visible'
                                }
                            },{
                                icon: {
                                    tag: 'span',
                                    cssClass: `custom_icon hand_shake_black`,
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "instance",
                                        action: "send_storage_commit"
                                    }, e);
                                },
                                title: $localize `:@@study.send_storage_commitment_request_for_study:Send Storage Commitment Request for this instance`,
                                showIf:(e,config)=>{
                                    return  this.selectedWebServiceHasClass(options.selectedWebService,"DCM4CHEE_ARC_AET")
                                },
                                permission: {
                                    id: 'action-studies-verify_storage_commitment',
                                    param: 'visible'
                                }
                            },{
                                icon: {
                                    tag: 'span',
                                    cssClass: `custom_icon ticker_export_black`,
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "instance",
                                        action: "send_instance_availability_notification"
                                    }, e);
                                },
                                title: $localize `:@@send_instance_availability_notification_for_this_study:Send Instance Availability Notification for this Instance`,
                                showIf:(e,config)=>{
                                    return  this.selectedWebServiceHasClass(options.selectedWebService,"DCM4CHEE_ARC_AET")
                                },
                                permission: {
                                    id: 'action-studies-verify_storage_commitment',
                                    param: 'visible'
                                }
                            }
                        ]
                    },
                    headerDescription: $localize `:@@actions:Actions`,
                    pxWidth: 35
                }), new TableSchemaElement({
                    type: "actions",
                    header: "",
                    actions: [
                        {
                            icon: {
                                tag: 'span',
                                cssClass: 'glyphicon glyphicon-th-list',
                                text: ''
                            },
                            click: (e) => {
                                console.log("e", e);
                                e.showFileAttributes = false;
                                e.showAttributes = !e.showAttributes;
                            },
                            title: $localize `:@@study.show_attributes:Show attributes`
                        }
                    ],
                    headerDescription: $localize `:@@actions:Actions`,
                    pxWidth: 40
                }), new TableSchemaElement({
                    type: "actions",
                    header: "",
                    actions: [
                        {
                            icon: {
                                tag: 'span',
                                cssClass: 'glyphicon glyphicon-list',
                                text: ''
                            },
                            click: (e) => {
                                console.log("e", e);
                                e.showAttributes = false;
                                e.showFileAttributes = !e.showFileAttributes;
                            },
                            title: $localize `:@@study.show_attributes_from_file:Show attributes from file`
                        }
                    ],
                    headerDescription: $localize `:@@actions:Actions`,
                    pxWidth: 40
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@sop_class_name:SOP Class Name`,
                    pathToValue: "00080016.Value[0]",
                    headerDescription: $localize `:@@sop_class_name:SOP Class Name`,
                    widthWeight: 0.9,
                    calculatedWidth: "20%",
                    cssClass:"border-left",
                    hook:options.getSOPClassUIDName
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@instance_number:Instance Number`,
                    pathToValue: "00200013.Value[0]",
                    headerDescription: $localize `:@@instance_number:Instance Number`,
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@content_date:Content Date`,
                    pathToValue: "00080023.Value[0]",
                    headerDescription: $localize `:@@content_date:Content Date`,
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@study.content_time:Content Time`,
                    pathToValue: "00080033.Value[0]",
                    headerDescription: $localize `:@@study.content_time:Content Time`,
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "pipe",
                    header: $localize `:@@study.content_description:Content Description`,
                    headerDescription: $localize `:@@study.content_description:Content Description`,
                    widthWeight: 1.5,
                    calculatedWidth: "20%",
                    pipe: new DynamicPipe(ContentDescriptionPipe, undefined)
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "#F",
                    pathToValue: "00280008.Value[0]",
                    headerDescription: $localize `:@@number_of_frames:Number of Frames`,
                    widthWeight: 0.3,
                    calculatedWidth: "20%"
                })
            ],
            mwl:[
                new TableSchemaElement({
                    type: "index",
                    header: '',
                    pathToValue: '',
                    pxWidth: 40
                }),
                new TableSchemaElement({
                    type: "actions-menu",
                    header: "",
                    menu: {
                        toggle: (e) => {
                            console.log("e", e);
                            e.showMenu = !e.showMenu;
                        },
                        actions: [
                            {
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-pencil',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "mwl",
                                        action: "edit_mwl"
                                    }, e);
                                },
                                title: $localize `:@@study.edit_mwl:Edit MWL`,
                                showIf:(e,config)=>{
                                    return  this.selectedWebServiceHasClass(options.selectedWebService,"DCM4CHEE_ARC_AET")
                                },
                                permission: {
                                    id: 'action-studies-mwl',
                                    param: 'edit'
                                }
                            },
                            {
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-remove',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "mwl",
                                        action: "delete_mwl"
                                    }, e);
                                },showIf:(e,config)=>{
                                    return  this.selectedWebServiceHasClass(options.selectedWebService,"DCM4CHEE_ARC_AET")
                                },
                                title: $localize `:@@study.delete_mwl:Delete MWL`,
                                permission: {
                                    id: 'action-studies-mwl',
                                    param: 'delete'
                                }
                            },{
                                icon: {
                                    tag: 'i',
                                    cssClass: 'material-icons',
                                    text: 'file_upload'
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "mwl",
                                        action: "upload_file"
                                    }, e);
                                },
                                title: $localize `:@@upload_file:Upload file`,
                                permission: {
                                    id: 'action-studies-mwl',
                                    param: 'upload'
                                }
                            },{
                                icon: {
                                    tag: 'span',
                                    cssClass: `custom_icon calendar_step_black`,
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "mwl",
                                        action: "change_sps_status"
                                    }, e);
                                },
                                title: $localize `:@@mwl.change_sps_status:Change SPS status`,
                                permission: {
                                    id: 'action-studies-mwl',
                                    param: 'edit'
                                }
                            }
                        ]
                    },
                    headerDescription: $localize `:@@actions:Actions`,
                    pxWidth: 35
                }), new TableSchemaElement({
                    type: "actions",
                    header: "",
                    actions: [
                        {
                            icon: {
                                tag: 'span',
                                cssClass: 'glyphicon glyphicon-th-list',
                                text: ''
                            },
                            click: (e) => {
                                console.log("e", e);
                                e.showAttributes = !e.showAttributes;
                            },
                            title: $localize `:@@study.show_attributes:Show attributes`
                        }
                    ],
                    headerDescription: $localize `:@@actions:Actions`,
                    pxWidth: 40
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@study.requested_procedure_id:Requested Procedure ID`,
                    pathToValue: "00401001.Value[0]",
                    headerDescription: $localize `:@@study.requested_procedure_id:Requested Procedure ID`,
                    widthWeight: 2,
                    calculatedWidth: "20%",
                    cssClass:"border-left"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@study_instance_uid:Study Instance UID`,
                    pathToValue: "0020000D.Value[0]",
                    headerDescription: $localize `:@@study_instance_uid:Study Instance UID`,
                    widthWeight: 3.5,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@sps_start_date:SPS Start Date`,
                    pathToValue: "00400100.Value[0].00400002.Value[0]",
                    headerDescription: $localize `:@@scheduled_procedure_step_start_date:Scheduled Procedure Step Start Date`,
                    widthWeight: 1,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@study.sps_start:SPS Start`,
                    pathToValue: "00400100.Value[0].00400003.Value[0]",
                    headerDescription: $localize `:@@scheduled_procedure_step_start_time:Scheduled Procedure Step Start Time`,
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@sp_physicians_name:SP Physician's Name`,
                    pathToValue: "00400100.Value[0].00400006.Value[0].Alphabetic",
                    headerDescription: $localize `:@@scheduled_performing_physicians_name:Scheduled Performing Physician's Name`,
                    widthWeight: 2,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@accession_number:Accession Number`,
                    pathToValue: "00080050.Value[0]",
                    headerDescription: $localize `:@@accession_number:Accession Number`,
                    widthWeight: 2,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@admission_id:Admission ID`,
                    pathToValue: "[00380010].Value[0]",
                    headerDescription: $localize `:@@admission_id:Admission ID`,
                    widthWeight: 1,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header:  $localize `:@@modality:Modality`,
                    pathToValue: "00400100.Value[0].00080060.Value[0]",
                    headerDescription:  $localize `:@@modality:Modality`,
                    widthWeight: 1,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header:  $localize `:@@description:Description`,
                    pathToValue: "00400100.Value[0].00400007.Value[0]",
                    headerDescription: $localize `:@@study.scheduled_procedure_step_description:Scheduled Procedure Step Description`,
                    widthWeight: 3,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@study.ss_aet:SS AET`,
                    pathToValue: "00400100.Value[0].00400001.Value",
                    headerDescription: $localize `:@@scheduled_station_ae_title:Scheduled Station AE Title`,
                    widthWeight: 1.5,
                    calculatedWidth: "20%"
                })
            ],
            uwl:[
                new TableSchemaElement({
                    type: "index",
                    header: '',
                    pathToValue: '',
                    pxWidth: 40
                }),
                new TableSchemaElement({
                    type: "actions-menu",
                    header: "",
                    menu: {
                        toggle: (e) => {
                            console.log("e", e);
                            e.showMenu = !e.showMenu;
                        },
                        actions: [
                            {
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-unchecked',
                                    text: ''
                                },
                                click: (e) => {
                                    e.selected = !e.selected;
                                    actions.call($this, {
                                        event: "click",
                                        level: "uwl",
                                        action: "select"
                                    }, e);
                                },
                                title: $localize `:@@select:Select`,
                                showIf: (e, config) => {
                                    return !config.showCheckboxes && !e.selected;
                                }
                            },{
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-check',
                                    text: ''
                                },
                                click: (e) => {
                                    console.log("e", e);
                                    e.selected = !e.selected;
                                    actions.call($this, {
                                        event: "click",
                                        level: "uwl",
                                        action: "select"
                                    }, e);
                                },
                                title: $localize `:@@unselect:Unselect`,
                                showIf: (e, config) => {
                                    return !config.showCheckboxes && e.selected;
                                }
                            },
                            {
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-pencil',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "uwl",
                                        action: "edit_uwl"
                                    }, e);
                                },
                                title: $localize `:@@study.edit_uwl:Edit UWL`,
                                permission: {
                                    id: 'action-studies-uwl',
                                    param: 'edit'
                                }
                            },
                            {
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-duplicate',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "uwl",
                                        action: "clone_uwl"
                                    }, e);
                                },
                                title: $localize `:@@clone_uwl:Clone UWL`,
                                permission: {
                                    id: 'action-studies-uwl',
                                    param: 'edit'
                                }
                            },
                            {
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-repeat',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "uwl",
                                        action: "reschedule_uwl"
                                    }, e);
                                },
                                title: $localize `:@@reschedule_uwl:Reschedule UWL`,
                                permission: {
                                    id: 'action-studies-uwl',
                                    param: 'edit'
                                }
                            },
                            {
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-ban-circle',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "uwl",
                                        action: "cancel_uwl"
                                    }, e);
                                },
                                title: $localize `:@@title.cancel:Cancel`,
                                permission: {
                                    id: 'action-studies-uwl',
                                    param: 'edit'
                                }
                            }
                            /*                            ,
                                                        {
                                                            icon: {
                                                                tag: 'span',
                                                                cssClass: 'glyphicon glyphicon-remove',
                                                                text: ''
                                                            },
                                                            click: (e) => {
                                                                actions.call($this, {
                                                                    event: "click",
                                                                    level: "uwl",
                                                                    action: "delete_uwl"
                                                                }, e);
                                                            },
                                                            title: $localize `:@@study.delete_uwl:Delete UWL`,
                                                            permission: {
                                                                id: 'action-studies-uwl',
                                                                param: 'delete'
                                                            }
                                                        },{
                                                            icon: {
                                                                tag: 'i',
                                                                cssClass: 'material-icons',
                                                                text: 'file_upload'
                                                            },
                                                            click: (e) => {
                                                                actions.call($this, {
                                                                    event: "click",
                                                                    level: "uwl",
                                                                    action: "upload_file"
                                                                }, e);
                                                            },
                                                            title: $localize `:@@upload_file:Upload file`,
                                                            permission: {
                                                                id: 'action-studies-mwl',
                                                                param: 'upload'
                                                            }
                                                        }*/
                        ]
                    },
                    headerDescription: $localize `:@@actions:Actions`,
                    pxWidth: 40
                }),
                new TableSchemaElement({
                    type: "actions",
                    header: "",
                    actions: [
                        {
                            icon: {
                                tag: 'span',
                                cssClass: 'glyphicon glyphicon-th-list',
                                text: ''
                            },
                            click: (e) => {
                                console.log("e", e);
                                e.showAttributes = !e.showAttributes;
                            },
                            title: $localize `:@@study.show_attributes:Show attributes`
                        }
                    ],
                    headerDescription: $localize `:@@actions:Actions`,
                    pxWidth: 40
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@worklist_label:Worklist Label`,
                    pathToValue: "00741202.Value[0]",
                    headerDescription: $localize `:@@worklist_label:Worklist Label`,
                    widthWeight: 2,
                    calculatedWidth: "20%",
                    cssClass:"border-left"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@admission_id:Admission ID`,
                    pathToValue: "[00380010].Value[0]",
                    headerDescription: $localize `:@@admission_id:Admission ID`,
                    widthWeight: 1,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@study.input_readiness:Input Readiness`,
                    pathToValue: "00404041.Value[0]",
                    headerDescription: $localize `:@@input_readiness_state:Input Readiness State`,
                    widthWeight: 1.4,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@study.procedure_step:Procedure Step`,
                    pathToValue: "00741000.Value[0]",
                    headerDescription: $localize `:@@procedure_step_state:Procedure Step State`,
                    widthWeight: 1,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@study.step_priority:Step Priority`,
                    pathToValue: "00741200.Value[0]",
                    headerDescription: $localize `:@@scheduled_procedure_step_priority:Scheduled Procedure Step Priority`,
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@study.start_date_and_time:Start Date and Time`,
                    pathToValue: "00404005.Value[0]",
                    headerDescription: $localize `:@@scheduled_procedure_step_start_date_and_time:Scheduled Procedure Step Start Date and Time`,
                    widthWeight: 2,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@procedure_step_label:Procedure Step Label`,
                    pathToValue: "00741204.Value[0]",
                    headerDescription: $localize `:@@procedure_step_label:Procedure Step Label`,
                    widthWeight: 2,
                    calculatedWidth: "20%"
                }),

                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@study.e._completion_time:E. Completion Time`,
                    pathToValue: "00404011.Value[0]",
                    headerDescription: $localize `:@@expected_completion_date_and_time:Expected Completion Date and Time`,
                    widthWeight: 2,
                    calculatedWidth: "20%",
                    cssClass:"border-left"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@study.step_m._date_and_time:Step M. Date and Time`,
                    pathToValue: "00404010.Value[0]",
                    headerDescription: $localize `:@@scheduled_procedure_step_modification_date_and_time:Scheduled Procedure Step Modification Date and Time`,
                    widthWeight: 4,
                    calculatedWidth: "20%",
                    cssClass:"border-left"
                })
            ],
            diff:[
                new TableSchemaElement({
                    type: "index",
                    header: '',
                    pathToValue: '',
                    pxWidth: 40
                }), new TableSchemaElement({
                    type: "actions",
                    header: "",
                    actions: [
                        {
                            icon: {
                                tag: 'span',
                                cssClass: 'glyphicon glyphicon-th-list',
                                text: ''
                            },
                            click: (e) => {
                                console.log("e", e);
                                e.showAttributes = !e.showAttributes;
                            },
                            title: $localize `:@@study.show_attributes:Show attributes`
                        }
                    ],
                    headerDescription: $localize `:@@actions:Actions`,
                    pxWidth: 40
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@study_id:Study ID`,
                    pathToValue: "[00200010].Value[0]",
                    showBorderPath:"[00200010].showBorder",
                    headerDescription: $localize `:@@study_id:Study ID`,
                    widthWeight: 0.9,
                    calculatedWidth: "20%",
                    cssClass:"border-left"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@study_instance_uid:Study Instance UID`,
                    pathToValue: "[0020000D].Value[0]",
                    showBorderPath:"[0020000D].showBorder",
                    headerDescription: $localize `:@@study_instance_uid:Study Instance UID`,
                    widthWeight: 3,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@study_date:Study Date`,
                    pathToValue: "[00080020].Value[0]",
                    showBorderPath:"[00080020].showBorder",
                    headerDescription: $localize `:@@study_date:Study Date`,
                    widthWeight: 0.6,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@study.study_time:Study Time`,
                    pathToValue: "[00080030].Value[0]",
                    showBorderPath:"[00080030].showBorder",
                    headerDescription: $localize `:@@study.study_time:Study Time`,
                    widthWeight: 0.6,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@sp_physicians_name:SP Physician's Name`,
                    pathToValue: "00400100.Value[0].00400006.Value[0]",
                    showBorderPath:"00400100.Value[0].00400006.showBorder",
                    headerDescription: $localize `:@@scheduled_performing_physicians_name:Scheduled Performing Physician's Name`,
                    widthWeight: 2,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@accession_number:Accession Number`,
                    pathToValue: "[00080050].Value[0]",
                    showBorderPath:"[00080050].showBorder",
                    headerDescription: $localize `:@@accession_number:Accession Number`,
                    widthWeight: 1,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@admission_id:Admission ID`,
                    pathToValue: "[00380010].Value[0]",
                    headerDescription: $localize `:@@admission_id:Admission ID`,
                    widthWeight: 1,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@modalities:Modalities`,
                    pathToValue: "[00080061].Value[0]",
                    showBorderPath:"[00080061].showBorder",
                    headerDescription: $localize `:@@modalities_in_study:Modalities in Study`,
                    widthWeight: 0.5,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@study_description:Study Description`,
                    pathToValue: "[00081030].Value[0]",
                    showBorderPath:"[00081030].showBorder",
                    headerDescription: $localize `:@@study_description:Study Description`,
                    widthWeight: 2,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@number_of_related_series:#S`,
                    pathToValue: "[00201206].Value[0]",
                    showBorderPath:"[00201206].showBorder",
                    headerDescription: $localize `:@@number_of_study_related_series:Number of Study Related Series`,
                    widthWeight: 0.4,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@number_of_instances:#I`,
                    pathToValue: "[00201208].Value[0]",
                    showBorderPath:"[00201208].showBorder",
                    headerDescription: $localize `:@@number_of_study_related_instances:Number of Study Related Instances`,
                    widthWeight: 0.4,
                    calculatedWidth: "20%"
                })
            ]
        };

        if (_.hasIn(options, "tableParam.config.showCheckboxes") && options.tableParam.config.showCheckboxes) {
            Object.keys(schema).forEach(mode => {
                schema[mode].splice(1, 0, new TableSchemaElement({
                    type: "actions",
                    header: "",
                    actions: [
                        {
                            icon: {
                                tag: 'span',
                                cssClass: 'glyphicon glyphicon-unchecked',
                                text: ''
                            },
                            click: (e, level) => {
                                e.selected = !e.selected;
                                actions.call($this, {
                                    event: "click",
                                    level: level,
                                    action: "select"
                                }, e);
                            },
                            title: $localize `:@@select:Select`,
                            showIf: (e, config) => {
                                return !e.selected;
                            }
                        }, {
                            icon: {
                                tag: 'span',
                                cssClass: 'glyphicon glyphicon-check',
                                text: ''
                            },
                            click: (e, level) => {
                                console.log("e", e);
                                e.selected = !e.selected;
                                actions.call($this, {
                                    event: "click",
                                    level: level,
                                    action: "select"
                                }, e);
                            },
                            title: $localize `:@@unselect:Unselect`,
                            showIf: (e, config) => {
                                return e.selected;
                            }
                        }
                    ],
                    headerDescription: $localize `:@@select:Select`,
                    pxWidth: 40
                }))
            });
        }

        if (_.hasIn(options, "studyConfig.tab") && options.studyConfig.tab === "patient") {
            schema.patient.splice(0,1, new TableSchemaElement({
                type: "index",
                header: '',
                pathToValue: '',
                pxWidth: 40,
            }))
        }

            return schema;
    }
    updateAccessControlIdOfSelections(multipleObjects: SelectionActionElement, selectedWebService: DcmWebApp, accessControlID:string){
        return forkJoin((<any[]>multipleObjects.getAllAsArray().filter((element: SelectedDetailObject) => (element.dicomLevel === "study")).map((element: SelectedDetailObject) => {
            return this.$http.put(
                `${this.getURL(element.object.attrs, selectedWebService, "study")}/access/${accessControlID}`,
                {},
                this.jsonHeader
            );
        })));
    }
    updateAccessControlId(matchingMode:AccessControlIDMode, selectedWebService:DcmWebApp, accessControlID:string, studyInstanceUID?:string, filters?:any){
        if(matchingMode === "update_access_control_id_to_matching"){
            return this.$http.post(
                `${this.getDicomURL("study", selectedWebService)}/access/${accessControlID}${j4care.param(filters)}`,
                {},
                this.jsonHeader
            );
        }else{
            return this.$http.put(
                `${this.getDicomURL("study", selectedWebService)}/${studyInstanceUID}/access/${accessControlID}`,
                {},
                this.jsonHeader
            );
        }
    }

    modifyStudy(study, deviceWebservice: StudyWebService, header: HttpHeaders) {
        const url = this.getModifyStudyUrl(deviceWebservice);
        if (url) {
            return this.$http.post(url, study, header);
        }
        return throwError({error: $localize `:@@study.error_on_getting_the_webapp_url:Error on getting the WebApp URL`});
    }

    getModifyStudyUrl(deviceWebservice: StudyWebService) {
        return this.getDicomURL("study", this.getModifyStudyWebApp(deviceWebservice));
    }

    getModifyStudyWebApp(deviceWebservice: StudyWebService): DcmWebApp {
        if (deviceWebservice.selectedWebService.dcmWebServiceClass.indexOf("DCM4CHEE_ARC_AET") > -1) {
            return deviceWebservice.selectedWebService;
        } else {
            return undefined;
        }
    }

    modifyMWL(mwl, deviceWebservice: StudyWebService, header: HttpHeaders) {
        const url = this.getModifyMWLUrl(deviceWebservice);
        if (url) {
            return this.$http.post(url, mwl, header);
        }
        return throwError({error: $localize `:@@study.error_on_getting_the_webapp_url:Error on getting the WebApp URL`});
    }
    changeSPSStatusSingleMWL(dcmWebApp:DcmWebApp,spsState:string, mwlModel:MwlDicom){
        const studyInstanceUID = _.get(mwlModel,"attrs[0020000D].Value[0]");
        const spsID = _.get(mwlModel,"attrs[00400100].Value[0][00400009].Value[0]");
        if(studyInstanceUID && spsID){
            return this.$http.post(`${this.getDicomURL("mwl",dcmWebApp)}/${studyInstanceUID}/${spsID}/status/${spsState}`,{});
        }else{
            return throwError({
                message: $localize `:@@scheduled_procedure_step_id_or_study_instance_uid_were_missing:Scheduled Procedure Step ID or Study Instance UID were missing!`
            })
        }
    }
    changeSPSStatusSelectedMWL(multipleObjects: SelectionActionElement,dcmWebApp:DcmWebApp,spsState:string){
        let observables = [];
        multipleObjects.preActionElements.getAllAsArray().filter((element: SelectedDetailObject) => (element.dicomLevel === "mwl")).map((element: SelectedDetailObject) => {
            const studyInstanceUID = _.get(element.object,"attrs[0020000D].Value[0]");
            const spsID = _.get(element.object,"attrs[00400100].Value[0][00400009].Value[0]");
            observables.push(this.$http.post(`${this.getDicomURL("mwl",dcmWebApp)}/${studyInstanceUID}/${spsID}/status/${spsState}`,{}).pipe(
                catchError(err => of({isError: true, error: err})),
            ));
        });
        return forkJoin(observables);
    }
    changeSPSStatusMatchingMWL(dcmWebApp:DcmWebApp, status:string, filters:any){
        return this.$http.post(`${this.getDicomURL("mwl",dcmWebApp)}/status/${status}${j4care.param(filters)}`,{});
    }

    modifyUWL(uwl, deviceWebservice: StudyWebService, header: HttpHeaders) {
        const url = this.getModifyMWLUrl(deviceWebservice);
        if (url) {
            return this.$http.post(url, uwl, header);
        }
        return throwError({error: $localize `:@@study.error_on_getting_the_webapp_url:Error on getting the WebApp URL`});
    }

    getModifyMWLUrl(deviceWebservice: StudyWebService) {
        return this.getDicomURL("mwl", this.getModifyMWLWebApp(deviceWebservice));
    }
    getModifyUWLUrl(deviceWebservice: StudyWebService) {
        return this.getDicomURL("uwl", this.getModifyMWLWebApp(deviceWebservice));
    }

    getModifyMWLWebApp(deviceWebservice: StudyWebService): DcmWebApp {
        if (deviceWebservice.selectedWebService.dcmWebServiceClass.indexOf("DCM4CHEE_ARC_AET") > -1) {
            return deviceWebservice.selectedWebService;
        } else {
            return undefined;
        }
    }
    getModifyUWLWebApp(deviceWebservice: StudyWebService): DcmWebApp {
        if (deviceWebservice.selectedWebService.dcmWebServiceClass.indexOf("DCM4CHEE_ARC_AET") > -1) {
            return deviceWebservice.selectedWebService;
        } else {
            return undefined;
        }
    }

    copyMove(selectedElements:SelectionActionElement,dcmWebApp:DcmWebApp, rejectionCode?):Observable<any>{
        try{
            const target:SelectedDetailObject = selectedElements.postActionElements.getAllAsArray()[0];
            let studyInstanceUID;
            let patientParams = {};
            let observables = [];

            if(!_.hasIn(target,"requestReady.StudyInstanceUID")){
                studyInstanceUID = j4care.generateOIDFromUUID();
                if(target.dicomLevel === "patient"){
                    patientParams["PatientID"] = _.get(target.object, "attrs.00100020.Value[0]");
                    patientParams["IssuerOfPatientID"] = _.get(target.object, "attrs.00100021.Value[0]");
                }
            }else{
                studyInstanceUID = _.get(target,"requestReady.StudyInstanceUID");
            }
            let url = `${this.getDicomURL("study", dcmWebApp)}/${studyInstanceUID}/${selectedElements.action}`;
            if(selectedElements.action === "move"){
                url += `/` + rejectionCode;
            }
            url += j4care.param(patientParams);
            selectedElements.preActionElements.getAllAsArray().forEach(object=>{
                observables.push(this.$http.post(url,object.requestReady).pipe(
                    catchError(err => of({isError: true, error: err})),
                ));
            });
            return forkJoin(observables);
        }catch (e) {
            return throwError(e);
        }
    };

    linkStudyToMwl(selectedElements:SelectionActionElement,dcmWebApp:DcmWebApp, rejectionCode):Observable<any>{
        try{
            let observables = [];
            const target:SelectedDetailObject = selectedElements.postActionElements.getAllAsArray()[0];
            selectedElements.preActionElements.getAllAsArray().forEach(object=>{
                const url = `${this.getDicomURL("mwl", dcmWebApp)}/${target.object.attrs['0020000D'].Value[0]}/${_.get(target.object.attrs,'[00400100].Value[0][00400009].Value[0]')}/move/${rejectionCode}`;
                observables.push(this.$http.post(
                    url,
                    object.requestReady,
                    this.jsonHeader
                ).pipe(
                    catchError(err => of({isError: true, error: err})),
                ));
            });
            return forkJoin(observables);
        }catch (e) {
            return throwError(e);
        }
    }

    mergePatients = (selectedElements:SelectionActionElement,deviceWebservice: StudyWebService):Observable<any> => {
        if(selectedElements.preActionElements.getAttrs("patient").length > 1){
            return throwError({error:$localize `:@@multi_patient_merge_not_supported:Multi patient merge is not supported!`});
        }else{
            return this.getModifyPatientUrl(deviceWebservice).pipe(
                switchMap((url:string)=>{
                    console.log("url",url);
                    return this.$http.put(
                        `${url}/${this.getPatientId(selectedElements.preActionElements.getAttrs("patient")[0])}?merge=true`,
                        selectedElements.postActionElements.getAttrs("patient"),
                        this.jsonHeader
                    )
                })
            )
        }
    };
    rescheduleUPS(workitemUID,deviceWebservice: StudyWebService, model){
        return this.getModifyUPSUrl(deviceWebservice)
            .pipe(switchMap((url:string)=>{
                if (url) {
                    if (workitemUID) {
                        return this.$http.post(`${url}/${workitemUID}/reschedule${j4care.objToUrlParams(model,true)}`,{});
                    }
                }
                return throwError({error: $localize `:@@error_on_getting_needed_webapp:Error on getting the needed WebApp (with one of the web service classes "DCM4CHEE_ARC_AET" or "PAM")`});
            }))
    }
    cancelUPS(workitemUID, deviceWebservice: StudyWebService, requester){
        return this.getModifyUPSUrl(deviceWebservice)
            .pipe(switchMap((url:string)=>{
                if (url) {
                    if (requester) {
                        return this.$http.post(`${url}/${workitemUID}/cancelrequest/${requester}`,{});
                    }
                }
                return throwError({error: $localize `:@@error_on_getting_needed_webapp:Error on getting the needed WebApp (with one of the web service classes "DCM4CHEE_ARC_AET" or "PAM")`});
            }))
    }
    modifyUPS(workitemUID: string, object, deviceWebservice: StudyWebService, template?:boolean) {
        // const url = this.getModifyPatientUrl(deviceWebservice);
        let header = new HttpHeaders({'Content-Type': 'application/dicom+json','Accept': 'application/dicom+json'});
        return this.getModifyUPSUrl(deviceWebservice)
            .pipe(switchMap((url:string)=>{
                if (url) {
                    if (workitemUID) {
                        //Change ups;
                        return this.$http.post(`${url}/${workitemUID}`, object, header);
                    } else {
                        //Create new patient
                        return this.$http.post(url + j4care.objToUrlParams({template:template},true), object,  header);
                    }
                }
                return throwError({error: $localize `:@@error_on_getting_needed_webapp:Error on getting the needed WebApp (with one of the web service classes "DCM4CHEE_ARC_AET" or "PAM")`});
            }))
    }

    getModifyUPSUrl(deviceWebService: StudyWebService) {
        return this.getDicomURLFromWebService(deviceWebService, "uwl");
    }
    modifyPatient(patientId: string, patientObject, deviceWebservice: StudyWebService, queued?, batchID?) {
        // const url = this.getModifyPatientUrl(deviceWebservice);
        return this.getModifyPatientUrl(deviceWebservice)
            .pipe(switchMap((url:string)=>{
                if (url) {
                    if (patientId) {
                        //Change patient;
                        return this.$http.put(`${url}/${patientId}${j4care.objToUrlParams({queued:queued,batchID:batchID}, true)}`, patientObject, undefined,true);
                    } else {
                        //Create new patient
                        return this.$http.post(url, patientObject);
                    }
                }
                return throwError({error: $localize `:@@error_on_getting_needed_webapp:Error on getting the needed WebApp (with one of the web service classes "DCM4CHEE_ARC_AET" or "PAM")`});
            }))
    }

    getModifyPatientUrl(deviceWebService: StudyWebService) {
        return this.getDicomURLFromWebService(deviceWebService, "patient");
    }

    getModifyPatientWebApp(deviceWebService: StudyWebService): Observable<DcmWebApp> {
        return this.getWebAppFromWebServiceClassAndSelectedWebApp(deviceWebService, "DCM4CHEE_ARC_AET", "PAM");
    }

    getDicomURLFromWebService(deviceWebService: StudyWebService, mode:DicomMode) {
        return this.getModifyPatientWebApp(deviceWebService).pipe(map((webApp:DcmWebApp)=>{
            return this.getDicomURL(mode, webApp);
        }));
    }

    getWebAppFromWebServiceClassAndSelectedWebApp(deviceWebService: StudyWebService, neededWebServiceClass: string, alternativeWebServiceClass: string):Observable<DcmWebApp> {
        if (_.hasIn(deviceWebService, "selectedWebService.dcmWebServiceClass") && deviceWebService.selectedWebService.dcmWebServiceClass.indexOf(neededWebServiceClass) > -1) {
            return of(deviceWebService.selectedWebService);
        } else {
            try {
                return this.webAppListService.getWebApps({
                    dcmWebServiceClass: alternativeWebServiceClass,
                    dicomAETitle: deviceWebService.selectedWebService.dicomAETitle
                }).pipe(map((webApps:DcmWebApp[])=>webApps[0]));
/*                return deviceWebService.webServices.filter((webService: DcmWebApp) => { //TODO change this to observable to get the needed webservice from server
                    if (webService.dcmWebServiceClass.indexOf(alternativeWebServiceClass) > -1 && webService.dicomAETitle === deviceWebService.selectedWebService.dicomAETitle) {
                        return true;
                    }
                    return false;
                })[0];*/
            } catch (e) {
                j4care.log(`Error on getting the ${alternativeWebServiceClass} WebApp getModifyPatientUrl`, e);
                return throwError($localize `:@@error_on_getting_param_webapp:Error on getting the ${alternativeWebServiceClass}:@@webappcass: WebApp getModifyPatientUrl`);
            }
        }
    }

    getUploadFileWebApp(deviceWebService: StudyWebService):Observable<DcmWebApp> {
        return this.getWebAppFromWebServiceClassAndSelectedWebApp(deviceWebService, "STOW_RS", "STOW_RS");
    }

    appendPatientIdTo(patient, obj) {
        if (_.hasIn(patient, '00100020')) {
            obj['00100020'] = obj['00100020'] || {};
            obj['00100020'] = patient['00100020'];
        }
        if (_.hasIn(patient, '00100021')) {
            obj['00100021'] = obj['00100021'] || {};
            obj['00100021'] = patient['00100021'];
        }
        if (_.hasIn(patient, '00100024')) {
            obj['00100024'] = obj['00100024'] || {};
            obj['00100024'] = patient['00100024'];
        }
    }

    getIod(fileIodName:string){
        fileIodName = fileIodName || "study";
        if(this.iod[fileIodName]){
            return of(this.iod[fileIodName]);
        }else{
            return this.$http.get(`assets/iod/${fileIodName}.iod.json`).pipe(map(iod=>{
                this.iod[fileIodName] = iod;
                return iod;
            }));
        }
    }

    /*
    *
        Upload Context              None	    Patient	    Study	    Series	    MWL
        Patient IE   	            editable	read-only	read-only	read-only	read-only
        Study IE	                editable	editable	read-only	read-only	read-only
        Series IE	                editable	editable	editable	read-only	editable
        Equipment IE	            editable	editable	editable	read-only	editable
        Image IE	                editable	editable	editable	editable	editable
        Encapsulated Document IE	editable	editable	editable	editable	editable
    * */
    getIodFromContext(fileType:string, context:("patient"|"study"|"series"|"mwl")){

        let level;
        let iodFileNames = [];
        if(context === "patient"){
            level = 0;
        }
        if(context === "study" || context === "mwl"){
            level = 1;
        }
        if(context === "series"){
            level = 2;
        }
        if(fileType.indexOf("video") > -1){
            //VIDEO
            //"patient"
            iodFileNames = [
                "study",
                "series",
                "equipment",
                "image",
                "sop",
                "vlImageAcquisitionContext",
                "multiFrame"
            ]
        }
        if(fileType.indexOf("image") > -1) {
            //"patient"
            iodFileNames = [
                "study",
                "series",
                "equipment",
                "photographicEquipment",
                "image",
                "sop",
                "vlImageAcquisitionContext"
            ]
        }
        if(fileType.indexOf("pdf") > -1) {
            //"patient"
            iodFileNames = [
                "study",
                "series",
                "equipment",
                "scEquipment",
                "sop",
                "encapsulatedDocument"
            ]
        }
        return forkJoin(iodFileNames.filter((m,i)=> i >= level).map(m=>this.getIod(m))).pipe(map(res=>{
            let merged = {};
            res.forEach(o=>{
                merged = Object.assign(merged,o)
            });
            return merged;
        }));
    }

    getUPSIod(mode:UPSModifyMode) {
        if(mode && (mode === "create" || mode === "clone")){
            return this.getIod("upsCreate");
        }
        return this.getIod("upsUpdate");
    };
    getPatientIod() {
        return this.getIod("patient");
    };

    getStudyIod() {
        return this.getIod("study");
    };

    getMwlIod() {
        return this.getIod("mwl");
    };

    getPrepareParameterForExpiriationDialog(study, exporters, infinit) {
        let expiredDate: Date;
        let title = $localize `:@@set_expired_date_for_the_study:Set expired date for the study.`;
        let schema: any = [
            [
                [
                    {
                        tag: "label",
                        text: $localize `:@@expired_date:Expired date`
                    },
                    {
                        tag: "p-calendar",
                        filterKey: "expiredDate",
                        description: $localize `:@@expired_date:Expired Date`
                    }
                ]
            ]
        ];
        let schemaModel = {};
        if (infinit) {
            if (_.hasIn(study, "7777102B.Value[0]") && study["7777102B"].Value[0] === "FROZEN") {
                schemaModel = {
                    setExpirationDateToNever: false,
                    FreezeExpirationDate: false
                };
                title = $localize `:@@unfreeze_unprotect_expiration_date_of_the_study:Unfreeze/Unprotect Expiration Date of the Study`;
                schema = [
                    [
                        [
                            {
                                tag: "label",
                                text: $localize `:@@expired_date:Expired Date`
                            },
                            {
                                tag: "p-calendar",
                                filterKey: "expiredDate",
                                description: $localize `:@@expired_date:Expired Date`
                            }
                        ]
                    ]
                ];
            } else {
                title = $localize `:@@freeze_protect_expiration_date_of_the_study:Freeze/Protect Expiration Date of the Study`;
                schemaModel = {
                    setExpirationDateToNever: true,
                    FreezeExpirationDate: true
                };
                schema = [
                    [
                        [
                            {
                                tag: "label",
                                text: $localize `:@@expired_date:Expired date`,
                                showIf: (model) => {
                                    return !model.setExpirationDateToNever
                                }
                            },
                            {
                                tag: "p-calendar",
                                filterKey: "expiredDate",
                                description: $localize `:@@expired_date:Expired Date`,
                                showIf: (model) => {
                                    return !model.setExpirationDateToNever
                                }
                            }
                        ], [
                        {
                            tag: "dummy"
                        },
                        {
                            tag: "checkbox",
                            filterKey: "setExpirationDateToNever",
                            description: $localize `:@@set_expiration_date_to_never_if_you_want_also_to_protect_the_study:Set Expiration Date to 'never' if you want also to protect the study`,
                            text: $localize `:@@set_expiration_date_to_never_if_you_want_also_to_protect_the_study:Set Expiration Date to 'never' if you want also to protect the study`
                        }
                    ], [
                        {
                            tag: "dummy"
                        },
                        {
                            tag: "checkbox",
                            filterKey: "FreezeExpirationDate",
                            description: $localize `:@@freeze_expiration_date:Freeze Expiration Date`,
                            text: $localize `:@@freeze_expiration_date:Freeze Expiration Date`
                        }
                    ]
                    ]
                ];
            }
        } else {
            if (_.hasIn(study, "77771023.Value.0") && study["77771023"].Value[0] != "") {
                let expiredDateString = study["77771023"].Value[0];
                expiredDate = new Date(expiredDateString.substring(0, 4) + '.' + expiredDateString.substring(4, 6) + '.' + expiredDateString.substring(6, 8));
            } else {
                expiredDate = new Date();
            }
            schemaModel = {
                expiredDate: j4care.formatDate(expiredDate, 'yyyyMMdd')
            };
            title += "<p>Set exporter if you wan't to export on expiration date too.";
            schema[0].push([
                {
                    tag: "label",
                    text: $localize `:@@exporter:Exporter`
                },
                {
                    tag: "select",
                    filterKey: "exporter",
                    description: $localize `:@@exporter:Exporter`,
                    options: exporters.map(exporter => new SelectDropdown(exporter.id, exporter.description || exporter.id))
                }])
        }
        return {
            content: title,
            form_schema: schema,
            result: {
                schema_model: schemaModel
            },
            saveButton: $localize `:@@SAVE:SAVE`
        };
    }

    setExpiredDate(deviceWebservice: StudyWebService, studyUID, expiredDate, exporter, params?: any) {
        const url = this.getModifyStudyUrl(deviceWebservice);
        let localParams = "";
        if (exporter) {
            localParams = `?ExporterID=${exporter}`
        }
        if (params && Object.keys(params).length > 0) {
            if (localParams) {
                localParams += j4care.objToUrlParams(params);
            } else {
                localParams = `?${j4care.objToUrlParams(params)}`
            }
        }
        return this.$http.put(`${url}/${studyUID}/expire/${expiredDate}${localParams}`, {})
    }

    getExporters = () => this.$http.get('../export');

    deleteStudy = (studyInstanceUID: string, dcmWebApp: DcmWebApp, param) =>
        this.$http.delete(`${this.getDicomURL("study", dcmWebApp)}/${studyInstanceUID}${j4care.param(param)}`);

    deleteRejectedInstances = (reject, params) => this.$http.delete(`../reject/${reject}${j4care.param(params)}`);

    rejectRestoreMultipleObjects(multipleObjects: SelectionActionElement, selectedWebService: DcmWebApp, rejectionCode: string) {
        return forkJoin(multipleObjects.getAllAsArray().filter((element: SelectedDetailObject) => (element.dicomLevel != "patient")).map((element: SelectedDetailObject) => {
            return this.$http.post(
                `${this.getURL(element.object.attrs, selectedWebService, element.dicomLevel)}/reject/${rejectionCode}`,
                {},
                this.jsonHeader
            );
        }));
    }

    rejectMatchingStudies(webApp: DcmWebApp, rejectionCode, params:any){
        return this.$http.post(
            `${this.getDicomURL("study", webApp)}/reject/${rejectionCode}${j4care.param(params)}`,
            {},
            this.jsonHeader
        )
    }

    rejectStudy(studyAttr, webService:StudyWebService, rejectionCode) {
        let _webApp;
        return this.getWebAppFromWebServiceClassAndSelectedWebApp(webService, "DCM4CHEE_ARC_AET", "REJECT").pipe(map(webApp=>{
            _webApp = webApp;
            return `${this.studyURL(studyAttr, webApp)}/reject/${rejectionCode}`;
        })).pipe(switchMap(url=>{
            return this.$http.post(
                url,
                {},
                this.jsonHeader,
                undefined,
                _webApp
            )
        }));
/*        return
            this.$http.post(
            `${this.studyURL(studyAttr, webApp)}/reject/${rejectionCode}`, //TODO this will work only for internal aets (look this 'DCM4CHEE_ARC_AET' if not found look for this class'REJECT')
            {},
            this.jsonHeader
        )}*/
    }

    rejectSeries(studyAttr, webService:StudyWebService, rejectionCode) {
        let _webApp;
        return this.getWebAppFromWebServiceClassAndSelectedWebApp(webService, "DCM4CHEE_ARC_AET", "REJECT").pipe(map(webApp=>{
            _webApp = webApp;
            return `${this.seriesURL(studyAttr, webApp)}/reject/${rejectionCode}`;
        })).pipe(switchMap(url=>{
            return this.$http.post(
                url,
                {},
                this.jsonHeader,
                undefined,
                _webApp
            )
        }));
    }

    rejectInstance(studyAttr, webService:StudyWebService, rejectionCode) {
        let _webApp;
        return this.getWebAppFromWebServiceClassAndSelectedWebApp(webService, "DCM4CHEE_ARC_AET", "REJECT").pipe(map(webApp=>{
            _webApp = webApp;
            return `${this.instanceURL(studyAttr, webApp)}/reject/${rejectionCode}`;
        })).pipe(switchMap(url=>{
            return this.$http.post(
                url,
                {},
                this.jsonHeader,
                undefined,
                _webApp
            )
        }));
    }


    mapCode(m, i, newObject, mapCodes) {
        if (_.hasIn(mapCodes, i)) {
            if (_.isArray(mapCodes[i])) {
                _.forEach(mapCodes[i], (seq, j) => {
                    newObject[seq.code] = _.get(m, seq.map);
                    newObject[seq.code].vr = seq.vr;
                });
            } else {
                newObject[mapCodes[i].code] = m;
                newObject[mapCodes[i].code].vr = mapCodes[i].vr;
            }
        }
    }

    getMsgFromResponse(res, defaultMsg = null) {
        let msg;
        let endMsg = '';
        try {
            //TODO information could be in res.error too
            msg = res.json();
            if (_.hasIn(msg, "completed")) {
                endMsg = `Completed: ${msg.completed}<br>`;
            }
            if (_.hasIn(msg, "warning")) {
                endMsg = endMsg + `Warning: ${msg.warning}<br>`;
            }
            if (_.hasIn(msg, "failed")) {
                endMsg = endMsg + `Failed: ${msg.failed}<br>`;
            }
            if (_.hasIn(msg, "errorMessage")) {
                endMsg = endMsg + `${msg.errorMessage}<br>`;
            }
            if (_.hasIn(msg, "error")) {
                endMsg = endMsg + `${msg.error}<br>`;
            }
            if (endMsg === "") {
                endMsg = defaultMsg;
            }
        } catch (e) {
            if (defaultMsg) {
                endMsg = defaultMsg;
            } else {
                endMsg = res.statusText;
            }
        }
        return endMsg;
    }

    export = (url, objects?: SelectionActionElement, urlSuffix?: string, selectedWebService?: DcmWebApp) => {
        if (url) {
            return this.$http.post(url, {}, this.jsonHeader);
        } else {
            return forkJoin(objects.getAllAsArray().filter((element: SelectedDetailObject) => (element.dicomLevel != "patient")).map((element: SelectedDetailObject) => {
                return this.$http.post(
                    this.getURL(element.object.attrs, selectedWebService, element.dicomLevel) + urlSuffix,
                    {},
                    this.jsonHeader
                );
            }));
        }
    };

    getQueueNames = () => this.$http.get('../queue');

    getRejectNotes = (params?: any) => this.$http.get(`../reject/${j4care.param(params)}`);

    createEmptyStudy = (patientDicomAttrs, dcmWebApp) => this.$http.post(this.getDicomURL("study", dcmWebApp), patientDicomAttrs, this.dicomHeader);

    convertStringLDAPParamToObject(object:any, path:string, keys:string[]){
        try{
            _.get(object,path).forEach(param=>{
                keys.forEach(key=>{
                    if(param.indexOf(key) > -1){
                        object[key] = param.replace(key + '=','');
                    }
                })
            })
        }catch (e) {

        }
    }

    webAppHasPermission(webApps:DcmWebApp[]){
        if((this.appService.user && this.appService.user.roles && this.appService.user.roles.length > 0 && this.appService.user.su) || (this.appService.global && this.appService.global.notSecure)){
            return webApps;
        }else {
            return webApps.filter((webApp:DcmWebApp)=>{
                    if(_.hasIn(webApp,"dcmProperty") && webApp.dcmProperty.length > 0){
                        let roles = this.getWebAppRoles(webApp) || [];
                        if(roles.length > 0){
                            let check:boolean = false;
                            roles.forEach(role=>{
                                check = check || this.appService.user.roles.indexOf(role) > -1;
                            });
                            return check;
                        }else{
                            j4care.log($localize `:@@study.no_role_found_in_the_property_dcmproperty_of_webapp:No role found in the property dcmProperty of WebApp`,webApp);
                            return true;
                        }
                    }else{
                        return true;
                    }
                });
        }
    }

    getWebAppRoles(webApp):string[]{
        try{
            const regex = /roles=(.*)/gm;
            const regex2 = /(\w+)/gm;
            let roles = [];
            let m,m2;
            while ((m = regex.exec(webApp.dcmProperty)) !== null) {
                if (m.index === regex.lastIndex) {
                    regex.lastIndex++;
                }
                while ((m2 = regex2.exec(m[1])) !== null) {
                    if (m2.index === regex2.lastIndex) {
                        regex2.lastIndex++;
                    }
                    roles.push(m2[1]);
                }
            }
            return roles;
        }catch (e) {
            console.log("webApp=",webApp);
            j4care.log($localize `:@@study.something_went_wrong_on_extracting_roles_from_dcmproperty_of_webapp:Something went wrong on extracting roles from dcmProperty of WebApp`,e);
            return [];
        }
    }

    getTextFromAction(action:SelectionAction){
        switch (action){
            case "copy":
                return $localize `:@@selection.action.copy:Copy`;
            case "cut":
                return $localize `:@@selection.action.cut:Cut`;
            case "link":
                return $localize `:@@selection.action.link:Link`;
            case "merge":
                return $localize `:@@selection.action.merge:Merge`;
            default:
                return $localize `:@@move:Move`;
        }
    }

    getLevelText(level:DicomLevel){
        switch (level){
            case "study":
                return $localize `:@@study:study`;
            case "series":
                return $localize `:@@series:series`;
            case "instance":
                return $localize `:@@instance:instance`;
            case "patient":
                return $localize `:@@patient:patient`;
            case "diff":
                return $localize `:@@diff:diff`;
            case "mwl":
                return $localize `:@@mwl:mwl`;
        }
    }
}
