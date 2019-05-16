import { Injectable } from '@angular/core';
import {AccessLocation, DicomMode, DicomResponseType, FilterSchema, SelectDropdown} from "../../interfaces";
import {Globalvar} from "../../constants/globalvar";
import {Aet} from "../../models/aet";
import {AeListService} from "../../configuration/ae-list/ae-list.service";
import {j4care} from "../../helpers/j4care.service";
import {Headers} from "@angular/http";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {Observable} from "rxjs/Observable";
import * as _ from 'lodash'
import {GSPSQueryParams} from "../../models/gsps-query-params";
import {StorageSystemsService} from "../../monitoring/storage-systems/storage-systems.service";
import {DevicesService} from "../../configuration/devices/devices.service";
import {StudyDeviceWebserviceModel} from "./study-device-webservice.model";
import {DcmWebApp} from "../../models/dcm-web-app";
import {HttpHeaders} from "@angular/common/http";

@Injectable()
export class StudyService {

    constructor(
      private aeListService:AeListService,
      private $http:J4careHttpService,
      private storageSystems:StorageSystemsService,
      private devicesService:DevicesService
    ) { }

    getEntrySchema(devices, aetWebService):{schema:FilterSchema, lineLength:number}{
        return {
            schema: j4care.prepareFlatFilterObject(Globalvar.STUDY_FILTER_ENTRY_SCHEMA(devices,aetWebService),1),
            lineLength: 1
        }
    }
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
                schema = Globalvar.STUDY_FILTER_SCHEMA(aets,false);
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
                    description: "QUERIE ONLY THE COUNT"
                },{
                    tag: "button",
                    id: "size",
                    text: quantityText.size,
                    description: "QUERIE ONLY THE SIZE"
                });
        }
        return {
            lineLength:lineLength,
            schema:j4care.prepareFlatFilterObject(schema,lineLength)
        }
    }


    getStudies(filterModel, deviceWebservice:StudyDeviceWebserviceModel, responseType?:DicomResponseType):Observable<any>{
        let header;
        if(!responseType || responseType === "object"){
            header = {
                headers:  new HttpHeaders({'Accept': 'application/dicom+json'})
            };
        }
        let params = j4care.objToUrlParams(filterModel);
        params = params ? `?${params}`:params;

        return this.$http.get(
            `${this.getDicomURL("study", deviceWebservice, responseType)}${params || ''}`,
                header,
                false,
                deviceWebservice.selectedWebApp
            ).map(res => j4care.redirectOnAuthResponse(res));
    }

    getSeries(studyInstanceUID:string, filterModel:any, deviceWebservice:StudyDeviceWebserviceModel, responseType?:DicomResponseType):Observable<any>{
        let header;
        if(!responseType || responseType === "object"){
            header = {
                headers:  new HttpHeaders({'Accept': 'application/dicom+json'})
            };
        }
        let params = j4care.objToUrlParams(filterModel);
        params = params ? `?${params}`:params;

        return this.$http.get(
            `${this.getDicomURL("study", deviceWebservice, responseType)}/${studyInstanceUID}/series${params || ''}`,
                header,
            false,
            deviceWebservice.selectedWebApp
            ).map(res => j4care.redirectOnAuthResponse(res));
    }

    getInstances(studyInstanceUID:string, seriesInstanceUID:string, filterModel:any, deviceWebservice:StudyDeviceWebserviceModel, responseType?:DicomResponseType):Observable<any>{
        let header;
        if(!responseType || responseType === "object"){
            header = {
                headers:  new HttpHeaders({'Accept': 'application/dicom+json'})
            };
        }
        let params = j4care.objToUrlParams(filterModel);
        params = params ? `?${params}`:params;

        return this.$http.get(
            `${this.getDicomURL("study", deviceWebservice, responseType)}/${studyInstanceUID}/series/${seriesInstanceUID}/instances${params || ''}`,
                header,
            false,
            deviceWebservice.selectedWebApp
            ).map(res => j4care.redirectOnAuthResponse(res));
    }
    getDicomURL(mode:DicomMode, deviceWebservice:StudyDeviceWebserviceModel, responseType?:DicomResponseType):string{
        console.log("object",deviceWebservice);
        try{
            let url = j4care.getUrlFromDcmWebApplication(deviceWebservice.selectedWebApp);
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

    test(dcmWebApp:DcmWebApp){
        this.$http.get("",undefined,false,dcmWebApp).subscribe(res=>{
            console.log("res",res);
        })
    }
}
