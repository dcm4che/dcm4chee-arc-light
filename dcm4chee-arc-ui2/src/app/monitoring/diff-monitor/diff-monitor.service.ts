import { Injectable } from '@angular/core';
import {DevicesService} from "../../configuration/devices/devices.service";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {j4care} from "../../helpers/j4care.service";
import {AppService} from "../../app.service";
import {DatePipe} from "@angular/common";
import {Router} from "@angular/router";
import {HttpHeaders} from "@angular/common/http";

@Injectable()
export class DiffMonitorService {

    header = new HttpHeaders({ 'Content-Type': 'application/json' });
    constructor(
        private deviceService: DevicesService,
        private mainservice:AppService,
        private $http:J4careHttpService,
        private dataPipe:DatePipe,
        private router:Router
    ) { }

    statusValues(){
        return [
            {
                value:"SCHEDULED",
                text:$localize `:@@SCHEDULED:SCHEDULED`
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

  getFormSchema(aes, aets, countText, devices){
      return [
          {
              tag:"select",
              options:devices,
              showStar:true,
              filterKey:"dicomDeviceName",
              description:$localize `:@@device_name:Device Name`,
              placeholder:$localize `:@@device_name:Device Name`
          },{
              tag:"select",
              options:aets,
              showStar:true,
              filterKey:"LocalAET",
              description:$localize `:@@local_aet:Local AET`,
              placeholder:$localize `:@@local_aet:Local AET`
          },{
              tag:"select",
              options:aes,
              showStar:true,
              filterKey:"PrimaryAET",
              description:$localize `:@@primary_aet:Primary AET`,
              placeholder:$localize `:@@primary_aet:Primary AET`
          },
          {
              tag:"select",
              options:aes,
              showStar:true,
              filterKey:"SecondaryAET",
              description:$localize `:@@secondary_aet:Secondary AET`,
              placeholder:$localize `:@@secondary_aet:Secondary AET`
          },
          {
              tag:"checkbox",
              filterKey:"checkMissing",
              description:$localize `:@@check_missing:Check Missing`,
              text:$localize `:@@check_missing:Check Missing`
          },{
              tag:"checkbox",
              filterKey:"checkDifferent",
              description:$localize `:@@check_different:Check Different`,
              text:$localize `:@@check_different:Check Different`
          },
          {
              tag:"input",
              type:"text",
              filterKey:"comparefield",
              description:$localize `:@@compare_field:Compare field`,
              placeholder:$localize `:@@compare_field:Compare field`
          },{
              tag:"select",
              options:this.statusValues(),
              filterKey:"status",
              showStar:true,
              description:$localize `:@@status_of_tasks_to_filter_by:Status of tasks to filter by`,
              placeholder: $localize `:@@status:Status`
          },
          {
              tag:"range-picker",
              filterKey:"createdTime",
              description:$localize `:@@created_date:Created Date`
          },{
              tag:"range-picker",
              filterKey:"updatedTime",
              description:$localize `:@@created_date:Created Date`
          },
          {
              tag:"input",
              type:"text",
              filterKey:"batchID",
              description:$localize `:@@batch_id:Batch ID`,
              placeholder:$localize `:@@batch_id:Batch ID`
          },{
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
              tag:"label",
              text:$localize `:@@page_size:Page Size`
          },{
              tag:"input",
              type:"number",
              filterKey:"limit",
              description:$localize `:@@page_size:Page Size`,
              placeholder:$localize `:@@page_size:Page Size`
          },
          {
              tag:"button",
              text:countText,
              id:"count",
              description:$localize `:@@get_count:Get Count`
          },
          {
              tag:"button",
              text:$localize `:@@search:Search`,
              id:"search",
              description:$localize `:@@search_patients:Search Patients`
          }
      ];
  }

    getTableColumens($this, action){
        return [
            {
                type:"index",
                title:"#",
                description:$localize `:@@index:Index`,
                widthWeight:0.1,
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
                        },
                        title:$localize `:@@show_details:Show details`
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
                        title:$localize `:@@cancel_this_task:Cancel this task`
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
                    },
                    {
                        icon:{
                            tag:'span',
                            cssClass:'glyphicon glyphicon-eye-open',
                            text:''
                        },
                        click:(e)=>{
                            this.router.navigate(['./studies'],{
                                queryParams:{
                                    batchID:e.batchID,
                                    pk:e.pk,
                                    mode:"diff"
                                }
                            })
                            // e.showAttributes = !e.showAttributes;
                        }
                    }
                ],
                description:$localize `:@@index:Index`,
                widthWeight:0.3,
                calculatedWidth:"6%"
            },{
                type:"model",
                title:$localize `:@@primary_aet:Primary AET`,
                key:"PrimaryAET",
                description: $localize `:@@aet_primary_c_find_scp:AE Title of the primary C-FIND SCP`,
                widthWeight:1,
                calculatedWidth:"20%"
            },{
                type:"model",
                title:$localize `:@@secondary_aet:Secondary AET`,
                key:"SecondaryAET",
                description: $localize `:@@ae_title_of_the_secondary_c_find_scp:AE Title of the secondary C-FIND SCP`,
                widthWeight:1,
                calculatedWidth:"20%",
                cssClass:"hideOn1100px"
            },{
                type:"model",
                title:$localize `:@@compare_field:Compare field`,
                key:"comparefield",
                description:$localize `:@@compare_attribute_set_id:Compare attribute set id`,
                widthWeight:1,
                calculatedWidth:"20%",
                cssClass:"hideOn1100px"
            },{
                type:"model",
                title:$localize `:@@status:Status`,
                key:"status",
                description:$localize `:@@status_of_tasks:Status of tasks`,
                widthWeight:1,
                calculatedWidth:"20%"
            },{
                type:"model",
                title:$localize `:@@batch_id:Batch ID`,
                key:"batchID",
                description:$localize `:@@batch_id:Batch ID`,
                widthWeight:1,
                calculatedWidth:"20%"
            },{
                type:"model",
                title:$localize `:@@created_time:Created time`,
                key:"createdTime",
                description:$localize `:@@list_compare_studies_tasks_which_were_created_between:list Compare Studies Tasks which were created between`,
                widthWeight:1,
                calculatedWidth:"20%",
                cssClass:"hideOn800px"
            },{
                type:"model",
                title:$localize `:@@updated_time:Updated time`,
                key:"updatedTime",
                description:$localize `:@@list_compare_studies_tasks_which_were_updated_between:list Compare Studies Tasks which were updated between`,
                widthWeight:1,
                calculatedWidth:"20%",
                cssClass:"hideOn800px"
            }
        ];
    }
    getTableBatchGroupedColumens(showDetails){
        return [
            {
                type:"index",
                title:"#",
                description:$localize `:@@index:Index`,
                widthWeight:0.1,
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
                title:$localize `:@@scheduled_time_range:Scheduled time range`,
                key:"scheduledTimeRange",
                description:$localize `:@@scheduled_time_range:Scheduled time range`,
                modifyData:(data)=> this.stringifyRangeArray(data),
                widthWeight:1.4,
                calculatedWidth:"20%",
                cssClass:"hideOn1100px"
            },{
                type:"model",
                title:$localize `:@@processing_start_time_range:Processing start time range`,
                key:"processingStartTimeRange",
                description:$localize `:@@processing_start_time_range:Processing start time range`,
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
                diffMode:true,
                widthWeight:2,
                calculatedWidth:"30%",
                cssClass:"hideOn800px"
            }
        ];
    }
    stringifyRangeArray(data){
        try{
            return `${this.dataPipe.transform(data[0],'yyyy.MM.dd HH:mm:ss')} - ${this.dataPipe.transform(data[0],'yyyy.MM.dd HH:mm:ss')}`
        }catch (e){
            return data;
        }
    }
    getDevices(){
        return this.deviceService.getDevices()
    }
    getDiffTask(filters, batchGrouped){
        let urlParam = this.mainservice.param(filters);
        urlParam = urlParam?`?${urlParam}`:'';
        let url = `../monitor/diff${urlParam}`;
        if(batchGrouped)
            url = `../monitor/diff/batch${urlParam}`;
        return this.$http.get(url)

    }
    getDiffTasksCount(filters){
        let urlParam = this.mainservice.param(filters);
        urlParam = urlParam?`?${urlParam}`:'';
        let url = `../monitor/diff/count${urlParam}`;
        return this.$http.get(url)
    }

    cancelAll(filter){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.post(`../monitor/diff/cancel${urlParam}`, {}, this.header)
    }
    cancel(pk){
        return this.$http.post(`../monitor/diff/${pk}/cancel`, {});
    }
    rescheduleAll(filter){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.post(`../monitor/diff/reschedule${urlParam}`, {}, this.header)
    }
    reschedule(pk, data){
        return this.$http.post(`../monitor/diff/${pk}/reschedule`, data);
    }
    deleteAll(filter){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.delete(`../monitor/diff${urlParam}`, this.header)
    }
    delete(pk){
        return this.$http.delete('../monitor/diff/' + pk);
    }

}
