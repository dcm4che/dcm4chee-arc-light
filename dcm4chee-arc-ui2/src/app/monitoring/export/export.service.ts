import { Injectable } from '@angular/core';
import {AppService} from '../../app.service';
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {DevicesService} from "../../configuration/devices/devices.service";
import {WindowRefService} from "../../helpers/window-ref.service";
import * as _ from 'lodash-es';
import {j4care} from "../../helpers/j4care.service";
import {HttpHeaders} from "@angular/common/http";
import {TableSchemaElement} from "../../models/dicom-table-schema-element";
import {TableService} from "../../table.service";
import {SelectDropdown} from "../../interfaces";

@Injectable()
export class ExportService {

    header = new HttpHeaders({ 'Content-Type': 'application/json' });
    constructor(
        public $http:J4careHttpService,
        public mainservice: AppService,
        private deviceService:DevicesService,
        private tableService:TableService
    ) {}

    search(filters, offset, batch) {
        return this.$http.get(`${j4care.addLastSlash(this.mainservice.baseUrl)}monitor/export${(batch?'/batch':'')}?${this.mainservice.param(this.queryParams(filters, offset))}`);;
    };

    getCount(filters) {
        let filterClone = _.cloneDeep(filters);
        delete filterClone.offset;
        delete filterClone.limit;
        delete filterClone.orderby;
        return this.$http.get(`${j4care.addLastSlash(this.mainservice.baseUrl)}monitor/export/count?${this.mainservice.param(this.paramWithoutLimit(filterClone))}`);
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
    cancel(taskID){
        return this.$http.post(`${j4care.addLastSlash(this.mainservice.baseUrl)}monitor/export/${taskID}/cancel`, {});
    }
    cancelAll(filter){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.post(`${j4care.addLastSlash(this.mainservice.baseUrl)}monitor/export/cancel${urlParam}`, {}, this.header)
            ;
    }
    delete(taskID){
        return this.$http.delete(`${j4care.addLastSlash(this.mainservice.baseUrl)}monitor/export/${taskID}`);
    }
    deleteAll(filter){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.delete(`${j4care.addLastSlash(this.mainservice.baseUrl)}monitor/export${urlParam}`, this.header)
            ;
    }
    reschedule(taskID, exporterID, filter?){
        filter = filter || "";
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.post(`${j4care.addLastSlash(this.mainservice.baseUrl)}monitor/export/${taskID}/reschedule/${exporterID + urlParam}`, {});
    }
    rescheduleAll(filter, exporterID){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        let exporter = exporterID? `/${exporterID}`:'';
        return this.$http.post(`${j4care.addLastSlash(this.mainservice.baseUrl)}monitor/export/reschedule${exporter}${urlParam}`, {}, this.header);
    }
    downloadCsv(filter){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        // let header = new Headers({ 'Content-Type': 'text/csv' });
        let header = new HttpHeaders({ 'Accept': 'text/csv' });
        return this.$http.get(`${j4care.addLastSlash(this.mainservice.baseUrl)}/monitor/export${urlParam}`, header)
    }
    getDevices(){
        return this.deviceService.getDevices()
    }
    statusValues(){
        return [
            {
                value:"SCHEDULED",
                text:$localize `:@@SCHEDULED:SCHEDULED`,
                key:"scheduled"
            },{
                value:"SCHEDULED FOR RETRY",
                text:$localize `:@@S_FOR_RETRY:S. FOR RETRY`,
                key:"scheduled-for-retry"
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
                        text:$localize `:@@exporter_id:Exporter ID`,
                    },
                    {
                        tag:"select",
                        options:exporters.map(exporter=>{
                            return {
                                text:exporter.description,
                                value:exporter.id
                            }
                        }),
                        showStar:true,
                        filterKey:"selectedExporter",
                        description:$localize `:@@exporter_id:Exporter ID`,
                        placeholder:$localize `:@@exporter_id:Exporter ID`
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
                        text:$localize `:@@schedule_at_desc:Schedule at (if not set, schedule immediately)`
                    },
                    {
                        tag:"single-date-time-picker",
                        type:"text",
                        filterKey:"scheduledTime",
                        description:$localize `:@@schedule_at_desc:Schedule at (if not set, schedule immediately)`
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
                        tag:"multi-select",
                        options:exporters.map(d=>{
                            return{
                                text:d.description || d.id,
                                value:d.id
                            }
                        }),
                        showSearchField:true,
                        filterKey:"ExporterID",
                        description:$localize `:@@exporter_id:Exporter ID`,
                        placeholder:$localize `:@@exporter_id:Exporter ID`
                    },
                    {
                        tag:"html-select",
                        options:devices.map(d=>{
                            return{
                                text:d.dicomDeviceName,
                                value:d.dicomDeviceName
                            }
                        }),
                        showStar:true,
                        showSearchField:true,
                        filterKey:"dicomDeviceName",
                        description:$localize `:@@device_name_to_filter_by:Device Name to filter by`,
                        placeholder:$localize `:@@device_name:Device Name`
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
                        description:$localize `:@@maximal_number_of_tasks_in_returned_list:Maximal number of tasks in returned list`
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
                        description:$localize `:@@status_of_tasks_to_filter_by:Status of tasks to filter by`,
                        placeholder:$localize `:@@status:Status`
                    }
                ]
            ],[
                [
                    {
                        tag:"range-picker",
                        filterKey:"createdTime",
                        description:$localize `:@@created_date:Created Date`
                    },
                    {
                        tag:"range-picker",
                        filterKey:"updatedTime",
                        description:$localize `:@@updated_date:Updated Date`
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
                            },
                            {
                                value:"scheduledTime",
                                text:$localize `:@@sort_by_scheduled_time_asc:Sort by scheduled time (ASC)`
                            },
                            {
                                value:"-scheduledTime",
                                text:$localize `:@@sort_by_scheduled_time_desc:Sort by scheduled time (DESC)`
                            }
                        ],
                        filterKey:"orderby",
                        description:$localize `:@@sort:Sort`,
                        placeholder:$localize `:@@sort:Sort`
                    },
                    {
                        tag:"input",
                        type:"text",
                        filterKey:"StudyInstanceUID",
                        description:$localize `:@@unique_identifier_of_the_study_to_filter_by:Unique Identifier of the Study to filter by`,
                        placeholder:$localize `:@@study_instance_uid:Study Instance UID`
                    }
                ],
                [
                    {
                        tag:"button",
                        id:"count",
                        text:countText,
                        description:$localize `:@@query_only_the_count:QUERY ONLY THE COUNT`
                    },
                    {
                        tag:"button",
                        id:"submit",
                        text:$localize `:@@SUBMIT:SUBMIT`,
                        description:$localize `:@@maximal_number_of_tasks_in_returned_list:Maximal number of tasks in returned list`
                    }
                ]
            ]
        ];
    }

    getTableSchema($this, action, options){
        if(_.hasIn(options,"grouped") && options.grouped){
            return [
                new TableSchemaElement({
                    type:"index",
                    title:"#",
                    description:$localize `:@@index:Index`,
                    widthWeight:0.2,
                    calculatedWidth:"4%"
                }),new TableSchemaElement({
                    type:"actions",
                    title:"",
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
                            title:$localize `:@@show_details:Show details`
                        },{
                            icon:{
                                tag:'span',
                                cssClass:'glyphicon glyphicon-list-alt',
                                text:''
                            },
                            click:(e)=>{
                                console.log("e",e);
                                action.call($this,'task-detail', e);
                            },
                            title:$localize `:@@title.export.show_tasks_detail:Show Tasks Detail`
                        },
                        {
                            icon:{
                                tag:'span',
                                cssClass:'glyphicon glyphicon-remove-circle',
                                text:''
                            },
                            click:(e)=>{
                                console.log("e",e);
                                action.call($this,'delete-batched', e);
                            },
                            permission: {
                                id: 'action-monitoring->export-single_action',
                                param: 'visible'
                            },
                            title:$localize `:@@title.delete_task_with_this_batchid:Delete Task with this BatchID`
                        }
                    ],
                    description:$localize `:@@actions:Actions`,
                    pxWidth:105
                }),
                new TableSchemaElement({
                    type:"value",
                    title:$localize `:@@batch_id:Batch ID`,
                    pathToValue:"batchID",
                    description: $localize `:@@batch_id:Batch ID`,
                    widthWeight:1,
                    calculatedWidth:"20%"
                }),
                new TableSchemaElement({
                    type:"value",
                    title:$localize `:@@remote_aet:Remote AET`,
                    pathToValue:"RemoteAET",
                    description: $localize `:@@remote_aet:Remote AET`,
                    widthWeight:1,
                    calculatedWidth:"20%"
                }),
                new TableSchemaElement({
                    type:"value",
                    title:$localize `:@@destination_aet:Destination AET`,
                    pathToValue:"DestinationAET",
                    description: $localize `:@@destination_aet:Destination AET`,
                    widthWeight:1,
                    calculatedWidth:"20%"
                }),
                new TableSchemaElement({
                    type:"value",
                    title:$localize `:@@scheduled_time_range:Scheduled Time Range`,
                    pathToValue:"scheduledTimeRange",
                    description: $localize `:@@scheduled_time_range:Scheduled Time Range`,
                    widthWeight:1,
                    calculatedWidth:"20%"
                }),
                new TableSchemaElement({
                    type:"value",
                    title:$localize `:@@processing_start_time_range:Processing Start Time Range`,
                    pathToValue:"processingStartTimeRange",
                    description: $localize `:@@processing_start_time_range:Processing Start Time Range`,
                    widthWeight:1,
                    calculatedWidth:"20%"
                }),
                new TableSchemaElement({
                    type:"value",
                    title:$localize `:@@processing_end_time_range:Processing end time range`,
                    pathToValue:"processingEndTimeRange",
                    description: $localize `:@@processing_end_time_range:Processing end time range`,
                    widthWeight:1,
                    calculatedWidth:"20%"
                }),
                new TableSchemaElement({
                    type:"progress",
                    title:$localize `:@@tasks:Tasks`,
                    pathToValue:"tasks",
                    description: $localize `:@@tasks:Tasks`,
                    widthWeight:1.5,
                    cssClass:"no-padding",
                    calculatedWidth:"20%"
                })
            ]
        }
        return [
            new TableSchemaElement({
                type:"index",
                title:"#",
                description:$localize `:@@index:Index`,
                widthWeight:0.2,
                calculatedWidth:"4%",
                pxWidth:30
            }),new TableSchemaElement({
            type:"actions",
            title:"",
            headerActions:[
                {
                    icon: {
                        tag: 'span',
                        cssClass: 'glyphicon glyphicon-unchecked',
                        text: ''
                    },
                    click: (models, config) => {
                        models.forEach(m=>{
                            m.selected = true;
                        });
                        config.allSelected = true;
                    },
                    title: $localize `:@@select:Select`,
                    showIf: (e, config) => {
                        return !config.allSelected;
                    }
                }, {
                    icon: {
                        tag: 'span',
                        cssClass: 'glyphicon glyphicon-check',
                        text: ''
                    },
                    click: (models,config) => {
                        models.forEach(m=>{
                            m.selected = false;
                        });
                        config.allSelected = false;
                    },
                    title: $localize `:@@unselect:Unselect`,
                    showIf: (e, config) => {
                        return config.allSelected;
                    }
                },
                {
                    icon:{
                        tag:'span',
                        cssClass:'glyphicon glyphicon-ban-circle',
                        text:''
                    },
                    click:(e)=>{
                        console.log("e",e);
                        action.call($this,'cancel-selected', e);
                    },
                    permission: {
                        id: 'action-monitoring->export-single_action',
                        param: 'visible'
                    },
                    title:$localize `:@@title.cancel_selected:Cancel selected`
                },
                {
                    icon:{
                        tag:'span',
                        cssClass:'glyphicon glyphicon-repeat',
                        text:''
                    },
                    click:(e)=>{
                        console.log("e",e);
                        action.call($this,'reschedule-selected', e);
                    },
                    permission: {
                        id: 'action-monitoring->export-single_action',
                        param: 'visible'
                    },
                    title:$localize `:@@title.reschedule_selected:Reschedule selected`
                },
                {
                    icon:{
                        tag:'span',
                        cssClass:'glyphicon glyphicon-remove-circle',
                        text:''
                    },
                    click:(e)=>{
                        console.log("e",e);
                        action.call($this,'delete-selected', e);
                    },
                    permission: {
                        id: 'action-monitoring->export-single_action',
                        param: 'visible'
                    },
                    title:$localize `:@@title.delete_selected:Delete selected`
                }
            ],
            actions:[
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
                        return !e.selected;
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
                        return e.selected;
                    }
                },
                {
                    icon:{
                        tag:'span',
                        cssClass:'glyphicon glyphicon-ban-circle',
                        text:''
                    },
                    click:(e)=>{
                        console.log("e",e);
                        action.call($this,'cancel', e);
                    },
                    title:$localize `:@@cancel_this_task:Cancel this task`,
                    permission: {
                        id: 'action-monitoring->export-single_action',
                        param: 'visible'
                    },
                    showIf:(match, config) => {
                        return ((match.status && match.status === 'SCHEDULED') || (match.status && match.status === 'IN PROCESS'));
                    }
                },
                {
                    icon:{
                        tag:'span',
                        cssClass:'glyphicon glyphicon-repeat',
                        text:''
                    },
                    click:(e)=>{
                        console.log("e",e);
                        action.call($this,'reschedule', e);
                    },
                    title:$localize `:@@reschedule_this_task:Reschedule this task`,
                    permission: {
                        id: 'action-monitoring->export-single_action',
                        param: 'visible'
                    }
                },
                {
                    icon:{
                        tag:'span',
                        cssClass:'glyphicon glyphicon-remove-circle',
                        text:''
                    },
                    click:(e)=>{
                        console.log("e",e);
                        action.call($this,'delete', e);
                    },
                    permission: {
                        id: 'action-monitoring->export-single_action',
                        param: 'visible'
                    },
                    title:$localize `:@@delete_this_task:Delete this task`
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
                    },
                    title:$localize `:@@show_details:Show details`
                }
            ],
            description:$localize `:@@actions:Actions`,
            pxWidth:105
        }),
            ...this.tableService.getTableSchema(_.concat(
                [
                    "dicomDeviceName",
                    "queue",
                ],
                this.tableService.getTimeColumnBasedOnFilter(options.filterObject),
                [
                    "processingStartTime_scheduledTime",
                    "processingEndTime_processingStartTime",
                    "LocalAET",
                    "ExporterID",
                    "Modality",
                    "NumberOfInstances",
                    "status",
                    "failures",
                    "batchID"
                ]
            ))
        ]
    }


}
