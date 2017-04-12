import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'utc'
})
export class UtcPipe implements PipeTransform {

  transform(value: any, args?: any): any {
      return new Date(new Date(value).getUTCFullYear(), new Date(value).getUTCMonth(), new Date(value).getUTCDate(),  new Date(value).getUTCHours(), new Date(value).getUTCMinutes(), new Date(value).getUTCSeconds());
  }

}
