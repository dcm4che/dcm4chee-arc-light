import { Pipe, PipeTransform } from '@angular/core';
import * as _ from "lodash";

@Pipe({
  name: 'largeIntFormat'
})
export class LargeIntFormatPipe implements PipeTransform {

  transform(value: any, args?: any): any {
    try {
      if(_.hasIn(value,"value")){
        value = value.value.toString() || value;
      }else{
        value = value.toString() || value;
      }
      if(value && value.length > 3 && value.indexOf('.') === -1){
        let result = '';
        while(value.length > 3){
            result = ' ' + value.slice(-3) + result;
            value = value.slice(0,value.length-3)
        }
        result = value + result;
        return result;
      }
      return value;
    }catch (e){
      return value;
    }
  }

}
