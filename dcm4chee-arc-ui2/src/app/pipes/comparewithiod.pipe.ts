import { Pipe, PipeTransform } from '@angular/core';

/*
* This is a simple ( not deep ) key comparer between two objects.
* The argument parameter ( args ) can contain a simple object ( like IOD )
* or an object and one of the strings "simple"|"rest-only"|"both" in form of an array like [iod, "both"].
*
* Different modes:
* "simple" is the same as if there were put only the iod object, the return is new object that contains only the keys and objects that are also in iod.
*
* "rest-only" will return a new object that will contain only the keys thar are NOT in iod.
*
* "both" will return a tupel in which the first element will be the objects that contains the elements that are in iod and in the second the object that contains the keys that are not in iod
*
* */
@Pipe({
  name: 'comparewithiod'
})
export class ComparewithiodPipe implements PipeTransform {

    transform(value: any, args?: any): any {
        let tempValue:any = {};
        let restVal:any = {};
        let tempIod:any;
        let mode:"simple"|"rest-only"|"both" = "simple"
        if(args instanceof  Array && args.length === 2){
            tempIod = args[0];
            mode = args[1] || "simple";
        }else{
            tempIod = args;
        }
        Object.keys(value).filter(attr => {
            if (this.isInIod(attr, tempIod)){
                tempValue[attr] = {};
                tempValue[attr] = value[attr];
            }else{
                if(mode != "simple"){
                    restVal[attr] = restVal[attr]  || {};
                    restVal[attr] = value[attr];
                }
            }
        });
        if(mode === "rest-only"){
            return restVal;
        }
        if(mode === "both"){
            return [tempValue, restVal];
        }
        return tempValue;
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
