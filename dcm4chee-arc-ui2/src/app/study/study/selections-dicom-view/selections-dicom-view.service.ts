import { Injectable } from '@angular/core';
import {DicomLevel} from "../../../interfaces";
import {TableSchemaElement} from "../../../models/dicom-table-schema-element";
import {DynamicPipe} from "../../../helpers/dicom-studies-table/dicom-studies-table.interfaces";
import {PatientIssuerPipe} from "../../../pipes/patient-issuer.pipe";
import {CustomDatePipe} from "../../../pipes/custom-date.pipe";

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
                    type: 'pipe',
                    header: $localize `:@@patient_identifiers:Patient Identifiers`,
                    headerDescription: $localize `:@@patient_identifiers:Patient Identifiers`,
                    widthWeight: 2,
                    calculatedWidth: "20%",
                    pipe: new DynamicPipe(PatientIssuerPipe, undefined)
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@b_date:B. Date`,
                    pathToValue: "00100030.Value[0]",
                    headerDescription: $localize `:@@patients_birth_date:Patient's Birth Date`,
                    widthWeight: 0.5,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@sex:Sex`,
                    pathToValue: "00100040.Value[0]",
                    headerDescription: $localize `:@@patients_sex:Patient's Sex`,
                    widthWeight: 0.2,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@number_of_related_studies:#S`,
                    pathToValue: "00201200.Value[0]",
                    headerDescription: $localize `:@@number_of_patient_related_studies:Number of Patient Related Studies`,
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
                  widthWeight: 2,
                  calculatedWidth: "20%"
              }), new TableSchemaElement({
                  type: "value",
                  header: $localize `:@@accession_number:Accession Number`,
                  pathToValue: "[00080050].Value[0]",
                  headerDescription: $localize `:@@accession_number:Accession Number`,
                  widthWeight: 1.5,
                  calculatedWidth: "20%"
              }), new TableSchemaElement({
                  type: "value",
                  header: $localize `:@@study_desc:Study Desc`,
                  pathToValue: "[00081030].Value[0]",
                  headerDescription: $localize `:@@study_description:Study Description`,
                  widthWeight: 2,
                  calculatedWidth: "20%"
              }),
              new TableSchemaElement({
                  type: "value",
                  header: $localize `:@@study_date:Study Date`,
                  pathToValue: "[00080020].Value[0]",
                  headerDescription: $localize `:@@study_date:Study Date`,
                  widthWeight: 1,
                  calculatedWidth: "20%"
              }),
              new TableSchemaElement({
                  type: "value",
                  header: $localize `:@@number_of_related_series:#S`,
                  pathToValue: "[00201206].Value[0]",
                  headerDescription: $localize `:@@number_of_study_related_series:Number of Study Related Series`,
                  widthWeight: 0.3,
                  calculatedWidth: "20%"
              }),
              new TableSchemaElement({
                  type: "value",
                  header:$localize `:@@number_of_instances:#I`,
                  pathToValue: "[00201208].Value[0]",
                  headerDescription: $localize `:@@number_of_study_related_instances:Number of Study Related Instances`,
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
        case "series":
            return [
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@station_name:Station Name`,
                    pathToValue: "00081010.Value[0]",
                    headerDescription: $localize `:@@station_name:Station Name`,
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@series_number:Series Number`,
                    pathToValue: "00200011.Value[0]",
                    headerDescription: $localize `:@@series_number:Series Number`,
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
                    headerDescription: $localize `:@@number_of_series_related_instances:Number of Series Related Instances`,
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
                    header: $localize `:@@sop_class_uid:SOP Class UID`,
                    pathToValue: "00080016.Value[0]",
                    headerDescription: $localize `:@@sop_class_uid:SOP Class UID`,
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: $localize `:@@instance_number:Instance Number`,
                    pathToValue: "00200013.Value[0]",
                    headerDescription: $localize `:@@instance_number:Instance Number`,
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
                    headerDescription: $localize `:@@number_of_frames:Number of Frames`,
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
