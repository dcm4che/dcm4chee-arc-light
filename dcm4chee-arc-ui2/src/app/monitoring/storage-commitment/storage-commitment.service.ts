import { Injectable } from '@angular/core';
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
            text:$localize `:@@PENDING:PENDING`
        },{
            value:"COMPLETED",
            text:$localize `:@@COMPLETED:COMPLETED`
        },{
            value:"WARNING",
            text:$localize `:@@WARNING:WARNING`
        },{
            value:"FAILED",
            text:$localize `:@@FAILED:FAILED`
        }
    ];
    search(filters, offset) {
        return this.$http.get('../stgcmt' + '?' + this.mainservice.param(this.queryParams(filters, offset)));
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

    getExporters = () => this.$http.get('../export');

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
                        description:$localize `:@@storage-commitment.exporter_id:Exporter ID`,
                        placeholder:$localize `:@@storage-commitment.exporter_id:Exporter ID`
                    },
                    {
                        tag:"select",
                        options:this.statusValue,
                        filterKey:"status",
                        showStar:true,
                        description:$localize `:@@storage-commitment.status_of_tasks_to_filter_by:Status of tasks to filter by`,
                        placeholder:$localize `:@@status:Status`
                    }
                ],
                [
                    {
                        tag:"label",
                        text:$localize `:@@storage-commitment.page_size:Page Size`
                    },
                    {
                        tag:"input",
                        type:"number",
                        filterKey:"limit",
                        description:$localize `:@@storage-commitment.page_size:Page Size`
                    }
                ]
            ],[
                [
                    {
                        tag:"input",
                        type:"text",
                        filterKey:"StudyUID",
                        description:$localize `:@@storage-commitment.study_instance_uid:Study Instance UID`,
                        placeholder:$localize `:@@storage-commitment.study_instance_uid:Study Instance UID`
                    },{
                        tag:"input",
                        type:"text",
                        filterKey:"batchID",
                        description:$localize `:@@storage-commitment.batch_id:Batch ID`,
                        placeholder:$localize `:@@storage-commitment.batch_id:Batch ID`
                    }
                ],
                [
                    {
                        tag:"button",
                        id:"submit",
                        text:$localize `:@@SUBMIT:SUBMIT`,
                        description:$localize `:@@storage-commitment.get_storage_commitments:Get Storage commitments`
                    },{
                        tag:"dummy"
                    }
                ]

            ]
        ]
    }
}
