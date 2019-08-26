import { Injectable } from '@angular/core';
import {FilterSchema, SelectDropdown} from "../../interfaces";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import * as _ from "lodash";
import {AeListService} from "../ae-list/ae-list.service";
import {DevicesService} from "../devices/devices.service";
import {Device} from "../../models/device";
import {Aet} from "../../models/aet";
import {j4care} from "../../helpers/j4care.service";
import {TableSchemaElement} from "../../models/dicom-table-schema-element";


@Injectable()
export class WebAppsListService {

    webAppsUrl = `../webapps`;
    constructor(
        private $http:J4careHttpService,
        private devicesService:DevicesService,
        private aeListService:AeListService
    ) { }

    getWebApps(filter?:any){
        return this.$http.get(`${this.webAppsUrl}/${j4care.param(filter)}`);
    }

    getServiceClasses = () => {
        return this.$http.get("./assets/schema/webApplication.schema.json").map(schema=>{
          return (<any[]>_.get(schema,"properties.dcmWebServiceClass.items.enum")).map(serviceClass=>{
              return new SelectDropdown(serviceClass,serviceClass);
          });
        });
    };

    getDevices = () => this.devicesService.getDevices().map(devices=>{
        return devices.map((device:Device)=>{
            return new SelectDropdown(device.dicomDeviceName,device.dicomDeviceName,device.dicomDeviceDescription);
        })
    });

    getAes = () => this.aeListService.getAes().map(aes=>{
        return aes.map((aet:Aet)=>{
            return new SelectDropdown(aet.dicomAETitle,aet.dicomAETitle,aet.dicomDescription);
        })
    });

    getFilterSchema = (devices, aets, webServiceClasses):FilterSchema => [
        {
            tag:"select",
            filterKey:"dicomDeviceName",
            options:devices,
            showStar:true,
            description:"Device Name",
            placeholder:"Device Name"
        },{
            tag:"input",
            type:"text",
            filterKey:"dicomDescription",
            description:"Device Description",
            placeholder:"Device Description"
        },{
            tag:"input",
            type:"text",
            filterKey:"dcmWebAppName",
            description:"Web Application Name",
            placeholder:"Web Application Name"
        },{
            tag:"input",
            type:"text",
            filterKey:"dcmWebServicePath",
            description:"Web Service Path",
            placeholder:"Web Service Path"
        },{
            tag:"select",
            filterKey:"dcmWebServiceClass",
            options:webServiceClasses,
            showStar:true,
            description:"Web Service Class",
            placeholder:"Web Service Class"
        },{
            tag:"select",
            filterKey:"dicomAETitle",
            options:aets,
            showStar:true,
            description:"Application Entity Title",
            placeholder:"Application Entity Title"
        },{
            tag:"input",
            type:"text",
            filterKey:"dcmKeycloakClientID",
            description:"Keycloak Client ID",
            placeholder:"Keycloak Client ID"
        },{
            tag:"input",
            type:"text",
            filterKey:"dicomApplicationCluster",
            description:"Application Cluster",
            placeholder:"Application Cluster"
        },
        {
            tag:"button",
            text:"SUBMIT",
            description:"Get Web Apps"
        }
    ];

    getTableSchema(){
        return [
            new TableSchemaElement({
                type:"index",
                pxWidth:40
            }),
            new TableSchemaElement({
                type:"value",
                title:"Device",
                header:"Device",
                widthWeight:0.6,
                pathToValue:"dicomDeviceName"
            }),
            new TableSchemaElement({
                type:"value",
                title:"Name",
                header:"Name",
                widthWeight:1,
                pathToValue:"dcmWebAppName"
            }),
            new TableSchemaElement({
                type:"value",
                title:"Description",
                header:"Description",
                widthWeight:2,
                pathToValue:"dicomDescription"
            }),
            new TableSchemaElement({
                type:"value",
                title:"Services",
                header:"Services",
                widthWeight:2,
                pathToValue:"dcmWebServiceClass"
            }),
            new TableSchemaElement({
                type:"value",
                title:"URLs",
                header:"URLs",
                widthWeight:2,
                pathToValue:"url"
            })
        ];
    }

}
