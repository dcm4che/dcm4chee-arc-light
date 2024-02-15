import { Injectable } from '@angular/core';
import {j4care} from "../../helpers/j4care.service";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {DevicesService} from "../../configuration/devices/devices.service";
import {AppService} from "../../app.service";
import * as _ from 'lodash-es';
import {DatePipe} from "@angular/common";
import {HttpHeaders} from "@angular/common/http";
import {TableService} from "../../table.service";

@Injectable()
export class StorageVerificationService {
    header = new HttpHeaders({ 'Content-Type': 'application/json' });
    constructor(
      public $http:J4careHttpService,
      public mainservice: AppService,
      private dataPipe:DatePipe,
      private deviceService:DevicesService,
      private tableService:TableService
    ) { }

    statusValues(){
      return [
          {
              value:"SCHEDULED",
              text:$localize `:@@SCHEDULED:SCHEDULED`
          },
          {
              value:"SCHEDULED FOR RETRY",
              text:$localize `:@@S_FOR_RETRY:S. FOR RETRY`,
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
          }
      ];
    }
    getSorageVerifications(filter, batch){
      return this.$http.get(`${j4care.addLastSlash(this.mainservice.baseUrl)}monitor/stgver${(batch?'/batch':'')}?${this.mainservice.param(filter)}`)
          ;
    }
    getSorageVerificationsCount(filter) {
      let filterClone = _.cloneDeep(filter);
      delete filterClone.offset;
      delete filterClone.limit;
      delete filterClone.orderby;
      return this.$http.get(`${j4care.addLastSlash(this.mainservice.baseUrl)}monitor/stgver/count?${this.mainservice.param(filterClone)}`);
    };
    getTableSchema($this, action, options){
      return [
          {
              type:"index",
              title:"#",
              description: $localize `:@@index:Index`,
              widthWeight:0.2,
              calculatedWidth:"4%",
              pxWidth:30
          },{
              type:"buttons",
              title:"",
              buttons:[
                  {
                      icon:{
                          tag:'span',
                          cssClass:'glyphicon glyphicon-th-list',
                          text:''
                      },
                      click:(e)=>{
                          console.log("e",e);
                          e.showAttributes = !e.showAttributes;
                      }
                  },{
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
                      title:$localize `:@@reschedule_this_task:Reschedule this task`
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
                      title:$localize `:@@delete_this_task:Delete this task`
                  }
              ],
              description:$localize `:@@index:Index`,
              widthWeight:0.6,
              calculatedWidth:"6%",
              pxWidth:105
          },
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
                  "StorageID",
                  "StgCmtPolicy",
                  "completed_failed",
                  "status",
                  "failures",
                  "batchID"
              ]
          ))
      ];
    }
    /*{
              type:"model",
              title:$localize `:@@localaet:Local AET`,
              key:"LocalAET",
              description:$localize `:@@localaet:Local AET`,
              widthWeight:0.8,
              calculatedWidth:"20%"
          },
          {
              type:"model",
              title:$localize `:@@batch_id:Batch ID`,
              key:"batchID",
              description:$localize `:@@batch_id:Batch ID`,
              widthWeight:0.8,
              calculatedWidth:"20%"
          },
          {
              type:"model",
              title:$localize `:@@study_instance_uid:Study Instance UID`,
              key:"StudyInstanceUID",
              description:$localize `:@@study_instance_uid:Study Instance UID`,
              widthWeight:2,
              calculatedWidth:"20%"
          },
          {
              type:"model",
              title:$localize `:@@status:Status`,
              key:"status",
              description:$localize `:@@status:Status`,
              widthWeight:0.7,
              calculatedWidth:"20%"
          },
          {
              type:"model",
              title:$localize `:@@storage-verification.storage_policy:Storage Policy`,
              key:"StgCmtPolicy",
              description:$localize `:@@storage-verification.storage_verification_policy:Storage Verification Policy`,
              widthWeight:1,
              calculatedWidth:"20%"
          },
          {
              type:"model",
              title:$localize `:@@storage-verification.outcome_message:Outcome Message`,
              key:"outcomeMessage",
              description:$localize `:@@storage-verification.outcome_message:Outcome Message`,
              widthWeight:4,
              calculatedWidth:"20%"
          }*/
    getTableBatchGroupedColumens(showDetails){
        return [
            {
                type:"index",
                title:"#",
                description:$localize `:@@index:Index`,
                widthWeight:0.2,
                calculatedWidth:"4%"
            },{
                type:"buttons",
                title:"",
                buttons:[
                    {
                        icon:{
                            tag:'span',
                            cssClass:'glyphicon glyphicon-th-list',
                            text:''
                        },
                        click:(e)=>{
                            console.log("e",e);
                            e.showAttributes = !e.showAttributes;
                        }
                    },
                    {
                        icon:{
                            tag:'span',
                            cssClass:'glyphicon glyphicon-list-alt',
                            text:''
                        },
                        click:(e)=>{
                            showDetails.apply(this,[e]);
                        }
                    }
                ],
                description:$localize `:@@index:Index`,
                widthWeight:0.3,
                calculatedWidth:"6%"
            },{
                type:"model",
                title:$localize `:@@batch_id:Batch ID`,
                key:"batchID",
                description:$localize `:@@batch_id:Batch ID`,
                widthWeight:0.4,
                calculatedWidth:"20%"
            },{
                type:"model",
                title:$localize `:@@primary_aet:Primary AET`,
                key:"PrimaryAET",
                description:$localize `:@@aet_primary_c_find_scp:AE Title of the primary C-FIND SCP`,
                widthWeight:1,
                calculatedWidth:"20%"
            },{
                type:"model",
                title:$localize `:@@secondary_aet:Secondary AET`,
                key:"SecondaryAET",
                description:$localize `:@@ae_title_of_the_secondary_c_find_scp:AE Title of the secondary C-FIND SCP`,
                widthWeight:1,
                modifyData:(data)=> data.join(', ') || data,
                calculatedWidth:"20%",
                cssClass:"hideOn1100px"
            },{
                type:"model",
                title:$localize `:@@scheduled_time_range:Scheduled Time Range`,
                key:"scheduledTimeRange",
                description:$localize `:@@scheduled_time_range:Scheduled Time Range`,
                modifyData:(data)=> this.stringifyRangeArray(data),
                widthWeight:1.4,
                calculatedWidth:"20%",
                cssClass:"hideOn1100px"
            },{
                type:"model",
                title:$localize `:@@processing_start_time_range:Processing Start Time Range`,
                key:"processingStartTimeRange",
                description:$localize `:@@processing_start_time_range:Processing Start Time Range`,
                widthWeight:1.4,
                modifyData:(data)=> this.stringifyRangeArray(data),
                calculatedWidth:"20%",
                cssClass:"hideOn1100px"
            },{
                type:"model",
                title:$localize `:@@processing_end_time_range:Processing end time range`,
                key:"processingEndTimeRange",
                description:$localize `:@@processing_end_time_range:Processing end time range`,
                modifyData:(data)=> this.stringifyRangeArray(data),
                widthWeight:1.4,
                calculatedWidth:"20%",
                cssClass:"hideOn1100px"
            },{
                type:"progress",
                title:$localize `:@@tasks:Tasks`,
                description:$localize `:@@tasks:Tasks`,
                key:"tasks",
                diffMode:false,
                widthWeight:2,
                calculatedWidth:"30%",
                cssClass:"hideOn800px"
            }
        ];
    }
  getFilterSchema(devices, localAET, countText){
    return [
        {
            tag:"html-select",
            options:devices,
            showStar:true,
            showSearchField:true,
            filterKey:"dicomDeviceName",
            description:$localize `:@@device_name_to_filter_by:Device Name to filter by`,
            placeholder:$localize `:@@device_name:Device Name`
        },
        {
            tag:"html-select",
            options:localAET,
            showStar:true,
            showSearchField:true,
            filterKey:"LocalAET",
            description:$localize `:@@archive_ae_title_to_filter_by:Archive AE Title to filter by`,
            placeholder:$localize `:@@archive_ae_title:Archive AE Title`
        },
        {
            tag:"input",
            type:"text",
            filterKey:"StudyInstanceUID",
            description:$localize `:@@unique_identifier_of_the_study_to_filter_by:Unique Identifier of the Study to filter by`,
            placeholder:$localize `:@@study_instance_uid:Study Instance UID`
        },
        {
            tag:"select",
            options:this.statusValues(),
            filterKey:"status",
            showStar:true,
            description:$localize `:@@status_of_tasks_to_filter_by:Status of tasks to filter by`,
            placeholder:$localize `:@@status:Status`
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
        },
        {
            tag:"input",
            type:"text",
            filterKey:"batchID",
            description:$localize `:@@batch_id:Batch ID`,
            placeholder:$localize `:@@batch_id:Batch ID`
        },        {
            tag:"select",
            options:[
                {
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
            tag:"label",
            text:$localize `:@@limit:Limit`
        },
        {
            tag:"input",
            type:"number",
            filterKey:"limit",
            description:$localize `:@@maximal_number_of_tasks_in_returned_list:Maximal number of tasks in returned list`
        },

        {
            tag:"button",
            id:"count",
            text:countText,
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
    stringifyRangeArray(data){
        try{
            return `${this.dataPipe.transform(data[0],'yyyy.MM.dd HH:mm:ss')} - ${this.dataPipe.transform(data[0],'yyyy.MM.dd HH:mm:ss')}`
        }catch (e){
            return data;
        }
    }
    cancelAll(filter){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.post(`${j4care.addLastSlash(this.mainservice.baseUrl)}monitor/stgver/cancel${urlParam}`, {}, this.header)
            ;
    }
    cancel = (taskID) => this.$http.post(`${j4care.addLastSlash(this.mainservice.baseUrl)}monitor/stgver/${taskID}/cancel`, {});

    rescheduleAll(filter){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.post(`${j4care.addLastSlash(this.mainservice.baseUrl)}monitor/stgver/reschedule${urlParam}`, {}, this.header)
            ;
    }
    reschedule(taskID, filters?){
        let urlParam = this.mainservice.param(filters);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.post(`${j4care.addLastSlash(this.mainservice.baseUrl)}monitor/stgver/${taskID}/reschedule${urlParam}`, {});
    }
    deleteAll(filter){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.delete(`${j4care.addLastSlash(this.mainservice.baseUrl)}monitor/stgver${urlParam}`, this.header)
            ;
    }

    delete = (taskID)=> this.$http.delete(`${j4care.addLastSlash(this.mainservice.baseUrl)}monitor/stgver/${taskID}`);

    getDevices = ()=> this.deviceService.getDevices();

    scheduleStorageVerification  = (param, aet) => this.$http.post(`${j4care.addLastSlash(this.mainservice.baseUrl)}aets/${aet}/stgver/studies?${this.mainservice.param(param)}`,{});

    getUniqueID = () => this.mainservice.getUniqueID();
}
