/**
 * Created by shefki on 9/20/16.
 */

import {Injectable} from "@angular/core";
import {FormElement} from "./form-element";
import {FormControl, Validators, FormGroup, FormArray, FormBuilder} from "@angular/forms";

@Injectable()
export class FormService{
    constructor(private _fb:FormBuilder){}

    toFormGroup(formelements:FormElement<any>[]){
        // let group:any = {};
        //
        // formelements.forEach(element => {
        //     console.log("+element",element);
        //     if(element.controlType === "array"){
        //         let arr:FormGroup[] = [];
        //         let locobj = {};
        //         element["options"].forEach((option:any) => {
        //             console.log("option=",option);
        //             option["element"].forEach((e:any) =>{
        //                 console.log("e",e);
        //                 locobj[e.key] = e.value || ''
        //             });
        //             arr.push(new FormGroup(this.getFormControlObject(locobj)));
        //         });
        //
        //         group[element.key] = new FormArray(arr);
        //         ////////////////////Test v
        //         // let testobj = {};
        //         // let testobj2 = {};
        //         // let testarr:any[] = [];
        //         // // testobj["testkey"] = new FormControl("");
        //         // // testobj["testke2y"] = new FormControl("");
        //         //
        //         // // testobj = {
        //         // //     "testkey":new FormControl(""),
        //         // //     "testke2y":new FormControl("")
        //         // // }
        //         // let keyarr:any = {};
        //         // keyarr["testkey"] = "value1";
        //         // keyarr["testke2y"] = "value2";
        //         // // testobj2 = {
        //         // //     "testkey":new FormControl(""),
        //         // //     "testke2y":new FormControl("")
        //         // // }
        //         // testarr.push(new FormGroup({
        //         //         "testkey":new FormControl(""),
        //         //         "testke2y":new FormControl("")
        //         //     }));
        //         // // testarr.push(new FormGroup(this.getFormControlObject(keyarr)));
        //         //
        //         // // group['arraytest']= new FormArray([new FormGroup({"testkey":new FormControl("")})]);
        //         // group['arraytest']= this._fb.array([this._fb.group({
        //         //     "testkey":"",
        //         //     "testke2y":""
        //         // })])
        //     }else{
        //         group[element.key] = element.required ? new FormControl(element.value || '', Validators.required)
        //                                             : new FormControl(element.value || '');
        //     }
        //
        //
        // });
        // console.log("group",group);
        return new FormGroup(this.convertFormElement(formelements));
    }
    private convertFormElement(formelements:FormElement<any>[]){
        let group:any = {};
        formelements.forEach(element => {
            switch (element.controlType) {
                case "arrayobject":
                        let arr: FormGroup[] = [];
                        let locobj = {};
                        element["options"].forEach((option: any) => {
                            option["element"].forEach((e: any) => {
                                if(e.controlType=== "arrayobject"){
                                    let subgroup = this.convertFormElement([e]);
                                    locobj[e.key] = subgroup[e.key];
                                }else{
                                    if(e.controlType === "arrayelement"){
                                        locobj[e.key] = e.value || [""];
                                    }else{
                                        locobj[e.key] = e.value || ''
                                    }
                                }
                            });
                            arr.push(new FormGroup(this.getFormControlObject(locobj)));
                        });
                        group[element.key] = new FormArray(arr);
                    break;
                case "arrayelement":
                    let singleElementValues:FormControl[] = [];
                    if(element.value){
                        if(element["type"] === "number"){
                            element.value.forEach((value:string) => {
                                singleElementValues.push(new FormControl(parseInt(value)));
                            });
                        }else{
                            element.value.forEach((value:string) => {
                                singleElementValues.push(new FormControl(value));
                            });
                        }
                        group[element.key] = new FormArray(singleElementValues);
                    }else{
                        if(element["type"] === "number"){

                            group[element.key] = new FormArray([new FormControl(singleElementValues)]);
                        }else{
                            group[element.key] = new FormArray([new FormControl("")]);
                        }
                    }
                    break;
                case "checkbox":
                    let checkboxSingleElementValues:FormControl[] = [];
                    let checkboxArr = [];
                    element["options"].forEach((option: any) => {
                        console.log("check option",option.value);
                        if(option.active){
                            checkboxArr.push(new FormControl(option.value));
                        }
                    });
                    // group[element.key] = new FormArray([new FormControl("")]);

                    console.log("checkboxArr",checkboxArr);
                    group[element.key] = new FormArray(checkboxArr);
                    break;

                default:
                    group[element.key] = element.required ? new FormControl(element.value || '', Validators.required)
                        : new FormControl(element.value || '');
            }
        });
        return group;
    }
    private getFormControlObject(keys:any){
        let retobj:any = {};
        Object.keys(keys).forEach(function(key) {
            if(typeof keys[key] != "object"){
                retobj[key] = new FormControl(keys[key]);
            }else{
                if(Array.isArray(keys[key])){
                    // retobj[key];
                    let tmpArr:FormControl[] = [];
                    console.log("+++++++keys[key]",keys[key]);
                    keys[key].forEach((kayvalue:any) => {
                        // retobj[key] = retobj[key] || [];
                        tmpArr.push(new FormControl(kayvalue));
                    });
                    retobj[key] = new FormArray(tmpArr);
                }else{
                    retobj[key] = keys[key];
                }
            }
        });
        return retobj;
    }
}