import {FormElement} from './form-element';
/**
 * Created by shefki on 9/20/16.
 */
export class InputText extends FormElement<string>{
    controlType = 'text';
    type: string;

    constructor(options: {} = {}){
        super(options);
        this.type = options['type'] || '';
    }
}