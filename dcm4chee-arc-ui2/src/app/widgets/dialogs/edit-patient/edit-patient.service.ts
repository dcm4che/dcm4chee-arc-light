import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class EditPatientService {

  constructor() { }


  getSimpleFormSchema(){
    return [
      {
        type: "input",
        filterKey: "batchID",
        modelPath: "test.mymodel",
        description: $localize`:@@batch_id:Batch ID`,
        placeholder: $localize`:@@batch_id:Batch ID`
      },
      {
        tag:"issuer-selector",
        issuers:[
          {
            key:"PatientID",
            label:$localize `:@@patient_id:Patient ID`
          },{
            key:"IssuerOfPatientID",
            label:$localize `:@@issuer_of_patient_id:Issuer of Patient ID`
          }, {
            key:"IssuerOfPatientIDQualifiersSequence.UniversalEntityID",
            label:$localize `:@@issuer_of_patient_id_seq_universal_entity_id:Issuer of Patient ID Qualifiers Sequence - Universal Entity ID`
          }, {
            key:"IssuerOfPatientIDQualifiersSequence.UniversalEntityIDType",
            label:$localize `:@@issuer_of_patient_id_seq_universal_entity_id_type:Issuer of Patient ID Qualifiers Sequence - Universal Entity ID Type`
          }
        ],
        description:$localize `:@@patient_identifiers:Patient Identifiers`,
        placeholder:$localize `:@@patient_identifiers:Patient Identifiers`
      }
/*        [
        [
          {
            tag: "label",
            text: $localize`:@@person_name:Person name`
          },
          {
            tag:"person-name-picker",
            filterKey:"PatientName",
            description:$localize`:@@person_name:Person name`,
            placeholder:$localize`:@@person_name:Person name`
          }
        ],[
          {
            tag: "label",
            text: $localize `:@@patient_identifiers:Patient Identifiers`
          },
          {
            tag:"issuer-selector",
            issuers:[
              {
                key:"PatientID",
                label:$localize `:@@patient_id:Patient ID`
              },{
                key:"IssuerOfPatientID",
                label:$localize `:@@issuer_of_patient_id:Issuer of Patient ID`
              }, {
                key:"IssuerOfPatientIDQualifiersSequence.UniversalEntityID",
                label:$localize `:@@issuer_of_patient_id_seq_universal_entity_id:Issuer of Patient ID Qualifiers Sequence - Universal Entity ID`
              }, {
                key:"IssuerOfPatientIDQualifiersSequence.UniversalEntityIDType",
                label:$localize `:@@issuer_of_patient_id_seq_universal_entity_id_type:Issuer of Patient ID Qualifiers Sequence - Universal Entity ID Type`
              }
            ],
            description:$localize `:@@patient_identifiers:Patient Identifiers`,
            placeholder:$localize `:@@patient_identifiers:Patient Identifiers`
          }
        ],

          [

            {
              tag: "label",
              text: $localize`:@@batch_id:Batch ID`
            },
            {
              tag: "input",
              type: "text",
              filterKey: "batchID",
              description: $localize`:@@batch_id:Batch ID`,
              placeholder: $localize`:@@batch_id:Batch ID`
            }
        ]
      ]*/
    ]
  }
}
