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
import { HttpClient, HttpHandler } from "@angular/common/http";
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
          {provide:j4care, useClass:StudyServiceDependenc},
          {provide:HttpClient, useClass:StudyServiceDependenc}
      ],
      teardown: { destroyAfterEach: false }
     });
  });

  it('should be created', inject([StudyService], (service: StudyService) => {
    expect(service).toBeTruthy();
  }));
  it('should get the patient identifier', inject([StudyService], (service: StudyService) => {
    expect(service.getPatientIdentifierOf(
        {
            "00100020": {
                "vr": "LO",
                "Value": [
                    "1395056"
                ]
            },
            "00100021": {
                "vr": "LO",
                "Value": [
                    "2.16.840.1.113883.2.4.6.1.6020502.1.1"
                ]
            },
            "00100024": {
                "vr": "SQ",
                "Value": [
                    {
                        "00400032": {
                            "vr": "UT",
                            "Value": [
                                "2.16.840.1.113883.2.4.6.1.6020502.1.1"
                            ]
                        },
                        "00400033": {
                            "vr": "CS",
                            "Value": [
                                "ISO"
                            ]
                        },
                        "00400035": {
                            "vr": "CS",
                            "Value": [
                                "PAT_CODE"
                            ]
                        }
                    }
                ]
            }
        }
    )).toBe("1395056^^^2.16.840.1.113883.2.4.6.1.6020502.1.1&2.16.840.1.113883.2.4.6.1.6020502.1.1&ISO^PAT_CODE");
      expect(service.getPatientIdentifierOf(
          {
              "00100020": {
                  "vr": "LO",
                  "Value": [
                      "1395056"
                  ]
              },
              "00100021": {
                  "vr": "LO",
                  "Value": [
                      "2.16.840.1.113883.2.4.6.1.6020502.1.1"
                  ]
              },
              "00100024": {
                  "vr": "SQ",
                  "Value": [
                      {
                          "00400032": {
                              "vr": "UT",
                              "Value": [
                                  "2.16.840.1.113883.2.4.6.1.6020502.1.1"
                              ]
                          },
                          "00400033": {
                              "vr": "CS",
                              "Value": [
                                  "ISO"
                              ]
                          },
                          "00400035": {
                              "vr": "CS"
                          }
                      }
                  ]
              }
          }
      )).toBe("1395056^^^2.16.840.1.113883.2.4.6.1.6020502.1.1&2.16.840.1.113883.2.4.6.1.6020502.1.1&ISO");
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
                      "MPPS_RS",
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
                      "MPPS_RS",
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
                  "MPPS_RS",
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

    it("Collect selected objects", inject([StudyService], (service: StudyService) =>{
            expect(service.collectSelectedObjects([
                {
                    "StudyInstanceUID": "1.2.840.113674.1115.261.200",
                    "ReferencedSeriesSequence": [
                        {
                            "SeriesInstanceUID": "1.2.840.113674.1115.261.182.300"
                        }
                    ]
                },
                {
                    "StudyInstanceUID": "1.2.840.113674.1115.261.200",
                    "ReferencedSeriesSequence": [
                        {
                            "SeriesInstanceUID": "1.2.840.113674.1115.261.178.300",
                            "ReferencedSOPSequence": [
                                {
                                    "ReferencedSOPClassUID": "1.2.840.10008.5.1.4.1.1.4",
                                    "ReferencedSOPInstanceUID": "1.2.840.113674.950809133404076.100"
                                }
                            ]
                        }
                    ]
                },
                {
                    "StudyInstanceUID": "1.2.840.113674.1115.261.200",
                    "ReferencedSeriesSequence": [
                        {
                            "SeriesInstanceUID": "1.2.840.113674.1115.261.178.300",
                            "ReferencedSOPSequence": [
                                {
                                    "ReferencedSOPClassUID": "1.2.840.10008.5.1.4.1.1.4",
                                    "ReferencedSOPInstanceUID": "1.2.840.113674.950809133401055.100"
                                }
                            ]
                        }
                    ]
                },
                {
                    "StudyInstanceUID": "1.2.840.113674.1115.261.200",
                    "ReferencedSeriesSequence": [
                        {
                            "SeriesInstanceUID": "1.2.840.113674.1115.261.178.300",
                            "ReferencedSOPSequence": [
                                {
                                    "ReferencedSOPClassUID": "1.2.840.10008.5.1.4.1.1.4",
                                    "ReferencedSOPInstanceUID": "1.2.840.113674.950809133405086.100"
                                }
                            ]
                        }
                    ]
                }
            ])).toEqual([{
                "StudyInstanceUID": "1.2.840.113674.1115.261.200",
                "ReferencedSeriesSequence": [
                    {
                        "SeriesInstanceUID": "1.2.840.113674.1115.261.182.300"
                    },
                    {
                        "SeriesInstanceUID": "1.2.840.113674.1115.261.178.300",
                        "ReferencedSOPSequence": [
                            {
                                "ReferencedSOPClassUID": "1.2.840.10008.5.1.4.1.1.4",
                                "ReferencedSOPInstanceUID": "1.2.840.113674.950809133404076.100"
                            },
                            {
                                "ReferencedSOPClassUID": "1.2.840.10008.5.1.4.1.1.4",
                                "ReferencedSOPInstanceUID": "1.2.840.113674.950809133401055.100"
                            },
                            {
                                "ReferencedSOPClassUID": "1.2.840.10008.5.1.4.1.1.4",
                                "ReferencedSOPInstanceUID": "1.2.840.113674.950809133405086.100"
                            }
                        ]
                    }
                ]
            }]);
            expect(service.collectSelectedObjects([
                {
                    "StudyInstanceUID": "1.2.840.113674.1335.106.200",
                    "ReferencedSeriesSequence": [
                        {
                            "SeriesInstanceUID": "1.2.840.113674.1335.106.184.300"
                        }
                    ]
                },
                {
                    "StudyInstanceUID": "1.2.392.200036.9125.0.199302241758.16",
                    "ReferencedSeriesSequence": [
                        {
                            "SeriesInstanceUID": "1.2.392.200036.9125.0.199302241758.16",
                            "ReferencedSOPSequence": [
                                {
                                    "ReferencedSOPClassUID": "1.2.840.10008.5.1.4.1.1.1",
                                    "ReferencedSOPInstanceUID": "1.2.392.200036.9125.0.19950720112207"
                                }
                            ]
                        }
                    ]
                },
                {
                    "StudyInstanceUID": "1.2.840.113674.1335.106.200",
                    "ReferencedSeriesSequence": [
                        {
                            "SeriesInstanceUID": "1.2.840.113674.1335.106.185.300",
                            "ReferencedSOPSequence": [
                                {
                                    "ReferencedSOPClassUID": "1.2.840.10008.5.1.4.1.1.4",
                                    "ReferencedSOPInstanceUID": "1.2.840.113674.950809132816137.100"
                                }
                            ]
                        }
                    ]
                },
                {
                    "StudyInstanceUID": "1.2.840.113674.1335.106.200",
                    "ReferencedSeriesSequence": [
                        {
                            "SeriesInstanceUID": "1.2.840.113674.1335.106.185.300",
                            "ReferencedSOPSequence": [
                                {
                                    "ReferencedSOPClassUID": "1.2.840.10008.5.1.4.1.1.4",
                                    "ReferencedSOPInstanceUID": "1.2.840.113674.950809132818158.100"
                                }
                            ]
                        }
                    ]
                }
            ])).toEqual([
                {
                    "StudyInstanceUID": "1.2.840.113674.1335.106.200",
                    "ReferencedSeriesSequence": [
                        {
                            "SeriesInstanceUID": "1.2.840.113674.1335.106.184.300"
                        },
                        {
                            "SeriesInstanceUID": "1.2.840.113674.1335.106.185.300",
                            "ReferencedSOPSequence": [
                                {
                                    "ReferencedSOPClassUID": "1.2.840.10008.5.1.4.1.1.4",
                                    "ReferencedSOPInstanceUID": "1.2.840.113674.950809132816137.100"
                                },
                                {
                                    "ReferencedSOPClassUID": "1.2.840.10008.5.1.4.1.1.4",
                                    "ReferencedSOPInstanceUID": "1.2.840.113674.950809132818158.100"
                                }
                            ]
                        }
                    ]
                },
                {
                    "StudyInstanceUID": "1.2.392.200036.9125.0.199302241758.16",
                    "ReferencedSeriesSequence": [
                        {
                            "SeriesInstanceUID": "1.2.392.200036.9125.0.199302241758.16",
                            "ReferencedSOPSequence": [
                                {
                                    "ReferencedSOPClassUID": "1.2.840.10008.5.1.4.1.1.1",
                                    "ReferencedSOPInstanceUID": "1.2.392.200036.9125.0.19950720112207"
                                }
                            ]
                        }
                    ]
                }

            ]);
        })
    );
    it("Should extract the roles if configured from a web app object",inject([StudyService], (service: StudyService)=>{
        const webAppObject = {
            "dcmProperty": [
                "roles=admin, user"
            ]
        }
        expect(service.getWebAppRoles(webAppObject)).toEqual(["admin","user"]);
        const webAppObject2 = {
            "dcmProperty": [
                "roles=admin user"
            ]
        }
        expect(service.getWebAppRoles(webAppObject2)).toEqual(["admin","user"]);

        const webAppObject3 = {
            "dcmProperty": [
                "roles=admin,user,test"
            ]
        }
        expect(service.getWebAppRoles(webAppObject3)).toEqual(["admin","user","test"]);
        const webAppObject4 = {
            "dcmProperty": [
                "roles=admin,user,test,"
            ]
        }
        expect(service.getWebAppRoles(webAppObject3)).toEqual(["admin","user","test"]);

        })
    );

});
