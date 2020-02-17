import {Directive} from '@angular/core';
import {AbstractControl, NG_VALIDATORS, ValidatorFn} from '@angular/forms';
import * as _ from 'lodash';

@Directive({
    selector: '[appCustomValidator]',
    providers: [{ provide: NG_VALIDATORS, useExisting: CustomValidatorDirective, multi: true }]
})
export class CustomValidatorDirective{

    static requiredArray(options: any): ValidatorFn {
        return (control: AbstractControl): {[key: string]: any} => {
            // {'msg': {'requiredMax': min, 'actual': control.value}}
/*            console.log("options",options);
            let oneOfOptionsActive = false;
            _.forEach(options,(m,i)=>{
                if(m.active && m.active === true){
                    oneOfOptionsActive = true;
                }
            })*/
            let check = (control.value === undefined || control.value === null || control.value === '' || (_.size(control.value) < 1) || (_.isArray(control.value) && control.value[0] === ''));
            return check ?
            {'msg': $localize `:@@field_required:This field is required!`} :
                null;
        };
    }
    static required(options: any): ValidatorFn {
        return (control: AbstractControl): {[key: string]: any} => {
            // {'msg': {'requiredMax': min, 'actual': control.value}}
            let oneOfOptionsActive = false;
            _.forEach(options, (m, i) => {
                if (m.active && m.active === true){
                    oneOfOptionsActive = true;
                }
            });
            let check = ((control.value === undefined || control.value === null || control.value === '') && !oneOfOptionsActive);
            return check ?
            {'msg': $localize `:@@field_required:This field is required!`} :
                null;
        };
    }
    static min(min: number): ValidatorFn {
        return (control: AbstractControl): {[key: string]: any} => {
            if (!control.value && control.value != "0") {
                return null;  // don't validate empty values to allow optional controls
            }
            // {'msg': {'requiredMax': min, 'actual': control.value}}
            return control.value < min ?
            {'msg': $localize `:@@given_value_smaller_than_min:The given value ${control.value} is smaller than the allowed min value ${min}!`} :
                null;
        };
    }

    static max(max: number): ValidatorFn {
        return (control: AbstractControl): {[key: string]: any} => {
            if (!control.value && control.value != "0") {
                return null;  // don't validate empty values to allow optional controls
            }
            // {'msg': {'requiredMax': max, 'actual': control.value}} :
            return control.value > max ?
            {'msg': $localize `:@@given_value_bigger_than_max:The given value ${control.value} is bigger than the allowed max value ${max}!`} :
                null;
        };
    }
    static regExp(patern: string): ValidatorFn {
        return (control: AbstractControl): {[key: string]: any} => {
            if (!control.value) {
                return null;  // don't validate empty values to allow optional controls
            }
            let re = new RegExp(patern, 'g');
            // {'msg': {'pattern': patern, 'value': control.value}} :
            return (re.exec(control.value) === null) ?
            {'msg': $localize `:@@value_not_valid_string:The given value is not a valid string!`} :
                null;
        };
    }
}
