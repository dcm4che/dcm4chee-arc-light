import {
    AfterContentChecked,
    Component,
    EventEmitter,
    Injector,
    Input,
    OnDestroy,
    OnInit,
    Output, ViewContainerRef
} from '@angular/core';
import {j4care} from "../j4care.service";
import * as _ from 'lodash';
import {AppService} from "../../app.service";
import {DeviceConfiguratorService} from "../../configuration/device-configurator/device-configurator.service";
import {DevicesService} from "../../configuration/devices/devices.service";
import {ConfirmComponent} from "../../widgets/dialogs/confirm/confirm.component";
import {MatDialog, MatDialogConfig, MatDialogRef} from "@angular/material";
import {RangePickerService} from "../../widgets/range-picker/range-picker.service";

@Component({
    selector: 'filter-generator',
    templateUrl: './filter-generator.component.html',
    styleUrls: ['./filter-generator.component.scss']
})
export class FilterGeneratorComponent implements OnInit, OnDestroy, AfterContentChecked {

    @Input() schema;
    @Input() model;
    private _filterTreeHeight;
    @Input() filterID;
    @Input() hideClearButtons;
    @Input() filterIdTemplate;
    @Input() doNotSave;
    @Output() submit  = new EventEmitter();
    @Output() onChange  = new EventEmitter();
    @Output() onTemplateSet  = new EventEmitter();
    @Input() ignoreOnClear; //string[], pas here all filter keys that should be ignored on clear
    @Input() defaultSubmitId:string;
    dialogRef: MatDialogRef<any>;
    cssBlockClass = '';
    hideLoader = false;
    filterForm;
    parentId;
    filterTemplatePath = 'dcmDevice.dcmuiConfig["0"].dcmuiFilterTemplateObject';
    filterTemplates;
    showFilterTemplateList = false;
    showFilterButtons = false;
    hoverActive = false;
    constructor(
        private inj:Injector,
        private appService:AppService,
        private viewContainerRef: ViewContainerRef,
        public dialog: MatDialog,
        public config: MatDialogConfig,
        private deviceConfigurator:DeviceConfiguratorService,
        private devices:DevicesService,
        private rangePicker:RangePickerService
    ) {
        console.log("test",this._filterTreeHeight)
    }
    get filterTreeHeight() {
        return this._filterTreeHeight;
    }

    @Input("filterTreeHeight")
    set filterTreeHeight(value) {
        this._filterTreeHeight = value;
        if(this._filterTreeHeight) {
            this.cssBlockClass = `height_${this._filterTreeHeight}`;
        }
    }

    ngOnInit() {
        if(this._filterTreeHeight) {
            this.cssBlockClass = `height_${this._filterTreeHeight}`;
        }
        if(!this.filterID){
            try{
                this.filterID = `${location.hostname}-${this.inj['view'].parentNodeDef.renderParent.element.name}`;
            }catch (e){
                this.filterID = `${location.hostname}-${location.hash.replace(/#/g,'').replace(/\//g,'-')}`;
            }
        }
        if(!_.isBoolean(this.doNotSave)){
           let savedFilters = localStorage.getItem(this.filterID);
            let parsedFilter = JSON.parse(savedFilters);
            if(this.doNotSave){
                this.doNotSave.forEach(f=>{
                    if(parsedFilter && parsedFilter[f]){
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
        this.onTemplateSet.emit(this.model);
    }

    onKeyUp(e){
        console.log("e",e.code);
        if(e.keyCode === 13){
            this.submitEmit(this.defaultSubmitId);
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
    confirm(confirmparameters){
        this.config.viewContainerRef = this.viewContainerRef;
        this.dialogRef = this.dialog.open(ConfirmComponent, {
            height: 'auto',
            width: '465px'
        });
        this.dialogRef.componentInstance.parameters = confirmparameters;
        return this.dialogRef.afterClosed();
    };
    createNewFilterTemplateToDevice(newTemplateName, device){
        let newObject = {
            dcmuiFilterTemplateDefault:false,
            dcmuiFilterTemplateDescription:'Test description',
            dcmuiFilterTemplateFilters:Object.keys(this.model).filter(m=>{
                return this.model[m];
            }).map(k=>{
                return `${k}=${this.model[k]}`
            }),
            dcmuiFilterTemplateGroupName:newTemplateName,
            dcmuiFilterTemplateID:this.filterIdTemplate || this.filterID
        };
        if(_.hasIn(device, this.filterTemplatePath)){
            (<any[]>_.get(device,this.filterTemplatePath)).push(newObject);
        }else{
            _.set(device, this.filterTemplatePath, [newObject]);
        }
        console.log("device",device);
        return device;
    }

    removeFilterTemplate(filter){
        this.confirm({
            content: 'Are you sure you want to remove this filter-template?'
        }).subscribe((ok)=>{
            if(ok){
                console.log("filter",filter);
            }
        });
    }
    saveFilterTemplate(){
        if(!this.appService.deviceName){
            this.confirm({
                content: 'Archive device name not found, reload the page and try again!'
            }).subscribe((ok)=>{});
        }else{
            console.log("device name",this.appService.deviceName);
            this.deviceConfigurator.getDevice(this.appService.deviceName).subscribe(arch=>{
                console.log("arch",arch);

                this.confirm({
                    content: 'Set the name for the new filter template:',
                    input: {
                        name: 'newdevice',
                        type: 'text'
                    },
                    result: {input: ''},
                    saveButton: 'SAVE'
                }).subscribe((ok)=>{
                    if(ok){
                        console.log("result.input",ok.input);
                        let device = this.createNewFilterTemplateToDevice(ok.input, arch);
                        console.log("device",device);
                        this.devices.saveDeviceChanges(this.appService.deviceName,device).subscribe(res=>{

                        },err=>{
                            console.error(err);
                        })
                    }
                });
            },err=>{
                console.log("arch",err);
            })
        }
    }
    openTemplateList(){
        if(!this.appService.deviceName){
            this.confirm({
                content: 'Archive device name not found, reload the page and try again!'
            }).subscribe((ok)=>{});
        }else{
            console.log("device name",this.appService.deviceName);
            this.showFilterTemplateList = true;
            this.deviceConfigurator.getDevice(this.appService.deviceName).subscribe(arch=>{
                if(_.hasIn(arch,this.filterTemplatePath)){
                    this.filterTemplates = (<any[]>_.get(arch,this.filterTemplatePath)).filter(filter=>{
                        return filter.dcmuiFilterTemplateID === this.filterIdTemplate;
                    });
                }else{
                    console.log("no filter template found");
                }
            },err=>{
                console.error(err);
            });
        }
    }
    mouseEnterFilter(){
        this.hoverActive = true;
        this.showFilterButtons = true;
    }
    mouseLeaveFilter(){
        this.hoverActive = false;
        setTimeout(()=>{
            if(this.hoverActive === false){
                this.showFilterTemplateList = false;
                this.showFilterButtons = false;
            }
        },500);
    }
    inFilterClicked(){
        this.showFilterTemplateList = false;
    }
    openTemplateFilter(filter){
        this.showFilterTemplateList = false;
        this.showFilterButtons = false;
        const regex = /(\w*)=(\w*)/;
        let newObject = {};
        let m;
        filter.dcmuiFilterTemplateFilters.forEach(filter=>{
            if ((m = regex.exec(filter)) !== null) {
                    newObject[m[1]] = m[2];
            }
        });
        console.log("newOjbect",newObject);
        this.model = newObject;

    }
    ngOnDestroy(){
        if(!_.isBoolean(this.doNotSave)){
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
}
