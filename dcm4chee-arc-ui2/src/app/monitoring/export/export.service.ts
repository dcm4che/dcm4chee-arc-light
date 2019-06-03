import { Injectable } from '@angular/core';
import {Http, Headers} from '@angular/http';
import {AppService} from '../../app.service';
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {DevicesService} from "../../configuration/devices/devices.service";
import {WindowRefService} from "../../helpers/window-ref.service";
import * as _ from 'lodash';
import {j4care} from "../../helpers/j4care.service";
import {HttpHeaders} from "@angular/common/http";

@Injectable()
export class ExportService {

    header = new HttpHeaders({ 'Content-Type': 'application/json' });
    constructor(public $http:J4careHttpService, public mainservice: AppService, private deviceService:DevicesService) {
    }

    search(filters, offset, batch) {
        return this.$http.get(`../monitor/export${(batch?'/batch':'')}?${this.mainservice.param(this.queryParams(filters, offset))}`).map(res => j4care.redirectOnAuthResponse(res));;
    };

    getCount(filters) {
        let filterClone = _.cloneDeep(filters);
        delete filterClone.offset;
        delete filterClone.limit;
        delete filterClone.orderby;
        return this.$http.get('../monitor/export' + '/count' + '?' +  this.mainservice.param(this.paramWithoutLimit(filterClone)))
            .map(res => j4care.redirectOnAuthResponse(res));
    };
    paramWithoutLimit(filters){
        let clonedFilters = this.queryParams(filters,undefined);
        delete clonedFilters.limit;
        return clonedFilters;
    }
    queryParams(filters, offset) {
/*        var params = {
            offset: (offset && offset != '') ? offset : 0,
            limit: limit,
            status:undefined
        }*/
        let clonedFilters = _.cloneDeep(filters);
        clonedFilters.offset = (offset && offset != '') ? offset : 0;
        if (clonedFilters.status && clonedFilters.status === '*'){
            delete clonedFilters.status;
        }
        if (clonedFilters.ExporterID && clonedFilters.ExporterID === '*'){
            delete clonedFilters.ExporterID;
        }
        if (clonedFilters.updatedTimeObject){
            delete clonedFilters.updatedTimeObject;
        }
        if (clonedFilters.createdTimeObject){
            delete clonedFilters.createdTimeObject;
        }
        return clonedFilters;
    }
    cancel(pk){
        return this.$http.post('../monitor/export/' + pk + '/cancel', {});
    }
    cancelAll(filter){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.post(`../monitor/export/cancel${urlParam}`, {}, this.header)
            .map(res => j4care.redirectOnAuthResponse(res));
    }
    delete(pk){
        return this.$http.delete('../monitor/export/' + pk);
    }
    deleteAll(filter){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.delete(`../monitor/export${urlParam}`, this.header)
            .map(res => j4care.redirectOnAuthResponse(res));
    }
    reschedule(pk, exporterID, filter?){
        filter = filter || "";
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.post('../monitor/export/' + pk + '/reschedule/' + exporterID + urlParam, {});
    }

    rescheduleAll(filter, exporterID){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        let exporter = exporterID? `/${exporterID}`:'';
        return this.$http.post(`../monitor/export/reschedule${exporter}${urlParam}`, {}, this.header)
            .map(res => j4care.redirectOnAuthResponse(res));
    }
    downloadCsv(filter){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        // let header = new Headers({ 'Content-Type': 'text/csv' });
        let header = new HttpHeaders({ 'Accept': 'text/csv' });
        return this.$http.get(`/dcm4chee-arc/monitor/export${urlParam}`, header)
    }
    getDevices(){
        return this.deviceService.getDevices()
    }
    statusValues(){
        return [
            "TO SCHEDULE",
            "SCHEDULED",
            "IN PROCESS",
            "COMPLETED",
            "WARNING",
            "FAILED",
            "CANCELED"
        ];
    }
    getDialogSchema(exporters, devices, text?){
        return [
            [
                [
                    {
                        tag:"label_large",
                        text:text || "Change the exporter for all rescheduled tasks. To reschedule with the original exporters associated with the tasks, leave blank:"
                    }
                ],
                [
                    {
                        tag:"label",
                        text:"Exporter ID",
                    },
                    {
                        tag:"select",
                        options:exporters.map(exporter=>{
                            return {
                                text:exporter.description,
                                value:exporter.id
                            }
                        }),
                        filterKey:"selectedExporter",
                        description:"Exporter ID",
                        placeholder:"Exporter ID"
                    }
                ],
                [
                    {
                        tag:"label_large",
                        text:'Select device if you wan\'t to reschedule to an other device:'
                    }
                ],
                [
                    {
                        tag:"label",
                        text:"Device"
                    },
                    {
                        tag:"select",
                        options:devices.map(device=>{
                            return {
                                text:device.dicomDeviceName,
                                value:device.dicomDeviceName
                            }
                        }),
                        showStar:true,
                        filterKey:"newDeviceName",
                        description:"Device",
                        placeholder:"Device"
                    }
                ]
            ]
        ]
    }
    getFilterSchema(exporters, devices, countText){
        return [
            [
                [
                    {
                        tag:"select",
                        options:exporters.map(d=>{
                            return{
                                text:d.description || d.id,
                                value:d.id
                            }
                        }),
                        showStar:true,
                        filterKey:"ExporterID",
                        description:"Exporter ID",
                        placeholder:"Exporter ID"
                    },
                    {
                        tag:"select",
                        options:devices.map(d=>{
                            return{
                                text:d.dicomDeviceName,
                                value:d.dicomDeviceName
                            }
                        }),
                        showStar:true,
                        filterKey:"dicomDeviceName",
                        description:"Device Name to filter by",
                        placeholder:"Device Name"
                    }
                ],
                [
                    {
                        tag:"label",
                        text:"Limit"
                    },
                    {
                        tag:"input",
                        type:"number",
                        filterKey:"limit",
                        description:"Maximal number of tasks in returned list"
                    }
                ],
                [
                    {
                        tag:"input",
                        type:"text",
                        filterKey:"batchID",
                        description:"Batch ID",
                        placeholder:"Batch ID"
                    },
                    {
                        tag:"select",
                        options:this.statusValues().map(status=>{
                            return {
                                text:status,
                                value:status
                            }
                        }),
                        filterKey:"status",
                        showStar:true,
                        description:"Status of tasks to filter by",
                        placeholder:"Status"
                    }
                ]
            ],[
                [
                    {
                        tag:"range-picker",
                        filterKey:"createdTime",
                        description:"Created Date"
                    },
                    {
                        tag:"range-picker",
                        filterKey:"updatedTime",
                        description:"Updated Date"
                    }
                ],
                [
                    {
                        tag:"select",
                        options:[{
                            value:'createdTime',
                            text:'Sort by creation time (ASC)'
                        },
                            {
                                value:'-createdTime',
                                text:'Sort by creation time (DESC)'
                            },
                            {
                                value:'updatedTime',
                                text:'Sort by updated time (ASC)'
                            },
                            {
                                value:'-updatedTime',
                                text:'Sort by updated time (DESC)'
                            }
                        ],
                        showStar:true,
                        filterKey:"orderby",
                        description:"Sort",
                        placeholder:"Sort"
                    },
                    {
                        tag:"input",
                        type:"text",
                        filterKey:"StudyInstanceUID",
                        description:"Unique Identifier of the Study to filter by",
                        placeholder:"Study Instance UID"
                    }
                ],
                [
                    {
                        tag:"button",
                        id:"count",
                        text:countText,
                        description:"QUERIE ONLY THE COUNT"
                    },
                    {
                        tag:"button",
                        id:"submit",
                        text:"SUBMIT",
                        description:"Maximal number of tasks in returned list"
                    }
                ]
            ]
        ];
    }
}
