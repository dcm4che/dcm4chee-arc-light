import { Injectable } from '@angular/core';
import {
    AccessLocation,
    DicomLevel,
    DicomMode,
    DicomResponseType, DiffAttributeSet,
    FilterSchema,
    SelectDropdown, SelectedDetailObject,
    UniqueSelectIdObject
} from "../../interfaces";
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
    StudySchemaOptions, TableAction
} from "../../helpers/dicom-studies-table/dicom-studies-table.interfaces";
import {ContentDescriptionPipe} from "../../pipes/content-description.pipe";
import {TableSchemaElement} from "../../models/dicom-table-schema-element";
import {KeycloakService} from "../../helpers/keycloak-service/keycloak.service";
import {WebAppsListService} from "../../configuration/web-apps-list/web-apps-list.service";
import {RetrieveMonitoringService} from "../../monitoring/external-retrieve/retrieve-monitoring.service";
import {StudyWebService} from "./study-web-service.model";
import {PermissionService} from "../../helpers/permissions/permission.service";
import {SelectionsDicomObjects} from "./selections-dicom-objects.model";
import {SelectionActionElement} from "./selection-action-element.models";
declare var DCM4CHE: any;
import 'rxjs/add/observable/throw';
import {forkJoin} from 'rxjs/observable/forkJoin';
import {catchError} from "rxjs/operators";
import {of} from "rxjs/observable/of";
import {FormatTMPipe} from "../../pipes/format-tm.pipe";
import {FormatDAPipe} from "../../pipes/format-da.pipe";
import {FormatAttributeValuePipe} from "../../pipes/format-attribute-value.pipe";

@Injectable()
export class StudyService {

    iod = {};
    integerVr = ['DS', 'FL', 'FD', 'IS', 'SL', 'SS', 'UL', 'US'];

    dicomHeader = new HttpHeaders({'Content-Type': 'application/dicom+json'});
    jsonHeader = new HttpHeaders({'Content-Type': 'application/json'});

    constructor(
        private aeListService: AeListService,
        private $http: J4careHttpService,
        private storageSystems: StorageSystemsService,
        private devicesService: DevicesService,
        private webAppListService: WebAppsListService,
        private permissionService: PermissionService
    ) {}

    getWebApps(filter?:any) {
        return this.webAppListService.getWebApps(filter);
    }

    getEntrySchema(devices, aetWebService): { schema: FilterSchema, lineLength: number } {
        return {
            schema: j4care.prepareFlatFilterObject(Globalvar.STUDY_FILTER_ENTRY_SCHEMA(devices, aetWebService), 1),
            lineLength: 1
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
            return patientId;
        } else {
            return undefined;
        }
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

    getFilterSchema(tab: DicomMode, aets: Aet[], quantityText: { count: string, size: string }, filterMode: ('main' | 'expand'), webApps?: DcmWebApp[], attributeSet?:SelectDropdown<DiffAttributeSet>[]) {
        let schema: FilterSchema;
        let lineLength: number = 3;
        switch (tab) {
            case "patient":
                schema = Globalvar.PATIENT_FILTER_SCHEMA(aets, filterMode === "expand").filter(filter => {
                    return filter.filterKey != "aet";
                });
                lineLength = filterMode === "expand" ? 1 : 2;
                break;
            case "mwl":
                schema = Globalvar.MWL_FILTER_SCHEMA( filterMode === "expand");
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
                schema.push({
                    tag: "html-select",
                    options: Globalvar.ORDERBY_NEW
                        .filter(order => order.mode === tab)
                        .map(order => {
                            return new SelectDropdown(order.value, order.label, order.title, order.title, order.label);
                        }),
                    filterKey: 'orderby',
                    text: "Order By",
                    title: "Order By",
                    placeholder: "Order By",
                    cssClass: 'study_order'

                });
            }
            schema.push({
                tag: "html-select",
                options: webApps
                    .map((webApps: DcmWebApp) => {
                        return new SelectDropdown(webApps, webApps.dcmWebAppName, webApps.dicomDescription);
                    }),
                filterKey: 'webApp',
                text: "Web App Service",
                title: "Web App Service",
                placeholder: "Web App Service",
                cssClass: 'study_order',
                showSearchField: true
            });
            schema.push({
                tag: "button",
                id: "submit",
                text: "SUBMIT",
                description: tab === "diff" ? "Show DIFFs" : "Query Studies"
            });
            if(tab != "diff"){
                schema.push({
                    tag: "dummy"
                })
            }else{
/*                schema.push({
                    tag: "button",
                    id: "trigger_diff",
                    text: "TRIGGER DIFF",
                    description: "Trigger DIFFs"
                });*/
            }
            if(tab != "diff"){
                schema.push({
                    tag: "button",
                    id: "count",
                    text: quantityText.count,
                    showRefreshIcon: true,
                    showDynamicLoader: false,
                    description: "QUERY ONLY THE COUNT"
                },{
                    tag: "button",
                    id: "size",
                    showRefreshIcon: true,
                    showDynamicLoader: false,
                    text: quantityText.size,
                    description: "QUERY ONLY THE SIZE"
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
            return this.getWebAppFromWebServiceClassAndSelectedWebApp(studyWebService, "DCM4CHEE_ARC_AET_DIFF", "DCM4CHEE_ARC_AET_DIFF").map(webApp=>{
                return `${j4care.getUrlFromDcmWebApplication(webApp)}`;
            }).switchMap(url=>{
                return this.$http.get(
                    `${url}${j4care.param(filterModel) || ''}`,
                    header
                )
            });
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

    deleteMWL(dcmWebApp: DcmWebApp, studyInstanceUID:string, scheduledProcedureStepID:string,  responseType?: DicomResponseType){
        return this.$http.delete(`${this.getDicomURL("patient", dcmWebApp, responseType)}/${studyInstanceUID}/${scheduledProcedureStepID}`);
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
        ).map(res => j4care.redirectOnAuthResponse(res));
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
        ).map(res => j4care.redirectOnAuthResponse(res));
    }

    testAet(url, dcmWebApp: DcmWebApp) {
        return this.$http.get(
            url,//`http://test-ng:8080/dcm4chee-arc/ui2/rs/aets`,
            this.jsonHeader,
            false,
            dcmWebApp
        ).map(res => j4care.redirectOnAuthResponse(res));
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
        ).map(res => j4care.redirectOnAuthResponse(res));
    }

    getDicomURL(mode: DicomMode, dcmWebApp: DcmWebApp, responseType?: DicomResponseType): string {
        console.log("object", dcmWebApp);
        try {
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
            return url;
        } catch (e) {
            j4care.log("Error on getting dicomURL in study.service.ts", e);
        }
    }

    wadoURL(webService: StudyWebService, ...args: any[]): Observable<string> {
        let arg = arguments;
        return this.getWebAppFromWebServiceClassAndSelectedWebApp(webService, "WADO_URI", "WADO_URI").map(webApp=>{
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
        });
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
        .map(res => j4care.redirectOnAuthResponse(res))
        .map(res => {
            if ((!entity || entity === "Patient") && res.dcmTag) {
                let privateAttr = [parseInt('77770010', 16), parseInt('77771010', 16), parseInt('77771011', 16)];
                res.dcmTag.push(...privateAttr);
            }
            return res;
        });
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
        return this.$http.get(`${url || '..'}/pdq/${PDQServiceID}/patients/${patientID}`).map(res => j4care.redirectOnAuthResponse(res));
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
            idObject.id += `_${attrs['0020000D'].Value[0]}`;
            idObject.idParts.push(attrs['0020000E'].Value[0]);
        }
        if (dicomLevel === "instance") {
            idObject.id += `_${attrs['00080018'].Value[0]}`;
            idObject.idParts.push(attrs['00080018'].Value[0]);
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

    verifyStorage = (attrs, studyWebService: StudyWebService, level: DicomLevel, params: any) => {
        let url = `${this.getURL(attrs, studyWebService.selectedWebService, level)}/stgver`;

        return this.$http.post(url, {}, this.dicomHeader);
    };

    scheduleStorageVerification = (param, studyWebService: StudyWebService) => this.$http.post(`${this.getDicomURL("study", studyWebService.selectedWebService)}/stgver${j4care.param(param)}`, {});

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
                                console.log("menu", menu);
                                console.log("menu.permission", menu.permission);
                                console.log("checkVisibility", this.permissionService.checkVisibility(menu.permission));
                                if (menu.permission) {
                                    return this.permissionService.checkVisibility(menu.permission);
                                }
                                return true
                            });
                            console.log("element", element);
                            console.log("result", result);
                            _.set(element, key, result);
                            console.log("result", result);
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
                                title: "Select",
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
                                title: "Unselect",
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
                                title: "Query National Patient Registry",
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
                                title: 'Edit this Patient',
                                permission: {
                                    id: 'action-studies-patient',
                                    param: 'edit'
                                }
                            }, {
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
                                },
                                title: 'Add new MWL',
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
                                title: 'Upload file',
                                permission: {
                                    id: 'action-studies-study',
                                    param: 'upload'
                                }
                            }, {
                                icon: {
                                    tag: 'span',
                                    cssClass: 'custom_icon csv_icon_black',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "study",
                                        action: "download_csv"
                                    }, e);
                                },
                                title: 'Download as CSV',
                                permission: {
                                    id: 'action-studies-download',
                                    param: 'visible'
                                }
                            }
                        ]
                    },
                    headerDescription: "Actions",
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
                            title: "Toggle Attributes"
                        }
                    ],
                    headerDescription: "Actions",
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
                                if(options.studyConfig.tab === "mwl") {
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
                                }
                            },
                            title: (options.studyConfig.tab === "mwl") ? "Hide MWLs":"Hide Studies",
                            showIf: (e) => {
                                if(options.studyConfig.tab === "mwl"){
                                    return e.showMwls;
                                }else{
                                    if(options.studyConfig.tab === "diff") {
                                        return e.showDiffs;
                                    }else{
                                        return e.showStudies;
                                    }
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
                                if(options.studyConfig.tab === "mwl") {
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
                                }
                                // actions.call(this, 'study_arrow',e);
                            },
                            title: (options.studyConfig.tab === "mwl") ? "Show MWLs":"Show Studies",
                            showIf: (e) => {
                                if(options.studyConfig.tab === "mwl") {
                                    return !e.showMwls
                                }else{
                                    if(options.studyConfig.tab === "diff") {
                                        return !e.showDiffs;
                                    }else{
                                        return !e.showStudies
                                    }
                                }
                            }
                        }
                    ],
                    headerDescription: (options.studyConfig.tab === "mwl") ? "Toggle MWLs":"Toggle studies",
                    pxWidth: 40
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Patient's Name",
                    pathToValue: "00100010.Value[0].Alphabetic",
                    headerDescription: "Patient's Name",
                    widthWeight: 1,
                    calculatedWidth: "20%",
                    cssClass:"border-left"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Patient ID",
                    pathToValue: "00100020.Value[0]",
                    headerDescription: "Patient ID",
                    widthWeight: 1,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Issuer of Patient",
                    pathToValue: "00100021.Value[0]",
                    headerDescription: "Issuer of Patient ID",
                    widthWeight: 1,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Birth Date",
                    pathToValue: "00100030.Value[0]",
                    headerDescription: "Patient's Birth Date",
                    widthWeight: 0.5,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Sex",
                    pathToValue: "00100040.Value[0]",
                    headerDescription: "Patient's Sex",
                    widthWeight: 0.2,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Patient Comments",
                    pathToValue: "00104000.Value[0]",
                    headerDescription: "Patient Comments",
                    widthWeight: 3,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "#S",
                    pathToValue: "00201200.Value[0]",
                    headerDescription: "Number of Patient Related Studies",
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
                                },
                                title: "Select",
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
                                },
                                title: "Unselect",
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
                                title: 'Edit this study',
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
                                title: 'Set/Change expired date',
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
                                title: options.trash.active ? 'Restore study' : 'Reject study',
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
                                title: 'Verify storage commitment',
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
                                        action: "download",
                                        mode: "uncompressed"
                                    }, e);
                                },
                                title: 'Retrieve Study uncompressed',
                                permission: {
                                    id: 'action-studies-download',
                                    param: 'visible'
                                }
                            }, {
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-download-alt',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "study",
                                        action: "download",
                                        mode: "compressed",
                                    }, e);
                                },
                                title: 'Retrieve Study as stored at the archive',
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
                                title: 'Upload file',
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
                                title: 'Export study',
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
                                title: 'Delete study permanently',
                                showIf: (e) => {
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
                                icon: {
                                    tag: 'span',
                                    cssClass: 'custom_icon csv_icon_black',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "series",
                                        action: "download_csv"
                                    }, e);
                                },
                                title: "Download as CSV",
                                permission: {
                                    id: 'action-studies-download',
                                    param: 'visible'
                                }
                            }
                        ]
                    },
                    headerDescription: "Actions",
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
                            title: "Toggle Attributes"
                        }
                    ],
                    headerDescription: "Actions",
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
                            title: "Hide Series",
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
                            title: "Show Series",
                            showIf: (e) => {
                                return !e.showSeries
                            },
                            permission: {
                                id: 'action-studies-serie',
                                param: 'visible'
                            }
                        }
                    ],
                    headerDescription: "Show studies",
                    widthWeight: 0.3,
                    calculatedWidth: "6%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Study ID",
                    pathToValue: "[00200010].Value[0]",
                    headerDescription: "Study ID",
                    widthWeight: 0.9,
                    calculatedWidth: "20%",
                    cssClass:"border-left"
                }), new TableSchemaElement({
                    type: "value",
                    header: "Study Instance UID",
                    pathToValue: "[0020000D].Value[0]",
                    headerDescription: "Study Instance UID",
                    widthWeight: 3,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Study Date",
                    pathToValue: "[00080020].Value[0]",
                    headerDescription: "Study Date",
                    widthWeight: 0.6,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Study Time",
                    pathToValue: "[00080030].Value[0]",
                    headerDescription: "Study Time",
                    widthWeight: 0.6,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "R. Physician's Name",
                    pathToValue: "[00080090].Value[0].Alphabetic",
                    headerDescription: "Referring Physician's Name",
                    widthWeight: 1,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Accession Number",
                    pathToValue: "[00080050].Value[0]",
                    headerDescription: "Accession Number",
                    widthWeight: 1,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Modalities",
                    pathToValue: "[00080061].Value[0]",
                    headerDescription: "Modalities in Study",
                    widthWeight: 0.5,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Study Description",
                    pathToValue: "[00081030].Value[0]",
                    headerDescription: "Study Description",
                    widthWeight: 2,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "#S",
                    pathToValue: "[00201206].Value[0]",
                    headerDescription: "Number of Study Related Series",
                    widthWeight: 0.2,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "#I",
                    pathToValue: "[00201208].Value[0]",
                    headerDescription: "Number of Study Related Instances",
                    widthWeight: 0.2,
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
                                title: options.trash.active ? 'Restore series' : 'Reject series',
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
                                title: 'Verify storage commitment',
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
                                        action: "download",
                                        mode: "uncompressed"
                                    }, e);
                                },
                                title: 'Retrieve Series uncompressed',
                                permission: {
                                    id: 'action-studies-download',
                                    param: 'visible'
                                }
                            }, {
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-download-alt',
                                    text: ''
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "series",
                                        action: "download",
                                        mode: "compressed",
                                    }, e);
                                },
                                title: 'Retrieve Series as stored at the archive',
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
                                title: 'Export series',
                                permission: {
                                    id: 'action-studies-serie',
                                    param: 'export'
                                }
                            },{
                                icon: {
                                    tag: 'span',
                                    cssClass: 'custom_icon csv_icon_black',
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
                                title: 'Download as CSV',
                                permission: {
                                    id: 'action-studies-download',
                                    param: 'visible'
                                }
                            }
                        ]
                    },
                    headerDescription: "Actions",
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
                            title: "Show attributes"
                        }
                    ],
                    headerDescription: "Actions",
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
                            title: "Hide Instances",
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
                            title: "Show Instaces",
                            showIf: (e) => {
                                return !e.showInstances
                            },
                            permission: {
                                id: 'action-studies-serie',
                                param: 'visible'
                            }
                        }
                    ],
                    headerDescription: "Show Instances",
                    widthWeight: 0.2,
                    calculatedWidth: "6%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Station Name",
                    pathToValue: "00081010.Value[0]",
                    headerDescription: "Station Name",
                    widthWeight: 0.9,
                    calculatedWidth: "20%",
                    cssClass:"border-left"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Series Number",
                    pathToValue: "00200011.Value[0]",
                    headerDescription: "Series Number",
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "PPS Start Date",
                    pathToValue: "00400244.Value[0]",
                    headerDescription: "Performed Procedure Step Start Date",
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "PPS Start Time",
                    pathToValue: "00400245.Value[0]",
                    headerDescription: "Performed Procedure Step Start Time",
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Body Part",
                    pathToValue: "00180015.Value[0]",
                    headerDescription: "Body Part Examined",
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Modality",
                    pathToValue: "00080060.Value[0]",
                    headerDescription: "Modality",
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Series Description",
                    pathToValue: "0008103E.Value[0]",
                    headerDescription: "Series Description",
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "#I",
                    pathToValue: "00201209.Value[0]",
                    headerDescription: "Number of Series Related Instances",
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
                                title: options.trash.active ? 'Restore instance' : 'Reject instance',
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
                                title: 'Verify storage commitment',
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
                                        action: "download",
                                        mode: "uncompressed"
                                    }, e);
                                },
                                title: 'Download Uncompressed DICOM Object',
                                permission: {
                                    id: 'action-studies-download',
                                    param: 'visible'
                                }
                            },
                            {
                                icon: {
                                    tag: 'span',
                                    cssClass: 'glyphicon glyphicon-download-alt',
                                    text: '',
                                },
                                click: (e) => {
                                    actions.call($this, {
                                        event: "click",
                                        level: "instance",
                                        action: "download",
                                        mode: "compressed",
                                    }, e);
                                },
                                title: 'Download DICOM Object',
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
                                title: 'Export instance',
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
                                title: 'View DICOM Object',
                                permission: {
                                    id: 'action-studies-download',
                                    param: 'visible'
                                }
                            }
                        ]
                    },
                    headerDescription: "Actions",
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
                            title: 'Show attributes'
                        }
                    ],
                    headerDescription: "Actions",
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
                                e.showFileAttributes = !e.showFileAttributes;
                            },
                            title: 'Show attributes from file'
                        }
                    ],
                    headerDescription: "Actions",
                    pxWidth: 40
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "SOP Class UID",
                    pathToValue: "00080016.Value[0]",
                    headerDescription: "SOP Class UID",
                    widthWeight: 0.9,
                    calculatedWidth: "20%",
                    cssClass:"border-left"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Instance Number",
                    pathToValue: "00200013.Value[0]",
                    headerDescription: "Instance Number",
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Content Date",
                    pathToValue: "00080023.Value[0]",
                    headerDescription: "Content Date",
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Content Time",
                    pathToValue: "00080033.Value[0]",
                    headerDescription: "Content Time",
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "pipe",
                    header: "Content Description",
                    headerDescription: "Content Description",
                    widthWeight: 1.5,
                    calculatedWidth: "20%",
                    pipe: new DynamicPipe(ContentDescriptionPipe, undefined)
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "#F",
                    pathToValue: "00280008.Value[0]",
                    headerDescription: "Number of Frames",
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
                                title: 'Edit MWL',
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
                                },
                                title: 'Delete MWL',
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
                                title: 'Upload file',
                                permission: {
                                    id: 'action-studies-mwl',
                                    param: 'upload'
                                }
                            }
                        ]
                    },
                    headerDescription: "Actions",
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
                            title: 'Show attributes'
                        }
                    ],
                    headerDescription: "Actions",
                    pxWidth: 40
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Requested Procedure ID",
                    pathToValue: "00401001.Value[0]",
                    headerDescription: "Requested Procedure ID",
                    widthWeight: 2,
                    calculatedWidth: "20%",
                    cssClass:"border-left"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Study Instance UID",
                    pathToValue: "0020000D.Value[0]",
                    headerDescription: "Study Instance UID",
                    widthWeight: 3.5,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "SPS Start Date",
                    pathToValue: "00400100.Value[0].00400002.Value[0]",
                    headerDescription: "Scheduled Procedure Step Start Date",
                    widthWeight: 1,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "SPS Start",
                    pathToValue: "00400100.Value[0].00400003.Value[0]",
                    headerDescription: "Scheduled Procedure Step Start Time",
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "SP Physician's Name",
                    pathToValue: "00400100.Value[0].00400006.Value[0]",
                    headerDescription: "Scheduled Performing Physician's Name",
                    widthWeight: 2,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Accession Number",
                    pathToValue: "00080050.Value[0]",
                    headerDescription: "Accession Number",
                    widthWeight: 2,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Modality",
                    pathToValue: "00400100.Value[0].00080060.Value[0]",
                    headerDescription: "Modality",
                    widthWeight: 1,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Description",
                    pathToValue: "00400100.Value[0].00400007.Value[0]",
                    headerDescription: "Scheduled Procedure Step Description",
                    widthWeight: 3,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "SS AET",
                    pathToValue: "00400100.Value[0].00400001.Value[0]",
                    headerDescription: "Scheduled Station AE Title",
                    widthWeight: 1.5,
                    calculatedWidth: "20%"
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
                            title: 'Show attributes'
                        }
                    ],
                    headerDescription: "Actions",
                    pxWidth: 40
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Study ID",
                    pathToValue: "[00200010].Value[0]",
                    showBorderPath:"[00200010].showBorder",
                    headerDescription: "Study ID",
                    widthWeight: 0.9,
                    calculatedWidth: "20%",
                    cssClass:"border-left"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Study Instance UID",
                    pathToValue: "[0020000D].Value[0]",
                    showBorderPath:"[0020000D].showBorder",
                    headerDescription: "Study Instance UID",
                    widthWeight: 3,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Study Date",
                    pathToValue: "[00080020].Value[0]",
                    showBorderPath:"[00080020].showBorder",
                    headerDescription: "Study Date",
                    widthWeight: 0.6,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Study Time",
                    pathToValue: "[00080030].Value[0]",
                    showBorderPath:"[00080030].showBorder",
                    headerDescription: "Study Time",
                    widthWeight: 0.6,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "SP Physician's Name",
                    pathToValue: "00400100.Value[0].00400006.Value[0]",
                    showBorderPath:"00400100.Value[0].00400006.showBorder",
                    headerDescription: "Scheduled Performing Physician's Name",
                    widthWeight: 2,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Accession Number",
                    pathToValue: "[00080050].Value[0]",
                    showBorderPath:"[00080050].showBorder",
                    headerDescription: "Accession Number",
                    widthWeight: 1,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Modalities",
                    pathToValue: "[00080061].Value[0]",
                    showBorderPath:"[00080061].showBorder",
                    headerDescription: "Modalities in Study",
                    widthWeight: 0.5,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Study Description",
                    pathToValue: "[00081030].Value[0]",
                    showBorderPath:"[00081030].showBorder",
                    headerDescription: "Study Description",
                    widthWeight: 2,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "#S",
                    pathToValue: "[00201206].Value[0]",
                    showBorderPath:"[00201206].showBorder",
                    headerDescription: "Number of Study Related Series",
                    widthWeight: 0.4,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "#I",
                    pathToValue: "[00201208].Value[0]",
                    showBorderPath:"[00201208].showBorder",
                    headerDescription: "Number of Study Related Instances",
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
                            title: "Select",
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
                            title: "Unselect",
                            showIf: (e, config) => {
                                return e.selected;
                            }
                        }
                    ],
                    headerDescription: "Select",
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

    modifyStudy(study, deviceWebservice: StudyWebService, header: HttpHeaders) {
        const url = this.getModifyStudyUrl(deviceWebservice);
        if (url) {
            return this.$http.post(url, study, header);
        }
        return Observable.throw({error: "Error on getting the WebApp URL"});
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
        return Observable.throw({error: "Error on getting the WebApp URL"});
    }

    getModifyMWLUrl(deviceWebservice: StudyWebService) {
        return this.getDicomURL("mwl", this.getModifyMWLWebApp(deviceWebservice));
    }

    getModifyMWLWebApp(deviceWebservice: StudyWebService): DcmWebApp {
        if (deviceWebservice.selectedWebService.dcmWebServiceClass.indexOf("DCM4CHEE_ARC_AET") > -1) {
            return deviceWebservice.selectedWebService;
        } else {
            return undefined;
        }
    }


    mergePatients = (selectedElements:SelectionActionElement,deviceWebservice: StudyWebService):Observable<any> => {
        if(selectedElements.preActionElements.getAttrs("patient").length > 1){
            return Observable.throw({error:"Multi patient merge is not supported!"});
        }else{
            this.getModifyPatientUrl(deviceWebservice)
            .switchMap((url:string)=>{
                console.log("url",url);
                return this.$http.put(
                    `${url}/${this.getPatientId(selectedElements.preActionElements.getAttrs("patient")[0])}?merge=true`,
                    selectedElements.postActionElements.getAttrs("patient"),
                    this.jsonHeader
                )
            })
        }
    };

    modifyPatient(patientId: string, patientObject, deviceWebservice: StudyWebService) {
        // const url = this.getModifyPatientUrl(deviceWebservice);
        return this.getModifyPatientUrl(deviceWebservice)
            .switchMap((url:string)=>{
                if (url) {
                    if (patientId) {
                        //Change patient;
                        return this.$http.put(`${url}/${patientId}`, patientObject);
                    } else {
                        //Create new patient
                        return this.$http.post(url, patientObject);
                    }
                }
                return Observable.throw({error: "Error on getting the WebApp URL"});
            })
    }

    getModifyPatientUrl(deviceWebService: StudyWebService) {
        return this.getDicomURLFromWebService(deviceWebService, "patient");
    }

    getModifyPatientWebApp(deviceWebService: StudyWebService): Observable<DcmWebApp> {
        return this.getWebAppFromWebServiceClassAndSelectedWebApp(deviceWebService, "DCM4CHEE_ARC_AET", "PAM_RS");
    }

    getDicomURLFromWebService(deviceWebService: StudyWebService, mode: ("patient" | "study")) {
        return this.getModifyPatientWebApp(deviceWebService).map((webApp:DcmWebApp)=>{
            return this.getDicomURL(mode, webApp);
        })
    }

    getWebAppFromWebServiceClassAndSelectedWebApp(deviceWebService: StudyWebService, neededWebServiceClass: string, alternativeWebServiceClass: string):Observable<DcmWebApp> {
        if (_.hasIn(deviceWebService, "selectedWebService.dcmWebServiceClass") && deviceWebService.selectedWebService.dcmWebServiceClass.indexOf(neededWebServiceClass) > -1) {
            return Observable.of(deviceWebService.selectedWebService);
        } else {
            try {
                return this.webAppListService.getWebApps({
                    dcmWebServiceClass: alternativeWebServiceClass,
                    dicomAETitle: deviceWebService.selectedWebService.dicomAETitle
                }).map((webApps:DcmWebApp[])=>webApps[0]);
/*                return deviceWebService.webServices.filter((webService: DcmWebApp) => { //TODO change this to observable to get the needed webservice from server
                    if (webService.dcmWebServiceClass.indexOf(alternativeWebServiceClass) > -1 && webService.dicomAETitle === deviceWebService.selectedWebService.dicomAETitle) {
                        return true;
                    }
                    return false;
                })[0];*/
            } catch (e) {
                j4care.log(`Error on getting the ${alternativeWebServiceClass} WebApp getModifyPatientUrl`, e);
                return undefined;
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
            return Observable.of(this.iod[fileIodName]);
        }else{
            return this.$http.get(`assets/iod/${fileIodName}.iod.json`).map(iod=>{
                this.iod[fileIodName] = iod;
                return iod;
            });
        }
    }

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
        let title = "Set expired date for the study.";
        let schema: any = [
            [
                [
                    {
                        tag: "label",
                        text: "Expired date"
                    },
                    {
                        tag: "p-calendar",
                        filterKey: "expiredDate",
                        description: "Expired Date"
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
                title = "Unfreeze/Unprotect Expiration Date of the Study";
                schema = [
                    [
                        [
                            {
                                tag: "label",
                                text: "Expired Date"
                            },
                            {
                                tag: "p-calendar",
                                filterKey: "expiredDate",
                                description: "Expired Date"
                            }
                        ]
                    ]
                ];
            } else {
                title = "Freeze/Protect Expiration Date of the Study";
                schemaModel = {
                    setExpirationDateToNever: true,
                    FreezeExpirationDate: true
                };
                schema = [
                    [
                        [
                            {
                                tag: "label",
                                text: "Expired date",
                                showIf: (model) => {
                                    return !model.setExpirationDateToNever
                                }
                            },
                            {
                                tag: "p-calendar",
                                filterKey: "expiredDate",
                                description: "Expired Date",
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
                            description: "Set Expiration Date to 'never' if you want also to protect the study",
                            text: "Set Expiration Date to 'never' if you want also to protect the study"
                        }
                    ], [
                        {
                            tag: "dummy"
                        },
                        {
                            tag: "checkbox",
                            filterKey: "FreezeExpirationDate",
                            description: "Freeze Expiration Date",
                            text: "Freeze Expiration Date"
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
                    text: "Exporter"
                },
                {
                    tag: "select",
                    filterKey: "exporter",
                    description: "Exporter",
                    options: exporters.map(exporter => new SelectDropdown(exporter.id, exporter.description || exporter.id))
                }])
        }
        return {
            content: title,
            form_schema: schema,
            result: {
                schema_model: schemaModel
            },
            saveButton: 'SAVE'
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

    deleteStudy = (studyInstanceUID: string, dcmWebApp: DcmWebApp) => this.$http.delete(`${this.getDicomURL("study", dcmWebApp)}/${studyInstanceUID}`);

    deleteRejectedInstances = (reject, params) => this.$http.delete(`../reject/${reject}${j4care.param(params)}`);

    rejectRestoreMultipleObjects(multipleObjects: SelectionActionElement, selectedWebService: DcmWebApp, rejectionCode: string) {
        return Observable.forkJoin(multipleObjects.getAllAsArray().filter((element: SelectedDetailObject) => (element.dicomLevel != "patient")).map((element: SelectedDetailObject) => {
            return this.$http.post(
                `${this.getURL(element.object.attrs, selectedWebService, element.dicomLevel)}/reject/${rejectionCode}`,
                {},
                this.jsonHeader
            );
        }));
    }

    rejectStudy(studyAttr, webApp: DcmWebApp, rejectionCode) {
        return this.$http.post(
            `${this.studyURL(studyAttr, webApp)}/reject/${rejectionCode}`,
            {},
            this.jsonHeader
        )
    }

    rejectSeries(studyAttr, webApp: DcmWebApp, rejectionCode) {
        return this.$http.post(
            `${this.seriesURL(studyAttr, webApp)}/reject/${rejectionCode}`,
            {},
            this.jsonHeader
        )
    }

    rejectInstance(studyAttr, webApp: DcmWebApp, rejectionCode) {
        return this.$http.post(
            `${this.instanceURL(studyAttr, webApp)}/reject/${rejectionCode}`,
            {},
            this.jsonHeader
        )
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
            return Observable.forkJoin(objects.getAllAsArray().filter((element: SelectedDetailObject) => (element.dicomLevel != "patient")).map((element: SelectedDetailObject) => {
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
            return Observable.throw(e);
        }
    };

}
