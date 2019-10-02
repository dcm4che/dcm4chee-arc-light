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
                    header: "Patient's Name",
                    pathToValue: "00100010.Value[0].Alphabetic",
                    headerDescription: "Patient's Name",
                    widthWeight: 1,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Patient ID",
                    pathToValue: "00100020.Value[0]",
                    headerDescription: "Patient ID",
                    widthWeight: 1,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Issuer of Patient",
                    pathToValue: "00100021.Value[0]",
                    headerDescription: "Issuer of Patient ID",
                    widthWeight: 1,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "#S",
                    pathToValue: "00201200.Value[0]",
                    headerDescription: "Number of Patient Related Studies",
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
                            title: "Remove from selection"
                        }
                    ],
                    headerDescription: "Actions",
                    pxWidth: 40
                }),
            ];
        case "study":
          return [
              new TableSchemaElement({
                  type: "value",
                  header: "Study ID",
                  pathToValue: "[00200010].Value[0]",
                  headerDescription: "Study ID",
                  widthWeight: 0.9,
                  calculatedWidth: "20%"
              }), new TableSchemaElement({
                  type: "value",
                  header: "Study Instance UID",
                  pathToValue: "[0020000D].Value[0]",
                  headerDescription: "Study Instance UID",
                  widthWeight: 3,
                  calculatedWidth: "20%"
              }),
              new TableSchemaElement({
                  type: "value",
                  header: "Study Date",
                  pathToValue: "[00080020].Value[0]",
                  headerDescription: "Study Date",
                  widthWeight: 0.6,
                  calculatedWidth: "20%"
              }),
              new TableSchemaElement({
                  type: "value",
                  header: "#S",
                  pathToValue: "[00201206].Value[0]",
                  headerDescription: "Number of Study Related Series",
                  widthWeight: 0.2,
                  calculatedWidth: "20%"
              }),
              new TableSchemaElement({
                  type: "value",
                  header: "#I",
                  pathToValue: "[00201208].Value[0]",
                  headerDescription: "Number of Study Related Instances",
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
                          title: "Remove from selection"
                      }
                  ],
                  headerDescription: "Actions",
                  pxWidth: 40
              })
          ];
        case "series":
            return [
                new TableSchemaElement({
                    type: "value",
                    header: "Station Name",
                    pathToValue: "00081010.Value[0]",
                    headerDescription: "Station Name",
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Series Number",
                    pathToValue: "00200011.Value[0]",
                    headerDescription: "Series Number",
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Body Part",
                    pathToValue: "00180015.Value[0]",
                    headerDescription: "Body Part Examined",
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Modality",
                    pathToValue: "00080060.Value[0]",
                    headerDescription: "Modality",
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "#I",
                    pathToValue: "00201209.Value[0]",
                    headerDescription: "Number of Series Related Instances",
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
                            title: "Remove from selection"
                        }
                    ],
                    headerDescription: "Actions",
                    pxWidth: 40
                })
            ];
        case "instance":
            return [
                new TableSchemaElement({
                    type: "value",
                    header: "SOP Class UID",
                    pathToValue: "00080016.Value[0]",
                    headerDescription: "SOP Class UID",
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Instance Number",
                    pathToValue: "00200013.Value[0]",
                    headerDescription: "Instance Number",
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Content Date",
                    pathToValue: "00080023.Value[0]",
                    headerDescription: "Content Date",
                    widthWeight: 0.9,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "#F",
                    pathToValue: "00280008.Value[0]",
                    headerDescription: "Number of Frames",
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
                            title: "Remove from selection"
                        }
                    ],
                    headerDescription: "Actions",
                    pxWidth: 40
                })
            ];
        default:
            return [
                new TableSchemaElement({
                    type: "value",
                    header: "Patient's Name",
                    pathToValue: "00100010.Value[0].Alphabetic",
                    headerDescription: "Patient's Name",
                    widthWeight: 1,
                    calculatedWidth: "20%"
                }),
                new TableSchemaElement({
                    type: "value",
                    header: "Patient ID",
                    pathToValue: "00100020.Value[0]",
                    headerDescription: "Patient ID",
                    widthWeight: 1,
                    calculatedWidth: "20%"
                }),new TableSchemaElement({
                    type: "value",
                    header: "Study ID",
                    pathToValue: "[00200010].Value[0]",
                    headerDescription: "Study ID",
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
                            title: "Remove from selection"
                        }
                    ],
                    headerDescription: "Actions",
                    pxWidth: 40
                })
            ]
    }
  }
}
