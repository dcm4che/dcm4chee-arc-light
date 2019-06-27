import { Injectable } from '@angular/core';
import {Http} from '@angular/http';
import {AppService} from '../../app.service';
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {WindowRefService} from "../../helpers/window-ref.service";
import {j4care} from "../../helpers/j4care.service";

@Injectable()
export class StorageCommitmentService {


    constructor(public $http:J4careHttpService, public mainservice: AppService) {
    }

    statusValue = [
        {
            value:"PENDING",
            text:"PENDING"
        },{
            value:"COMPLETED",
            text:"COMPLETED"
        },{
            value:"WARNING",
            text:"WARNING"
        },{
            value:"FAILED",
            text:"FAILED"
        }
    ];
    search(filters, offset) {
        return this.$http.get('../stgcmt' + '?' + this.mainservice.param(this.queryParams(filters, offset))).map(res => j4care.redirectOnAuthResponse(res));
    };
    queryParams(filters, offset) {
/*                var params = {
         offset: (offset && offset != '') ? offset : 0,
         limit: limit,
         status:undefined
         }*/
        let filter = Object.assign({},filters);
        filter.limit++;
        filter.offset = (offset && offset != '') ? offset : 0;
        if (filter.status && filter.status === '*'){
            delete filter.status;
        }
        if (filter.ExporterID && filter.ExporterID === '*'){
            delete filter.ExporterID;
        }
        return filter;
    }
    flush(status, before) {
        let urlParam = this.mainservice.param({
            status: status,
            updatedBefore: before
        });
        return this.$http.delete('../stgcmt' + '?' + urlParam);
    };
    // cancel(pk){
    //     return this.$http.post("../monitor/export/"+pk+"/cancel",{});
    // }
    delete(pk){
        return this.$http.delete('../stgcmt/' + pk);
    }
    // reschedule(pk, exporterID){
    //     return this.$http.post("../monitor/export/"+pk+"/reschedule/"+exporterID,{});
    // }

    getExporters = () => this.$http.get('../export').map(res => j4care.redirectOnAuthResponse(res));

    getFiltersSchema(exporters){
        return [
            [
                [
                    {
                        tag:"select",
                        options:exporters.map(d=>{
                            return{
                                text:d.description || d.id,
                                value:d.id
                            }
                        }),
                        showStar:true,
                        filterKey:"ExporterID",
                        description:"Exporter ID",
                        placeholder:"Exporter ID"
                    },
                    {
                        tag:"select",
                        options:this.statusValue,
                        filterKey:"status",
                        showStar:true,
                        description:"Status of tasks to filter by",
                        placeholder:"Status"
                    }
                ],
                [
                    {
                        tag:"label",
                        text:"Page Size"
                    },
                    {
                        tag:"input",
                        type:"number",
                        filterKey:"limit",
                        description:"Page Size"
                    }
                ]
            ],[
                [
                    {
                        tag:"input",
                        type:"text",
                        filterKey:"StudyUID",
                        description:"Study Instance UID",
                        placeholder:"Study Instance UID"
                    },{
                        tag:"input",
                        type:"text",
                        filterKey:"batchID",
                        description:"Batch ID",
                        placeholder:"Batch ID"
                    }
                ],
                [
                    {
                        tag:"button",
                        id:"submit",
                        text:"SUBMIT",
                        description:"Get Storage commitments"
                    },{
                        tag:"dummy"
                    }
                ]

            ]
        ]
    }
}
