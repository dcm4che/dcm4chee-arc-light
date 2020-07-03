import { Injectable } from '@angular/core';
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {AppService} from "../../app.service";
import {DevicesService} from "../../configuration/devices/devices.service";
import * as _ from 'lodash-es';
import {j4care} from "../../helpers/j4care.service";
import {HttpHeaders} from "@angular/common/http";

@Injectable()
export class RetrieveMonitoringService {

    header = new HttpHeaders({ 'Content-Type': 'application/json' });
    constructor(
      public $http:J4careHttpService,
      public mainservice: AppService,
      private deviceService:DevicesService
    ) { }

    getExternalRetrieveEntries(filter, offset, batch){
        filter.offset = (offset && offset != '') ? offset : 0;
        return this.$http.get(`../monitor/retrieve${(batch?'/batch':'')}?${this.mainservice.param(filter)}`)
            ;
    };
    getCount(filter) {
        let filterClone = _.cloneDeep(filter);
            delete filterClone.offset;
            delete filterClone.limit;
            delete filterClone.orderby;
        return this.$http.get('../monitor/retrieve/count' + '?' + this.mainservice.param(filterClone))
            ;
    };
    getExporters(){
      return this.$http.get('../export')
          ;
    }
    delete(pk){
        return this.$http.delete('../monitor/retrieve/' + pk);
    }
    deleteAll(filter){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.delete(`../monitor/retrieve${urlParam}`, this.header)
            ;
    }
    reschedule(pk, data){
        return this.$http.post(`../monitor/retrieve/${pk}/reschedule${j4care.param(data)}`, {});
    }
    rescheduleAll(filter){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.post(`../monitor/retrieve/reschedule${urlParam}`, {}, this.header)
            ;
    }
    cancel(pk){
        return this.$http.post('../monitor/retrieve/' + pk + '/cancel', {});
    }

    cancelAll(filter){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.post(`../monitor/retrieve/cancel${urlParam}`, {}, this.header)
            ;
    }
    downloadCsv(filter){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        // let header = new Headers({ 'Content-Type': 'text/csv' });
        let header = new HttpHeaders({ 'Accept': 'text/csv' });
        return this.$http.get(`/dcm4chee-arc/monitor/retrieve${urlParam}`, header)
    }

    statusValues(){
        return [
            {
                value:"TO SCHEDULE",
                text:$localize `:@@to_schedule:TO SCHEDULE`
            },{
                value:"SCHEDULED",
                text:$localize `:@@SCHEDULED:SCHEDULED`
            },
            {
                value:$localize `IN PROCESS`,
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
            }
        ];
    }
    getFilterSchema(localAET,destinationAET,remoteAET,devices, countText, queueNames){
        let destinationAet:any = {};
        if(destinationAET){
            destinationAet = {
                tag:"select",
                options:destinationAET,
                showStar:true,
                filterKey:"DestinationAET",
                placeholder:$localize `:@@destination_aet:Destination AET`,
                description:$localize `:@@destination_ae_title_to_filter_by:Destination AE Title to filter by`
            };
        }else{
            destinationAet = {
                tag:"input",
                type:"text",
                filterKey:"DestinationAET",
                placeholder:$localize `:@@destination_aet:Destination AET`,
                description:$localize `:@@destination_ae_title_to_filter_by:Destination AE Title to filter by`
            }
        }
    return [
        [
                [
                    {
                        tag:"label",
                        text:$localize `:@@device_name:Device name`
                    },
                    {
                        tag:"select",
                        options:devices,
                        showStar:true,
                        filterKey:"dicomDeviceName",
                        description:$localize `:@@device_name_to_filter_by:Device Name to filter by`
                    }
                ],[
                    {
                        tag:"label",
                        text:$localize `:@@localaet:Local AET`
                    },{
                        tag:"select",
                        options:localAET,
                        showStar:true,
                        filterKey:"LocalAET",
                        description:$localize `:@@archive_ae_title_to_filter_by:Archive AE Title to filter by`
                    }
                ],
                [
                    {
                        tag:"label",
                        text:$localize `:@@remoteaet:Remote AET`
                    },
                    {
                        tag:"select",
                        options:remoteAET,
                        showStar:true,
                        filterKey:"RemoteAET",
                        description:$localize `:@@c_move_scp_aet_filter:C-MOVE SCP AE Title to filter by`
                    }
                ]
            ],[
                [
                    {
                        tag:"multi-select",
                        options:queueNames,
                        filterKey:"dcmQueueName",
                        description:$localize `:@@queue_name:Queue Name`,
                        showSearchField:true,
                        showStar:true,
                        placeholder:$localize `:@@queue_name:Queue Name`
                    },
                    destinationAet
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
                                text:$localize `:@@sort_by_creation_time_asc:Sort by creation time (ASC)`
                            },
                            {
                                value:'-createdTime',
                                text:$localize `:@@sort_by_creation_time_desc:Sort by creation time (DESC)`
                            },
                            {
                                value:'updatedTime',
                                text:$localize `:@@sort_by_updated_time_asc:Sort by updated time (ASC)`
                            },
                            {
                                value:'-updatedTime',
                                text:$localize `:@@sort_by_updated_time_desc:Sort by updated time (DESC)`
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
    getQueueNames(){
        return this.$http.get('../queue');
    }
    getDevices(){
        return this.deviceService.getDevices()
    }
}
