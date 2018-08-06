import {
    AfterContentChecked,
    Component,
    EventEmitter,
    Injector,
    Input,
    OnDestroy,
    OnInit,
    Output
} from '@angular/core';
import {j4care} from "../j4care.service";
import * as _ from 'lodash';

@Component({
    selector: 'filter-generator',
    templateUrl: './filter-generator.component.html',
    styleUrls: ['./filter-generator.component.scss']
})
export class FilterGeneratorComponent implements OnInit, OnDestroy, AfterContentChecked {

    @Input() schema;
    @Input() model;
    @Output() submit  = new EventEmitter();
    @Output() onChange  = new EventEmitter();
    cssBlockClass = '';
    hideLoader = false;
    filterForm;
    constructor(
        private inj:Injector
    ) { }
    parentId;
    ngOnInit() {
        try{
            this.parentId = `${location.hostname}-${this.inj['view'].parentNodeDef.renderParent.element.name}`;
        }catch (e){
            this.parentId = `${location.hostname}-${location.hash.replace(/#/g,'').replace(/\//g,'-')}`;
        }
       let savedFilters = localStorage.getItem(this.parentId);
       if(savedFilters)
           this.model = _.merge(this.model,JSON.parse(savedFilters));
    }
    submitEmit(id){
        this.model = j4care.clearEmptyObject(this.model);
      if(id){
        this.submit.emit({model:this.model,id:id});
      }else{
        this.submit.emit(this.model);
      }
    }
    filterChange(test){
        this.onChange.emit(this.model);
    }
    clear(){
        // this.model = {};
        Object.keys(this.model).forEach(filter=>{
           this.model[filter] = '';
        });
    }
    trackByFn(index, item) {
        return index; // or item.id
    }
    ngAfterContentChecked(){
        if(!this.hideLoader){
            setTimeout(()=>{
                this.hideLoader = true;
            },100);
        }
    }
    ngOnDestroy(){
        localStorage.setItem(this.parentId, JSON.stringify(this.model));
    }
}
