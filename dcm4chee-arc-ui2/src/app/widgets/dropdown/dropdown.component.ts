import {
    AfterContentInit, AfterViewChecked, ChangeDetectorRef,
    Component,
    ContentChild,
    ContentChildren,
    EventEmitter,
    Input,
    Output,
    QueryList
} from '@angular/core';
import {OptionService} from "./option.service";
import {SelectDropdown} from "../../interfaces";
import {OptionComponent} from "./option.component";
import {animate, state, style, transition, trigger} from "@angular/animations";

@Component({
    selector: 'j4care-select',
    templateUrl: './dropdown.component.html',
    styleUrls: ['./dropdown.component.scss'],
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
export class DropdownComponent implements AfterContentInit, AfterViewChecked {
    selectedValue:string;
    selectedDropdown:SelectDropdown;
    @Input() placeholder:string;
    uniqueId;
    @Input('model')
    set model(value){
        if(!(this.selectedDropdown && this.selectedDropdown.value === value)){
            this.selectedValue = value;
            this.selectedDropdown  = this.getSelectDropdownFromValue(value);
            this.setSelectedElement();
        }
    }
    @ContentChild(OptionComponent) template: OptionComponent;
    @ContentChildren(OptionComponent) children:QueryList<OptionComponent>;

    @Output() modelChange =  new EventEmitter();
    showDropdown:boolean = false;
    constructor() {}

    toggleDropdown(){
        this.showDropdown = !this.showDropdown;
    }

    ngAfterContentInit(): void {
        this.children.forEach(result=>{
           result.selectEvent.subscribe(e=>{
               this.modelChange.emit(e.value);
               this.showDropdown = false;
           })
        });
        if(this.selectedValue){
            this.selectedDropdown = this.getSelectDropdownFromValue(this.selectedValue);
        }
    }

    getSelectDropdownFromValue(value):SelectDropdown{
        let endDropdown:any =  new SelectDropdown(value,'');
        if(value && this.children){
            this.children.forEach(element=>{
                if(element.value === value){
                    endDropdown = element;
                }
            });
        }
        return endDropdown;
    }
    setSelectedElement(){
/*        console.log("insetselectedelement",this.children);
        console.log("selectedValue",this.selectedValue);*/
        if(this.children && this.selectedValue){
            this.children.forEach(element=>{
                // console.log("uniqueId3",element.uniqueId);
                if(element.value === this.selectedValue || element.value === this.selectedValue){
                    element.selected = true;
                }else{
                    element.selected = false;
                }
            });
        }
    }

    ngAfterViewChecked(): void {
        setTimeout(()=>{
            this.setSelectedElement();
        },100);
    }
}
