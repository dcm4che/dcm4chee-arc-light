import { Injectable } from '@angular/core';
import {FormElement} from "../../../helpers/form/form-element";
import {SelectDropdown} from "../../../interfaces";
import * as _ from 'lodash-es';
import {j4care} from "../../../helpers/j4care.service";

@Injectable({
  providedIn: 'root'
})
export class EditPatientService {

  iod;
  constructor() { }

 extractPatientIdentifiers(patientIdentifier:string):{text:string,modelPath:string}|{}|undefined{
    try{
      if(patientIdentifier){
        const regex = /(\w+)(\^{3})?(\w*)&?(\w*)&?(\w*)\^?(\w*)/;
        let match;

        if ((match = regex.exec(patientIdentifier)) !== null) {
          // The result can be accessed through the `m`-variable.
          let result:{text:string,modelPath:string}|{} = {};
          if(match[1]){
            result["PatientID"] = {
              text:match[1],
              modelPath:"00100020.Value[0]",
              firstLevelCode:"00100020"
            }
          }
          if(match[3]){
            result["IssuerOfPatientID"] = {
              text:match[3],
              modelPath:"00100021.Value[0]",
              firstLevelCode:"00100021"
            }
          }
          if(match[4]){
            result["UniversalEntityID"] = {
              text:match[4],
              //       ["00100024"].Value[0]["00400032"].Value[0]
              modelPath:"00100024.Value[0][00400032].Value[0]",
              firstLevelCode:"00100024"
            }
          }
          if(match[5]){
            result["UniversalEntityIDType"] = {
              text:match[5],
              modelPath:"00100024.Value[0][00400033].Value[0]",
              firstLevelCode:"00100024"
            }
          }
          if(match[6]){
            result["TypeofPatientID"] = {
              text:match[6],
              modelPath:"00100022.Value[0]",
              firstLevelCode:"00100022"
            }
          }
          return result;
        }
        return;
      }
    }catch (e) {
      return
    }
 }
  getSimpleFormSchema(){
    return [
      new FormElement({
        type: "composed-input",
        inputSize:5,
        joinString:"^",
        modelPath: "00100010.Value[0].Alphabetic",
        placeholderElements:[
          $localize `:@@family_ame:Family Name`,
          $localize `:@@given_name:Given Name`,
          $localize `:@@middle_name:Middle Name`,
          $localize `:@@prefix:Prefix`,
          $localize `:@@suffix:Suffix`,
        ],
        description: $localize`:@@patient_name_alphabetic:Patient name ( Alphabetic )`,
        placeholder: $localize`:@@patient_name_alphabetic:Patient name ( Alphabetic )`
      }),
      new FormElement({
        type: "composed-input",
        inputSize:5,
        joinString:"^",
        modelPath:  "00100010.Value[0].Ideographic",
        placeholderElements:[
          $localize `:@@family_ame:Family Name`,
          $localize `:@@given_name:Given Name`,
          $localize `:@@middle_name:Middle Name`,
          $localize `:@@prefix:Prefix`,
          $localize `:@@suffix:Suffix`,
        ],
        description: $localize`:@@patient_name_ideographic:Patient name ( Ideographic )`,
        placeholder: $localize`:@@patient_name_ideographic:Patient name ( Ideographic )`
      }),
      new FormElement({
        type: "composed-input",
        inputSize:5,
        joinString:"^",
        modelPath: "00100010.Value[0].Phonetic",
        placeholderElements:[
          $localize `:@@family_ame:Family Name`,
          $localize `:@@given_name:Given Name`,
          $localize `:@@middle_name:Middle Name`,
          $localize `:@@prefix:Prefix`,
          $localize `:@@suffix:Suffix`,
        ],
        description: $localize`:@@patient_name_phonetic:Patient name ( Phonetic )`,
        placeholder: $localize`:@@patient_name_phonetic:Patient name ( Phonetic )`
      }),
      new FormElement({
        type: "input",
        modelPath: "00100020.Value[0]",
        onChangeHook:(element, event, model)=>{

        },
        onFocusOutHook:(element,event,model)=>{
          const value = _.get(model,element.modelPath);
          const extracted = this.extractPatientIdentifiers(value);
          console.log("extracted",extracted);
          if(extracted){
            Object.keys(extracted).forEach((key)=>{
              if(extracted[key].text){
                if(!_.hasIn(model, extracted[key].modelPath) && !_.hasIn(model, extracted[key].firstLevelCode) ){
                  model[extracted[key].firstLevelCode]  = _.cloneDeep(this.iod[extracted[key].firstLevelCode]);
                  j4care.removeKeyFromObject(model[extracted[key].firstLevelCode],"multi");
                  j4care.removeKeyFromObject(model[extracted[key].firstLevelCode],"required");
                }
                _.set(model,extracted[key].modelPath,extracted[key].text);
              }
            });
          }
        },
        description: $localize `:@@patient_id:Patient ID`,
        placeholder: $localize `:@@patient_id:Patient ID`
      }),
      new FormElement({
        type: "input",
        modelPath: "00100021.Value[0]",
        description: $localize `:@@issuer_of_patient_id:Issuer of Patient ID`,
        placeholder: $localize `:@@issuer_of_patient_id:Issuer of Patient ID`
      }),
      new FormElement({
        tag:"p-calendar",
        modelPath: "00100030.Value[0]",
        onlyDate:true,
        description: $localize `:@@patients_birth_date:Patient's Birth Date`,
        placeholder: $localize `:@@patients_birth_date:Patient's Birth Date`
      }),
      new FormElement({
        tag:"select",
        options:[
          new SelectDropdown("F",$localize `:@@female:Female`),
          new SelectDropdown("M",$localize `:@@male:Male`),
          new SelectDropdown("O", $localize `:@@other:Other`),
        ],
        modelPath: "00100040.Value[0]",
        description:$localize `:@@patients_sex:Patient's Sex`,
        placeholder:$localize `:@@patients_sex:Patient's Sex`
      }),
/*      ,
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
      }*/
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
