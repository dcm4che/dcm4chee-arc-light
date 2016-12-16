import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'formatTM'
})
export class FormatTMPipe implements PipeTransform {

  transform(value: any, args?: any): any {
      if (!value || value.length < 3)
          return value;
      if (value.charAt(2) === ":")
          return value.substr(0, 8);
      var hh_mm = value.substr(0, 2) + ":" + value.substr(2, 2);
      return value.length < 5 ? hh_mm : hh_mm + ":" + value.substr(4, 2);

  }

}
