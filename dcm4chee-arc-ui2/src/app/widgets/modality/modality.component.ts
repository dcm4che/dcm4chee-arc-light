import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {Globalvar} from "../../constants/globalvar";

@Component({
  selector: 'modality',
  templateUrl: './modality.component.html'
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
    ngOnInit() {
    }

}
