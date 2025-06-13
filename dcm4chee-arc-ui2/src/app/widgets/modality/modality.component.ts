import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {Globalvar} from "../../constants/globalvar";
import {FormsModule} from '@angular/forms';
import {ClickOutsideDirective} from '../../helpers/click-outside.directive';
import {CommonModule} from '@angular/common';

@Component({
    selector: 'modality',
    templateUrl: './modality.component.html',
    styles: [`
        .input_field {
            width: 100%;
        }
    `],
    imports: [
        FormsModule,
        ClickOutsideDirective,
        CommonModule
    ],
    standalone: true
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
