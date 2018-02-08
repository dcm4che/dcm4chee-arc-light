import {FormElement} from './form-element';
/**
 * Created by shefki on 3/22/17.
 */

export class Checkbox extends FormElement<string>{
    controlType = 'checkbox';
    options: {key: string, value: any, active: boolean}[] = [];
    search:string = '';

    constructor(options: {} = {}){
        super(options);
        this.options = options['options'] || [];
    }
}