import { Injectable } from '@angular/core';
import {AppService} from '../../app.service';
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {j4care} from "../../helpers/j4care.service";

@Injectable()
export class StorageSystemsService {

    constructor(public $http:J4careHttpService, public mainservice: AppService) {
    }
    usage = [
        {
            value:"dcmObjectStorageID",
            text:$localize `:@@storage-systems.object_storage:Object Storage`
        },
        {
            value:"dcmMetadataStorageID",
            text:$localize `:@@storage-systems.metadata_storage:Metadata Storage`
        },
        {
            value:"dcmSeriesMetadataStorageID",
            text:$localize `:@@storage-systems.seriesmetadata_storage:SeriesMetadata Storage`
        },
    ];
    uriSchema = [
        {
            text:$localize `:@@file:file`,
            value:"file"
        },{
            text:$localize `:@@jclouds:jclouds`,
            value:"jclouds"
        },{
            text:$localize `:@@emc-ecs-s3:emc-ecs-s3`,
            value:"emc-ecs-s3"
        },{
            text:$localize `:@@hcp:hcp`,
            value:"hcp"
        },{
            text:$localize `:@@documentum:documentum`,
            value:"documentum"
        }
    ];
    search(filters, offset) {
        return this.$http.get(`${j4care.addLastSlash(this.mainservice.baseUrl)}storage?${this.mainservice.param(this.queryParams(filters, offset))}`);
    };
    queryParams(filters, offset) {
        filters.offset = (offset && offset != '') ? offset : 0;
        if (filters.status && filters.status === '*'){
            delete filters.status;
        }
        if (filters.ExporterID && filters.ExporterID === '*'){
            delete filters.ExporterID;
        }
        return filters;
    }
    flush(status, before) {
        let urlParam = this.mainservice.param({
            status: status,
            updatedBefore: before
        });
        return this.$http.delete(`${j4care.addLastSlash(this.mainservice.baseUrl)}stgcmt?${urlParam}`);
    };
    delete(pk){
        return this.$http.delete(`${j4care.addLastSlash(this.mainservice.baseUrl)}stgcmt/'${pk}`);
    }

    getFiltersSchema(aets){
        return [
            [
                [
                    {
                        tag:"html-select",
                        options:aets.map(d=>{
                            return{
                                text:d.dicomAETitle,
                                value:d.dicomAETitle
                            }
                        }),
                        showStar:true,
                        showSearchField:true,
                        filterKey:"dicomAETitle",
                        description:$localize `:@@aetitle:AETitle`,
                        placeholder:$localize `:@@aetitle:AETitle`
                    },
                    {
                        tag:"select",
                        options:this.uriSchema,
                        showStar:true,
                        filterKey:"uriScheme",
                        description:$localize `:@@storage-systems.uri_schema:Uri Schema`,
                        placeholder:$localize `:@@storage-systems.uri_schema:Uri Schema`
                    }
                ],
                [
                    {
                        tag:"select",
                        options:this.usage,
                        showStar:true,
                        filterKey:"usage",
                        description:$localize `:@@usage:Usage`,
                        placeholder:$localize `:@@usage:Usage`
                    },
                    {
                        tag:"combined",
                        firstField:{
                            tag:"number",
                            type:"number",
                            min:1,
                            filterKey:"usableSpaceBelow",
                            placeholder:$localize `:@@storage-systems.usablespace_below:UsableSpace below`,
                            title:$localize `:@@storage-systems.usablespace_below:UsableSpace below`
                        },
                        secondField:{
                            tag:"select",
                            filterKey:"usableSpaceBelowMode",
                            showStar:false,
                            options:[
                                {"value":"TB",text:"TB"},
                                {"value":"GB",text:"GB"},
                                {"value":"MB",text:"MB"},
                                {"value":"BYTE",text:"Byte"},
                            ],
                            placeholder:$localize `:@@unit:Unit`,
                            title:$localize `:@@unit:Unit`
                        }
                    }
                ]
            ],[
                [
                    {
                        tag:"input",
                        type:"text",
                        filterKey:"dcmStorageClusterID",
                        placeholder:$localize `:@@storage_cluster_id:Storage Cluster ID`,
                        description:$localize `:@@storage_cluster_id:Storage Cluster ID`
                    },
                    {
                        tag:"button",
                        id:"submit",
                        text:$localize `:@@SUBMIT:SUBMIT`,
                        description:$localize `:@@get_storage_commitments:Get Storage commitments`
                    }
                ],[]
            ]
        ]
    }

    getNextStorage(storages:any){
        let grouped = {};
        storages.forEach(storage=>{
            if(j4care.isSetInObject(storage, "dicomAETitle")){
                storage["dicomAETitle"].forEach(aet=>{
                    grouped[aet] = grouped[aet] || [];
                    grouped[aet].push(storage);
                });
            }
        });
    }
}
