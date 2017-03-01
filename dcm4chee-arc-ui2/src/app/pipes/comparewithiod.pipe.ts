import { Pipe, PipeTransform } from '@angular/core';
import * as _ from "lodash";

@Pipe({
  name: 'comparewithiod'
})
export class ComparewithiodPipe implements PipeTransform {

    transform(value: any, args?: any): any {
        if(args === ""){
            return value;
        }else{
            console.log("value",value);
            console.log("args",args);
            for(let v in value){
                console.log("v",v);
                if(!this.isInIod(v,args)){
                    delete value[v];
                }
            }
/*            Object.keys(value).map((element)=>{
                console.log("element",element);
                return element;
            });*/

            return value;
            /*            return value.filter((obj)=>{
                            console.log("obj",obj)
                            let keys = Object.keys(obj);
                            console.log("keys",keys);
                            return true;
            /!*
                            let objString = JSON.stringify(obj).toLowerCase();
                            _.each(keys,(k)=>{
                                let re = new RegExp('"'+k.toLowerCase()+'"',"g");
                                objString = objString.replace(re,'');
                            });
                            return objString.indexOf(args.toLowerCase()) !== -1;*!/
                        });*/
        }
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
