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
    @Input() filterTreeHeight;
    @Input() filterID;
    @Input() doNotSave;
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
        if(this.filterTreeHeight) {
            this.cssBlockClass = `height_${this.filterTreeHeight}`;
        }
        if(!this.filterID){
            try{
                this.filterID = `${location.hostname}-${this.inj['view'].parentNodeDef.renderParent.element.name}`;
            }catch (e){
                this.filterID = `${location.hostname}-${location.hash.replace(/#/g,'').replace(/\//g,'-')}`;
            }
        }
       let savedFilters = localStorage.getItem(this.filterID);
        let parsedFilter = JSON.parse(savedFilters);
        if(this.doNotSave){
            this.doNotSave.forEach(f=>{
                if(parsedFilter[f]){
                    delete parsedFilter[f];
                }
            })
        }
       if(savedFilters){
           this.model = _.mergeWith(this.model, parsedFilter,(a, b)=>{
               if(a){
                   return a;
               }
               if(!a && a != '' && b){
                   return b;
               }else{
                   return a;
               }
           });
       }
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
    dateChanged(key, e){
        if(e){
            this.model[key] = e;
        }else{
            delete this.model[key];
        }
        this.filterChange(e);
    }
    splitDateRangeChanged(e){
        if(e){
            this.model['SplitStudyDateRange'] = e;
        }else{
            delete this.model['SplitStudyDateRange'];
        }
        this.filterChange(e);
    }
    ngOnDestroy(){
        if(this.doNotSave){
            this.doNotSave.forEach(f=>{
                if(this.model[f]){
                    delete this.model[f];
                }
            })
        }
        localStorage.setItem(this.filterID, JSON.stringify(this.model));
    }
}
