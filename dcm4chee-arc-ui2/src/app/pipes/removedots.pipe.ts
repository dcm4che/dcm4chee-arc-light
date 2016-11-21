import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'removedots'
})
export class RemovedotsPipe implements PipeTransform {

  transform(value: any, args?: any): any {
      return function (value) {
          if(value){
              return value.replace(/\./g, '');
          }else{
              return "";
          }
      };
  }

}
