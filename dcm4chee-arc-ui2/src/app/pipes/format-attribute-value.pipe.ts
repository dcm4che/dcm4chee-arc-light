import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
    name: 'formatAttributeValue',
    standalone: false
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
          return value.BulkDataURI || '';
      }catch (e){
          return value;
      }
  }

}
