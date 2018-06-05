import { Injectable } from '@angular/core';
import {DevicesService} from "../../devices/devices.service";

@Injectable()
export class DiffMonitorService {

    constructor(
        private deviceService: DevicesService
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
              options:aes,
              showStar:true,
              filterKey:"LocalAET",
              description:"Local AET",
              placeholder:"Local AET"
          },{
              tag:"select",
              options:aets,
              showStar:true,
              filterKey:"PrimaryAET",
              description:"Primary AET",
              placeholder:"Primary AET"
          },
          {
              tag:"select",
              options:aets,
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
    getDevices(){
        return this.deviceService.getDevices()
    }
}
