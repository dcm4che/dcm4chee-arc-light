import { Injectable } from '@angular/core';
import {AppService} from '../../app.service';
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {DevicesService} from "../../configuration/devices/devices.service";
import {j4care} from "../../helpers/j4care.service";
import {HttpHeaders} from "@angular/common/http";
import {TableSchemaElement} from "../../models/dicom-table-schema-element";
import {TableService} from "../../table.service";
import * as _ from 'lodash-es';

@Injectable()
export class QueuesService{

    header = new HttpHeaders({ 'Content-Type': 'application/json' });
    constructor(
        public $http:J4careHttpService,
        public mainservice: AppService,
        private deviceService:DevicesService,
        private tableService:TableService
    ) { }

    search(queueName, status, offset, limit, dicomDeviceName,createdTime,updatedTime, batchID, localAET, remoteAET, orderby) {

        return this.$http.get(this.url(queueName) + '?' + this.mainservice.param(this.queryParams(status, offset, limit, dicomDeviceName,createdTime,updatedTime, batchID, localAET, remoteAET, orderby)));
    };

    getCount(queueName, status, offset, limit, dicomDeviceName,createdTime,updatedTime, batchID, localAET, remoteAET, orderby) {
        return this.$http.get(this.url(queueName) + '/count' + '?' + this.mainservice.param(this.queryParams(status, offset, limit, dicomDeviceName,createdTime,updatedTime, batchID, localAET, remoteAET, orderby)));
    };

    cancel(queueName, msgId) {
        return this.$http.post(this.url3(queueName, msgId, 'cancel'), {}, this.header);
    };

    cancelAll(filter,queueName){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.post(`${j4care.addLastSlash(this.mainservice.baseUrl)}queue/${queueName}/cancel${urlParam}`, {}, this.header);
    }

    reschedule(queueName, msgId, filter) {
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.post(`${j4care.addLastSlash(this.mainservice.baseUrl)}queue/${queueName}/${msgId}/reschedule${urlParam}`, {}, this.header);
    }
    rescheduleAll(filter,queueName){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.post(`${j4care.addLastSlash(this.mainservice.baseUrl)}queue/${queueName}/reschedule${urlParam}`, {}, this.header);
    }
    delete(queueName, msgId) {
        return this.$http.delete(this.url2(queueName, msgId));
    };
    deleteAll(filter,queueName){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.delete(`${j4care.addLastSlash(this.mainservice.baseUrl)}queue/${queueName}${urlParam}`, this.header);
    }
    url(queueName) {
        return `${j4care.addLastSlash(this.mainservice.baseUrl)}queue/${queueName}`;
    }
    url2(queueName, msgId) {
        return this.url(queueName) + '/' + msgId;
    }
    url3(queueName, msgId, command) {
        return this.url2(queueName, msgId) + '/' + command;
    }
    config(params) {
        console.log('paramsconfig', params);
        let header = new HttpHeaders({ 'Content-Type': 'application/json' });
        header.append('params', params);
        return header;
    }

    queryParams(status, offset, limit, dicomDeviceName,createdTime,updatedTime, batchID, localAET, remoteAET, orderby) {
        let params = {
            offset: offset,
            limit: limit,
            dicomDeviceName: dicomDeviceName,
            status: undefined,
            createdTime:createdTime,
            updatedTime:updatedTime,
            batchID:batchID,
            localAET:localAET,
            remoteAET:remoteAET,
            orderby:undefined
        };
        if (orderby != '*')
            params.orderby = orderby;
        if (status != '*')
            params.status = status;
        return params;
    }
    getDevices(){
        return this.deviceService.getDevices()
    }
    sortValues(){
        return [
            {
                value:"createdTime",
                text:$localize `:@@sort_by_creation_time_asc:Sort by creation time (ASC)`
            },
            {
                value:"-createdTime",
                text:$localize `:@@sort_by_creation_time_desc:Sort by creation time (DESC)`
            },
            {
                value:"updatedTime",
                text:$localize `:@@sort_by_updated_time_asc:Sort by updated time (ASC)`
            },
            {
                value:"-updatedTime",
                text:$localize `:@@sort_by_updated_time_desc:Sort by updated time (DESC)`
            },
            {
                value:"scheduledTime",
                text:$localize `:@@sort_by_scheduled_time_asc:Sort by scheduled time (ASC)`
            },
            {
                value:"-scheduledTime",
                text:$localize `:@@sort_by_scheduled_time_desc:Sort by scheduled time (DESC)`
            }
        ]
    };

    statuses(){
        return [
            {
                value:"SCHEDULED",
                text:$localize `:@@SCHEDULED:SCHEDULED`
            },
            {
                value:"SCHEDULED FOR RETRY",
                text:$localize `:@@S_FOR_RETRY:S. FOR RETRY`
            },
            {
                value:"IN PROCESS",
                text:$localize `:@@in_process:IN PROCESS`
            },
            {
                value:"COMPLETED",
                text:$localize `:@@COMPLETED:COMPLETED`
            },
            {
                value:"WARNING",
                text:$localize `:@@WARNING:WARNING`
            },
            {
                value:"FAILED",
                text:$localize `:@@FAILED:FAILED`
            },
            {
                value:"CANCELED",
                text:$localize `:@@CANCELED:CANCELED`
            },
        ]
    }
    getFilterSchema(queueNames, devices, localAETs, remoteAETs, countText){
        return [
            {
                tag:"html-select",
                options:queueNames.map(d=>{
                    return{
                        text:d.description || d.name,
                        value:d.name
                    }
                }),
                filterKey:"queueName",
                showSearchField:true,
                description:$localize `:@@queue_name:Queue Name`,
                placeholder:$localize `:@@queue:Queue`
            },{
                tag:"select",
                options:this.sortValues(),
                filterKey:"orderby",
                description:$localize `:@@sort:Sort`,
                placeholder:$localize `:@@sort:Sort`
            },
            {
                tag:"select",
                options:this.statuses(),
                showStar:true,
                filterKey:"status",
                description:$localize `:@@status:Status`,
                placeholder:$localize `:@@status:Status`
            },{
                tag:"html-select",
                options:devices,
                showStar:true,
                showSearchField:true,
                filterKey:"dicomDeviceName",
                description:$localize `:@@device_name:Device Name`,
                placeholder:$localize `:@@device_name:Device Name`
            },
            {
                tag:"label",
                text:$localize `:@@page_size:Page Size`
            },
            {
                tag:"input",
                type:"number",
                filterKey:"limit",
                description:$localize `:@@limit:Limit`
            },
            {
                tag:"range-picker",
                filterKey:"createdTime",
                description:$localize `:@@created_date:Created Date`
            },
            {
                tag:"range-picker",
                filterKey:"updatedTime",
                description:$localize `:@@updated_date:Updated Date`
            },{
                tag:"input",
                type:"text",
                filterKey:"batchID",
                description:$localize `:@@batch_id:Batch ID`,
                placeholder:$localize `:@@batch_id:Batch ID`
            },{
                    tag:"html-select",
                    options:j4care.mapAetToDropdown(localAETs),
                    showStar:true,
                    showSearchField:true,
                    filterKey:"localAET",
                    placeholder:$localize `:@@localaet:Local AET`,
                    description:$localize `:@@archive_ae_title_to_filter_by:Archive AE Title to filter by`
            }, {
                tag:"html-select",
                options:j4care.mapAetToDropdown(remoteAETs),
                showStar:true,
                showSearchField:true,
                filterKey:"remoteAET",
                placeholder:$localize `:@@remoteaet:Remote AET`,
                description:$localize `:@@remote_ae_title_to_filter_by:Remote AE Title to filter by`
            },
            {
                tag:"dummy"
            },
            {
                tag:"button",
                text:countText,
                id:"count",
                description:$localize `:@@query_only_the_count:Query only the count`
            },
            {
                tag:"button",
                id:"submit",
                text:$localize `:@@SUBMIT:SUBMIT`,
                description:$localize `:@@maximal_number_of_tasks_in_returned_list:Maximal number of tasks in returned list`
            }
        ]
    }

    getTableColumns($this, action, options){
        return [
            new TableSchemaElement({
                type:"index",
                title:"#",
                description:$localize `:@@index:Index`,
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
                            id: 'action-monitoring->queues-single_action',
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
                            id: 'action-monitoring->queues-single_action',
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
                            id: 'action-monitoring->queues-single_action',
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
                            id: 'action-monitoring->queues-single_action',
                            param: 'visible'
                        },
                        showIf:(match) => {
                            return (match.status
                                    && (match.status === 'SCHEDULED'
                                        || match.status === 'SCHEDULED FOR RETRY'
                                        || match.status === 'IN PROCESS'));
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
                            id: 'action-monitoring->queues-single_action',
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
                            id: 'action-monitoring->queues-single_action',
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
                    "queue"
                ],
                this.tableService.getTimeColumnBasedOnFilter(options.filterObject),
                [
                    "processingStartTime_scheduledTime",
                    "processingEndTime_processingStartTime",
                    "LocalAET",
                    "RemoteAET",
                    "status",
                    "failures",
                    "batchID"
                ]
            ))
        ]
    }
}
