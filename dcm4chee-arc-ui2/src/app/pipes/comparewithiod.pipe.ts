import { Pipe, PipeTransform } from '@angular/core';
import * as _ from "lodash";

@Pipe({
  name: 'comparewithiod'
})
export class ComparewithiodPipe implements PipeTransform {

    transform(value: any, args?: any): any {
        let valcopy = {};
/*
        if(args === ""){
            return value;
        }else{
            console.log("value",value);
            console.log("args",args);
            for(let v in value){
                console.log("v",v);
                console.log("indexof777",v.indexOf('777'))
                console.log("slice",v.slice(0, 3));
                if(!this.isInIod(v,args) && v.indexOf('777') === -1){
                    console.log("indelete",value[v]);
                    console.log("v",v);
                    delete value[v];
                }

            }
            // delete value["00201208"];
            // delete value["00201206"];
            console.log("value",value);
            // console.log('value["00201208"]',value["00201208"]);
            return value;
        }*/
        Object.keys(value).filter(attr => {
            console.log("attr",attr);
            if(this.isInIod(attr,args)){
                valcopy[attr] = {};
                valcopy[attr] = value[attr];
            }
            // return (this.isInIod(attr,args));
        });
        console.log("valcopy");
        return valcopy;
    }
    isInIod(element, iod){
        for(let i in iod){
            if(element === i){
                return true;
            }
        }
        return false;
    }

}
