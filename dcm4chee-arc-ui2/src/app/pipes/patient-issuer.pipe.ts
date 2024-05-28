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
          //pie^^^issuerOfPID&universalentitiid&universalentitiidtype^typeOfPID
          let issuerOfPIDQualifiers:string|boolean = issuerOfPIDQualifiersUniversalEntityID === false
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
          const tooltipVersion = pid === false
              ? ''
              : issuer == ''
                  ? typeOfPID === false
                      ? `${pid}`
                      : `${pid}` + '^^^^' + typeOfPID
                  : typeOfPID === false
                      ? `${pid}` + '^^^' + issuer
                      : `${pid}` + '^^^' + issuer + '^' + typeOfPID;
          const htmlVersion = pid === false
                    ? ''
                    : issuer == ''
                        ? typeOfPID === false
                            ? `<b>${pid}</b>`
                            : `<b>${pid}</b><span>${typeOfPID}</span>`
                        : typeOfPID === false
                            ? `<b>${pid}</b><span>${issuer}</span>`
                            : `<b>${pid}</b><span>${issuer}^${typeOfPID}</span>`;
          if(tooltipVersion != ""){
              return {
                  tooltip:tooltipVersion,
                  html:`<span class="mixed_size">${htmlVersion}</span>`
              }
          }
          return "";
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
          if(allParts && allParts[0] && typeof allParts[0] === "string"){
            return allParts.join(", ");
          }else{
              let tooltipPart  = [];
              let htmlPart = "";
              allParts.forEach((part:any)=>{
                  if(part && part.tooltip){
                    tooltipPart.push(part.tooltip);
                  }
                  htmlPart += part.html || "";
              });
              return {
                  tooltip:tooltipPart.join(", "),
                  html:htmlPart
              }
          }
      }
  }

}
