import {
    ChangeDetectorRef,
    Component,
    ContentChild,
    ContentChildren,
    EventEmitter,
    Input,
    OnInit,
    Output,
    QueryList
} from '@angular/core';
import {OptionComponent} from "../dropdown/option.component";
import {SelectDropdown} from "../../interfaces";
import {animate, state, style, transition, trigger} from "@angular/animations";

@Component({
    selector: 'dcm-drop-down',
    templateUrl: './dcm-drop-down.component.html',
    styleUrls: ['./dcm-drop-down.component.scss'],
    animations:[
        trigger("showHide",[
            state("show",style({
                padding:"*",
                height:'*',
                opacity:1
            })),
            state("hide",style({
                padding:"0",
                opacity:0,
                height:'0px',
                margin:"0"
            })),
            transition("show => hide",[
                animate('0.1s')
            ]),
            transition("hide => show",[
                animate('0.2s cubic-bezier(.52,-0.01,.15,1)')
            ])
        ])
    ]
})
export class DcmDropDownComponent implements OnInit {
    selectedValue:any;
    selectedDropdown:SelectDropdown<any>;
    isAllCheck:boolean = false;
    multiSelectValue = [];
    search = "";
    addFieldPlaceholder = $localize `:@@add_element:Add element`;
    @Input() placeholder:string;
    @Input() multiSelectMode:boolean = false;
    @Input() showSearchField:boolean = false;
    @Input() mixedMode:boolean = false;
    @Input() maxSelectedValueShown = 2;
    @Input() showSelectedEmptyValue:boolean = false;
    private _options:SelectDropdown<any>[];
    @Input()
    set options(values:SelectDropdown<any>[]){
        this._options = values;
        if(values){
            values.forEach(((option:SelectDropdown<any>)=>{
                if(option.selected){
                    this.selectedDropdown = option;
                    this.selectedValue = option.value;
                }
            }))
        }else{
            this.selectedDropdown = undefined;
            this.selectedValue = undefined;
        }
    };
    get options():SelectDropdown<any>[]{
        return this._options;
    }
    _optionsTree;
    @Input()
    set optionsTree(value:{label:string, options:SelectDropdown<any>[]}[]){
        this._optionsTree = value;
        let count = 0;
        if(value){
            try{
                value.forEach(el=>{
                    el.options.forEach(option=>{
                        if(option.selected){
                            this.selectedDropdown = option;
                            this.selectedValue = option.value;
                            count++;
                        }
                    });
                });
                if(this.multiSelectValue && count != this.multiSelectValue.length){
                    this.selectElementInTreeByValue(this.multiSelectValue);
                }
            }catch (e) {
                console.error("Value not undefined but selectedValue could not be set",e);
                console.log("Value:",value);
            }
        }
    }
    get optionsTree(){
        return this._optionsTree;
    }
    @Input() editable:boolean = false;
    @Input() min:number;
    @Input() max:number;
    @Input() showStar:boolean = false;
    @Input('model')
    set model(value){
        if(!(this.selectedDropdown && this.selectedDropdown.value === value) && !this.multiSelectMode){
            if(value){
                this.selectedValue = value;
                this.selectedDropdown  = this.getSelectDropdownFromValue(value);
                this.setSelectedElement();
            }else{
                this.clearSelection();
            }
        }else{
            if(this.multiSelectMode){
                if(value && typeof value === "string"){
                    if(value.indexOf(",") > -1 ){
                        this.multiSelectValue = value.split(",");
                    }else{
                        this.multiSelectValue = [value];
                    }
                }else{
                    this.multiSelectValue = value || [];
                }
                this.setSelectedElement();
            }
        }
    }

    get model(){
        if(this.multiSelectMode){
            this.modelChange.emit(this.multiSelectValue);
            return this.multiSelectValue;
        }else{
            this.modelChange.emit(this.selectedValue);
            return this.selectedValue;
        }
    }
    @Output() modelChange =  new EventEmitter();

    showDropdown = false;
    constructor(
        private changeDetectorRef:ChangeDetectorRef
    ){}

    ngOnInit() {
    }

    inputChangedManually(e){
       console.log("e",e);
       this.selectedDropdown = undefined;
       this.modelChange.emit(this.selectedValue);
    }

    selectOptionByValue(value:string|string[]){
        if(this.multiSelectMode){

        }else{

        }
    }
    clearSelection(){
        this.selectedValue = undefined;
        this.selectedDropdown = undefined;
        if(this.options){
            this.options.forEach(option=>{
                option.selected = false;
            })
        }
    }
    getSelectDropdownFromValue(value):SelectDropdown<any>{
        if(value && this.options){
            for(let element of this.options){
                if(element.value === value){
                    return element;
                }
            };
        }
        return undefined;
    }
    selectElementInTreeByValue(values){
        try {
            let valueCopy = Array.from(values);
            let i = valueCopy.length;
            while(i--){
                this._optionsTree.forEach(optionBlock=>{
                    optionBlock.options.forEach((option:SelectDropdown<any>)=>{
                        if(valueCopy[i] === option.value){
                            option.selected = true;
                            valueCopy.splice(i,1);
                        }
                    });
                });
            }
            if(valueCopy.length > 0){
                valueCopy.forEach((value:string)=>{
                    this.optionsTree[0].options.unshift(new SelectDropdown(value, value,undefined,undefined,undefined,undefined,true));
                })
            }
        }catch (e) {
            console.error(e);
        }
    }
    allChecked(e){
        this.multiSelectValue = [];
        this.options.forEach(element=>{
            element.selected = this.isAllCheck;
            if(this.isAllCheck){
                this.multiSelectValue.push(element.value);
            }
        });
        this.modelChange.emit(this.multiSelectValue);
        // this.changeDetectorRef.detectChanges();
    }
    setSelectedElement(){
        if(this.multiSelectMode){
            if(this.options && this.multiSelectValue){
                let count = 0;
                this.options.forEach(element=>{
                    // console.log("uniqueId3",element.uniqueId);
                    if(this.multiSelectValue.indexOf(element.value) > -1){
                        element.selected = true;
                        count++;
                    }else{
                        element.selected = false;
                    }
                });
                if(count === this.options.length){ //TODO make clear optional
                    this.isAllCheck = true;
                }else{
                    this.isAllCheck = false;
                }
                this.modelChange.emit(this.multiSelectValue);
            }else{
                console.warn("in tree",this.optionsTree);
                console.log("mutliselectvalue",this.multiSelectValue);
            }
        }else{
            if(this.options && this.selectedValue){
                this.options.forEach(element=>{
                    if(element.value === this.selectedValue || element.value === this.selectedValue){
                        element.selected = true;
                    }else{
                        element.selected = false;
                    }
                });
                this.modelChange.emit(this.selectedValue);
            }else{
                console.error("in else",this.options,"selectedValue",this.selectedValue);
            }
        }

        // this.changeDetectorRef.detectChanges();
    }
    customTreeElement;
    addCustomElement(e){
        try{
            this.optionsTree[0].options.unshift(new SelectDropdown(this.customTreeElement,this.customTreeElement));
            this.customTreeElement = "";
        }catch (e) {
            
        }
    }
    select(element){
        if(this.multiSelectMode){
            if(element === ""){
                this.multiSelectValue = [];
                this.options.forEach(option=>{
                    option.selected = false;
                });
                this.isAllCheck = false;
            }else{
                let index = this.multiSelectValue.indexOf(element.value);
                if(index> -1){
                    this.multiSelectValue.splice(index, 1);
                    element.selected = false;
                }else{
                    this.multiSelectValue.push(element.value);
                    element.selected = true;
                }
            }
            if((this.options && this.multiSelectValue.length === this.options.length) || (this.optionsTree && this.multiSelectValue.length === this.optionsTree.length) ){
                this.isAllCheck = true;
            }else{
                this.isAllCheck = false;
            }
            this.modelChange.emit(this.multiSelectValue);
        }else{
            if(!this.mixedMode && this.options){
                this.options.forEach(option =>{ option.selected = false;});
            }
            if(element === ""){
                this.selectedValue = "";
                this.selectedDropdown = undefined;
            }else{
                element.selected = true;
                this.selectedDropdown = element;
                this.selectedValue = element.value;
            }
            this.showDropdown = false;
            this.modelChange.emit(this.selectedValue);
        }
    }
    toggleDropdown(){
        this.showDropdown = !this.showDropdown;
    }
}
