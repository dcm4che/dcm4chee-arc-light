import { Injectable } from '@angular/core';
import {j4care} from "../../helpers/j4care.service";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {DevicesService} from "../../devices/devices.service";
import {AppService} from "../../app.service";
import * as _ from 'lodash';

@Injectable()
export class StorageVerificationService {

  constructor(
      public $http:J4careHttpService,
      public mainservice: AppService,
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
  getSorageVerifications(filter, offset, batch){
      filter.offset = (offset && offset != '') ? offset : 0;
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
  getFilterSchema(devices, localAET, countText){
    return [
        {
            tag:"select",
            options:devices,
            showStar:true,
            filterKey:"dicomDeviceName",
            description:"Device Name to filter by"
        },
        {
            tag:"select",
            options:localAET,
            showStar:true,
            filterKey:"LocalAET",
            description:"Archive AE Title to filter by"
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
  getDevices(){
      return this.deviceService.getDevices()
  }
}
