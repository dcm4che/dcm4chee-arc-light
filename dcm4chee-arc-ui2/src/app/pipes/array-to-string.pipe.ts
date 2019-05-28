import { Pipe, PipeTransform } from '@angular/core';
import * as _ from "lodash";
@Pipe({
    name: 'arrayToString',
    pure: false
})
export class ArrayToStringPipe implements PipeTransform {

    transform(value: any, args?: any): any {
        try{
            if(value){
                let delimiter = args || ", ";
                if(_.isString(value[0])){
                    return value.join(delimiter);
                }else{
                    if(_.hasIn(value,"0.text") || _.hasIn(value,"0.value") || _.hasIn(value,"0.label")){
                        return value.map(el=>{
                            return el.text || el.value || el.label;
                        }).join(delimiter);
                    }else{
                        return value.map(el=>{
                            return _.values(el).filter(e=>_.isString(e))[0];
                        }).join(delimiter);
                    }
                }
            }
        }catch (e) {
            console.log("Error on pipe ArrayToStringPipe",e);
        }
        return null;
    }

}
