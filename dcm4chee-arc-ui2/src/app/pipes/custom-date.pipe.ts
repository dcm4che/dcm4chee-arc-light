import { Pipe, PipeTransform } from '@angular/core';
import {ConfiguredDateTameFormatObject, RangeObject} from "../interfaces";
import {j4care} from "../helpers/j4care.service";
import * as _ from 'lodash-es';

@Pipe({
    name: 'customDate',
    standalone: true
})
export class CustomDatePipe implements PipeTransform {

  transform(value: string, args?: ConfiguredDateTameFormatObject):string {
    try{
        if((value && typeof value === "string") || (_.hasIn(value,"Value[0]") && typeof _.get(value,"Value[0]") === "string")){
            let tempValue;
            if(_.hasIn(value,"Value[0]")){
                tempValue = _.get(value,"Value[0]");
            }else{
                tempValue = value;
            }
            if(args){
                let extractedDateTime:RangeObject = j4care.extractDateTimeFromString(tempValue);
                if(extractedDateTime.firstDateTime.FullYear && extractedDateTime.firstDateTime.Hours){
                //DATE-TIME
                 return j4care.formatDate(extractedDateTime.firstDateTime.dateObject, args.dateTimeFormat) || tempValue;
                }
                if(extractedDateTime.firstDateTime.FullYear && !extractedDateTime.firstDateTime.Hours){
                //DATE
                 return j4care.formatDate(extractedDateTime.firstDateTime.dateObject, args.dateFormat) || tempValue;
                }
                if(!extractedDateTime.firstDateTime.FullYear && extractedDateTime.firstDateTime.Hours){
                //TIME
                 return j4care.formatDate(extractedDateTime.firstDateTime.dateObject, args.timeFormat) || tempValue;
                }
            }else{
                return tempValue;
            }
        }
    }catch (e){
        console.groupCollapsed("Custom Date Pipe params:");
        console.log("value",value);
        console.log("args",args);
        console.error("Formatting doesn't work", e);
        console.groupEnd();
        return value;
    }
    return value;
  }

}
