/**
 * Created by shefki on 9/20/16.
 */

import {Injectable} from '@angular/core';
import {FormElement} from './form-element';
import {FormControl, Validators, FormGroup, FormBuilder, AbstractControl} from '@angular/forms';
import * as _ from 'lodash';
import {CustomValidatorDirective} from '../custom-validator/custom-validator.directive';

function testValidation(c: AbstractControl){
    console.log('c');
}
@Injectable()
export class FormService{
    constructor(private _fb: FormBuilder){}

    toFormGroup(formelements: FormElement<any>[]):FormGroup{
        return this._fb.group(this.convertFormElement(formelements));
    }

    private convertFormElement(formelements: FormElement<any>[]){
        let group: any = {};
        let $this = this;
        formelements.forEach(element => {
            let validation: any;
            let validationElement = 0;
            if (_.hasIn(element, 'validation')){
                if (_.size(element['validation']) > 1){
                    let validationArray = [];
                    _.forEach(element['validation'], (m, i) => {
                        switch (i){
                            case 'minimum':
                                validationArray.push(CustomValidatorDirective.min(m));
                                break;
                            case 'maximum':
                                validationArray.push(CustomValidatorDirective.max(m));
                                break;
                            case 'pattern':
                                validationArray.push(CustomValidatorDirective.regExp(m));
                                break;
                            default:
                                if (i === 'required' && m === true){
                                    validationArray.push(Validators.required);
                                    if (_.hasIn(element, 'validation.required') && element['validation'].required){
                                        if (_.hasIn(element, 'options')){
                                            if (element['controlType'] === 'checkbox' || element['controlType'] === 'arrayelement'){
                                                validationArray.push(CustomValidatorDirective.requiredArray(element['options']));
                                            }else{
                                                validationArray.push(CustomValidatorDirective.required(element['options']));
                                            }
                                        }else{
                                            validationArray.push(Validators.required);
                                        }
                                    }
                                }
                        }
                    });
                    validation = Validators.compose(validationArray);
                }else{
                    if (_.hasIn(element, 'validation.required') && element['validation'].required){
                        if (_.hasIn(element, 'options') || element['controlType'] === 'arrayelement'){
                            if (element['controlType'] === 'checkbox' || element['controlType'] === 'arrayelement'){
                                validation = CustomValidatorDirective.requiredArray(element['options']);
                            }else{
                                validation = CustomValidatorDirective.required(element['options']);
                            }
                        }else{
                            validation = Validators.required;
                        }
                    }
                }
            }
            switch (element.controlType) {
                case 'arrayobject':
                        let arr: FormGroup[] = [];
                        let locobj = {};
                        element['options'].forEach((option: any) => {
                            option['element'].forEach((e: any) => {
                                if (e.controlType === 'arrayobject'){
                                    let subgroup = this.convertFormElement([e]);
                                    locobj[e.key] = subgroup[e.key];
                                }else{
                                    if (e.controlType === 'arrayelement'){
                                        locobj[e.key] = e.value || [''];
                                    }else{
                                        locobj[e.key] = e.value || '';
                                    }
                                }
                            });
                            arr.push($this._fb.group(this.getFormControlObject(locobj)));
                        });
                        group[element.key] = $this._fb.array(arr);
                    break;
                case 'arrayelement':
                    let singleElementValues: FormControl[] = [];
                    console.log('element.value', element.value);
                    if (element.value){
                        if (element['type'] === 'number'){
                            _.forEach(element.value, (value: string) => {
                                singleElementValues.push($this._fb.control(parseInt(value)));
                            });
                        }else{
                            _.forEach(element.value, (value: string) => {
                                singleElementValues.push($this._fb.control(value));
                            });
                        }
                        group[element.key] = validation ? $this._fb.array(singleElementValues, validation) : $this._fb.array(singleElementValues);
                    }else{
                        if (element['type'] === 'number'){
                            group[element.key] = validation ? $this._fb.array([$this._fb.control(singleElementValues)], validation) : $this._fb.array([$this._fb.control(singleElementValues)]);
                        }else{
                            group[element.key] = validation ? $this._fb.array([$this._fb.control('')], validation) : $this._fb.array([$this._fb.control('')]);
                        }
                    }
                    break;
                case 'checkbox':
                    let checkboxArr = [];
                    element['options'].forEach((option: any) => {
                        if (option.active){
                            checkboxArr.push($this._fb.control(option.value));
                        }
                    });
                    group[element.key] = validation ? $this._fb.array(checkboxArr, validation) : $this._fb.array(checkboxArr);
                    break;
                case 'dynamiccheckbox':
                    if(element['type'] === 'array'){
                        element['value'] = element['value'] || [];
                        group[element.key] = validation ? $this._fb.array(element['value'], validation) : $this._fb.array(element['value']);
                    }else{
                        element['value'] = element['value'] || '';
                        group[element.key] = validation ? $this._fb.control(element['value'], validation)
                            : $this._fb.control(element['value']);

                    }
                    break;
                default:
                    if (element.key){
                        if (element['type'] === 'number'){
                            let localValue;
                            if(element.value || element.value === 0){
                                localValue = element.value;
                            }else{
                                localValue = NaN;
                            }
                            group[element.key] = validation ? $this._fb.control(localValue, validation)
                                : $this._fb.control(localValue);
                        }else{
                            let tempValue = "";
                            if(element.value || element.value === false){
                                tempValue = element.value;
                            }
                            group[element.key] = validation ? $this._fb.control(tempValue, validation)
                                : $this._fb.control(tempValue);
/*                            //Adding search to dropdown
                            if(element.controlType === "buttondropdown"){
                                group['search_'+element.key] = $this._fb.control('');
                            }*/
                        }
                    }
            }
        });
        return group;
    }
    private getFormControlObject(keys: any){
        let retobj: any = {};
        let $this = this;
        Object.keys(keys).forEach(function(key) {
            if (typeof keys[key] != 'object'){
                retobj[key] = $this._fb.control(keys[key]);
            }else{
                if (Array.isArray(keys[key])){
                    let tmpArr: FormControl[] = [];
                    keys[key].forEach((kayvalue: any) => {
                        tmpArr.push($this._fb.control(kayvalue));
                    });
                    retobj[key] = $this._fb.array(tmpArr);
                }else{
                    retobj[key] = keys[key];
                }
            }
        });
        return retobj;
    }
}