import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {Globalvar} from "../../constants/globalvar";

@Component({
    selector: 'modality',
    templateUrl: './modality.component.html',
    styles:[`
        .input_field{
            width:100%;
        }
    `]
})
export class ModalityComponent implements OnInit {

    @Input() model;
    @Output() modelChange = new EventEmitter();

    showModalitySelector = false;
    showMore = false;
    modalities = Globalvar.MODALITIES;
    constructor() { }
    selectModality(key){
        this.model = key;
        this.modelChange.emit(this.model);
        this.showModalitySelector = false;
    };
    clear(){
        this.model = "";
        this.modelChange.emit(this.model);
        this.showModalitySelector = false;
    }
    closeFromOutside(e){
        this.showModalitySelector = false;
    }
    add(){
        this.modelChange.emit(this.model);
        this.showModalitySelector = false;
    }
    ngOnInit() {
    }

}
