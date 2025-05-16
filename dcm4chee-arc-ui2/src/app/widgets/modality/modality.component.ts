import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {Globalvar} from "../../constants/globalvar";

@Component({
    selector: 'modality',
    templateUrl: './modality.component.html',
    styles: [`
        .input_field{
            width:100%;
        }
    `],
    standalone: false
})
export class ModalityComponent implements OnInit {

    @Input() model;
    @Output() modelChange = new EventEmitter();
    Object = Object;
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
    closeFromOutside(){
        this.showModalitySelector = false;
    }
    add(){
        this.modelChange.emit(this.model);
        this.showModalitySelector = false;
    }
    ngOnInit() {
    }

}
