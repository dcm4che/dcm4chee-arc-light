import { Injectable } from '@angular/core';
import {
    AccessControlIDMode,
    AccessLocation,
    DicomLevel,
    DicomMode,
    DicomResponseType,
    DiffAttributeSet,
    StorageSystems,
    FilterSchema,
    FilterSchemaElement,
    SelectDropdown,
    SelectedDetailObject,
    SelectionAction,
    StudyFilterConfig,
    UniqueSelectIdObject,
    UPSModifyMode
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
import {DcmWebApp, WebServiceClass} from "../../models/dcm-web-app";
import {HttpClient, HttpHeaders} from "@angular/common/http";
import {
    DicomTableSchema,
    DynamicPipe,
    StudySchemaOptions, TableAction
} from "../../helpers/dicom-studies-table/dicom-studies-table.interfaces";
import {ContentDescriptionPipe} from "../../pipes/content-description.pipe";
import {PatientIssuerPipe} from "../../pipes/patient-issuer.pipe";
import {PersonNamePipe} from "../../pipes/person-name.pipe";
import {TableSchemaElement} from "../../models/dicom-table-schema-element";
import {KeycloakService} from "../../helpers/keycloak-service/keycloak.service";
import {WebAppsListService} from "../../configuration/web-apps-list/web-apps-list.service";
import {StudyWebService} from "./study-web-service.model";
import {PermissionService} from "../../helpers/permissions/permission.service";
import {SelectionActionElement} from "./selection-action-element.models";
declare var DCM4CHE: any;
import {catchError, map, switchMap, tap} from "rxjs/operators";
import {FormatTMPipe} from "../../pipes/format-tm.pipe";
import {FormatDAPipe} from "../../pipes/format-da.pipe";
import {FormatAttributeValuePipe} from "../../pipes/format-attribute-value.pipe";
import {AppService} from "../../app.service";
import {MwlDicom} from "../../models/mwl-dicom";
import {DynamicPipePipe} from "../../pipes/dynamic-pipe.pipe";
import {DatePipe} from "@angular/common";
import {CustomDatePipe} from "../../pipes/custom-date.pipe";
import {SeriesDicom} from "../../models/series-dicom";
import {StudyDicom} from "../../models/study-dicom";

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
        private appService:AppService,
        private j4careService:j4care,
        private nativeHttp:HttpClient
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
    getTokenService(studyWebService?:StudyWebService,dcmWebApp?:DcmWebApp){
        if(studyWebService && studyWebService.selectedWebService && _.hasIn(studyWebService.selectedWebService, "dcmKeycloakClientID")){
            return this.$http.getRealm(studyWebService.selectedWebService);
        }else{
            if(dcmWebApp){
                return this.$http.getRealm(dcmWebApp);
            }
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
            if (obj.IssuerOfPatientID && obj.IssuerOfPatientID != "") {
                patientId += '^^^' + obj.IssuerOfPatientID;
            }
            if (_.hasIn(obj, 'IssuerOfPatientIDQualifiers.UniversalEntityID') && _.get(obj, 'IssuerOfPatientIDQualifiers.UniversalEntityID') != "") {
                patientId += '&' + obj.IssuerOfPatientIDQualifiers.UniversalEntityID;
            }
            if (_.hasIn(obj, 'IssuerOfPatientIDQualifiers.UniversalEntityIDType') && _.get(obj, 'IssuerOfPatientIDQualifiers.UniversalEntityIDType') != "") {
                patientId += '&' + obj.IssuerOfPatientIDQualifiers.UniversalEntityIDType;
            }
            if (_.hasIn(obj, '["00100020"].Value[0]') && _.get(obj, '["00100020"].Value[0]') != "") {
                patientId += obj["00100020"].Value[0];
            }
            if (_.hasIn(obj, '["00100021"].Value[0]') && _.get(obj, '["00100021"].Value[0]') != "")
                patientId += '^^^' + obj["00100021"].Value[0];
            else {
                if ((
                        _.hasIn(obj, '["00100024"].Value[0]["00400032"].Value[0]') &&
                        _.get(obj, '["00100024"].Value[0]["00400032"].Value[0]') != ""
                    ) ||
                        _.hasIn(obj, '["00100024"].Value[0]["00400033"].Value[0]') &&
                        _.get(obj, '["00100024"].Value[0]["00400033"].Value[0]') != ""
                    )
                    patientId += '^^^';
            }
            if (_.hasIn(obj, '["00100024"].Value[0]["00400032"].Value[0]') && _.get(obj, '["00100024"].Value[0]["00400032"].Value[0]') != "") {
                patientId += '&' + obj['00100024'].Value[0]['00400032'].Value[0];
            }
            if (_.hasIn(obj, '["00100024"].Value[0]["00400033"].Value[0]') && _.get(obj, '["00100024"].Value[0]["00400033"].Value[0]') != "") {
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
                    case "series":
                        return [
                            ...Globalvar.SERIES_FILTER_SCHEMA([], [],false),
                            ...Globalvar.SERIES_FILTER_SCHEMA([], [],true)
                        ].filter(filter => {
                            return filter.filterKey != "aet";
                        });
                    case "mwl":
                        return [
                            ...Globalvar.MWL_FILTER_SCHEMA(false),
                            ...Globalvar.MWL_FILTER_SCHEMA(true)
                        ];
                    case "mpps":
                        return [
                            ...Globalvar.MPPS_FILTER_SCHEMA(false),
                            ...Globalvar.MPPS_FILTER_SCHEMA(true)
                        ];
                    case "uwl":
                        return [
                            ...Globalvar.UWL_FILTER_SCHEMA(false),
                            ...Globalvar.UWL_FILTER_SCHEMA(true)
                        ];
                    case "diff":
                        return [
                            ...Globalvar.DIFF_FILTER_SCHEMA([],[],false),
                            ...Globalvar.DIFF_FILTER_SCHEMA([],[],true)
                        ].filter(filter => {
                            return filter.filterKey != "aet";
                        });
                    default:
                        return [
                            ...Globalvar.STUDY_FILTER_SCHEMA([], [], false),
                            ...Globalvar.STUDY_FILTER_SCHEMA([], [], true)
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

    getFilterSchema(
        tab: DicomMode,
        aets: Aet[],
        quantityText: { count: string, size: string },
        filterMode: ('main' | 'expand'),
        storages?:SelectDropdown<StorageSystems>[],
        studyWebService?: StudyWebService,
        attributeSet?:SelectDropdown<DiffAttributeSet>[],
        showCount?:boolean,
        showSize?:boolean,
        filter?:StudyFilterConfig,
        hook?:Function
    ) {
        let schema: FilterSchema;
        let lineLength: number = 3;
        switch (tab) {
            case "patient":
                schema = Globalvar.PATIENT_FILTER_SCHEMA(aets, filterMode === "expand").filter(filter => {
                    return filter.filterKey != "aet";
                });
                lineLength = filterMode === "expand" ? 1 : 3;
                break;
            case "series":
                schema = Globalvar.SERIES_FILTER_SCHEMA(aets, storages, filterMode === "expand").filter(filter => {
                    return filter.filterKey != "aet";
                });
                lineLength = 3;
                break;
            case "mwl":
                schema = Globalvar.MWL_FILTER_SCHEMA( filterMode === "expand");
                lineLength = filterMode === "expand" ? 1 : 3;
                break;
            case "mpps":
                schema = Globalvar.MPPS_FILTER_SCHEMA( filterMode === "expand");
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
                schema = Globalvar.STUDY_FILTER_SCHEMA(aets, storages, filterMode === "expand").filter(filter => {
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
                        webApp.dcmWebAppName,
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
            if (j4care.arrayIsNotEmpty(studyWebService,"webServices"))
                schema.push({
                    tag: "button",
                    id: "submit",
                    text: $localize `:@@SUBMIT:SUBMIT`,
                    description: this.getSubmitText(tab)
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
            if(tab === "study" && showSize){
                schema.push({
                    tag: "button",
                    id: "size",
                    showRefreshIcon: true,
                    showDynamicLoader: false,
                    text: quantityText.size,
                    description: $localize `:@@query_only_studies_size:Query only size of studies`
                })
            }
        }
        if(hook){
            schema = hook.call(this, schema);
        }
        return {
            lineLength: lineLength,
            schema: j4care.prepareFlatFilterObject(schema, lineLength)
        }
    }

    getNoServiceSpecificWebApps(tab: DicomMode) {
        let webServiceClass = 'QIDO_RS';
        let entityOp = $localize `:@@entity_op_view_studies:view studies`;
        switch (tab) {
            case "patient":
                entityOp = $localize `:@@entity_op_view_patients:view patients`;
                break;
            case "series":
                entityOp = $localize `:@@entity_op_view_series:view series`;
                break;
            case "mwl":
                webServiceClass = 'MWL_RS';
                entityOp = $localize `:@@entity_op_view_mwls:view MWLs`;
                break;
            case "mpps":
                webServiceClass = 'MPPS_RS';
                entityOp = $localize `:@@entity_op_view_mpps:view MPPS`;
                break;
            case "uwl":
                webServiceClass = 'UPS_RS';
                entityOp = $localize `:@@entity_op_view_ups:view UPS Workitems`;
                break;
            case "diff":
                webServiceClass = 'DCM4CHEE_ARC_AET_DIFF';
                entityOp = $localize `:@@entity_op_compare_archives:compare studies between two archives`;
                break;
        }
        return $localize `:@@configure_webapp_with_webservice:Configure at least one web application with ${webServiceClass}:@@webServiceClass: web service class to ${entityOp}:@@entityOp:`;
    }

    getSubmitText(tab: DicomMode) {
        switch (tab) {
            case "study":
                return $localize `:@@query_studies:Query Studies`;
            case "patient":
                return $localize `:@@query_patients:Query Patients`;
            case "series":
                return $localize `:@@query_studies:Query Series`;
            case "mwl":
                return $localize `:@@query_mwl:Query MWL`;
            case "mpps":
                return $localize `:@@query_mpps:Query MPPS`;
            case "uwl":
                return $localize `:@@query_uwl:Query UWL`;
            case "diff":
                return $localize `:@@study.show_diffs:Show DIFFs`;
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

    getMPPS(filterModel, dcmWebApp: DcmWebApp, responseType?: DicomResponseType): Observable<any> {
        let header: HttpHeaders;
        if (!responseType || responseType === "object") {
            header = this.dicomHeader
        }
        let params = j4care.objToUrlParams(filterModel);
        params = params ? `?${params}` : params;

        return this.$http.get(
            `${this.getDicomURL("mpps", dcmWebApp, responseType)}${params || ''}`,
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
        let taskID;
        let url;
        if((_.hasIn(filterModel,"batchID") && _.get(filterModel,"batchID") != "") || (_.hasIn(filterModel,"taskID") && _.get(filterModel,"taskID") != "")){
            batchID = _.get(filterModel,"batchID");
            taskID = _.get(filterModel,"taskID");
            delete filterModel["batchID"];
            delete filterModel["taskID"];
            if(batchID && batchID != ""){
                url = `${j4care.addLastSlash(this.appService.baseUrl)}monitor/diff/batch/${batchID}/studies${j4care.objToUrlParams(j4care.clearEmptyObject(filterModel),true)}`
            }else{
                if(taskID){
                    url = `${j4care.addLastSlash(this.appService.baseUrl)}monitor/diff/${taskID}/studies${j4care.objToUrlParams(j4care.clearEmptyObject(filterModel),true)}`
                }
            }
        }
        if((batchID || taskID) && url){
            return this.$http.get(
                url,
                header
            )
        }else{
            return this.getWebAppFromWebServiceClassAndSelectedWebApp(studyWebService, "DCM4CHEE_ARC_AET_DIFF", "DCM4CHEE_ARC_AET_DIFF")
                .pipe(map(webApp=>{
                        return `${j4care.getUrlFromDcmWebApplication(webApp, this.appService.baseUrl)}`;
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

    unmergePatient(dcmWebApp: DcmWebApp, patientId:string){
        return this.$http.post(`${this.getDicomURL("patient", dcmWebApp)}/${patientId}/unmerge`, undefined, true);
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

    getSeries(filterModel, dcmWebApp: DcmWebApp, responseType?: DicomResponseType): Observable<any> {
        let header: HttpHeaders;
        if (!responseType || responseType === "object") {
            header = this.dicomHeader
        }
        let params = j4care.objToUrlParams(filterModel);
        params = params ? `?${params}` : params;
        return this.$http.get(
            `${this.getDicomURL("series", dcmWebApp, responseType)}${params || ''}`,
            header,
            false,
            dcmWebApp
        );
    }

    getSeriesOfStudy(studyInstanceUID: string, filterModel: any, dcmWebApp: DcmWebApp, responseType?: DicomResponseType): Observable<any> {
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

    getSeriesInstanceUID(model){
        try{
            return _.get(model, "0020000E.Value[0]");
        }catch (e) {
            return undefined;
        }
    }

    getDicomURL(mode: DicomMode, dcmWebApp: DcmWebApp, responseType?: DicomResponseType, csvField?:number): string {
        console.log("object", dcmWebApp);
        if(dcmWebApp){
            try {
                let url = j4care.getUrlFromDcmWebApplication(dcmWebApp, this.appService.baseUrl);
                if(url){
                    switch (mode) {
                        case "patient":
                            url += '/patients';
                            break;
                        case "mwl":
                            url += '/mwlitems';
                            break;
                        case "mpps":
                            url += '/mpps';
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
                        case "series":
                            url += '/series';
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
                url = `${j4care.getUrlFromDcmWebApplication(webApp, this.appService.baseUrl)}?requestType=WADO`;
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

    recreateDBRecord(filters, selectedWebService:DcmWebApp, studyObject){
        return this.$http.post(
            `${this.studyURL(studyObject.attrs, selectedWebService)}/reimport${j4care.param(filters)}`,
            {},
            this.jsonHeader,
            undefined,
            selectedWebService
        );
    }
    private diffUrl(callingAet: Aet, firstExternalAet?: Aet, secondExternalAet?: Aet, baseUrl?: string) {

        return `${
        baseUrl ? j4care.addLastSlash(baseUrl) : j4care.addLastSlash(this.appService.baseUrl)
            }aets/${
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
            `${baseUrl ? j4care.addLastSlash(baseUrl): j4care.addLastSlash(this.appService.baseUrl)}attribute-filter/${entity || "Patient"}`
        )
        .pipe(map(res => {
            if ((!entity || entity === "Patient") && res["dcmTag"]) {
                let privateAttr = [parseInt('77770010', 16), parseInt('77771010', 16), parseInt('77771011', 16)];
                res["dcmTag"].push(...privateAttr);
            }
            if (entity && entity === "Study" && res["dcmTag"]) {
                let privateAttr = [parseInt('77770010', 16), parseInt('77771020', 16), parseInt('77771021', 16), parseInt('77771022', 16)];
                res["dcmTag"].push(...privateAttr);
            }
            return res;
        }));
    }

    getDiffAttributeSet = (baseUrl?: string) => this.$http.get(`${baseUrl ? j4care.addLastSlash(baseUrl): j4care.addLastSlash(this.appService.baseUrl)}attribute-set/DIFF_RS`);

    getAets = () => this.aeListService.getAets();

    getAes = () => this.aeListService.getAes();

    equalsIgnoreSpecificCharacterSet(attrs, other) {
        return Object.keys(attrs).filter(tag => tag != '00080005')
                .every(tag => _.isEqual(attrs[tag], other[tag]))
            && Object.keys(other).filter(tag => tag != '00080005')
                .every(tag => attrs[tag]);
    }

    queryPatientDemographics(patientID: string, PDQServiceID: string, url?: string) {
        return this.$http.get(`${url ? j4care.addLastSlash(url) : j4care.addLastSlash(this.appService.baseUrl)}pdq/${PDQServiceID}/patients/${patientID}`);
    }
    queryNationalPatientRegister(patientID){
        return this.$http.get(`${j4care.addLastSlash(this.appService.baseUrl)}xroad/RR441/${patientID}`)
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

    schedulestorageVerificationStudies = (param, studyWebService: StudyWebService) => this.$http.post(`${this.getDicomURL("study", studyWebService.selectedWebService)}/stgver${j4care.param(param)}`, {});

    schedulestorageVerificationSeries = (param, studyWebService: StudyWebService) => this.$http.post(`${this.getDicomURL("series", studyWebService.selectedWebService)}/stgver${j4care.param(param)}`, {});

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

    updateCharset = (charset:string, testUpdateCharset:string, param, studyWebService: StudyWebService) => {
        let paramString = `${j4care.param(param)}`;
        paramString = testUpdateCharset
            ? paramString == ''
                ? '?test=' + testUpdateCharset
                : paramString + '&test=' + testUpdateCharset
            : paramString;
        return this.$http.post(
            `${this.getDicomURL("patient", studyWebService.selectedWebService)}/charset/${charset}${paramString}`,
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

    sendStorageCommitmentRequestForMatchingStudies(studyWebService: StudyWebService,stgCmtSCP:string, filters:any){
        return this.$http.post(
            `${this.getDicomURL("study", studyWebService.selectedWebService)}/stgcmt/${stgCmtSCP}${j4care.param(filters)}`,
            {}
        );
    }
    sendStorageCommitmentRequestForMatchingSeries(studyWebService: StudyWebService,stgCmtSCP:string, filters:any){
        return this.$http.post(
            `${this.getDicomURL("series", studyWebService.selectedWebService)}/stgcmt/${stgCmtSCP}${j4care.param(filters)}`,
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
    sendInstanceAvailabilityNotificationForMatchingStudies(studyWebService: StudyWebService, ianscp:string, filters:any){
        return this.$http.post(
            `${this.getDicomURL("study", studyWebService.selectedWebService)}/ian/${ianscp}${j4care.param(filters)}`,
            {}
        );
    }
    sendInstanceAvailabilityNotificationForMatchingSeries(studyWebService: StudyWebService, ianscp:string, filters:any){
        return this.$http.post(
            `${this.getDicomURL("series", studyWebService.selectedWebService)}/ian/${ianscp}${j4care.param(filters)}`,
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

    PATIENT_STUDIES_TABLE_SCHEMA($this, actions:Function, options: StudySchemaOptions): DicomTableSchema {
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
                                    return j4care.is(options, "appService['xRoad']") || (j4care.is(options,"appService.global['PDQs']") && options.appService.global['PDQs'].length > 0);
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
                                },
                                id:"patient_create_mwl"
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
                                id:"patient_upload_file",
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
                                id:"study_download_csv",
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
                                id:"patient_open_viewer",
                                title: $localize `:@@study.open_patient_in_the_viewer:Open patient in the viewer`,
                                permission: {
                                    id: 'action-studies-viewer',
                                    param: 'visible'
                                },
                                showIf: (e, config) => {
                                    return _.hasIn(options,"selectedWebService.IID_PATIENT_URL");
                                }
                            }, {
                                icon: {
                                    tag: 'span',
                                    cssClass: `custom_icon unmerge_black`,
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "patient",
                                        action: "unmerge_patient"
                                    }, e);
                                },
                                id:"patient_unmerge_patient",
                                title: $localize `:@@unmerge_this_patient:Unmerge this Patient`,
                                permission: {
                                    id: 'action-studies-patient',
                                    param: 'unmerge'
                                },
                                showIf: (e, config) => {
                                    return (_.hasIn(e,'attrs.77771015'))
                                        && this.selectedWebServiceHasClass(options.selectedWebService,"DCM4CHEE_ARC_AET");
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
                            title: $localize `:@@study.toggle_attributes:Toggle Attributes`,
                            permission: {
                                id: 'action-studies-show-attributes',
                                param: 'visible'
                            }
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
                                    case "mpps":
                                        e.showMpps = !e.showMpps;
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
                                if(!options.cd_mode){
                                    switch (options.studyConfig.tab) {
                                        case "mwl":
                                            msg = "MWLs";
                                            break;
                                        case "mpps":
                                            msg = "MPPSs";
                                            break;
                                        case "diff":
                                            msg = "DIFFs";
                                            break;
                                        case "uwl":
                                            msg = "UWLs";
                                            break;
                                    }
                                }
                                return string[0] + msg;
                            })`Hide ${''}`,
                            showIf: (e) => {
                                if(!options.cd_mode){
                                    switch (options.studyConfig.tab) {
                                        case "mwl":
                                            return e.showMwls;
                                        case "mpps":
                                            return e.showMpps;
                                        case "diff":
                                            return e.showDiffs;
                                        case "uwl":
                                            return e.showUwls;
                                        default:
                                            return e.showStudies;
                                    }
                                }else {
                                    return false;
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
                                    case "mpps":
                                        e.showMpps = !e.showMpps;
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
                                if(!options.cd_mode){
                                    switch (options.studyConfig.tab) {
                                        case "mwl":
                                            msg = "MWLs";
                                            break;
                                        case "mpps":
                                            msg = "MPPSs";
                                            break;
                                        case "diff":
                                            msg = "DIFFs";
                                            break;
                                        case "uwl":
                                            msg = "UWLs";
                                            break;
                                    }
                                }
                                return string[0] + msg;
                            })`Show ${''}`
                            ,
                            showIf: (e) => {
                                if(!options.cd_mode){
                                    switch (options.studyConfig.tab) {
                                        case "mwl":
                                            return !e.showMwls;
                                        case "mpps":
                                            return !e.showMpps;
                                        case "diff":
                                            return !e.showDiffs;
                                        case "uwl":
                                            return !e.showUwls;
                                        default:
                                            return !e.showStudies;
                                    }
                                }else{
                                    return false;
                                }
                            }
                        }
                    ],
                    headerDescription: ((string,...keys) => { //TODO change the code so you can use $localize
                        let msg = "Studies";
                        switch (options.studyConfig.tab) {
                            case "mwl":
                                msg = "MWLs";
                                break;
                            case "mpps":
                                msg = "MPPSs";
                                break;
                            case "diff":
                                msg = "DIFFs";
                                break;
                            case "uwl":
                                msg = "UWLs";
                                break;
                        }
                        return string[0] + msg;
                    })`Toggle ${''}`,
                    pxWidth: 40
                }),
                new TableSchemaElement({
                    type: "pipe",
                    header: $localize `:@@patients_name:Patient's Name`,
                    headerDescription: $localize `:@@patients_name:Patient's Name`,
                    widthWeight: 1.5,
                    saveTheOriginalValueOnTooltip:true,
                    calculatedWidth: "20%",
                    pathToValue:"00100010.Value.0",
                    pipe: new DynamicPipe(PersonNamePipe, [options.configuredPersonNameFormat])
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
                    type: "pipe",
                    header: $localize `:@@birth_date:Birth Date`,
                    pathToValue: "00100030.Value[0]",
                    headerDescription: $localize `:@@patients_birth_date:Patient's Birth Date`,
                    pipe: new DynamicPipe(CustomDatePipe, [options.configuredDateTimeFormats]),
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
                                id: "study_edit_study",
                                title: $localize `:@@study.edit_this_study:Edit this study`,
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
                                    cssClass: 'material-icons',
                                    text: 'visibility_off'
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "study",
                                        action: "mark_as_requested_unscheduled"
                                    }, e);
                                },
                                title: $localize `:@@mark_mode_study_text:Mark study as Requested or Unscheduled`,
                                id:"study_mark_as_requested_unscheduled",
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
                                id: "study_modify_expired_date",
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
                                    cssClass: j4care.is(options,"trash.active") ? 'glyphicon glyphicon-repeat' : 'glyphicon glyphicon-trash',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "study",
                                        action: "reject"
                                    }, e);
                                },
                                id: "study_reject",
                                title: j4care.is(options,"trash.active")  ? $localize `:@@study.restore_study:Restore study` : $localize `:@@study.reject_study:Reject study`,
                                permission: {
                                    id: 'action-studies-study',
                                    param: j4care.is(options,"trash.active")  ? 'restore' : 'reject'
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
                                id: "study_verify_storage",
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
                                id: "study_download",
                                title: $localize `:@@download:Download`,
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
                                id: "study_upload_file",
                                title: $localize `:@@upload_file:Upload file`,
                                permission: {
                                    id: 'action-studies-study',
                                    param: 'upload'
                                }
                            }, {
                                icon: {
                                    tag: 'span',
                                    cssClass: `custom_icon recreate_record`,
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "study",
                                        action: "recreate_record"
                                    }, e);
                                },
                                id:"study_recreate_record",
                                title: $localize `:@@recreate_db_record:Recreate DB Record`,
                                showIf:(e,config)=>{
                                    return  this.selectedWebServiceHasClass(options.selectedWebService,"DCM4CHEE_ARC_AET")
                                },
                                permission: {
                                    id: 'action-studies-study',
                                    param: 'recreate'
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
                                id: "study_export",
                                title: options.internal ? $localize `:@@study.export_study:Export study`: $localize `:@@study.retrieve_study:Retrieve study`,
                                permission: {
                                    id: 'action-studies-study',
                                    param: 'export'
                                },
                                showIf:(e)=>{
                                    return options.internal || this.webAppGroupHasClass(options.studyWebService, "MOVE");
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
                                id: "study_delete",
                                title: $localize `:@@study.delete_study_permanently:Delete study permanently`,
                                showIf: (e) => {
                                    return (
                                            j4care.is(options,"trash.active") ||
                                            j4care.is(options, "selectedWebService.dicomAETitleObject.dcmAllowDeleteStudyPermanently", "ALWAYS")
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
                                id: "series_download_csv",
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
                                id: "study_update_access_control_id",
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
                                id: "study_send_storage_commit",
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
                                id: "study_send_instance_availability_notification",
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
                                id: "study_open_viewer",
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
                            title: $localize `:@@study.toggle_attributes:Toggle Attributes`,
                            permission: {
                                id: 'action-studies-show-attributes',
                                param: 'visible'
                            }
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
                    type: "pipe",
                    header: $localize `:@@study_date:Study Date`,
                    pathToValue: "[00080020].Value[0]",
                    headerDescription: $localize `:@@study_date:Study Date`,
                    widthWeight: 0.6,
                    pipe: new DynamicPipe(CustomDatePipe, [options.configuredDateTimeFormats]),
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "pipe",
                    header: $localize `:@@study.study_time:Study Time`,
                    pathToValue: "[00080030].Value[0]",
                    headerDescription: $localize `:@@study.study_time:Study Time`,
                    pipe: new DynamicPipe(CustomDatePipe, [options.configuredDateTimeFormats]),
                    widthWeight: 0.6,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "pipe",
                    header: $localize `:@@study.r._physicians_name:R. Physician's Name`,
                    headerDescription: $localize `:@@referring_physician_name:Referring physician name`,
                    widthWeight: 1,
                    calculatedWidth: "20%",
                    pathToValue:"[00080090].PersonName[0]",
                    pipe: new DynamicPipe(PersonNamePipe, [options.configuredPersonNameFormat])
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
                                    cssClass: j4care.is(options,"trash.active")  ? 'glyphicon glyphicon-repeat' : 'glyphicon glyphicon-trash',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "series",
                                        action: "reject"
                                    }, e);
                                },
                                 id: "series_reject",
                                title: j4care.is(options,"trash.active")  ? $localize `:@@study.restore_series:Restore series` : $localize `:@@study.reject_series:Reject series`,
                                permission: {
                                    id: 'action-studies-serie',
                                    param: j4care.is(options,"trash.active") ? 'restore' : 'reject'
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
                                        level: "series",
                                        action: "edit_series"
                                    }, e);
                                },
                                id: "series_edit_series",
                                title: $localize `:@@study.edit_this_series:Edit this series`,
                                showIf:(e,config)=>{
                                    return  this.selectedWebServiceHasClass(options.selectedWebService,"DCM4CHEE_ARC_AET")
                                },
                                permission: {
                                    id: 'action-studies-serie',
                                    param: 'edit'
                                }
                            },{
                                icon: {
                                    tag: 'span',
                                    cssClass: 'material-icons',
                                    text: 'visibility_off'
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "series",
                                        action: "mark_as_requested_unscheduled"
                                    }, e);
                                },
                                id:"series_mark_as_requested_unscheduled",
                                title: $localize `:@@mark_mode_series_text:Mark series as Requested or Unscheduled`,
                                permission: {
                                    id: 'action-studies-serie',
                                    param: 'edit'
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
                                id: "series_verify_storage",
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
                                id: "series_download",
                                title: $localize `:@@download:Download`,
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
                                id: "series_upload_file",
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
                                id: "series_export",
                                title: options.internal ? $localize `:@@export_series:Export series` : $localize `:@@retrieve_series:Retrieve series`,
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
                                id: "series_send_storage_commit",
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
                                id: "series_send_instance_availability_notification",
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
                                id: "instance_download_csv",
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
                            title: $localize `:@@study.show_attributes:Show attributes`,
                            permission: {
                                id: 'action-studies-show-attributes',
                                param: 'visible'
                            }
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
                    type: "pipe",
                    header: $localize `:@@study.pps_start_date:PPS Start Date`,
                    pathToValue: "[00400244].Value[0]",
                    showBorderPath:"[00400244].showBorder",
                    headerDescription: $localize `:@@study.performed_procedure_step_start_date:Performed Procedure Step Start Date`,
                    pipe: new DynamicPipe(CustomDatePipe, [options.configuredDateTimeFormats]),
                    widthWeight: 0.6,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "pipe",
                    header: $localize `:@@study.pps_start_time:PPS Start Time`,
                    pathToValue: "[00400245].Value[0]",
                    showBorderPath:"[00400245].showBorder",
                    headerDescription: $localize `:@@study.performed_procedure_step_start_time:Performed Procedure Step Start Time`,
                    pipe: new DynamicPipe(CustomDatePipe, [options.configuredDateTimeFormats]),
                    widthWeight: 0.6,
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
                                    cssClass: j4care.is(options,"trash.active") ? 'glyphicon glyphicon-repeat' : 'glyphicon glyphicon-trash',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "instance",
                                        action: "reject"
                                    }, e);
                                },
                                id: "instance_reject",
                                title: j4care.is(options,"trash.active") ? $localize `:@@study.restore_instance:Restore instance` : $localize `:@@study.reject_instance:Reject instance`,
                                permission: {
                                    id: 'action-studies-instance',
                                    param: j4care.is(options,"trash.active") ? 'restore' : 'reject'
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
                                id: "instance_edit_series",
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
                                id: "instance_verify_storage",
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
                                id: "instance_download",
                                title: options.internal ? $localize `:@@export_instance:Export instance` : $localize `:@@retrieve_instance:Retrieve instance`,
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
                                id: "instance_upload_file",
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
                                id: "instance_export",
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
                                id: "instance_send_storage_commit",
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
                            title: $localize `:@@study.show_attributes:Show attributes`,
                            permission: {
                                id: 'action-studies-show-attributes',
                                param: 'visible'
                            }
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
                    type: "pipe",
                    header: $localize `:@@content_date:Content Date`,
                    pathToValue: "00080023.Value[0]",
                    headerDescription: $localize `:@@content_date:Content Date`,
                    pipe: new DynamicPipe(CustomDatePipe, [options.configuredDateTimeFormats]),
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "pipe",
                    header: $localize `:@@study.content_time:Content Time`,
                    pathToValue: "00080033.Value[0]",
                    headerDescription: $localize `:@@study.content_time:Content Time`,
                    pipe: new DynamicPipe(CustomDatePipe, [options.configuredDateTimeFormats]),
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
                            title: $localize `:@@study.show_attributes:Show attributes`,
                            permission: {
                                id: 'action-studies-show-attributes',
                                param: 'visible'
                            }
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
                    type: "pipe",
                    header: $localize `:@@sps_start_date:SPS Start Date`,
                    pathToValue: "00400100.Value[0].00400002.Value[0]",
                    headerDescription: $localize `:@@scheduled_procedure_step_start_date:Scheduled Procedure Step Start Date`,
                    pipe: new DynamicPipe(CustomDatePipe, [options.configuredDateTimeFormats]),
                    widthWeight: 1,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "pipe",
                    header: $localize `:@@study.sps_start:SPS Start`,
                    pathToValue: "00400100.Value[0].00400003.Value[0]",
                    headerDescription: $localize `:@@scheduled_procedure_step_start_time:Scheduled Procedure Step Start Time`,
                    pipe: new DynamicPipe(CustomDatePipe, [options.configuredDateTimeFormats]),
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "pipe",
                    header: $localize `:@@sp_physicians_name:SP Physician's Name`,
                    headerDescription: $localize `:@@scheduled_performing_physicians_name:Scheduled Performing Physician's Name`,
                    widthWeight: 2,
                    calculatedWidth: "20%",
                    pathToValue:"00400100.Value[0].00400006.Value[0]",
                    pipe: new DynamicPipe(PersonNamePipe, [options.configuredPersonNameFormat])
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
                    header:  $localize `:@@sps_description:SPS Description`,
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
            mpps:[
                new TableSchemaElement({
                    type: "index",
                    header: '',
                    pathToValue: '',
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
                            title: $localize `:@@study.show_attributes:Show attributes`,
                            permission: {
                                id: 'action-studies-show-attributes',
                                param: 'visible'
                            }
                        }
                    ],
                    headerDescription: $localize `:@@actions:Actions`,
                    pxWidth: 40
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@study.pps_id:PPS ID`,
                    pathToValue: "00400253.Value[0]",
                    headerDescription: $localize `:@@study.performed_procedure_step_id:Performed Procedure Step ID`,
                    widthWeight: 2,
                    calculatedWidth: "20%",
                    cssClass:"border-left"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@study_instance_uid:Study Instance UID`,
                    pathToValue: "00400270.Value[0].0020000D.Value[0]",
                    headerDescription: $localize `:@@study_instance_uid:Study Instance UID`,
                    widthWeight: 3,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "pipe",
                    header: $localize `:@@study.pps_start_date:PPS Start Date`,
                    pathToValue: "00400244.Value[0]",
                    headerDescription: $localize `:@@study.performed_procedure_step_start_date:Performed Procedure Step Start Date`,
                    widthWeight: 1,
                    pipe: new DynamicPipe(CustomDatePipe, [options.configuredDateTimeFormats]),
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "pipe",
                    header: $localize `:@@study.pps_start_time:PPS Start Time`,
                    pathToValue: "00400245.Value[0]",
                    headerDescription: $localize `:@@study.performed_procedure_step_start_time:Performed Procedure Step Start Time`,
                    pipe: new DynamicPipe(CustomDatePipe, [options.configuredDateTimeFormats]),
                    widthWeight: 1,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "pipe",
                    header: $localize `:@@study.pps_end_date:PPS End Date`,
                    pathToValue: "00400250.Value[0]",
                    headerDescription: $localize `:@@study.performed_procedure_step_end_date:Performed Procedure Step End Date`,
                    pipe: new DynamicPipe(CustomDatePipe, [options.configuredDateTimeFormats]),
                    widthWeight: 1,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "pipe",
                    header: $localize `:@@study.pps_end_time:PPS End Time`,
                    pathToValue: "00400251.Value[0]",
                    headerDescription: $localize `:@@study.performed_procedure_step_end_time:Performed Procedure Step End Time`,
                    pipe: new DynamicPipe(CustomDatePipe, [options.configuredDateTimeFormats]),
                    widthWeight: 1,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@accession_number:Accession Number`,
                    pathToValue: "00400270.Value[0].00080050.Value[0]",
                    headerDescription: $localize `:@@accession_number:Accession Number`,
                    widthWeight: 2,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@study.requested_procedure_id:Requested Procedure ID`,
                    pathToValue: "00400270.Value[0].00401001.Value[0]",
                    headerDescription: $localize `:@@study.requested_procedure_id:Requested Procedure ID`,
                    widthWeight: 2,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header:  $localize `:@@study.sps_desc:SPS Description`,
                    pathToValue: "00400270.Value[0].00400007.Value[0]",
                    headerDescription: $localize `:@@study.scheduled_procedure_step_description:Scheduled Procedure Step Description`,
                    widthWeight: 3,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@study.ss_aet:PS AET`,
                    pathToValue: "00400241.Value[0]",
                    headerDescription: $localize `:@@performed_station_ae_title:Performed Station AE Title`,
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
                            },{
                                icon: {
                                    tag: 'span',
                                    cssClass: `custom_icon calendar_step_black`,
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "uwl",
                                        action: "change_ups_state"
                                    }, e);
                                },
                                title: $localize `:@@uwl.change_ups_state:Change UPS state`,
                                permission: {
                                    id: 'action-studies-uwl',
                                    param: 'edit'
                                }
                            },
                            {
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-bell',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "uwl",
                                        action: "subscribe_ups"
                                    }, e);
                                },
                                title: $localize `:@@subscribe_ups_short:Subscribe UPS`,
                                permission: {
                                    id: 'action-studies-uwl',
                                    param: 'edit'
                                }
                            },
                            {
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-trash',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "uwl",
                                        action: "unsubscribe_ups"
                                    }, e);
                                },
                                title: $localize `:@@uwl.unsubscribe_ups:Unsubscribe UPS`,
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
                            title: $localize `:@@study.show_attributes:Show attributes`,
                            permission: {
                                id: 'action-studies-show-attributes',
                                param: 'visible'
                            }
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
                    type: "pipe",
                    header: $localize `:@@study.start_date_and_time:Start Date and Time`,
                    pathToValue: "00404005.Value[0]",
                    headerDescription: $localize `:@@scheduled_procedure_step_start_date_and_time:Scheduled Procedure Step Start Date and Time`,
                    pipe: new DynamicPipe(CustomDatePipe, [options.configuredDateTimeFormats]),
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
                    type: "pipe",
                    header: $localize `:@@study.e._completion_time:E. Completion Time`,
                    pathToValue: "00404011.Value[0]",
                    headerDescription: $localize `:@@expected_completion_date_and_time:Expected Completion Date and Time`,
                    pipe: new DynamicPipe(CustomDatePipe, [options.configuredDateTimeFormats]),
                    widthWeight: 2,
                    calculatedWidth: "20%",
                    cssClass:"border-left"
                }),
                new TableSchemaElement({
                    type: "pipe",
                    header: $localize `:@@study.step_m._date_and_time:Step M. Date and Time`,
                    pathToValue: "00404010.Value[0]",
                    headerDescription: $localize `:@@scheduled_procedure_step_modification_date_and_time:Scheduled Procedure Step Modification Date and Time`,
                    pipe: new DynamicPipe(CustomDatePipe, [options.configuredDateTimeFormats]),
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
                            title: $localize `:@@study.show_attributes:Show attributes`,
                            permission: {
                                id: 'action-studies-show-attributes',
                                param: 'visible'
                            }
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
                    type: "pipe",
                    header: $localize `:@@study_date:Study Date`,
                    pathToValue: "[00080020].Value[0]",
                    showBorderPath:"[00080020].showBorder",
                    headerDescription: $localize `:@@study_date:Study Date`,
                    pipe: new DynamicPipe(CustomDatePipe, [options.configuredDateTimeFormats]),
                    widthWeight: 0.6,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "pipe",
                    header: $localize `:@@study.study_time:Study Time`,
                    pathToValue: "[00080030].Value[0]",
                    showBorderPath:"[00080030].showBorder",
                    headerDescription: $localize `:@@study.study_time:Study Time`,
                    pipe: new DynamicPipe(CustomDatePipe, [options.configuredDateTimeFormats]),
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
        if(j4care.is(options, "studyTagConfig")){
            Object.keys(schema).forEach((modeKey:DicomLevel)=>{
                console.log("schema™modeKey",schema[modeKey]);
                schema[modeKey].forEach((element,i)=>{
                    console.log("element",element);
                    if(element.type === "actions-menu" && _.hasIn(element,"menu.actions[0]")){ //To prevent showing the single action buttons in the tag mode you have to extend this function with  || element.type === "actions" so you can filter the actions too
                        element.menu.actions = element.menu.actions.filter(a=>_.hasIn(options,"studyTagConfig.takeActionsOver") && options.studyTagConfig.takeActionsOver.indexOf(a.id) > -1);
                        if(element.menu.actions.length === 0 && options.studyTagConfig.hideEmptyActionMenu){
                            schema[modeKey].splice(i, 1); //If there is no action button in menu left, delete the menu point!
                        }
                    }
                })
            });
            if(_.hasIn(options,"studyTagConfig.addActions.addPath") && _.hasIn(options,"studyTagConfig.addActions.addFunction")){
                options.studyTagConfig.addActions.addFunction(actions,$this,_.get(schema, options.studyTagConfig.addActions.addPath), schema);
            }
            if(_.hasIn(options,"studyTagConfig.hookSchema")){
                options.studyTagConfig.hookSchema(schema,$this);
            }
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

    modifySeries(series, deviceWebservice: StudyWebService, header: HttpHeaders, studyInstanceUID?:string, seriesInstanceUID?:string) {
        const url = `${this.getModifyStudyUrl(deviceWebservice)}/${studyInstanceUID}/series/${seriesInstanceUID}`;
        if (url) {
            return this.$http.put(url, series, header);
        }
        return throwError({error: $localize `:@@study.error_on_getting_the_webapp_url:Error on getting the WebApp URL`});
    }
    getSeriesUrl(selectedWebService:DcmWebApp, series:SeriesDicom, studyInstanceUID?:string, seriesInstanceUID?:string){
        studyInstanceUID = studyInstanceUID || this.getStudyInstanceUID(series.attrs);
        seriesInstanceUID = seriesInstanceUID || this.getSeriesInstanceUID(series.attrs);
        return `${this.getDicomURL("study",selectedWebService)}/${studyInstanceUID}/series/${seriesInstanceUID}`;
    }
    modifyStudy(study, deviceWebservice: StudyWebService, header: HttpHeaders, studyInstanceUID?:string) {
        const url = `${this.getModifyStudyUrl(deviceWebservice)}/${studyInstanceUID}`;
        if (url) {
            return this.$http.put(url, study, header);
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

    importMatchingSPS(dcmWebApp:DcmWebApp, destination:string, filters:any){
        return this.$http.post(`${this.getDicomURL("mwl",dcmWebApp)}/import/${destination}${j4care.param(filters)}`,{});
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
                return throwError({error: $localize `:@@error_on_getting_needed_webapp_ups:Error on getting the needed WebApp (with one of the web service classes "DCM4CHEE_ARC_AET" or "UPS_RS")`});
            }))
    }

    changeUPSState(workitemUID, deviceWebservice: StudyWebService, requester, changeUPSStateAttrs) {
        let xmlHttpRequest = new XMLHttpRequest();
        let url;
        return this.getModifyUPSUrl(deviceWebservice)
            .pipe(
                switchMap((returnedUrl:string)=>{
                    url = returnedUrl
                    return this.getTokenService(deviceWebservice);
                }),
                map(token=>{
                    console.log("token1",token);
                    if(_.hasIn(token,"token")){
                        return _.get(token,"token");
                    }
                    return
                }),
                switchMap((token)=>{
                    console.log("token",token);
                    if (url) {
                        xmlHttpRequest.open('PUT', `${url}/${workitemUID}/state/${requester}`, false);
                        if(token){
                            xmlHttpRequest.setRequestHeader('Authorization', `Bearer ${token}`);
                        }
                        xmlHttpRequest.setRequestHeader("Content-Type","application/dicom+json");
                        xmlHttpRequest.setRequestHeader("Accept","application/dicom+json");
                        xmlHttpRequest.send(changeUPSStateAttrs);
                        let status = xmlHttpRequest.status;
                        if (status === 200) {
                            this.appService.showMsg($localize `:@@ups_workitem_state_changed_successfully:UPS Workitem state was changed successfully!`);
                        } else {
                            this.appService.showError($localize `:@@ups_workitem_change_state_failed:UPS workitem change state failed with status `
                                + status
                                + `\n- ` + xmlHttpRequest.getResponseHeader('Warning'));
                        }
                    }
                    return throwError({error: $localize `:@@error_on_getting_needed_webapp_ups:Error on getting the needed WebApp (with one of the web service classes "DCM4CHEE_ARC_AET" or "UPS_RS")`});
                }))
    }

    unsubscribeOrSuspendUPS(suspend:boolean, workitemUID, deviceWebservice: StudyWebService, subscriber) {
        return this.getModifyUPSUrl(deviceWebservice)
            .pipe(switchMap((url:string)=>{
                if (url) {
                    if (subscriber) {
                        return suspend === true
                            ? this.$http.post(`${url}/${workitemUID}/subscribers/${subscriber}/suspend`,{})
                            : this.$http.delete(`${url}/${workitemUID}/subscribers/${subscriber}`,{});
                    }
                }
                return throwError({error: $localize `:@@error_on_getting_needed_webapp_ups:Error on getting the needed WebApp (with one of the web service classes "DCM4CHEE_ARC_AET" or "UPS_RS")`});
            }))
    }

    cancelUPS(workitemUID, deviceWebservice: StudyWebService, requester){
        return this.getModifyUPSUrl(deviceWebservice)
            .pipe(switchMap((url:string)=>{
                if (url) {
                    if (requester) {
                        return this.$http.post(`${url}/${workitemUID}/cancelrequest/${requester}`,{}).pipe(map(res=>{
                            console.log("****res",res);
                            return res;
                        }));

/*                        return this._keycloakService.getToken().pipe(switchMap(token=>{
                            const headers = new HttpHeaders()
                                .set('Authorization', `Bearer ${token.token}`)
                                .set('Content-Type',  'application/json');

                            return this.nativeHttp.post(`${url}/${workitemUID}/cancelrequest/${requester}`,{},{
                                    headers,
                                    observe:"response"
                            })
                        }),map(res=>{
                            try{
                                return res.headers.get("Warning");
                            }catch (e) {
                                return res;
                            }
                        }))*/
                    } else
                        this.appService.showWarning($localize `:@@requester_aet_warning_msg:Requester AET should be set`);
                }
                return throwError({error: $localize `:@@error_on_getting_needed_webapp_ups:Error on getting the needed WebApp (with one of the web service classes "DCM4CHEE_ARC_AET" or "UPS_RS")`});
            }))
    }

    subscribeUPS(workitemUID: string, params, deviceWebservice: StudyWebService, subscriber) {
        return this.getModifyUPSUrl(deviceWebservice)
            .pipe(switchMap((url:string)=>{
                if (url) {
                    if (subscriber) {
                        return this.$http.post(`${url}/${workitemUID}/subscribers/${subscriber}?${params}`,{});
                    }
                }
                return throwError({error: $localize `:@@error_on_getting_needed_webapp_ups:Error on getting the needed WebApp (with one of the web service classes "DCM4CHEE_ARC_AET" or "UPS_RS")`});
            }))
    }

    modifyUPS(workitemUID: string, object, deviceWebservice: StudyWebService, msg:string, mode:UPSModifyMode, template?:boolean) {
        let xmlHttpRequest = new XMLHttpRequest();
        let url;
        return this.getModifyUPSUrl(deviceWebservice)
            .pipe(
                switchMap((returnedUrl:string)=>{
                    url = returnedUrl
                    return this.getTokenService(deviceWebservice);
                }),
                map(token=>{
                    console.log("token1",token);
                    if(_.hasIn(token,"token")){
                        return _.get(token,"token");
                    }
                    return
                }),
                switchMap((token)=>{
                    console.log("token",token);
                    if (url) {

                        if (workitemUID) {
                            //Change ups;
                            xmlHttpRequest.open('POST', `${url}/${workitemUID}`, false);
                            if(token){
                                xmlHttpRequest.setRequestHeader('Authorization', `Bearer ${token}`);
                            }
                            xmlHttpRequest.setRequestHeader("Content-Type","application/dicom+json");
                            xmlHttpRequest.setRequestHeader("Accept","application/dicom+json");
                            xmlHttpRequest.send(JSON.stringify(j4care.removeKeyFromObject(object, ["required","enum", "multi"])));
                            let status = xmlHttpRequest.status;
                            if (status === 200) {
                                this.appService.showMsg(msg);
                            } else {
                                this.appService.showError($localize `:@@ups_workitem_update_failed:UPS workitem update failed with status `
                                    + status
                                    + `\n- ` + xmlHttpRequest.getResponseHeader('Warning'));
                            }
                        } else {
                            //Create or clone new workitem
                            xmlHttpRequest.open('POST', url + j4care.objToUrlParams({template:template},true), false);
                            if(token){
                                xmlHttpRequest.setRequestHeader('Authorization', `Bearer ${token}`);
                            }
                            xmlHttpRequest.setRequestHeader("Content-Type","application/dicom+json");
                            xmlHttpRequest.setRequestHeader("Accept","application/dicom+json");
                            xmlHttpRequest.send(JSON.stringify(j4care.removeKeyFromObject(object, ["required","enum", "multi"])));
                            let status = xmlHttpRequest.status;
                            if (status === 201) {
                                this.appService.showMsg(msg + xmlHttpRequest.getResponseHeader('Location'));
                            } else {
                                let errMsg = template
                                    ? mode === "create"
                                        ? $localize `:@@ups_template_creation_failed:UPS template creation failed with status `
                                        : $localize `:@@ups_template_cloning_failed:UPS template cloning failed with status `
                                    : mode === "create"
                                        ? $localize `:@@ups_workitem_creation_failed:UPS workitem creation failed with status `
                                        : $localize `:@@ups_workitem_cloning_failed:UPS workitem cloning failed with status `;
                                this.appService.showError(errMsg + status
                                    + `<br>\n` + `- ` + xmlHttpRequest.getResponseHeader('Warning'));
                            }
                        }
                    }
                    return throwError({error: $localize `:@@error_on_getting_needed_webapp_ups:Error on getting the needed WebApp (with one of the web service classes "DCM4CHEE_ARC_AET" or "UPS_RS")`});
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

    webAppGroupHasClass(studyWebService:StudyWebService, webServiceClass:WebServiceClass){
        try{
            return (_.hasIn(studyWebService,"selectedWebService.dcmWebServiceClass") && studyWebService.selectedWebService.dcmWebServiceClass.indexOf(webServiceClass) > -1) ||
                studyWebService.webServices.filter((webService:DcmWebApp)=>webService.dicomDeviceName === studyWebService.selectedWebService.dicomDeviceName && webService.dcmWebServiceClass.indexOf(webServiceClass) > -1).length > 0 ||
                studyWebService.allWebServices.filter((webService:DcmWebApp)=>webService.dicomDeviceName === studyWebService.selectedWebService.dicomDeviceName && webService.dcmWebServiceClass.indexOf(webServiceClass) > -1).length > 0
        }catch(e){
            return false;
        }

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
    getIodObjectsFromNames(iodFileNames:string[]){
        const iodServices = [];
        iodFileNames.forEach(iodFileName=>{
            iodServices.push(this.getIod(iodFileName));
        });
        return forkJoin(iodServices);
    }

    iodToSelectedDropdown(iodObject):SelectDropdown<any>[]{
        return this.getAllAttributeKeyPathsFromIODObject(iodObject).map(iodKey=>{
            let label = this.getLabelFromIODTag(iodKey);
            return new SelectDropdown(iodKey,label,`${label} ( ${iodKey} )`,undefined,undefined,{
                key:iodKey,
                label:label
            });
        });
    }

    getLabelFromIODTag(dicomTagPath){
        return dicomTagPath.replace(/(\w){8}/g,(g)=>{ // get DICOM label [chain] to key [chain]
            return DCM4CHE.elementName.forTag(g);
        });
    }
    getAllAttributeKeyPathsFromIODObject(iodObject){
        return _.uniqWith(
            Object.keys(j4care.flatten(iodObject)).map(key=>{
                return key.replace(/\.items|\.enum|\.multi|\.required|\.vr|\[\w\]/g,""); //remove everything that is not a DICOM attribute
            }),
            _.isEqual
        );
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
    getIodFromContext(fileTypeOrExt:string, context:("patient"|"study"|"series"|"mwl")){
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
        if(fileTypeOrExt === "mtl"
            || fileTypeOrExt === "obj"
            || fileTypeOrExt === "stl"
            || fileTypeOrExt === "genozip") {
            //"patient"
            iodFileNames = [
                "study",
                "series",
                "equipment",
                "scEquipment",
                "sop",
                "encapsulatedDocument"
            ]
        } else {
            if(fileTypeOrExt.indexOf("video") > -1){
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
            if(fileTypeOrExt.indexOf("image") > -1) {
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
            if(fileTypeOrExt.indexOf("pdf") > -1
                || fileTypeOrExt.indexOf("xml") > -1
                || fileTypeOrExt.indexOf("model/mtl") > -1
                || fileTypeOrExt.indexOf("model/obj") > -1
                || fileTypeOrExt.indexOf("application/x-tgif") > -1
                || fileTypeOrExt.indexOf("application/vnd.genozip") > -1
                || fileTypeOrExt.indexOf("application/sla") > -1
                || fileTypeOrExt.indexOf("model/x.stl-binary") > -1
                || fileTypeOrExt.indexOf("model/stl") > -1) {
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
        if(mode && (mode === "create" || mode === "clone" || mode === "subscribe")){
            let iodFileNames = [
                "patient",
                "upsCreate"
            ]
            return forkJoin(iodFileNames.map(m=>this.getIod(m))).pipe(map(res=>{
                let merged = {};
                res.forEach(o=>{
                    merged = Object.assign(merged,o)
                });
                return merged;
            }));
        }
        return this.getIod("upsUpdate");
    };
    getPatientIod() {
        return this.getIod("patient");
    };

    getStudyIod() {
        return this.getIod("study");
    };

    getSeriesIod() {
        return this.getIod("series");
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
                        ],
                        [
                            {
                                tag: "label",
                                text: $localize `:@@exporter:Exporter`
                            },
                            {
                                tag: "select",
                                filterKey: "exporter",
                                description: $localize `:@@exporter:Exporter`,
                                options: exporters.map(exporter => new SelectDropdown(exporter.id, exporter.description || exporter.id))
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
            if (_.hasIn(study.attrs, "77771023.Value.0") && study.attrs["77771023"].Value[0] != "") {
                let expiredDateString = study.attrs["77771023"].Value[0];
                expiredDate = new Date(expiredDateString.substring(0, 4) + '.' + expiredDateString.substring(4, 6) + '.' + expiredDateString.substring(6, 8));
            } else {
                expiredDate = new Date();
            }
            schemaModel = {
                expiredDate: j4care.formatDate(expiredDate, 'yyyyMMdd')
            };
            title += $localize `:@@studies.set_exporter_if_you_want_to_export_on_expiration_date_too:<p>Set exporter if you want to export on expiration date too.`;
            schema = [
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
                    ],
                    [
                        {
                            tag: "label",
                            text: $localize `:@@exporter:Exporter`
                        },
                        {
                            tag: "select",
                            filterKey: "exporter",
                            description: $localize `:@@exporter:Exporter`,
                            options: exporters.map(exporter => new SelectDropdown(exporter.id, exporter.description || exporter.id))
                        }
                    ],
                    [
                        {
                            tag:"label",
                            text:$localize `:@@freeze_expiration_date:Freeze Expiration Date`
                        },
                        {
                            tag:"select",
                            options:[
                                new SelectDropdown(null, "-"),
                                new SelectDropdown("true", $localize `:@@FREEZE:FREEZE`, $localize `:@@freeze_expiration_date_state:Freeze expiration date and set expiration state to FROZEN`),
                                new SelectDropdown("false", $localize `:@@UNFREEZE:UNFREEZE`, $localize `:@@unfreeze_expiration_date_state:Unfreeze expiration date and set expiration state to UPDATEABLE`)
                            ],
                            filterKey:"freezeExpirationDate",
                            description:$localize `:@@freeze_expiration_date_options:Freeze Expiration Date Options`,
                            placeholder:$localize `:@@freeze_expiration_date:Freeze Expiration Date`
                        }
                    ],
                    [
                        {
                            tag:"label",
                            text:$localize `:@@protect_study:Protect Study`
                        },
                        {
                            tag: "checkbox",
                            filterKey: "protectStudy",
                            description: $localize `:@@set_expiration_date_to_never_to_protect_the_study:Existing / new Expiration Date will be nullified and study expiration state will be set to FROZEN to protect the study from being expired or rejected`,
                        }
                    ]
                ]
            ];
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

    setExpiredDate(deviceWebservice: StudyWebService, studyUID, expiredDate, exporter, freezeExpirationDate, params?: any) {
        const url = this.getModifyStudyUrl(deviceWebservice);
        let localParams = "";
        if (exporter) {
            localParams = `?ExporterID=${exporter}`;
            if (freezeExpirationDate != null)
                localParams += `&FreezeExpirationDate=${freezeExpirationDate}`;
        } else {
            if (freezeExpirationDate != null)
                localParams += `?FreezeExpirationDate=${freezeExpirationDate}`;
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

    getExporters = () => this.$http.get(`${j4care.addLastSlash(this.appService.baseUrl)}export`);

    deleteStudy = (studyInstanceUID: string, dcmWebApp: DcmWebApp, param) =>
        this.$http.delete(`${this.getDicomURL("study", dcmWebApp)}/${studyInstanceUID}${j4care.param(param)}`);

    deleteRejectedInstances = (reject, params) => this.$http.delete(`${j4care.addLastSlash(this.appService.baseUrl)}reject/${reject}${j4care.param(params)}`);

    rejectRestoreMultipleObjects(multipleObjects: SelectionActionElement, selectedWebService: DcmWebApp, rejectionCode: string) {
        return forkJoin(multipleObjects.getAllAsArray().filter((element: SelectedDetailObject) => (element.dicomLevel != "patient")).map((element: SelectedDetailObject) => {
            return this.$http.post(
                `${this.getURL(element.object.attrs, selectedWebService, element.dicomLevel)}/reject/${rejectionCode}`,
                {},
                this.jsonHeader
            );
        }));
    }
    deleteMultipleObjects(multipleObjects: SelectionActionElement, selectedWebService: DcmWebApp, param?:any){
        return forkJoin(multipleObjects.getAllAsArray().map((element: SelectedDetailObject) => {
            if(element.dicomLevel === "patient" && _.hasIn(element,"object.attrs")){
                return this.deletePatient(selectedWebService,this.getPatientId(element.object.attrs));
            }
            if(element.dicomLevel === "study" && _.hasIn(element,"object.attrs")){
                return this.deleteStudy(this.getStudyInstanceUID(element.object.attrs),selectedWebService,param);
            }
        }));
    }

    rejectMatchingStudies(webApp: DcmWebApp, rejectionCode, params:any){
        return this.$http.post(
            `${this.getDicomURL("study", webApp)}/reject/${rejectionCode}${j4care.param(params)}`,
            {},
            this.jsonHeader
        )
    }

    rejectMatchingSeries(webApp:DcmWebApp, rejectionCode, params:any){
        return this.$http.post(
            `${this.getDicomURL("series", webApp)}/reject/${rejectionCode}${j4care.param(params)}`,
            {},
            this.jsonHeader
        )
    }

    applyRetentionPolicyMatchingSeries(webApp:DcmWebApp, params:any){
        return this.$http.post(
            `${this.getDicomURL("series", webApp)}/expire${j4care.param(params)}`,
            {},
            this.jsonHeader
        )
    }

    exportMatchingSeries(studyWebService:StudyWebService, params:any){
        let _webApp;
        const exporterID = params["exporterID"];
        delete params["exporterID"];

        return this.getWebAppFromWebServiceClassAndSelectedWebApp(
            studyWebService,
            "DCM4CHEE_ARC_AET",
            "MOVE_MATCHING"
        ).pipe(map(webApp=>{
            _webApp = webApp;
            return `${this.getDicomURL("series", webApp)}/export/${exporterID}${j4care.param(params)}`;
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
    
    exportMatchingSeriesDialogSchema(exporterIDs){
        return [
            [
                [
                    {
                        tag:"label",
                        text:$localize `:@@exporter:Exporter`
                    },
                    {
                        tag:"select",
                        type:"text",
                        options:exporterIDs.map(exporter=>{
                            return new SelectDropdown(exporter.id, exporter.id);
                        }),
                        filterKey:"exporterID",
                        description:$localize `:@@exporter:Exporter`,
                        placeholder:$localize `:@@exporter:Exporter`
                    }
                ],
                [
                    {
                        tag: "label",
                        text: $localize`:@@batch_ID:Batch ID`
                    },
                    {
                        tag: "input",
                        type: "text",
                        filterKey: "batchID",
                        description: $localize`:@@batch_ID:Batch ID`,
                        placeholder: $localize`:@@batch_ID:Batch ID`
                    }
                ],[
                    {
                        tag:"label",
                        text:$localize `:@@patient_verification_status:Patient Verification Status`
                    },
                    {
                        tag:"select",
                        type:"text",
                        options:[
                            new SelectDropdown("UNVERIFIED", $localize `:@@UNVERIFIED:UNVERIFIED`),
                            new SelectDropdown("VERIFIED", $localize `:@@VERIFIED:VERIFIED`),
                            new SelectDropdown("NOT_FOUND", $localize `:@@NOT_FOUND:NOT_FOUND`),
                            new SelectDropdown("VERIFICATION_FAILED", $localize `:@@VERIFICATION_FAILED:VERIFICATION_FAILED`)
                        ],
                        filterKey:"patientVerificationStatus",
                        description:$localize `:@@patient_verification_status:Patient Verification Status`,
                        placeholder:$localize `:@@status:Status`
                    }
                ],
                [
                    {
                        tag: "label",
                        text: $localize`:@@all_of_modalities_in_study:All of Modalities in Study`
                    },
                    {
                        tag:"checkbox",
                        type:"text",
                        filterKey:"allOfModalitiesInStudy",
                        description:$localize `:@@all_of_modalities_in_study:All of Modalities in Study`
                    },
                ],
                [
                    {
                        tag: "label",
                        text: $localize`:@@schedule_at:Schedule at`
                    },
                    {
                        tag:"single-date-time-picker",
                        type:"text",
                        filterKey:"scheduledTime",
                        description:$localize `:@@schedule_at_desc:Schedule at (if not set, schedule immediately)`
                    },
                ]
            ]
        ]
    }
    rejectMatchingSeriesDialogSchema(rjNoteCodes){
        return [
            [
                [
                    {
                        tag:"label",
                        text:$localize `:@@rejection_reason:Rejection Reason`
                    },
                    {
                        tag:"select",
                        type:"text",
                        options:rjNoteCodes,
                        filterKey:"rjNoteCode",
                        description:$localize `:@@rejection_reason:Rejection Reason`,
                        placeholder:$localize `:@@rejection_reason:Rejection Reason`
                    }
                ],[
                    {
                        tag:"label",
                        text:$localize `:@@patient_verification_status:Patient Verification Status`
                    },
                    {
                        tag:"select",
                        type:"text",
                        options:[
                            new SelectDropdown("UNVERIFIED", $localize `:@@UNVERIFIED:UNVERIFIED`),
                            new SelectDropdown("VERIFIED", $localize `:@@VERIFIED:VERIFIED`),
                            new SelectDropdown("NOT_FOUND", $localize `:@@NOT_FOUND:NOT_FOUND`),
                            new SelectDropdown("VERIFICATION_FAILED", $localize `:@@VERIFICATION_FAILED:VERIFICATION_FAILED`)
                        ],
                        filterKey:"patientVerificationStatus",
                        description:$localize `:@@patient_verification_status:Patient Verification Status`,
                        placeholder:$localize `:@@status:Status`
                    }
                ],
                [
                    {
                        tag: "label",
                        text: $localize`:@@batch_ID:Batch ID`
                    },
                    {
                        tag: "input",
                        type: "text",
                        filterKey: "batchID",
                        description: $localize`:@@batch_ID:Batch ID`,
                        placeholder: $localize`:@@batch_ID:Batch ID`
                    }
                ],
                [
                    {
                        tag: "label",
                        text: $localize`:@@schedule_at:Schedule at`
                    },
                    {
                        tag:"single-date-time-picker",
                        type:"text",
                        filterKey:"scheduledTime",
                        description:$localize `:@@schedule_at_desc:Schedule at (if not set, schedule immediately)`
                    },
                ],
                [
                    {
                        tag: "label",
                        text: $localize`:@@all_of_modalities_in_study:All of Modalities in Study`
                    },
                    {
                        tag:"checkbox",
                        type:"text",
                        filterKey:"allOfModalitiesInStudy",
                        description:$localize `:@@all_of_modalities_in_study:All of Modalities in Study`
                    },
                ]
            ]
        ]
    }

    restoreStudy(studyAttr, webService:StudyWebService, rejectionCode) {
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

    rejectStudy(studyAttr, webService:StudyWebService, rejectionCode, param?) {
        let _webApp;
        return this.getWebAppFromWebServiceClassAndSelectedWebApp(webService, "DCM4CHEE_ARC_AET", "REJECT").pipe(map(webApp=>{
            _webApp = webApp;
            return `${this.studyURL(studyAttr, webApp)}/reject/${rejectionCode}${j4care.param(param)}`;
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


    restoreSeries(seriesAttr, webService:StudyWebService, rejectionCode) {
        let _webApp;
        return this.getWebAppFromWebServiceClassAndSelectedWebApp(webService, "DCM4CHEE_ARC_AET", "REJECT").pipe(map(webApp=>{
            _webApp = webApp;
            return `${this.seriesURL(seriesAttr, webApp)}/reject/${rejectionCode}`;
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

    rejectSeries(seriesAttr, webService:StudyWebService, rejectionCode, param) {
        let _webApp;
        return this.getWebAppFromWebServiceClassAndSelectedWebApp(webService, "DCM4CHEE_ARC_AET", "REJECT").pipe(map(webApp=>{
            _webApp = webApp;
            return `${this.seriesURL(seriesAttr, webApp)}/reject/${rejectionCode}${j4care.param(param)}`;
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

    restoreInstance(instanceAttr, webService:StudyWebService, rejectionCode) {
        let _webApp;
        return this.getWebAppFromWebServiceClassAndSelectedWebApp(webService, "DCM4CHEE_ARC_AET", "REJECT").pipe(map(webApp=>{
            _webApp = webApp;
            return `${this.instanceURL(instanceAttr, webApp)}/reject/${rejectionCode}`;
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

    rejectInstance(instanceAttr, webService:StudyWebService, rejectionCode, param) {
        let _webApp;
        return this.getWebAppFromWebServiceClassAndSelectedWebApp(webService, "DCM4CHEE_ARC_AET", "REJECT").pipe(map(webApp=>{
            _webApp = webApp;
            return `${this.instanceURL(instanceAttr, webApp)}/reject/${rejectionCode}${j4care.param(param)}`;
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
        if (_.hasIn(res, "error") && _.hasIn(res.error, "errorMessage")) {
            endMsg = endMsg + `${res.error.errorMessage}<br>`;
        } else {
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

    retrieve = (webApp:DcmWebApp, param, object, level,multipleObjects?:SelectionActionElement) => {
        let tempParam = _.clone(param);
        delete tempParam["destination"];
        if(multipleObjects){
            return forkJoin(multipleObjects.getAllAsArray().filter((element: SelectedDetailObject) => (element.dicomLevel != "patient")).map((element: SelectedDetailObject) => {
                return this.$http.post(
                    `${this.getURL(element.object.attrs,webApp,element.dicomLevel) }/export/dicom:${param.destination}${j4care.param(tempParam)}`,
                    {},
                    this.jsonHeader
                );
            }));
        }else{
            return this.$http.post(`${this.getURL(object.attrs,webApp,level) }/export/dicom:${param.destination}${j4care.param(tempParam)}`,{});
        }
    };

    getQueueNames = () => this.$http.get(`${j4care.addLastSlash(this.appService.baseUrl)}queue`);

    getRejectNotes = (params?: any) => this.$http.get(`${j4care.addLastSlash(this.appService.baseUrl)}reject/${j4care.param(params)}`);

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
            const regex2 = /([\w-]+)/gm;
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


    markAsRequestedOrUnscheduled(dcmWebApp:DcmWebApp, studyInstanceUID, object, dicomLevel:string, dicomObject:StudyDicom|SeriesDicom){
        dicomLevel = dicomLevel || "study";
        if(dicomLevel === "study"){
            return this.$http.put(`${this.getDicomURL(dicomLevel, dcmWebApp)}/${studyInstanceUID}/request`, object, new HttpHeaders({'Content-Type': 'application/dicom+json'}),undefined,dcmWebApp);
        }
        if(dicomLevel === "series"){
            return this.$http.put(`${this.getSeriesUrl(dcmWebApp,<SeriesDicom> dicomObject)}/request`, object, new HttpHeaders({'Content-Type': 'application/dicom+json'}),undefined,dcmWebApp);
        }
    }

    getRequestSchema(){
        let requestSchema = [];
        return this.getIod("series").pipe(map(res=>{
            const requestIOD = _.get(res,"00400275.items");
            Object.keys(requestIOD).forEach(dicomKey=>{
                if(requestIOD[dicomKey].vr === "SQ" && _.hasIn(requestIOD[dicomKey],"items")){
                    requestSchema.push([
                        {
                            tag:"label_large",
                            text:DCM4CHE.elementName.forTag(dicomKey)
                        }
                    ]);
                    const subItems = _.get(requestIOD[dicomKey],"items");
                    Object.keys(subItems).forEach(itemKey=>{
                        requestSchema.push([
                            {
                                tag:"label",
                                text:DCM4CHE.elementName.forTag(itemKey),
                                prefix:"> "
                            },
                            {
                                tag:"input",
                                type:"text",
                                filterKey: `${dicomKey}.${itemKey}`,
                                description:DCM4CHE.elementName.forTag(itemKey),
                                placeholder:DCM4CHE.elementName.forTag(itemKey)
                            }
                        ])
                    })
                }else{
                    requestSchema.push([
                        {
                            tag:"label",
                            text:DCM4CHE.elementName.forTag(dicomKey)
                        },
                        {
                            tag:"input",
                            type:"text",
                            filterKey: dicomKey,
                            description:DCM4CHE.elementName.forTag(dicomKey),
                            placeholder:DCM4CHE.elementName.forTag(dicomKey)
                        }
                    ])
                }
            });
            return [requestSchema, requestIOD];
        }));
    }
    /*
     {
        00080050: "test1",
        00080051.00400031: "test2"
     }

     to
     {
        00080050:{
            vr:"SH",
            Value:["test1"]
        },
        00080051:{
            vr:"SQ",
            Value:[
                {
                    00400031:{
                        vr:"SH",
                        Value:["test1"]
                    }
                }
            ]
        }
     }
    *
    * */
    convertFilterModelToDICOMObject(object, iod, exception:string[]=[]){
        let newObject = {};
        Object.keys(object).forEach(modelKey=>{
            if(exception.indexOf(modelKey) === -1){
                if(modelKey.indexOf(".") > -1){
                    const [firstKey,secondKey] = modelKey.split(".");
                    console.log("first",firstKey)
                    console.log("second",secondKey)
                    const iodObject = _.get(iod,`${firstKey}.items.${secondKey}`);
                    delete iodObject["required"];
                    if(iodObject){
                        if(newObject[firstKey]){
                            _.set(newObject,`${firstKey}.Value[0].${secondKey}`,{
                                ...iodObject,
                                Value:[
                                    object[modelKey]
                                ]
                            })
                        }else{
                            newObject[firstKey] = {
                                "vr":"SQ",
                                "Value":[
                                    {
                                        [secondKey]:{
                                            ...iodObject,
                                            Value:[
                                                object[modelKey]
                                            ]
                                        }
                                    }
                                ]
                            }
                        }
                    }
                }else{
                    const iodObject = _.get(iod,modelKey);
                    delete iodObject["required"];
                    if(iodObject){
                        newObject[modelKey] = {
                            ...iodObject,
                            Value:[
                                object[modelKey]
                            ]
                        }
                    }
                }
            }
        });
        return newObject;
    }
}
