import {Injector, Pipe, PipeTransform} from '@angular/core';
import * as _ from 'lodash';
import {DynamicPipe} from "../helpers/dicom-studies-table/dicom-studies-table.interfaces";

@Pipe({
  name: 'dynamicPipe'
})
export class DynamicPipePipe implements PipeTransform {

    public constructor(private injector: Injector) {
    }

    transform(value: any, dynamicPipe:DynamicPipe, func:Function, ...args): any {
        if (!value) {
            return value;
        }
        else {
            if(dynamicPipe && _.hasIn(dynamicPipe, "pipeToken")){
                let pipe = this.injector.get(dynamicPipe.pipeToken);
                return pipe.transform(value, ...dynamicPipe.pipeArgs);
            }else{
                return func.call(this, value, args);
            }
        }
    }
}
