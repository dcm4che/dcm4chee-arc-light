import {FormElement} from './form-element';
/**
 * Created by shefki on 9/20/16.
 */
export class InputNumber extends FormElement<number>{
    controlType = 'number';
    type: string;

    constructor(options: {} = {}){
        super(options);
        this.type = options['type'] || '';
    }
}