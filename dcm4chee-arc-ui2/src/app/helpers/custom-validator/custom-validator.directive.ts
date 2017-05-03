import { Directive } from '@angular/core';
import {minValidation} from "./custom-validator.validator";
import {AbstractControl, Validator, NG_VALIDATORS, ValidatorFn} from "@angular/forms";

@Directive({
    selector: '[appCustomValidator]',
    providers: [{ provide: NG_VALIDATORS, useExisting: CustomValidatorDirective, multi: true }]
})
export class CustomValidatorDirective {

    static min(min: number): ValidatorFn {
        return (control: AbstractControl): {[key: string]: any} => {
            if (!control.value) {
                return null;  // don't validate empty values to allow optional controls
            }
            return control.value < min ?
            {'min': {'requiredMax': min, 'actual': control.value}} :
                null;
        };
    }

    static max(max: number): ValidatorFn {
        return (control: AbstractControl): {[key: string]: any} => {
            if (!control.value) {
                return null;  // don't validate empty values to allow optional controls
            }
            return control.value > max ?
            {'max': {'requiredMax': max, 'actual': control.value}} :
                null;
        };
    }
    static regExp(patern: string): ValidatorFn {
        return (control: AbstractControl): {[key: string]: any} => {
            if (!control.value) {
                return null;  // don't validate empty values to allow optional controls
            }
            var re = new RegExp(patern, 'g');
            console.log("exec",re.exec(control.value));
            console.log("exec",(re.exec(control.value) === null));
            return (re.exec(control.value) === null) ?
            {'regExp': {'pattern': patern, 'value': control.value}} :
                null;
        };
    }
}
