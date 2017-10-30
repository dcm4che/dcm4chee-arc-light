import { Component, OnInit } from '@angular/core';
import {AppService} from "../../app.service";
import {SlimLoadingBarService} from "ng2-slim-loading-bar";
import * as _ from 'lodash';

@Component({
  selector: 'external-retrieve',
  templateUrl: './external-retrieve.component.html'
})
export class ExternalRetrieveComponent implements OnInit {
    before;
    localAET;
    remoteAET;
    destinationAET;
    filter;
  constructor(
      public cfpLoadingBar: SlimLoadingBarService,
      public mainservice: AppService
  ) { }

    ngOnInit(){
        this.initCheck(10);
    }
    initCheck(retries){
        let $this = this;
        if(_.hasIn(this.mainservice,"global.authentication")){
            this.init();
        }else{
            if (retries){
                setTimeout(()=>{
                    $this.initCheck(retries-1);
                },20);
            }else{
                this.init();
            }
        }
    }
    init(){
        this.localAET = this.remoteAET = this.destinationAET = [
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
        ];
        this.filter = [
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
                            options:this.localAET,
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
                            options:this.remoteAET,
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
                            options:this.destinationAET,
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
                                    value:"",
                                    text:"*"
                                },
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
                            text:"Page size"
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
    onSubmit(e){
        console.log("in external e",e);
    }
}
