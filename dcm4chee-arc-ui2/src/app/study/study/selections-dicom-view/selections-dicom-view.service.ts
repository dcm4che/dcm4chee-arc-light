import { Injectable } from '@angular/core';
import {DicomLevel} from "../../../interfaces";
import {TableSchemaElement} from "../../../models/dicom-table-schema-element";

@Injectable()
export class SelectionsDicomViewService {

  constructor() { }

  getTableSchema($this, actions, dicomLevel?:DicomLevel){
    switch (dicomLevel) {
        case "patient":
            return [
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@patients_name:Patient's Name`,
                    pathToValue: "00100010.Value[0].Alphabetic",
                    headerDescription: $localize `:@@patients_name:Patient's Name`,
                    widthWeight: 1,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@patient_id:Patient ID`,
                    pathToValue: "00100020.Value[0]",
                    headerDescription: $localize `:@@patient_id:Patient ID`,
                    widthWeight: 1,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@selections-dicom-view.issuer_of_patient:Issuer of Patient`,
                    pathToValue: "00100021.Value[0]",
                    headerDescription: $localize `:@@selections-dicom-view.issuer_of_patient_id:Issuer of Patient ID`,
                    widthWeight: 1,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@number_of_patient_related_studies:#S`,
                    pathToValue: "00201200.Value[0]",
                    headerDescription: $localize `:@@selections-dicom-view.number_of_patient_related_studies:Number of Patient Related Studies`,
                    widthWeight: 0.2,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "actions",
                    header: "",
                    actions: [
                        {
                            icon: {
                                tag: 'span',
                                cssClass: 'glyphicon glyphicon-remove',
                                text: ''
                            },
                            click: (e) => {
                                actions.call($this, e);
                            },
                            title: $localize `:@@selections-dicom-view.remove_from_selection:Remove from selection`
                        }
                    ],
                    headerDescription: $localize `:@@actions:Actions`,
                    pxWidth: 40
                }),
            ];
        case "study":
          return [
              new TableSchemaElement({
                  type: "value",
                  header: $localize `:@@study_id:Study ID`,
                  pathToValue: "[00200010].Value[0]",
                  headerDescription: $localize `:@@study_id:Study ID`,
                  widthWeight: 0.9,
                  calculatedWidth: "20%"
              }), new TableSchemaElement({
                  type: "value",
                  header: $localize `:@@study_instance_uid:Study Instance UID`,
                  pathToValue: "[0020000D].Value[0]",
                  headerDescription: $localize `:@@study_instance_uid:Study Instance UID`,
                  widthWeight: 3,
                  calculatedWidth: "20%"
              }),
              new TableSchemaElement({
                  type: "value",
                  header: $localize `:@@study_date:Study Date`,
                  pathToValue: "[00080020].Value[0]",
                  headerDescription: $localize `:@@study_date:Study Date`,
                  widthWeight: 0.6,
                  calculatedWidth: "20%"
              }),
              new TableSchemaElement({
                  type: "value",
                  header: $localize `:@@number_of_related_series:#S`,
                  pathToValue: "[00201206].Value[0]",
                  headerDescription: $localize `:@@number_of_study_related_series:Number of Study Related Series`,
                  widthWeight: 0.2,
                  calculatedWidth: "20%"
              }),
              new TableSchemaElement({
                  type: "value",
                  header:$localize `:@@number_of_instances:#I`,
                  pathToValue: "[00201208].Value[0]",
                  headerDescription: $localize `:@@number_of_study_related_instances:Number of Study Related Instances`,
                  widthWeight: 0.2,
                  calculatedWidth: "20%"
              }),
              new TableSchemaElement({
                  type: "actions",
                  header: "",
                  actions: [
                      {
                          icon: {
                              tag: 'span',
                              cssClass: 'glyphicon glyphicon-remove',
                              text: ''
                          },
                          click: (e) => {
                              actions.call($this, e);
                          },
                          title: $localize `:@@selections-dicom-view.remove_from_selection:Remove from selection`
                      }
                  ],
                  headerDescription: $localize `:@@actions:Actions`,
                  pxWidth: 40
              })
          ];
        case "series":
            return [
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@selections-dicom-view.station_name:Station Name`,
                    pathToValue: "00081010.Value[0]",
                    headerDescription: $localize `:@@selections-dicom-view.station_name:Station Name`,
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@selections-dicom-view.series_number:Series Number`,
                    pathToValue: "00200011.Value[0]",
                    headerDescription: $localize `:@@selections-dicom-view.series_number:Series Number`,
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@body_part:Body Part`,
                    pathToValue: "00180015.Value[0]",
                    headerDescription: $localize `:@@body_part_examined:Body Part Examined`,
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@modality:Modality`,
                    pathToValue: "00080060.Value[0]",
                    headerDescription: $localize `:@@modality:Modality`,
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header:$localize `:@@number_of_instances:#I`,
                    pathToValue: "00201209.Value[0]",
                    headerDescription: $localize `:@@selections-dicom-view.number_of_series_related_instances:Number of Series Related Instances`,
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "actions",
                    header: "",
                    actions: [
                        {
                            icon: {
                                tag: 'span',
                                cssClass: 'glyphicon glyphicon-remove',
                                text: ''
                            },
                            click: (e) => {
                                actions.call($this, e);
                            },
                            title: $localize `:@@selections-dicom-view.remove_from_selection:Remove from selection`
                        }
                    ],
                    headerDescription: $localize `:@@actions:Actions`,
                    pxWidth: 40
                })
            ];
        case "instance":
            return [
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@selections-dicom-view.sop_class_uid:SOP Class UID`,
                    pathToValue: "00080016.Value[0]",
                    headerDescription: $localize `:@@selections-dicom-view.sop_class_uid:SOP Class UID`,
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@selections-dicom-view.instance_number:Instance Number`,
                    pathToValue: "00200013.Value[0]",
                    headerDescription: $localize `:@@selections-dicom-view.instance_number:Instance Number`,
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@content_date:Content Date`,
                    pathToValue: "00080023.Value[0]",
                    headerDescription: $localize `:@@content_date:Content Date`,
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "#F",
                    pathToValue: "00280008.Value[0]",
                    headerDescription: $localize `:@@selections-dicom-view.number_of_frames:Number of Frames`,
                    widthWeight: 0.3,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "actions",
                    header: "",
                    actions: [
                        {
                            icon: {
                                tag: 'span',
                                cssClass: 'glyphicon glyphicon-remove',
                                text: ''
                            },
                            click: (e) => {
                                actions.call($this, e);
                            },
                            title: $localize `:@@selections-dicom-view.remove_from_selection:Remove from selection`
                        }
                    ],
                    headerDescription: $localize `:@@actions:Actions`,
                    pxWidth: 40
                })
            ];
        default:
            return [
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@patients_name:Patient's Name`,
                    pathToValue: "00100010.Value[0].Alphabetic",
                    headerDescription: $localize `:@@patients_name:Patient's Name`,
                    widthWeight: 1,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@patient_id:Patient ID`,
                    pathToValue: "00100020.Value[0]",
                    headerDescription: $localize `:@@patient_id:Patient ID`,
                    widthWeight: 1,
                    calculatedWidth: "20%"
                }),new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@study_id:Study ID`,
                    pathToValue: "[00200010].Value[0]",
                    headerDescription: $localize `:@@study_id:Study ID`,
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "actions",
                    header: "",
                    actions: [
                        {
                            icon: {
                                tag: 'span',
                                cssClass: 'glyphicon glyphicon-remove',
                                text: ''
                            },
                            click: (e) => {
                                actions.call($this, e);
                            },
                            title: $localize `:@@selections-dicom-view.remove_from_selection:Remove from selection`
                        }
                    ],
                    headerDescription: $localize `:@@actions:Actions`,
                    pxWidth: 40
                })
            ]
    }
  }
}
