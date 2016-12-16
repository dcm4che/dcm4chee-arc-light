import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'contentDescription'
})
export class ContentDescriptionPipe implements PipeTransform {

  transform(value: any, args?: any): any {
      function valueOf(attrs, code) {
          try{
              return attrs[code].Value[0];
          }catch(e){
              return false;
          }
      };
      function valuesOf(attr) {
          return attr && attr.Value && attr.Value.join();
      };
      function imageDescriptionOf(attrs) {
          var cols = valueOf(attrs,"00280011"); // Columns
          return cols && (cols + "x"
              + valueOf(attrs,"00280010") + " " // Rows
              + valueOf(attrs,"00280100") + " bit " // BitsAllocated
              + valuesOf(attrs["00080008"])); // ImageType
      };
      function srDescriptionOf(attrs) {
          var code = valueOf(attrs,"0040A043"); // ConceptNameCodeSequence
          return code && [
                  valueOf(attrs,"0040A496"), // PreliminaryFlag
                  valueOf(attrs,"0040A491"), // CompletionFlag
                  valueOf(attrs,"0040A493"), // VerificationFlag
                  valueOf(code,"0080104")  // CodeMeaning
              ].filter(function (obj) { return obj }).join(" ");
      };
      return valueOf(value,"00700081") // ContentDescription
              || imageDescriptionOf(value)
              || srDescriptionOf(value)
              || valueOf(value,"00420010"); // Document Title
  }

}
