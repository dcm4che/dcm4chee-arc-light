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
                    : issuer === false
                        ? typeOfPID === false
                            ? pid
                            : pid + '^^^^' + typeOfPID
                        : typeOfPID === false
                            ? pid + '^^^' + issuer 
                            : pid + '^^^' + issuer + '^' + typeOfPID;
      }
      return patientIdentifiersOf(attrs);
  }

}
