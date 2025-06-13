import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
    name: 'formatTag',
    standalone: true
})
export class FormatTagPipe implements PipeTransform {

  transform(value: any, args?: any): any {
      return '(' + value.slice(0, 4) + ',' + value.slice(4, 8) + ')';
  }

}
