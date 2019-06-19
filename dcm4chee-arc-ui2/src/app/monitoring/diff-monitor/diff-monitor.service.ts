import { Injectable } from '@angular/core';
import {DevicesService} from "../../configuration/devices/devices.service";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {j4care} from "../../helpers/j4care.service";
import {AppService} from "../../app.service";
import {DatePipe} from "@angular/common";
import {Router} from "@angular/router";
import {Headers} from "@angular/http";
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

  getFormSchema(aes, aets, countText, devices){
      return [
          {
              tag:"select",
              options:devices,
              showStar:true,
              filterKey:"dicomDeviceName",
              description:"Device Name",
              placeholder:"Device Name"
          },{
              tag:"select",
              options:aets,
              showStar:true,
              filterKey:"LocalAET",
              description:"Local AET",
              placeholder:"Local AET"
          },{
              tag:"select",
              options:aes,
              showStar:true,
              filterKey:"PrimaryAET",
              description:"Primary AET",
              placeholder:"Primary AET"
          },
          {
              tag:"select",
              options:aes,
              showStar:true,
              filterKey:"SecondaryAET",
              description:"Secondary AET",
              placeholder:"Secondary AET"
          },
          {
              tag:"checkbox",
              filterKey:"checkMissing",
              description:"Check Missing",
              text:"Check Missing"
          },{
              tag:"checkbox",
              filterKey:"checkDifferent",
              description:"Check Different",
              text:"Check Different"
          },
          {
              tag:"input",
              type:"text",
              filterKey:"comparefield",
              description:"Compare field",
              placeholder:"Compare field"
          },{
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
          },{
              tag:"range-picker",
              filterKey:"updatedTime",
              description:"Created Date"
          },
          {
              tag:"input",
              type:"text",
              filterKey:"batchID",
              description:"Batch ID",
              placeholder:"Batch ID"
          },{
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
              tag:"label",
              text:"Page Size"
          },{
              tag:"input",
              type:"number",
              filterKey:"limit",
              description:"Page Size",
              placeholder:"Page Size"
          },
          {
              tag:"button",
              text:countText,
              id:"count",
              description:"Get Count"
          },
          {
              tag:"button",
              text:"Search",
              id:"search",
              description:"Search Patients"
          }
      ];
  }

    getTableColumens($this, action){
        return [
            {
                type:"index",
                title:"#",
                description:"Index",
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
                        title:"Show details"
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
                description:"Index",
                widthWeight:0.3,
                calculatedWidth:"6%"
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
                calculatedWidth:"20%",
                cssClass:"hideOn1100px"
            },{
                type:"model",
                title:"Compare field",
                key:"comparefield",
                description:"Compare attribute set id",
                widthWeight:1,
                calculatedWidth:"20%",
                cssClass:"hideOn1100px"
            },{
                type:"model",
                title:"Status",
                key:"status",
                description:"Status of tasks",
                widthWeight:1,
                calculatedWidth:"20%"
            },{
                type:"model",
                title:"Batch ID",
                key:"batchID",
                description:"Batch ID",
                widthWeight:1,
                calculatedWidth:"20%"
            },{
                type:"model",
                title:"Created time",
                key:"createdTime",
                description:"list Compare Studies Tasks which were created between",
                widthWeight:1,
                calculatedWidth:"20%",
                cssClass:"hideOn800px"
            },{
                type:"model",
                title:"Updated time",
                key:"updatedTime",
                description:"list Compare Studies Tasks which were updated between",
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
                description:"Index",
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
