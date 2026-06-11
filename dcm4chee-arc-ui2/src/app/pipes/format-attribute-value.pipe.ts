import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
    name: 'formatAttributeValue',
    standalone: true
})
export class FormatAttributeValuePipe implements PipeTransform {

  transform(value: any, args?: any): any {
      try{
          if (value && value.Value && value.Value.length) {
              switch (value.vr) {
                  case 'SQ':
                      return value.Value.length + $localize `:@@items: Item(s)`;
                  case 'PN':
                      if (value.Value && value.Value[0]){
                          return value.Value.map(function(value){
                              return value.Alphabetic;
                          }).join();
                      }else{
                          return '';
                      }
                  default:
                      return value.Value.join();
              }
          }
          if(value && value.InlineBinary){
              return this.decodeInlineBinary(value.InlineBinary);
          }
          return value.BulkDataURI || '';
      }catch (e){
          return value;
      }
  }
    decodeInlineBinary(inlineBinary) {
        try {
            // Base64 -> bytes
            const binary = atob(inlineBinary);
            const bytes = Uint8Array.from(binary, c => c.charCodeAt(0));

            // Decode as UTF-8
            const text = new TextDecoder('utf-8', { fatal: false }).decode(bytes);

            // Check whether enough printable characters exist
            const printable = text.replace(/[\x20-\x7E]/g, '').length;
            const ratio = printable / text.length;

            if (ratio < 0.3) {
                return text.trim();
            }
                console.log("InlineBinary Data: ",bytes);
            return `[InlineBinary Data: ${bytes.length} bytes]`;
        } catch (e) {
            return '[Invalid Base64]';
        }
    }

}
