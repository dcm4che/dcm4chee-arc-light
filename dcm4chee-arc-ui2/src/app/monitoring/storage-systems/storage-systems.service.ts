import { Injectable } from '@angular/core';
import {Http} from '@angular/http';
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
            text:"Object Storage"
        },
        {
            value:"dcmMetadataStorageID",
            text:"Metadata Storage"
        },
        {
            value:"dcmSeriesMetadataStorageID",
            text:"SeriesMetadata Storage"
        },
    ];
    uriSchema = [
        {
            text:"file",
            value:"file"
        },{
            text:"jclouds",
            value:"jclouds"
        },{
            text:"emc-ecs-s3",
            value:"emc-ecs-s3"
        },{
            text:"hcp",
            value:"hcp"
        },{
            text:"documentu",
            value:"documentu"
        }
    ];
    search(filters, offset) {
        return this.$http.get('../storage' + '?' + this.mainservice.param(this.queryParams(filters, offset))).map(res => j4care.redirectOnAuthResponse(res));
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
        return this.$http.delete('../stgcmt' + '?' + urlParam);
    };
    delete(pk){
        return this.$http.delete('../stgcmt/' + pk);
    }

    getFiltersSchema(aets){
        return [
            [
                [
                    {
                        tag:"select",
                        options:aets.map(d=>{
                            return{
                                text:d.dicomAETitle,
                                value:d.dicomAETitle
                            }
                        }),
                        showStar:true,
                        filterKey:"dicomAETitle",
                        description:"AETitle",
                        placeholder:"AETitle"
                    },
                    {
                        tag:"select",
                        options:this.uriSchema,
                        showStar:true,
                        filterKey:"uriScheme",
                        description:"Uri Schema",
                        placeholder:"Uri Schema"
                    }
                ],
                [
                    {
                        tag:"select",
                        options:this.usage,
                        showStar:true,
                        filterKey:"usage",
                        description:"Usage",
                        placeholder:"Usage"
                    },
                    {
                        tag:"combined",
                        firstField:{
                            tag:"number",
                            type:"number",
                            min:1,
                            filterKey:"usableSpaceBelow",
                            placeholder:"Usablespace below",
                            title:"Usablespace below"
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
                            placeholder:"Unite",
                            title:"Unite"
                        }
                    }
                ]
            ],[
                [
                    {
                        tag:"input",
                        type:"text",
                        filterKey:"dcmStorageClusterID",
                        placeholder:"Storage Cluster ID",
                        description:"Storage Cluster ID"
                    },
                    {
                        tag:"button",
                        id:"submit",
                        text:"SUBMIT",
                        description:"Get Storage commitments"
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
