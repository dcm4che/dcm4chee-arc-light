import { Injectable } from '@angular/core';
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
        return this.$http.get(`../monitor/export${(batch?'/batch':'')}?${this.mainservice.param(this.queryParams(filters, offset))}`);;
    };

    getCount(filters) {
        let filterClone = _.cloneDeep(filters);
        delete filterClone.offset;
        delete filterClone.limit;
        delete filterClone.orderby;
        return this.$http.get('../monitor/export' + '/count' + '?' +  this.mainservice.param(this.paramWithoutLimit(filterClone)))
            ;
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
            ;
    }
    delete(pk){
        return this.$http.delete('../monitor/export/' + pk);
    }
    deleteAll(filter){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.delete(`../monitor/export${urlParam}`, this.header)
            ;
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
            ;
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
            {
                value:"TO SCHEDULE",
                text:$localize `:@@to_schedule:TO SCHEDULE`,
                key:"to-schedule"
            },{
                value:"SCHEDULED",
                text:$localize `:@@SCHEDULED:SCHEDULED`,
                key:"scheduled"
            },{
                value:"IN PROCESS",
                text:$localize `:@@in_process:IN PROCESS`,
                key:"in-process"
            },{
                value:"COMPLETED",
                text:$localize `:@@COMPLETED:COMPLETED`,
                key:"completed"
            },{
                value:"WARNING",
                text:$localize `:@@WARNING:WARNING`,
                key:"warning"
            },{
                value:"FAILED",
                text:$localize `:@@FAILED:FAILED`,
                key:"failed"
            },
            {
                value:"CANCELED",
                text:$localize `:@@CANCELED:CANCELED`,
                key:"canceled"
            }
        ];
    }
    getDialogSchema(exporters, devices, text?){
        return [
            [
                [
                    {
                        tag:"label_large",
                        text:text || $localize `:@@export.change_exporter_text:Change the exporter for all rescheduled tasks. To reschedule with the original exporters associated with the tasks, leave blank:`
                    }
                ],
                [
                    {
                        tag:"label",
                        text:$localize `:@@export.exporter_id:Exporter ID`,
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
                        description:$localize `:@@export.exporter_id:Exporter ID`,
                        placeholder:$localize `:@@export.exporter_id:Exporter ID`
                    }
                ],
                [
                    {
                        tag:"label_large",
                        text:$localize `:@@export.select_device_if_you_want_to_reschedule:Select device if you want to reschedule to an other device:`
                    }
                ],
                [
                    {
                        tag:"label",
                        text:$localize `:@@device:Device`
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
                        description:$localize `:@@device:Device`,
                        placeholder:$localize `:@@device:Device`
                    }
                ],
                [
                    {
                        tag:"label",
                        text:$localize `:@@export.scheduled_time:Scheduled Time`
                    },
                    {
                        tag:"single-date-time-picker",
                        type:"text",
                        filterKey:"scheduledTime",
                        description:$localize `:@@export.scheduled_times:Scheduled times`
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
                        description:$localize `:@@export.exporter_id:Exporter ID`,
                        placeholder:$localize `:@@export.exporter_id:Exporter ID`
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
                        description:$localize `:@@export.device_name_to_filter_by:Device Name to filter by`,
                        placeholder:$localize `:@@export.device_name:Device Name`
                    }
                ],
                [
                    {
                        tag:"label",
                        text:$localize `:@@limit:Limit`
                    },
                    {
                        tag:"input",
                        type:"number",
                        filterKey:"limit",
                        description:$localize `:@@export.maximal_number_of_tasks_in_returned_list:Maximal number of tasks in returned list`
                    }
                ],
                [
                    {
                        tag:"input",
                        type:"text",
                        filterKey:"batchID",
                        description:$localize `:@@batch_id:Batch ID`,
                        placeholder:$localize `:@@batch_id:Batch ID`
                    },
                    {
                        tag:"select",
                        options:this.statusValues(),
                        filterKey:"status",
                        showStar:true,
                        description:$localize `:@@export.status_of_tasks_to_filter_by:Status of tasks to filter by`,
                        placeholder:$localize `:@@status:Status`
                    }
                ]
            ],[
                [
                    {
                        tag:"range-picker",
                        filterKey:"createdTime",
                        description:$localize `:@@export.created_date:Created Date`
                    },
                    {
                        tag:"range-picker",
                        filterKey:"updatedTime",
                        description:$localize `:@@export.updated_date:Updated Date`
                    }
                ],
                [
                    {
                        tag:"select",
                        options:[{
                            value:'createdTime',
                            text: $localize `:@@sort_by_creation_time_asc:Sort by creation time (ASC)`
                        },
                            {
                                value:'-createdTime',
                                text: $localize `:@@sort_by_creation_time_desc:Sort by creation time (DESC)`
                            },
                            {
                                value:'updatedTime',
                                text: $localize `:@@sort_by_updated_time_asc:Sort by updated time (ASC)`
                            },
                            {
                                value:'-updatedTime',
                                text: $localize `:@@sort_by_updated_time_desc:Sort by updated time (DESC)`
                            }
                        ],
                        showStar:true,
                        filterKey:"orderby",
                        description:$localize `:@@sort:Sort`,
                        placeholder:$localize `:@@sort:Sort`
                    },
                    {
                        tag:"input",
                        type:"text",
                        filterKey:"StudyInstanceUID",
                        description:$localize `:@@export.unique_identifier_of_the_study_to_filter_by:Unique Identifier of the Study to filter by`,
                        placeholder:$localize `:@@export.study_instance_uid:Study Instance UID`
                    }
                ],
                [
                    {
                        tag:"button",
                        id:"count",
                        text:countText,
                        description:$localize `:@@export.querie_only_the_count:QUERIE ONLY THE COUNT`
                    },
                    {
                        tag:"button",
                        id:"submit",
                        text:$localize `:@@SUBMIT:SUBMIT`,
                        description:$localize `:@@export.maximal_number_of_tasks_in_returned_list:Maximal number of tasks in returned list`
                    }
                ]
            ]
        ];
    }
}
