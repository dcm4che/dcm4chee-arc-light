import { TestBed, inject } from '@angular/core/testing';

import { StudyService } from './study.service';
import {AeListService} from "../../configuration/ae-list/ae-list.service";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {StorageSystemsService} from "../../monitoring/storage-systems/storage-systems.service";
import {DevicesService} from "../../configuration/devices/devices.service";
import {WebAppsListService} from "../../configuration/web-apps-list/web-apps-list.service";
import {RetrieveMonitoringService} from "../../monitoring/external-retrieve/retrieve-monitoring.service";
import {PermissionService} from "../../helpers/permissions/permission.service";
import {LargeIntFormatPipe} from "../../pipes/large-int-format.pipe";
import {KeycloakService} from "../../helpers/keycloak-service/keycloak.service";
import {AppService} from "../../app.service";
import {HttpClient, HttpHandler} from "@angular/common/http";
import {Router} from "@angular/router";
import {StudyWebService} from "./study-web-service.model";
import {j4care} from "../../helpers/j4care.service";

class StudyServiceDependenc{
}

describe('StudyService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
          StudyService,
          {provide:KeycloakService, useClass:StudyServiceDependenc},
          {provide:AppService, useClass:StudyServiceDependenc},
          {provide:AeListService, useClass:StudyServiceDependenc},
          {provide:J4careHttpService, useClass:StudyServiceDependenc},
          {provide:StorageSystemsService, useClass:StudyServiceDependenc},
          {provide:DevicesService, useClass:StudyServiceDependenc},
          {provide:WebAppsListService, useClass:StudyServiceDependenc},
          {provide:RetrieveMonitoringService, useClass:StudyServiceDependenc},
          {provide:PermissionService, useClass:StudyServiceDependenc},
          {provide:LargeIntFormatPipe, useClass:StudyServiceDependenc},
          {provide:j4care, useClass:StudyServiceDependenc}
      ]
    });
  });

  it('should be created', inject([StudyService], (service: StudyService) => {
    expect(service).toBeTruthy();
  }));

  it("Has Study Web Service web service class", inject([StudyService], (service: StudyService) => {
      let studyWebService:any = {
          "webServices": [
              {
                  "dicomDeviceName": "dcm4chee-arc",
                  "dcmWebAppName": "DCM4CHEE",
                  "dicomDescription": "Hide instances rejected for Quality Reasons",
                  "dcmWebServicePath": "/dcm4chee-arc/aets/DCM4CHEE/rs",
                  "dcmWebServiceClass": [
                      "WADO_RS",
                      "STOW_RS",
                      "QIDO_RS",
                      "UPS_RS",
                      "MWL_RS",
                      "QIDO_COUNT",
                      "DCM4CHEE_ARC_AET",
                      "UPS_MATCHING"
                  ],
                  "dicomAETitle": "DCM4CHEE",
                  "dicomAETitleObject": {
                      "dicomAETitle": "DCM4CHEE",
                      "dicomDescription": "Hide instances rejected for Quality Reasons",
                      "dcmAllowDeletePatient": "WITHOUT_STUDIES",
                      "dcmAllowDeleteStudyPermanently": "REJECTED"
                  }
              },{
                  "dicomDeviceName": "dcm4chee-arc",
                  "dcmWebAppName": "TEST",
                  "dicomDescription": "Hide instances rejected for Quality Reasons",
                  "dcmWebServicePath": "/dcm4chee-arc/aets/DCM4CHEE/rs",
                  "dcmWebServiceClass": [
                      "MOVE",
                      "STOW_RS"
                  ],
                  "dicomAETitle": "DCM4CHEE",
                  "dicomAETitleObject": {
                      "dicomAETitle": "DCM4CHEE",
                      "dicomDescription": "Hide instances rejected for Quality Reasons",
                      "dcmAllowDeletePatient": "WITHOUT_STUDIES",
                      "dcmAllowDeleteStudyPermanently": "REJECTED"
                  }
              },
              {
                  "dicomDeviceName": "dcm4chee-arc",
                  "dcmWebAppName": "TEST-NG",
                  "dicomDescription": "Hide instances rejected for Quality Reasons",
                  "dcmWebServicePath": "/dcm4chee-arc/aets/TEST/rs",
                  "dcmKeycloakClientID": "curl",
                  "dcmWebServiceClass": [
                      "WADO_RS",
                      "STOW_RS",
                      "QIDO_RS",
                      "UPS_RS",
                      "MWL_RS",
                      "QIDO_COUNT",
                      "DCM4CHEE_ARC_AET",
                      "UPS_MATCHING"
                  ],
                  "dcmProperty": [
                      "UI_DEVICE_NAME=test-ng",
                      "UI_DEVICE_DESCRIPTION=test-ng description"
                  ],
                  "dicomNetworkConnection": [
                      {
                          "dicomHostname": "test-ng.lan.j4care.com",
                          "dicomPort": 8443,
                          "dicomTLSCipherSuite": [
                              "SSL_RSA_WITH_3DES_EDE_CBC_SHA"
                          ]
                      }
                  ]
              }
          ],
          "selectedWebService": {
              "dicomDeviceName": "dcm4chee-arc",
              "dcmWebAppName": "DCM4CHEE",
              "dicomDescription": "Hide instances rejected for Quality Reasons",
              "dcmWebServicePath": "/dcm4chee-arc/aets/DCM4CHEE/rs",
              "dcmWebServiceClass": [
                  "WADO_RS",
                  "STOW_RS",
                  "QIDO_RS",
                  "UPS_RS",
                  "MWL_RS",
                  "QIDO_COUNT",
                  "DCM4CHEE_ARC_AET",
                  "UPS_MATCHING"
              ],
              "dicomAETitle": "DCM4CHEE",
              "dicomAETitleObject": {
                  "dicomAETitle": "DCM4CHEE",
                  "dicomDescription": "Hide instances rejected for Quality Reasons",
                  "dcmAllowDeletePatient": "WITHOUT_STUDIES",
                  "dcmAllowDeleteStudyPermanently": "REJECTED"
              }
          }
      };
      expect(service.webAppGroupHasClass(studyWebService, "MOVE")).toBe(true);
      expect(service.webAppGroupHasClass(studyWebService, "QIDO_RS")).toBe(true);
      expect(service.webAppGroupHasClass(studyWebService, "FICTIVE")).toBe(false);
  }))

});
