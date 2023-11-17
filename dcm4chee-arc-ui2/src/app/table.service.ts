import { Injectable } from '@angular/core';
import {TableSchemaElement} from "./models/dicom-table-schema-element";
import {DynamicPipe} from "./helpers/dicom-studies-table/dicom-studies-table.interfaces";
import {PersonNamePipe} from "./pipes/person-name.pipe";
import {AppService} from "./app.service";
import * as _ from 'lodash-es'
import {CustomDatePipe} from "./pipes/custom-date.pipe";
import {j4care} from "./helpers/j4care.service";

@Injectable({
    providedIn: 'root'
})
export class TableService {

    constructor(
        public appService:AppService
    ) { }

    dateTimeFormat;
    personNameFormat;

    /*
    * @param {(TableSchemaElementKey|MappedTableSchemaElement)[]} toReturnElements - array of strings containing the element key or array of mapped table schema elements in the form {key:elementKey,overwrite:TableSchemaElement} (TableSchemaElement object should contain only the values that you want to overwrite), (string and object form can be combined in the same array).
    * @return {TableSchemaElement[]} - Returns an array of selected TableSchemaElement-s based on the input
    * */
    getTableSchema(toReturnElements:(TableSchemaElementKey|MappedTableSchemaElement)[]):TableSchemaElement[]{
        this.dateTimeFormat = _.hasIn(this.appService.global,"dateTimeFormat") ? this.appService.global["dateTimeFormat"] : undefined;
        this.personNameFormat = _.hasIn(this.appService.global,"personNameFormat") ? this.appService.global["personNameFormat"] : undefined;
        const allElements = {
            dicomDeviceName : new TableSchemaElement({
                type:"value",
                title:$localize `:@@device_name:Device Name`,
                pathToValue:"dicomDeviceName",
                description: $localize `:@@device_name:Device Name`,
                widthWeight:1,
                calculatedWidth:"20%"
            }),
            queue : new TableSchemaElement({
                type:"value",
                title:$localize `:@@queue_name:Queue Name`,
                pathToValue:"queue",
                description: $localize `:@@queue_name:Queue Name`,
                widthWeight:1,
                calculatedWidth:"20%"
            }),
            createdTime: new TableSchemaElement({
                type:"value",
                title:$localize `:@@created_time:Created time`,
                pathToValue:"createdTime",
                description:$localize `:@@created_time:Created time`,
                widthWeight:1.4,
                calculatedWidth:"20%",
                pipe: new DynamicPipe(CustomDatePipe, [this.dateTimeFormat])
            }),
            createdDate: new TableSchemaElement({
                type:"value",
                title:$localize `:@@created_date:Created Date`,
                pathToValue:"createdDate",
                description:$localize `:@@created_date:Created Date`,
                widthWeight:1,
                calculatedWidth:"20%",
                pipe: new DynamicPipe(CustomDatePipe, [this.dateTimeFormat])
            }),
            updateTime: new TableSchemaElement({
                type:"value",
                title:$localize `:@@updated_time:Updated time`,
                pathToValue:"updatedTime",
                description:$localize `:@@updated_time:Updated time`,
                widthWeight:1.4,
                calculatedWidth:"20%"
            }),
            scheduledTime: new TableSchemaElement({
                type:"value",
                title:$localize `:@@scheduled_time:Scheduled Time`,
                pathToValue:"scheduledTime",
                description: $localize `:@@scheduled_time:Scheduled Time`,
                widthWeight:1.4,
                calculatedWidth:"20%"
            }),
            processingStartTime_scheduledTime: new TableSchemaElement({
                type:"value",
                title:$localize `:@@process_delay:Process Delay`,
                description: $localize `:@@process_delay:Process Delay`,
                hook:(data)=> {
                    if(data)
                        return j4care.getDifferenceTime(data['scheduledTime'], data['processingStartTime']);
                    return "";
                },
                widthWeight:1.4,
                calculatedWidth:"20%"
            }),
            processingEndTime_processingStartTime: new TableSchemaElement({
                type:"value",
                title:$localize `:@@process_time:Process Time`,
                description: $localize `:@@process_time:Process Time`,
                hook:(data)=> {
                    if(data)
                        return j4care.getDifferenceTime(data['processingStartTime'], data['processingEndTime']);
                    return "";
                },
                widthWeight:1.4,
                calculatedWidth:"20%"
            }),
            LocalAET: new TableSchemaElement({
                type:"value",
                title:$localize `:@@local_aet:Local AET`,
                pathToValue:"LocalAET",
                description: $localize `:@@local_aet:Local AET`,
                widthWeight:1,
                calculatedWidth:"20%",
                cssClass:"hideOn1100px"
            }),
            RemoteAET: new TableSchemaElement({
                type:"value",
                title:$localize `:@@remote_aet:Remote AET`,
                pathToValue:"RemoteAET",
                description: $localize `:@@remote_aet:Remote AET`,
                widthWeight:1,
                calculatedWidth:"20%"
            }),
            ExporterID: new TableSchemaElement({
                type:"value",
                title:$localize `:@@exporter_id:Exporter ID`,
                pathToValue:"ExporterID",
                description: $localize `:@@exporter_id:Exporter ID`,
                widthWeight:1,
                calculatedWidth:"20%"
            }),
            Modality: new TableSchemaElement({
                type: "value",
                title: $localize `:@@modality:Modality`,
                pathToValue: "Modality",
                description: $localize `:@@modality:Modality`,
                widthWeight: 0.9,
                calculatedWidth: "20%"
            }),
            NumberOfInstances: new TableSchemaElement({
                type: "value",
                header: $localize `:@@number_of_instances:#I`,
                pathToValue: "NumberOfInstances",
                headerDescription: $localize `:@@number_of_study_related_instances:Number of Study Related Instances`,
                widthWeight: 0.8,
                calculatedWidth: "20%"
            }),
            DestinationAET: new TableSchemaElement({
                type:"value",
                title:$localize `:@@destination_aet:Destination AET`,
                pathToValue:"DestinationAET",
                description: $localize `:@@destination_aet:Destination AET`,
                widthWeight:1,
                calculatedWidth:"20%",
                cssClass:"hideOn1000px"
            }),
            remaining: new TableSchemaElement({
                type:"value",
                title:$localize `:@@amount_i:#I`,
                description: $localize `:@@completed_warning_failed:Completed / Warning / Failed`,
                hook:(data)=>{
                    if(data)
                        return `${data.completed || 0} / ${data.warning || 0} / ${data.failed || 0}`;
                    return "";
                },
                widthWeight:1,
                calculatedWidth:"20%"
            }),
            PrimaryAET: new TableSchemaElement({
                type:"value",
                title:$localize `:@@primary_aet:Primary AET`,
                pathToValue:"PrimaryAET",
                description: $localize `:@@primary_aet:Primary AET`,
                widthWeight:1,
                calculatedWidth:"20%"
            }),
            SecondaryAET: new TableSchemaElement({
                type:"value",
                title:$localize `:@@secondary_aet:Secondary AET`,
                pathToValue:"SecondaryAET",
                description: $localize `:@@secondary_aet:Secondary AET`,
                widthWeight:1,
                calculatedWidth:"20%"
            }),
            comparefield: new TableSchemaElement({
                type:"value",
                title:$localize `:@@compare:compare`,
                pathToValue:"comparefield",
                description: $localize `:@@compare:compare`,
                widthWeight:1,
                calculatedWidth:"20%",
                cssClass:"hideOn800px"
            }),
            matches: new TableSchemaElement({
                type:"value",
                title:$localize `:@@amount_s:#S`,
                description: $localize `:@@missing_different:Missing / Different`,
                hook:(data)=>`${data.missing || 0} / ${data.different || 0}`,
                widthWeight:0.8,
                calculatedWidth:"20%"
            }),
            StorageID: new TableSchemaElement({
                type:"value",
                title: $localize`:@@storage_id:Storage ID`,
                pathToValue:"StorageID",
                description: $localize`:@@storage_id:Storage ID`,
                widthWeight:1,
                calculatedWidth:"20%"
            }),
            StgCmtPolicy: new TableSchemaElement({
                type:"value",
                title: $localize`:@@StgCmtPolicy:Stg.Ver.Policy`,
                pathToValue:"StgCmtPolicy",
                description: $localize`:@@StgCmtPolicy:Stg.Ver.Policy`,
                widthWeight:1,
                calculatedWidth:"20%"
            }),
            completed_failed: new TableSchemaElement({
                type:"value",
                title:$localize `:@@amount_i:#I`,
                description: $localize`:@@completed_failed:Completed / Failed`,
                hook:(data)=>`${data.completed || 0} / ${data.failed || 0}`,
                widthWeight:1,
                calculatedWidth:"20%"
            }),
            status: new TableSchemaElement({
                type: "value",
                title: $localize `:@@status:Status`,
                pathToValue: "status",
                description: $localize `:@@status:Status`,
                hoverHook:(data)=>{
                    if(_.hasIn(data,"outcomeMessage") && data.outcomeMessage != ""){
                        return _.get(data, "outcomeMessage")
                    }
                    if(_.hasIn(data,"errorMessage") && data.errorMessage != ""){
                        return _.get(data, "errorMessage")
                    }
                    return _.get(data,"status");
                },
                widthWeight: 0.9,
                calculatedWidth: "20%",
                cssClass:"hideOn1100px"
            }),
            failures: new TableSchemaElement({
                type:"value",
                title:$localize `:@@failures:Failures`,
                pathToValue:"failures",
                description: $localize `:@@failures:Failures`,
                hoverHook:(data)=>{
                    if(_.hasIn(data,"errorMessage")){
                        return data.errorMessage;
                    }
                    return _.get(data,"failures");
                },
                widthWeight:0.8,
                calculatedWidth:"20%"
            }),
            batchID: new TableSchemaElement({
                type:"value",
                title:$localize `:@@batch_id:Batch ID`,
                pathToValue:"batchID",
                description: $localize `:@@batch_id:Batch ID`,
                widthWeight:1,
                calculatedWidth:"20%",
                cssClass:"hideOn800px"
            }),
            id: new TableSchemaElement({
                type:"value",
                title:$localize `:@@id:Id`,
                header:$localize `:@@id:Id`,
                widthWeight:1,
                pathToValue:"id"
            }),
            name: new TableSchemaElement({
                type:"value",
                title:$localize `:@@name:Name`,
                header:$localize `:@@name:Name`,
                widthWeight:1.5,
                pathToValue:"name"
            })
        };
        if(toReturnElements){
            let toBeReturnedElements = [];
            toReturnElements.forEach((element:(string|MappedTableSchemaElement))=>{
                if(element){
                    if(typeof element === "string"){
                        if(allElements[element]){
                            toBeReturnedElements.push(allElements[element]);
                        }
                    }else{
                        if(element.key && allElements[element.key]){
                            if(element.overwrite){
                                let selectedElement = _.clone(allElements[element.key]);
                                Object.keys(element.overwrite).forEach(optionKey=>{
                                    selectedElement[optionKey] = element.overwrite[optionKey];
                                });
                                toBeReturnedElements.push(selectedElement);
                            }else{
                                toBeReturnedElements.push(allElements[element.key])
                            }
                        }
                    }
                }
            });
            return toBeReturnedElements;
        }
        return Object.keys(allElements).map(k=>allElements[k]);
    }

    stringifyRangeArray(data){
        try{
            const pipe = new CustomDatePipe();
            return `${pipe.transform(data[0],this.dateTimeFormat)} - ${pipe.transform(data[1],this.dateTimeFormat)}`
        }catch (e){
            return data;
        }
    }

    getTimeColumnBasedOnFilter(filters){
        if(j4care.is(filters, "orderby")){
            if(filters.orderby.indexOf("updatedTime") > -1){
                return ["updateTime"]
            }
            if(filters.orderby.indexOf("createdTime") > -1){
                return ["createdTime"]
            }
            if(filters.orderby.indexOf("scheduledTime") > -1){
                return ["scheduledTime"]
            }
            return [
                "updateTime",
                "createdTime",
                "scheduledTime"
            ]
        }
    }
}

export interface MappedTableSchemaElement{
    key:TableSchemaElementKey;
    overwrite?:TableSchemaElement;
}
export type TableSchemaElementKey = "dicomDeviceName" |
    "queue" |
    "createdTime" |
    "createdDate" |
    "updateTime" |
    "scheduledTime" |
    "processingStartTime_scheduledTime" |
    "processingEndTime_processingStartTime" |
    "LocalAET" |
    "RemoteAET" |
    "ExporterID" |
    "Modality" |
    "NumberOfInstances" |
    "DestinationAET" |
    "remaining" |
    "PrimaryAET" |
    "SecondaryAET" |
    "comparefield" |
    "matches" |
    "StorageID" |
    "StgCmtPolicy" |
    "completed_failed" |
    "status" |
    "failures" |
    "id" |
    "name" |
    "batchID";