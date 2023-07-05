import { Pipe, PipeTransform } from '@angular/core';
import * as _ from 'lodash-es';
import {j4care} from "../helpers/j4care.service";

@Pipe({
  name: 'patientIssuer'
})
export class PatientIssuerPipe implements PipeTransform {

  transform(attrs: any, args?: any): any {
      function valueOf(attrs, tag) {
          try{
              return attrs[tag].Value[0];
          }catch (e){
              return false;
          }
      }

      function valueOfItem(attrs, seqTag, tag) {
          try{
              let item = attrs[seqTag].Value[0];
              return valueOf(item, tag);
          }catch (e){
              return false;
          }
      }

      function patientIdentifiersOf(attrs) {
          let pid = valueOf(attrs, '00100020');
          let issuerOfPID = valueOf(attrs, '00100021');
          let typeOfPID = valueOf(attrs, '00100022');
          let issuerOfPIDQualifiersUniversalEntityID = valueOfItem(attrs, '00100024', '00400032');
          let issuerOfPIDQualifiersUniversalEntityIDType = valueOfItem(attrs, '00100024', '00400033');
          let issuerOfPIDQualifiers = issuerOfPIDQualifiersUniversalEntityID === false
                                        ? issuerOfPIDQualifiersUniversalEntityIDType == false
                                            ? false
                                            : '&' + issuerOfPIDQualifiersUniversalEntityIDType
                                        : issuerOfPIDQualifiersUniversalEntityIDType == false
                                            ? issuerOfPIDQualifiersUniversalEntityID + '&'
                                            : issuerOfPIDQualifiersUniversalEntityID + '&' + issuerOfPIDQualifiersUniversalEntityIDType;
          let issuer = issuerOfPID === false
                          ? issuerOfPIDQualifiers === false
                              ? ''
                              : '&' + issuerOfPIDQualifiers
                          : issuerOfPIDQualifiers === false
                              ? issuerOfPID
                              : issuerOfPID + '&' + issuerOfPIDQualifiers;
          return pid === false
                    ? ''
                    : issuer == ''
                        ? typeOfPID === false
                            ? pid
                            : pid + '^^^^' + typeOfPID
                        : typeOfPID === false
                            ? pid + '^^^' + issuer
                            : pid + '^^^' + issuer + '^' + typeOfPID;
      }

      if(j4care.is(args,"dcmuiHideOtherPatientIDs", true)){
          return patientIdentifiersOf(attrs);
      }else{
          const allParts = [patientIdentifiersOf(attrs)]
          if(_.hasIn(attrs,'["00101002"].Value')){
              _.get(attrs,'["00101002"].Value').forEach(subAttrs=>{
                  allParts.push(patientIdentifiersOf(subAttrs));
              })
          }
          return allParts.join(", ");
      }
  }

}
