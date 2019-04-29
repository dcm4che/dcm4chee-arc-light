import {
    AfterContentInit, AfterViewChecked, ChangeDetectorRef,
    Component,
    ContentChild,
    ContentChildren,
    EventEmitter,
    Input,
    Output,
    QueryList,
    ChangeDetectionStrategy,
} from '@angular/core';
import {OptionService} from "./option.service";
import {SelectDropdown} from "../../interfaces";
import {OptionComponent} from "./option.component";
import {animate, state, style, transition, trigger} from "@angular/animations";
import {SearchPipe} from "../../pipes/search.pipe";

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
    ],
    // changeDetection: ChangeDetectionStrategy.OnPush
})
export class DropdownComponent implements AfterContentInit, AfterViewChecked {
    selectedValue:string;
    selectedDropdown:SelectDropdown<any>;
    @Input() placeholder:string;
    @Input() multiSelectMode:boolean = false;
    @Input() showSearchField:boolean = false;
    uniqueId;
    @Input() maxSelectedValueShown = 2;
    @Input('model')
    set model(value){
        if(!(this.selectedDropdown && this.selectedDropdown.value === value) && !this.multiSelectMode){
            this.selectedValue = value;
            this.selectedDropdown  = this.getSelectDropdownFromValue(value);
            this.setSelectedElement();
        }else{
            this.multiSelectValue = value;
            this.setSelectedElement();
        }
    }
    @ContentChild(OptionComponent) template: OptionComponent;
    @ContentChildren(OptionComponent) children:QueryList<OptionComponent>;

    @Output() modelChange =  new EventEmitter();
    showDropdown:boolean = false;
    multiSelectValue = [];
    search = '';
    isAllCheck:boolean = false;
    constructor(
        // // private changeDetectorRef: ChangeDetectorRef
    ) {}

    toggleDropdown(){
        this.showDropdown = !this.showDropdown;
    }

    ngAfterContentInit(): void {
        this.children.forEach(result=>{
            setTimeout(()=>{
                result.multiSelectMode = this.multiSelectMode;
                // // this.changeDetectorRef.detectChanges();
            },100);
            result.selectEvent.subscribe(e=>{
               if(this.multiSelectMode){
                   if(e.value === ""){
                       this.multiSelectValue = [];
                       this.isAllCheck = false;
                   }else{
                       if(this.multiSelectValue.indexOf(e.value) > -1){
                           this.multiSelectValue.splice(this.multiSelectValue.indexOf(e.value),1);
                       }else{
                           this.multiSelectValue.push(e.value);
                       }
                   }
                   this.modelChange.emit(this.multiSelectValue);
                    // this.changeDetectorRef.detectChanges();
               }else{
                   this.modelChange.emit(e.value);
                   this.showDropdown = false;
                    // this.changeDetectorRef.detectChanges();
               }
               console.log("multiSelectValue",this.multiSelectValue);
            })
        });
        if(this.selectedValue){
            this.selectedDropdown = this.getSelectDropdownFromValue(this.selectedValue);
        // this.changeDetectorRef.detectChanges();
        }
    }
    searchEvent(){
        this.children.forEach(childe=>{
            if(childe.value.toLowerCase().indexOf(this.search.toLowerCase()) > -1 || (childe.htmlLabel && JSON.stringify(childe.htmlLabel).toLowerCase().indexOf(this.search.toLowerCase()) > -1)){
                childe.showElement = true;
            }else{
                childe.showElement = false;
            }
        });
        // this.changeDetectorRef.detectChanges();
    }
    allChecked(e){
        console.log("e",e);
        console.log("checked",e.target.checked);
        console.log("isAllCheck",this.isAllCheck);
        // this.isAllCheck = e.target.checked;
        if(!this.isAllCheck){
            this.multiSelectValue = [];
        }
        this.children.forEach(element=>{
            element.selected = this.isAllCheck;
            if(this.isAllCheck){
                this.multiSelectValue.push(element.value);
            }
        })
        // this.changeDetectorRef.detectChanges();
    }
    getSelectDropdownFromValue(value):SelectDropdown<any>{
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
        if(this.multiSelectMode){
            if(this.children && this.multiSelectValue){
                let count = 0;
                this.children.forEach(element=>{
                    // console.log("uniqueId3",element.uniqueId);
                    if(this.multiSelectValue.indexOf(element.value) > -1){
                        element.selected = true;
                        count++;
                    }else{
                        element.selected = false;
                    }
                });
                if(count === this.children.length-1){ //TODO make clear optional
                    this.isAllCheck = true;
                }
            }
        }else{
            if(this.children && this.selectedValue){
                this.children.forEach(element=>{
                    if(element.value === this.selectedValue || element.value === this.selectedValue){
                        element.selected = true;
                    }else{
                        element.selected = false;
                    }
                });
            }
        }
        // this.changeDetectorRef.detectChanges();
    }

    ngAfterViewChecked(): void {
        setTimeout(()=>{
            this.setSelectedElement();
        },100);
    }
}
