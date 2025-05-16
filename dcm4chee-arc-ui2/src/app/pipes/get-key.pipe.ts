import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
    name: 'getKey',
    standalone: false
})
export class GetKeyPipe implements PipeTransform {

    transform(value, args?: string[]) : any {
        let keys = [];
        for (let key in value) {
            keys.push({_KEY: key, _VALUE: value[key]});
        }
        return keys;
    }

}
