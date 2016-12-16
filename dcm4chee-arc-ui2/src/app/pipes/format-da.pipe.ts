import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'formatDA'
})
export class FormatDAPipe implements PipeTransform {

  transform(value: any, args?: any): any {
          return (value && value.length == 8)
              ? value.substr(0, 4) + "-" + value.substr(4, 2) + "-" + value.substr(6)
              : value;
  }

}
