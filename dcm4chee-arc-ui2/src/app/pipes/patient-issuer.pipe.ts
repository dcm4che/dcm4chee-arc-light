import { Pipe, PipeTransform } from '@angular/core';

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

      function issuerOf(attrs) {
          let issuerOfPID = valueOf(attrs, '00100021');
          let issuerOfPIDQualifiersUniversalEntityID = valueOfItem(attrs, '00100024', '00400032');
          let issuerOfPIDQualifiersUniversalEntityIDType = valueOfItem(attrs, '00100024', '00400033');
          return issuerOfPID === false
                  ? issuerOfPIDQualifiersUniversalEntityID === false
                      ? ''
                      : '&' + issuerOfPIDQualifiersUniversalEntityID + '&' + issuerOfPIDQualifiersUniversalEntityIDType
                  : issuerOfPIDQualifiersUniversalEntityID === false
                      ? issuerOfPID
                      : issuerOfPID + '&' + issuerOfPIDQualifiersUniversalEntityID + '&' + issuerOfPIDQualifiersUniversalEntityIDType;
      }

      return issuerOf(attrs);
  }

}
