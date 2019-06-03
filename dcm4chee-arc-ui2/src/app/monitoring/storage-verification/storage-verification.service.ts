import { Injectable } from '@angular/core';
import {j4care} from "../../helpers/j4care.service";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {DevicesService} from "../../configuration/devices/devices.service";
import {AppService} from "../../app.service";
import * as _ from 'lodash';
import {DatePipe} from "@angular/common";
import {Headers} from "@angular/http";
import {HttpHeaders} from "@angular/common/http";

@Injectable()
export class StorageVerificationService {
    header = new HttpHeaders({ 'Content-Type': 'application/json' });
    constructor(
      public $http:J4careHttpService,
      public mainservice: AppService,
      private dataPipe:DatePipe,
      private deviceService:DevicesService
    ) { }

    statusValues(){
      return [
          {
              value:"SCHEDULED",
              text:"SCHEDULED"
          },
          {
              value:"IN PROCESS",
              text:"IN PROCESS"
          },
          {
              value:"COMPLETED",
              text:"COMPLETED"
          },
          {
              value:"WARNING",
              text:"WARNING"
          },
          {
              value:"FAILED",
              text:"FAILED"
          },
          {
              value:"CANCELED",
              text:"CANCELED"
          }
      ];
    }
    getSorageVerifications(filter, batch){
      return this.$http.get(`../monitor/stgver${(batch?'/batch':'')}?${this.mainservice.param(filter)}`)
          .map(res => j4care.redirectOnAuthResponse(res));
    }
    getSorageVerificationsCount(filter) {
      let filterClone = _.cloneDeep(filter);
      delete filterClone.offset;
      delete filterClone.limit;
      delete filterClone.orderby;
      return this.$http.get('../monitor/stgver/count' + '?' + this.mainservice.param(filterClone))
          .map(res => j4care.redirectOnAuthResponse(res));
    };
    getTableSchema($this, action){
      return [
          {
              type:"index",
              title:"#",
              description:"Index",
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
                      title:'Cancel this task'
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
                      title:'Reschedule this task'
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
                      title:'Delete this task'
                  }
              ],
              description:"Index",
              widthWeight:0.6,
              calculatedWidth:"6%"
          },
          {
              type:"model",
              title:"Local AET",
              key:"LocalAET",
              description:"Local AET",
              widthWeight:0.8,
              calculatedWidth:"20%"
          },
          {
              type:"model",
              title:"Batch ID",
              key:"batchID",
              description:"Batch ID",
              widthWeight:0.8,
              calculatedWidth:"20%"
          },
          {
              type:"model",
              title:"Study Instance UID",
              key:"StudyInstanceUID",
              description:"Study Instance UID",
              widthWeight:2,
              calculatedWidth:"20%"
          },
          {
              type:"model",
              title:"Status",
              key:"status",
              description:"Status",
              widthWeight:0.7,
              calculatedWidth:"20%"
          },
          {
              type:"model",
              title:"Storage Policy",
              key:"StgCmtPolicy",
              description:"Storage Verification Policy",
              widthWeight:1,
              calculatedWidth:"20%"
          },
          {
              type:"model",
              title:"Outcome Message",
              key:"outcomeMessage",
              description:"Outcome Message",
              widthWeight:4,
              calculatedWidth:"20%"
          }
      ];
    }
    getTableBatchGroupedColumens(showDetails){
        return [
            {
                type:"index",
                title:"#",
                description:"Index",
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
                description:"Index",
                widthWeight:0.3,
                calculatedWidth:"6%"
            },{
                type:"model",
                title:"Batch ID",
                key:"batchID",
                description:"Batch ID",
                widthWeight:0.4,
                calculatedWidth:"20%"
            },{
                type:"model",
                title:"Primary AET",
                key:"PrimaryAET",
                description:"AE Title of the primary C-FIND SCP",
                widthWeight:1,
                calculatedWidth:"20%"
            },{
                type:"model",
                title:"Secondary AET",
                key:"SecondaryAET",
                description:"AE Title of the secondary C-FIND SCP",
                widthWeight:1,
                modifyData:(data)=> data.join(', ') || data,
                calculatedWidth:"20%",
                cssClass:"hideOn1100px"
            },{
                type:"model",
                title:"Scheduled time range",
                key:"scheduledTimeRange",
                description:"Scheduled time range",
                modifyData:(data)=> this.stringifyRangeArray(data),
                widthWeight:1.4,
                calculatedWidth:"20%",
                cssClass:"hideOn1100px"
            },{
                type:"model",
                title:"Processing start time range",
                key:"processingStartTimeRange",
                description:"Processing start time range",
                widthWeight:1.4,
                modifyData:(data)=> this.stringifyRangeArray(data),
                calculatedWidth:"20%",
                cssClass:"hideOn1100px"
            },{
                type:"model",
                title:"Processing end time range",
                key:"processingEndTimeRange",
                description:"Processing end time range",
                modifyData:(data)=> this.stringifyRangeArray(data),
                widthWeight:1.4,
                calculatedWidth:"20%",
                cssClass:"hideOn1100px"
            },{
                type:"progress",
                title:"Tasks",
                description:"Tasks",
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
            tag:"select",
            options:devices,
            showStar:true,
            filterKey:"dicomDeviceName",
            description:"Device Name to filter by",
            placeholder:"Device Name"
        },
        {
            tag:"select",
            options:localAET,
            showStar:true,
            filterKey:"LocalAET",
            description:"Archive AE Title to filter by",
            placeholder:"Archive AE Title"
        },
        {
            tag:"input",
            type:"text",
            filterKey:"StudyInstanceUID",
            description:"Unique Identifier of the Study to filter by",
            placeholder:"Study Instance UID"
        },
        {
            tag:"select",
            options:this.statusValues(),
            filterKey:"status",
            showStar:true,
            description:"Status of tasks to filter by",
            placeholder:"Status"
        },
        {
            tag:"range-picker",
            filterKey:"createdTime",
            description:"Created Date"
        },
        {
            tag:"range-picker",
            filterKey:"updatedTime",
            description:"Updated Date"
        },
        {
            tag:"input",
            type:"text",
            filterKey:"batchID",
            description:"Batch ID",
            placeholder:"Batch ID"
        },        {
            tag:"select",
            options:[
                {
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
            tag:"label",
            text:"Limit"
        },
        {
            tag:"input",
            type:"number",
            filterKey:"limit",
            description:"Maximal number of tasks in returned list"
        },

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
        return this.$http.post(`../monitor/stgver/cancel${urlParam}`, {}, this.header)
            .map(res => j4care.redirectOnAuthResponse(res));
    }
    cancel = (pk) => this.$http.post(`../monitor/stgver/${pk}/cancel`, {});

    rescheduleAll(filter){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.post(`../monitor/stgver/reschedule${urlParam}`, {}, this.header)
            .map(res => j4care.redirectOnAuthResponse(res));
    }
    reschedule(pk, filters?){
        let urlParam = this.mainservice.param(filters);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.post(`../monitor/stgver/${pk}/reschedule${urlParam}`, {});
    }
    deleteAll(filter){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.delete(`../monitor/stgver${urlParam}`, this.header)
            .map(res => j4care.redirectOnAuthResponse(res));
    }

    delete = (pk)=> this.$http.delete('../monitor/stgver/' + pk);

    getDevices = ()=> this.deviceService.getDevices();

    scheduleStorageVerification  = (param, aet) => this.$http.post(`../aets/${aet}/stgver/studies?${this.mainservice.param(param)}`,{});

    getUniqueID = () => this.mainservice.getUniqueID();
}
