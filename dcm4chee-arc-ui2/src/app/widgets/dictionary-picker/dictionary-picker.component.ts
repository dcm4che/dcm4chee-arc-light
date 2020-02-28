import {
    Component, OnInit, EventEmitter, Input, Output
} from '@angular/core';
declare var DCM4CHE: any;
import * as _ from 'lodash';
import {SearchPipe} from "../../pipes/search.pipe";
import {WindowRefService} from "../../helpers/window-ref.service";
import {Globalvar} from "../../constants/globalvar";

@Component({
  selector: 'dictionary-picker',
  templateUrl: './dictionary-picker.component.html',
  styleUrls: ['./dictionary-picker.component.css']
})
export class DictionaryPickerComponent implements OnInit {
    Object = Object;
    @Input() dictionary;
    @Input() formelement;
    @Output() onValueSet = new EventEmitter();
    filter = '';
    dcmTags = [];
    constructor(
    ) { }

    ngOnInit() {
        switch(this.dictionary) {
            case 'dcmTag':
                _.forEach(DCM4CHE.elementName.forTag("all"),(m,i)=>{
                    this.dcmTags.push({
                        key:i,
                        text:m
                    })
                });
                break;
            case 'dcmTransferSyntax':
                _.forEach(DCM4CHE.TransferSyntax.nameOf("all"),(m,i)=>{
                    this.dcmTags.push({
                        key:i,
                        text:m
                    })
                });
                break;
            case 'dcmSOPClass':
                _.forEach(DCM4CHE.SOPClass.nameOf("all"),(m,i)=>{
                    this.dcmTags.push({
                        key:i,
                        text:m
                    })
                });
                break;
            case 'dcmLanguageChooser':
                _.forEach(Globalvar.LANGUAGES.getAllLanguages,(m,i)=>{
                    this.dcmTags.push({
                        key:`<img src="${m.flag}"/>`,
                        text:`${m.nativeName} (${i})`
                    })
                });
                break;
        }
    }
    ngAfterViewInit() {
        WindowRefService.nativeWindow.document.getElementsByClassName("dictionary_widget_search")[0].focus();
        // $('.dictionary_widget_search').focus();
    }
    addSelectedElement(element){
        this.onValueSet.emit(element.key);
    }

    keyDown(e){
        if(e.keyCode === 13){
            let filtered = new SearchPipe().transform(this.dcmTags, this.filter);
            if(filtered.length > 0){
                this.onValueSet.emit(filtered[0].key);
            }
        }
    }
    close(){
        this.onValueSet.emit("");
    }
}
