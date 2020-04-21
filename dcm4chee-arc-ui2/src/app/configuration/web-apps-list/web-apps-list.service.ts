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
import {map} from "rxjs/operators";
import { loadTranslations } from '@angular/localize';


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
        return this.$http.get("./assets/schema/webApplication.schema.json").pipe(map(schema=>{
          return (<any[]>_.get(schema,"properties.dcmWebServiceClass.items.enum")).map(serviceClass=>{
              return new SelectDropdown(serviceClass,serviceClass);
          });
        }));
    };

    getDevices = () => this.devicesService.getDevices().pipe(map((devices:any)=>{
        return devices.map((device:Device)=>{
            return new SelectDropdown(device.dicomDeviceName,device.dicomDeviceName,device.dicomDeviceDescription);
        })
    }));

    getAes = () => this.aeListService.getAes().pipe(map((aes:any)=>{
        return aes.map((aet:Aet)=>{
            return new SelectDropdown(aet.dicomAETitle,aet.dicomAETitle,aet.dicomDescription);
        })
    }));

    getFilterSchema = (devices, aets, webServiceClasses):FilterSchema => [
        {
            tag:"select",
            filterKey:"dicomDeviceName",
            options:devices,
            showStar:true,
            description:$localize `:@@device_name:Device Name`,
            placeholder:$localize `:@@device_name:Device Name`
        },{
            tag:"input",
            type:"text",
            filterKey:"dicomDescription",
            description:$localize `:@@device_description:Device Description`,
            placeholder:$localize `:@@device_description:Device Description`
        },{
            tag:"input",
            type:"text",
            filterKey:"dcmWebAppName",
            description:$localize `:@@web-apps-list.web_application_name:Web Application Name`,
            placeholder:$localize `:@@web-apps-list.web_application_name:Web Application Name`
        },{
            tag:"input",
            type:"text",
            filterKey:"dcmWebServicePath",
            description:$localize `:@@web-apps-list.web_service_path:Web Service Path`,
            placeholder:$localize `:@@web-apps-list.web_service_path:Web Service Path`
        },{
            tag:"select",
            filterKey:"dcmWebServiceClass",
            options:webServiceClasses,
            showStar:true,
            description:$localize `:@@web-apps-list.web_service_class:Web Service Class`,
            placeholder:$localize `:@@web-apps-list.web_service_class:Web Service Class`
        },{
            tag:"select",
            filterKey:"dicomAETitle",
            options:aets,
            showStar:true,
            description:$localize `:@@web-apps-list.application_entity_title:Application Entity Title`,
            placeholder:$localize `:@@web-apps-list.application_entity_title:Application Entity Title`
        },{
            tag:"input",
            type:"text",
            filterKey:"dcmKeycloakClientID",
            description:$localize `:@@web-apps-list.keycloak_client_id:Keycloak Client ID`,
            placeholder:$localize `:@@web-apps-list.keycloak_client_id:Keycloak Client ID`
        },{
            tag:"input",
            type:"text",
            filterKey:"dicomApplicationCluster",
            description:$localize `:@@application_cluster:Application Cluster`,
            placeholder:$localize `:@@application_cluster:Application Cluster`
        },
        {
            tag:"button",
            text: $localize `:@@SUBMIT:SUBMIT`,
            description:$localize `:@@web-apps-list.get_web_apps:Get Web Apps`
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
                title:$localize `:@@device:Device`,
                header: $localize `:@@device:Device`,
                widthWeight:0.6,
                pathToValue:"dicomDeviceName"
            }),
            new TableSchemaElement({
                type:"value",
                title:$localize `:@@dame:Name`,
                header: $localize `:@@dame:Name`,
                widthWeight:1,
                pathToValue:"dcmWebAppName"
            }),
            new TableSchemaElement({
                type:"value",
                title:$localize `:@@description:Description`,
                header: $localize `:@@description:Description`,
                widthWeight:2,
                pathToValue:"dicomDescription"
            }),
            new TableSchemaElement({
                type:"value",
                title:$localize `:@@services:Services`,
                header: $localize `:@@services:Services`,
                widthWeight:2,
                pathToValue:"dcmWebServiceClass"
            }),
            new TableSchemaElement({
                type:"value",
                title:$localize `:@@urls:URLs`,
                header: $localize `:@@urls:URLs`,
                widthWeight:2,
                pathToValue:"url"
            })
        ];
    }

}
