/**
 * Created by shefki on 9/20/16.
 */

export class FormElement<T>{
    value: T;
    key: string;
    tag: string;
    label: string;
    validation: any;
    style: any;
    optionsTree: any;
    order: number;
    disabled: boolean;
    showSearchField: boolean;
    showSelectedEmptyValue: boolean;
    onlyDate: boolean;
    description: string;
    joinString: string;
    placeholderElements: string[];
    inputSize:number;
    placeholder: string;
    showStar: boolean;
    controlType: string;
    modelPath:string;
    onChangeHook:Function;
    onFocusOutHook:Function;
    cssClass:string;
    url: string;
    msg: string;
    addUrl: string;
    materialIconName: string;
    title: string;
    show: boolean;
    format: string;
    downloadUrl:string;
    deviceName:string;
    type:string;
    showPicker:boolean;
    showPickerTooltipp:boolean;
    showTimePicker:boolean;
    showDurationPicker:boolean;
    showSchedulePicker:boolean;
    showCharSetPicker:boolean;
    showLanguagePicker:boolean;
    options:any;
    issuers:any;
    showPropertyPicker: string;
    constructor(options: {
        value?: T,
        key?: string,
        tag?: string,
        label?: string,
        validation?: any,
        optionsTree?: any,
        options?: any,
        order?: number,
        style?: string,
        cssClass?: string,
        disabled?: boolean,
        showSearchField?: boolean,
        showSelectedEmptyValue?: boolean,
        joinString?: string,
        inputSize?: number,
        onChangeHook?: Function,
        onFocusOutHook?: Function,
        onlyDate?: boolean,
        showStar?: boolean,
        description?: string,
        placeholder?: string,
        placeholderElements?: string[],
        controlType?: string,
        modelPath?: string,
        type?:string,
        show?: boolean;
        format?: string;
    } = {}) {
        this.value = options.value;
        this.key = options.key || '';
        this.tag = options.tag || '';
        this.label = options.label || '';
        this.style = options.style || '';
        this.cssClass = options.cssClass || '';
        this.validation = options.validation;
        this.optionsTree = options.optionsTree;
        this.options = options.options;
        this.joinString = options.joinString;
        this.onChangeHook = options.onChangeHook;
        this.onFocusOutHook = options.onFocusOutHook;
        this.inputSize = options.inputSize || 1;
        this.disabled = options.disabled || false;
        this.showSelectedEmptyValue = options.showSelectedEmptyValue || false;
        this.onlyDate = options.onlyDate || false;
        this.showStar = options.showStar || false;
        this.showSearchField = options.showSearchField || false;
        this.order = options.order === undefined ? 1 : options.order;
        this.description = options.description || '';
        this.modelPath = options.modelPath || '';
        this.placeholder = options.placeholder || '';
        this.placeholderElements = options.placeholderElements;
        this.controlType = options.controlType || '';
        this.show = options.show || false;
        this.format = options.format || undefined;
        if(options.type){
            this.type = options.type;
        }
    }
}