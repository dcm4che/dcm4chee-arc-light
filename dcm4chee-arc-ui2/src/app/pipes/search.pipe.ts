import { Pipe, PipeTransform } from '@angular/core';
import * as _ from 'lodash';

@Pipe({
  name: 'search'
})
export class SearchPipe implements PipeTransform {

  transform(value: any, args?: any): any {
      if (args === '' || !args){
          return value;
      }else{
          if (value){
            return value.filter((obj) => {
                let keys = _.keysIn(obj);
                let objString = JSON.stringify(obj).toLowerCase();
                _.each(keys, (k) => {
                    if (k){
                        let re = new RegExp('"' + k.toLowerCase() + '"', 'g');
                        objString = objString.replace(re, '');
                    }
                });
                return objString.indexOf(args.toLowerCase()) !== -1;
            });
          }
      }
  }

}
