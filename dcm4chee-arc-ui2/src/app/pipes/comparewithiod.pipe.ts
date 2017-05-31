import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'comparewithiod'
})
export class ComparewithiodPipe implements PipeTransform {

    transform(value: any, args?: any): any {
        let valcopy = {};

        Object.keys(value).filter(attr => {
            if (this.isInIod(attr, args)){
                valcopy[attr] = {};
                valcopy[attr] = value[attr];
            }
        });
        return valcopy;
    }
    isInIod(element, iod){
        for (let i in iod){
            if (element === i){
                return true;
            }
        }
        return false;
    }

}
