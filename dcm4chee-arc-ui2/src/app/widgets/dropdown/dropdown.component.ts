import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {OptionService} from "./option.service";
import {SelectDropdown} from "../../interfaces";

@Component({
  selector: 'j4care-select',
  templateUrl: './dropdown.component.html',
  styleUrls: ['./dropdown.component.scss']
})
export class DropdownComponent implements OnInit {
    selectedValue:SelectDropdown;

    @Input() model: SelectDropdown;

    @Output() modelChange =  new EventEmitter();

    showDropdown:boolean = false;
    constructor(public service:OptionService) {
        this.service.valueSet$.subscribe(value=>{
            console.log("value",value);
            this.selectedValue = value;
            this.modelChange.emit(value);
        })
    }

    ngOnInit() {
        this.selectedValue = this.model;
    }

    toggleDropdown(){
        this.showDropdown = !this.showDropdown;
    }

}
