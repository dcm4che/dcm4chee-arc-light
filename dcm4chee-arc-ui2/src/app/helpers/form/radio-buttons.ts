

import {FormElement} from './form-element';

export class RadioButtons extends FormElement<string>{
    controlType = 'radio';
    options: {key: string, value: any}[] = [];

    constructor(options: {} = {}){
        super(options);
        this.options = options['options'] || [];
    }
}