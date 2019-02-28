import { Injectable } from '@angular/core';
import {AccessLocation, DicomMode, DicomResponseType, FilterSchema} from "../../interfaces";
import {Globalvar} from "../../constants/globalvar";
import {Aet} from "../../models/aet";
import {AeListService} from "../../configuration/ae-list/ae-list.service";
import {j4care} from "../../helpers/j4care.service";
import {Headers} from "@angular/http";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {Observable} from "rxjs/Observable";
import * as _ from 'lodash'

@Injectable()
export class StudyService {

    constructor(
      private aeListService:AeListService,
      private $http:J4careHttpService
    ) { }

    getFilterSchema(tab:DicomMode, aets:Aet[], quantityText:{count:string,size:string}, hidden:boolean){
        let schema:FilterSchema;
        let lineLength:number = 3;
        switch(tab){
            case "patient":
                schema = Globalvar.PATIENT_FILTER_SCHEMA(aets,hidden);
                lineLength = hidden ? 1:2;
                break;
            case "mwl":
                break;
            case "diff":
                schema = [

                ];
                break;
            default:
                schema = Globalvar.STUDY_FILTER_SCHEMA(aets,hidden);
                lineLength = hidden ? 2:3;
        }
        if(!hidden){
            schema.push({
                    tag: "button",
                    id: "count",
                    text: quantityText.count,
                    description: "QUERIE ONLY THE COUNT"
                },{
                    tag: "button",
                    id: "size",
                    text: quantityText.size,
                    description: "QUERIE ONLY THE SIZE"
                },
                {
                    tag: "button",
                    id: "submit",
                    text: "SUBMIT",
                    description: "Query Studies"
                });
        }
        return {
            lineLength:lineLength,
            schema:j4care.prepareFlatFilterObject(schema,lineLength)
        }
    }


    getStudies(callingAet:Aet, filters:any, responseType?:DicomResponseType, accessLocation?:AccessLocation, externalAet?:Aet, baseUrl?:string):Observable<any>{
        let header;
        if(!responseType || responseType === "object"){
            header = {
                headers:  new Headers({'Accept': 'application/dicom+json'})
            };
        }
        let params = j4care.objToUrlParams(filters);
        params = params ? `?${params}`:params;

        return this.$http.get(
            `${this.getDicomURL("study", callingAet, responseType ,accessLocation, externalAet,undefined, baseUrl)}${params || ''}`,
                header
            ).map(res => j4care.redirectOnAuthResponse(res));
    }

    getDicomURL(mode:DicomMode, callingAet:Aet, responseType?:DicomResponseType, accessLocation?:AccessLocation,  externalAet?:Aet, secondExternalAet?:Aet, baseUrl?:string):string{

        let url = this.rsURL(callingAet, accessLocation,  externalAet, baseUrl);

        switch (mode) {
            case "patient":
                url += '/patients';
                break;
            case "mwl":
                url += '/mwlitems';
                break;
            case "diff":
                url = this.diffUrl(callingAet, externalAet, secondExternalAet, baseUrl);
                break;
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
}
