import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'trim'
})
export class TrimPipe implements PipeTransform {

    transform(value: string, l:any) : string {
        // console.log("value",value);
        // console.log("limit",l);
        let limit = !isNaN(l) ? parseInt(l, 10) : 10;
        let trail = isNaN(limit) ? value : '...';

        return value.length > limit ? value.substring(0, limit) + trail : value;
    }

}
