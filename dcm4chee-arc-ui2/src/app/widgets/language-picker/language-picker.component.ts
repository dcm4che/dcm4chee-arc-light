import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {Globalvar} from "../../constants/globalvar";
import {SearchPipe} from "../../pipes/search.pipe";
import * as _ from 'lodash-es';

@Component({
  selector: 'language-picker',
  templateUrl: './language-picker.component.html',
  styleUrls: ['./language-picker.component.scss']
})
export class LanguagePickerComponent implements OnInit {
  Object = Object;
  @Input() dictionary;
  @Input() formelement;
  @Output() onValueSet = new EventEmitter();
  filter = '';
  dcmTags = [];
  constructor() { }

  ngOnInit(): void {
      _.forEach(Globalvar.LANGUAGES.getAllLanguages,(m,i)=>{
          this.dcmTags.push({
              key:`${i}|${m.name}|${m.nativeName}|${m.flag}`,
              text:`<img src="${m.flag}" width="15"/> ${m.name} - ${m.nativeName}(${i})`
          })
      });
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
