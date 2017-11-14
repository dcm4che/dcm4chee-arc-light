import { Injectable } from '@angular/core';
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {WindowRefService} from "../../helpers/window-ref.service";
import {j4care} from "../../helpers/j4care.service";

@Injectable()
export class ExternalRetrieveService {

    constructor(
      public $http:J4careHttpService
    ) { }

    getExternalRetrieveEntries(filter, offset){
        filter.offset = (offset && offset != '') ? offset : 0;
        return this.$http.get('../monitor/retrieve' + j4care.getUrlParams(filter))
            .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})
    };

    getExporters(){
      return this.$http.get('../export')
          .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})

    }
    delete(pk){
        return this.$http.delete('../monitor/retrieve/' + pk);
    }
    reschedule(pk){
        return this.$http.post(`../monitor/retrieve/${pk}/reschedule`, {});
    }
    cancel(pk){
        return this.$http.post('../monitor/retrieve/' + pk + '/cancel', {});
    }
    getFilterSchema(localAET,destinationAET,remoteAET){
    return [
        {
            filter_block:[
                {
                    firstChild:{
                        tag:"label",
                        text:"Device name"
                    },
                    secondChild:{
                        tag:"input",
                        type:"text",
                        filterKey:"dicomDeviceName",
                        description:"Device Name to filter by"
                    }
                },
                {
                    firstChild:{
                        tag:"label",
                        text:"LocalAET"
                    },
                    secondChild:{
                        tag:"select",
                        options:localAET,
                        showStar:true,
                        filterKey:"LocalAET",
                        description:"Archive AE Title to filter by"
                    }
                },
                {
                    firstChild:{
                        tag:"label",
                        text:"RemoteAET"
                    },
                    secondChild:{
                        tag:"select",
                        options:remoteAET,
                        showStar:true,
                        filterKey:"RemoteAET",
                        description:"C-MOVE SCP AE Title to filter by"
                    }
                }
            ]
        },
        {
            filter_block:[
                {
                    firstChild:{
                        tag:"label",
                        text:"DestinationAET"
                    },
                    secondChild:{
                        tag:"select",
                        options:destinationAET,
                        showStar:true,
                        filterKey:"DestinationAET",
                        description:"Destination AE Title to filter by"
                    }
                },
                {
                    firstChild:{
                        tag:"label",
                        text:"StudyInstanceUID"
                    },
                    secondChild:{
                        tag:"input",
                        type:"text",
                        filterKey:"StudyInstanceUID",
                        description:"Unique Identifier of the Study to filter by"
                    }
                },
                {
                    firstChild:{
                        tag:"label",
                        text:"Status"
                    },
                    secondChild:{
                        tag:"select",
                        options:[
                            {
                                value:"TO SCHEDULE",
                                text:"TO SCHEDULE"
                            },
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
                        ],
                        filterKey:"status",
                        showStar:true,
                        description:"Status of tasks to filter by"
                    }
                }
            ]
        },
        {
            filter_block:[
                {
                    firstChild:{
                        tag:"label",
                        text:"Update before"
                    },
                    secondChild:{
                        tag:"p-calendar",
                        filterKey:"updatedBefore",
                        dateFormat:"yy-mm-dd",
                        description:"maximum update date of tasks to filter by. Format: YYYY-MM-DD"
                    }
                },
                {
                    firstChild:{
                        tag:"label",
                        text:"Limit"
                    },
                    secondChild:{
                        tag:"input",
                        type:"number",
                        filterKey:"limit",
                        description:"Maximal number of tasks in returned list"
                    }
                },
                {
                    firstChild:{
                        tag:"dummy"
                    },
                    secondChild:{
                        tag:"button",
                        text:"SUBMIT",
                        description:"Maximal number of tasks in returned list"
                    }
                }
            ]
        }
    ];
    }
}
