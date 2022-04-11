import { Injectable } from '@angular/core';
import {J4careHttpService} from "../../../helpers/j4care-http.service";
import {AppService} from "../../../app.service";
import {DeviceConfiguratorService} from "../../../configuration/device-configurator/device-configurator.service";
import {map} from "rxjs/operators";

@Injectable({
  providedIn: 'root'
})
export class CreateAeService {

  constructor(
      public $http:J4careHttpService,
      public appService:AppService,
      private deviceConfigurator:DeviceConfiguratorService
  ) { }

  getPrimaryDeviceType(){
      return this.deviceConfigurator.getSchema('device.schema.json').pipe(map((res:any)=>{
          try{
              return res.properties.dicomPrimaryDeviceType.items.enum;
          }catch (e){
              console.log("could not extract from schema",e);
              return [
                  "ARCHIVE",
                  "COMP",
                  "CAD",
                  "DSS",
                  "FILMD",
                  "M3D",
                  "MCD",
                  "PRINT",
                  "CAPTURE",
                  "LOG",
                  "RT",
                  "WSD",
                  "AR",
                  "BMD",
                  "BDUS",
                  "EPS",
                  "CR",
                  "CT",
                  "DX",
                  "ECG",
                  "ES",
                  "XC",
                  "GM",
                  "HD",
                  "IO",
                  "IVOCT",
                  "IVUS",
                  "KER",
                  "LEN",
                  "MR",
                  "MG",
                  "NM",
                  "OAM",
                  "OCT",
                  "OPM",
                  "OP",
                  "OPR",
                  "OPT",
                  "OPTBSV",
                  "OPTENF",
                  "OPV",
                  "OSS",
                  "PX",
                  "PT",
                  "RF",
                  "RG",
                  "SM",
                  "SRF",
                  "US",
                  "VA",
                  "XA"
              ];
          }
      }));
  }
  getWebAppsSchema(){
      return [
          [
              [
                  {
                      tag:"input",
                      type:"string",
                      filterKey:"limit",
                      description: "Name of the Web Application"
                  },
                  {
                      tag:"multi-checkbox",
                      options:[
                          {
                              text:"Test checkbox1",
                              key:"testkey1"
                          },{
                              text:"Test checkbox2",
                              key:"testkey2"
                          }
                      ],
                      description: "Name of the Web Application"
                  }
              ]

          ]
      ];
  }
  getSchema(){
    return {
        "title": "Web Application",
        "description": "Web Application information",
        "type": "object",
        "required": [
            "dcmWebAppName",
            "dcmWebServicePath",
            "dcmWebServiceClass"
        ],
        "properties": {
            "dcmWebAppName": {
                "title": "Web Application name",
                "description": "Name of the Web Application",
                "type": "string",
                "use": [
                    "$.dicomNetworkAE[*].dcmNetworkAE.dcmArchiveNetworkAE.dcmFallbackWadoURIWebAppName",
                    "$.dcmDevice.dcmArchiveDevice.dcmFallbackWadoURIWebAppName"
                ]
            },
            "dicomNetworkConnectionReference": {
                "title": "Web Application Network Connection(s)",
                "description": "Network Connection(s) on which the services of the Web application are available",
                "type": "array",
                "items": {
                    "type": "string"
                }
            },
            "dicomDescription": {
                "title": "Web Application Description",
                "description": "Unconstrained text description of the Web Application",
                "type": "string"
            },
            "dcmWebServicePath": {
                "title": "Web Service Path",
                "description": "HTTP Path of the services of the Web application",
                "type": "string"
            },
            "dcmWebServiceClass": {
                "title": "Web Service Class",
                "description": "Web Service Classes provided by the Web application",
                "type": "array",
                "items": {
                    "type": "string",
                    "enum": [
                        "QIDO_RS",
                        "STOW_RS",
                        "WADO_RS",
                        "WADO_URI",
                        "UPS_RS",
                        "MWL_RS",
                        "QIDO_COUNT",
                        "DCM4CHEE_ARC",
                        "DCM4CHEE_ARC_AET",
                        "DCM4CHEE_ARC_AET_DIFF",
                        "PAM",
                        "REJECT",
                        "MOVE",
                        "MOVE_MATCHING",
                        "UPS_MATCHING",
                        "ELASTICSEARCH",
                        "XDS_RS",
                        "AGFA_BLOB"
                    ]
                }
            },
            "dcmKeycloakClientID": {
                "title": "Keycloak Client ID",
                "description": "Keycloak Client ID for the Web application",
                "type": "string",
                "format": "dcmKeycloakClient"
            },
            "dicomAETitle": {
                "title": "AE Title",
                "description": "AE title of Network AE associated with this Web Application",
                "type": "string",
                "format": "dcmArchiveAETitle"
            },
            "dicomApplicationCluster": {
                "title": "Application Cluster",
                "description": "Locally defined names for a subset of related applications",
                "type": "array",
                "items": {
                    "type": "string"
                }
            },
            "dcmProperty": {
                "title": "Property",
                "description": "Property in format <name>=<value>. E.g.: roles=<accepted-user-role>[,...], IID_PATIENT_URL=http(s)://<viewer-host>:<viewer-port>/IHEInvokeImageDisplay?requestType=PATIENT&patientID={{patientID}} or IID_STUDY_URL=http(s)://<viewer-host>:<viewer-port>/IHEInvokeImageDisplay?requestType=STUDY&studyUID={{studyUID}}, ( Other valid parameters are: 'patientBirthDate' and 'accessionNumber' ) , you could define the target of the Url by setting it to the parameter 'IID_URL_TARGET=_blank|_self'",
                "type": "array",
                "items": {
                    "type": "string"
                },
                "format": "dcmProperty"
            },
            "dicomInstalled": {
                "title": "installed",
                "description": "True if the Web Application is installed on network. If not present, information about the installed status of the Web Application is inherited from the device",
                "type": "boolean"
            }
        }
    }

  }
}
