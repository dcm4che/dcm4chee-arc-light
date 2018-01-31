/**
 * Created by shefki on 9/20/16.
 */
import {
    Component, Input, ElementRef, ComponentFactoryResolver, ChangeDetectionStrategy,
    ViewContainerRef, ChangeDetectorRef, HostListener
} from '@angular/core';
import {FormGroup, FormControl, FormArray, FormBuilder} from '@angular/forms';
import {DynamicFormComponent} from './dynamic-form.component';
import {FormService} from '../../helpers/form/form.service';
import {FormElement} from '../../helpers/form/form-element';
import * as _ from 'lodash';
import {Router} from '@angular/router';
import {DeviceConfiguratorService} from '../../device-configurator/device-configurator.service';
import {CloneSelectorComponent} from '../dialogs/clone-selector/clone-selector.component';
import {MdDialogRef, MdDialog, MdDialogConfig} from '@angular/material';
import {UploadVendorComponent} from '../dialogs/upload-vendor/upload-vendor.component';
import {ConfirmComponent} from '../dialogs/confirm/confirm.component';
import {Http} from '@angular/http';
import {RemovePartSelectorComponent} from '../dialogs/remove-part-selector/remove-part-selector.component';
import {AppService} from '../../app.service';
import {ControlService} from "../../control/control.service";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {j4care} from "../../helpers/j4care.service";
import {WindowRefService} from "../../helpers/window-ref.service";

@Component({
    selector: 'df-element',
    templateUrl: './dynamic-form-element.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class DynamicFormElementComponent{

    @Input() formelement: FormElement<any>;
    @Input() formelements: FormElement<any>[];
    @Input() form: FormGroup;
    @Input() partSearch: string;
    dialogRef: MdDialogRef<any>;
    // activetab = "tab_1";
    partRemoved: boolean;
    constructor(
        private formservice: FormService,
        private formcomp: DynamicFormComponent,
        dcl: ComponentFactoryResolver,
        elementRef: ElementRef,
        private router: Router,
        private deviceConfiguratorService: DeviceConfiguratorService,
        public dialog: MdDialog,
        public config: MdDialogConfig,
        public viewContainerRef: ViewContainerRef,
        public $http:J4careHttpService,
        private ref: ChangeDetectorRef,
        private mainservice: AppService,
        private controlService:ControlService,
        private j4care:j4care,
        private _fb: FormBuilder
    ){
        // dcl.resolveComponentFactory(DynamicFormComponent);
        this.partRemoved = false;
    }
    get isValid(){
        return this.form.controls[this.formelement.key].valid;
    }
    confirm(confirmparameters){
        this.config.viewContainerRef = this.viewContainerRef;
        this.dialogRef = this.dialog.open(ConfirmComponent, {
            height: 'auto',
            width: '500px'
        });
        this.dialogRef.componentInstance.parameters = confirmparameters;
        /*        this.dialogRef.afterClosed().subscribe(result => {
         if(result){
         console.log("result", result);
         }else{
         console.log("false");
         }
         });*/
        return this.dialogRef.afterClosed();
    };
    downloadFile(url){
        let $this = this;
        let token;
        if(this.mainservice.global.notSecure){
                WindowRefService.nativeWindow.open(url);
        }else{
            this.$http.refreshToken().subscribe((response)=>{
                if(response && response.length != 0){
                    $this.$http.resetAuthenticationInfo(response);
                    token = response['token'];
                }else{
                    token = this.mainservice.global.authentication.token;
                }
                WindowRefService.nativeWindow.open(url + `?access_token=${token}`);
            });
        }
    }
    deleteFile(deviceName, formelement){
        let $this = this;
        this.confirm({
            content: 'Are you sure you want to delete the vendor data of this device?'
        }).subscribe(result => {
            if (result){
                console.log('delete file form device', deviceName);
                $this.$http.delete(`../devices/${deviceName}/vendordata`).subscribe((res) => {
                    console.log('deleted successfully');
/*                    var globalForm = $this.formcomp.getForm();
                    var valueObject = globalForm.value;
                    valueObject.dicomVendorData = false;
                    $this.formcomp.setFormModel(valueObject);
                    $this.form = $this.formservice.toFormGroup($this.formelements);
                    $this.formcomp.setForm($this.form);*/
/*                    _.forEach(formelement,(m, i)=>{
                        if(m.controlType && m.controlType === "filedownload"){
                            m.controlType = "fileupload";
                        }
                    });*/
                    console.log('formelements', $this.formelements);
                    console.log('formelement', $this.formelement);
                    // $this.formelements = [];
                    // formelement.controlType = "fileupload";
                    let test = {
                        controlType: 'filedownload',
                        description: 'Device specific vendor configuration information',
                        deviceName: 'Testdevi2',
                        downloadUrl: '../devices/Testdevi2/vendordata',
                        key: 'dicomVendorData',
                        label: 'Vendor Device Data',
                        order: 5.02,
                        show: true
                    };
                    let test2 = {
                        controlType: 'fileupload',
                        description: 'Device specific vendor configuration information',
                        deviceName: 'Testdevi2',
                        key: 'dicomVendorData',
                        label: 'Vendor Device Data',
                        modus: 'upload',
                        order: 5.02,
                        show: true
                    };
                    console.log(formelement);
/*
                    $this.router.navigateByUrl(`/device/edit/${deviceName}`);*/
                    // window.location.reload();
/*                    $this.router.navigateByUrl('/DummyComponent', true);
                    $this.router.navigateByUrl(`/device/edit/${deviceName}`);*/
                    // location.reload();
                    $this.deviceConfiguratorService.device = {};
                    $this.deviceConfiguratorService.schema = {};
                    $this.router.navigateByUrl('blank').then(() => {
                        $this.router.navigateByUrl(`/device/edit/${deviceName}`);
                    });
                    // $this.router.navigateByUrl(`/device/edit/${deviceName}`);
                });
            }
        });
    }
    uploadVendor(deviceName){
        let $this = this;
        this.dialogRef = this.dialog.open(UploadVendorComponent, {
            height: 'auto',
            width: '500px'
        });
        this.dialogRef.componentInstance.deviceName = deviceName;
        this.dialogRef.afterClosed().subscribe((selected) => {
            if (selected){
                console.log($this.formelements);
                $this.deviceConfiguratorService.device = {};
                $this.deviceConfiguratorService.schema = {};
                $this.controlService.reloadArchive().subscribe((res) => {
                        $this.mainservice.setMessage({
                            'title': 'Info',
                            'text': 'Archive reloaded successfully',
                            'status': 'info'
                        });
                        $this.router.navigateByUrl('blank').then(() => {
                            $this.router.navigateByUrl(`/device/edit/${deviceName}`);
                        });
                    }, (err) => {
                        $this.router.navigateByUrl('blank').then(() => {
                            $this.router.navigateByUrl(`/device/edit/${deviceName}`);
                        });
                    }
                );
            }
        });
    }
    addElement(element: any, formpart: FormControl[]){
        let globalForm = this.formcomp.getForm();
        let valueObject = globalForm.value;
        element.push(element[0]);
        formpart['options'].push(formpart['options'][0]);
        this.form = this.formservice.toFormGroup(this.formelements);
        this.formcomp.setForm(this.form);
        this.formcomp.setFormModel(valueObject);
    }
    removeObject(formelement, controls){
        let $this = this;
        this.confirm({
            content: 'Are you sure you want to remove this extension and all of its child objects?'
        }).subscribe(result => {
            if (result){
                $this.deviceConfiguratorService.removeExtensionFromDevice(formelement.devicereff);
                _.forEach($this.formelements, (m, i) => {
                    if (m['controlType'] === 'button' && m['devicereff'] === formelement.devicereff){
                        m.value = 0;
                    }
                });
                $this.ref.detectChanges();
            }
        });
    }
    extractIndexFromPath(path){
        if (_.endsWith(path, ']')){
            try {
                let indexStart = path.lastIndexOf('[');
                let index = path.substring(indexStart);
                index = _.replace(index, '[', '');
                index = _.replace(index, ']', '');
                let clearPath = path.substring(0, indexStart);
                return {
                    index: index,
                    path: clearPath
                };
            }catch (e){
                return null;
            }
        }else{
            if(_.startsWith(path,'/dicomNetworkConnection/')){
                try {
                    let indexStart = path.lastIndexOf('/');
                    let index = path.substring(indexStart);
                    index = _.replace(index, '/', '');
                    // index = _.replace(index, ']', '');
                    // let clearPath = path.substring(0, indexStart);
                    return index;
                }catch (e){
                    return null;
                }
            }
        }
    }
    removePart(formelement){
        let $this = this;
        let globalForm = this.formcomp.getForm();
        let value = globalForm.value;
        this.dialogRef = this.dialog.open(RemovePartSelectorComponent, {
            height: 'auto',
            width: '500px'
        });
        this.dialogRef.componentInstance.toRemoveElement = formelement;
        this.dialogRef.afterClosed().subscribe((selected) => {
            if (selected){
                let elementFound = false;
                _.forEach(formelement.options, (m, i) => {
                    if (m === selected){
                        //If removed element is referenced prevent removing it
                        if(formelement.key === "dicomNetworkConnection" && $this.isReferenceUsed($this.deviceConfiguratorService.device, i)){
                            // $this.deviceConfiguratorService.device['dicomNetworkAE'][0]['dicomAETitle'] = "AETITLECHANGED";
                            console.log("$this.deviceConfiguratorService.device",$this.deviceConfiguratorService.device);
                            $this.mainservice.setMessage({
                                'title': 'Warning',
                                'text': `This element is referenced, remove references first then you can delete this element!`,
                                'status': 'warning'
                            });
                        }else{
                            let newAddUrl = formelement.options[formelement.options.length-1].url;
                            formelement.options.splice(i, 1);
                            let check = $this.deviceConfiguratorService.removePartFromDevice($this.extractIndexFromPath(selected.currentElementUrl));
                            if (check){
                                elementFound = true;
                                $this.partRemoved = true;
                                $this.mainservice.setMessage({
                                    'title': 'Info',
                                    'text': `Element removed from object successfully!`,
                                    'status': 'info'
                                });
                                $this.mainservice.setMessage({
                                    'title': 'Click to save',
                                    'text': `Click save if you want to remove "${selected.title}" permanently!`,
                                    'status': 'warning'
                                });
                                formelement.addUrl = newAddUrl;
                                //If removed element was dicomNetworkConnection than update references in the object
                                if(formelement.key === "dicomNetworkConnection"){
                                    $this.updateReferences($this.deviceConfiguratorService.device, i);
                                }
                            }
                        }
                    }else{
                        if (elementFound){
                           let pathObject = $this.extractIndexFromPath(formelement.options[_.toInteger(i) - 1].currentElementUrl);
                            let oldCurrentElementUrl = formelement.options[_.toInteger(i) - 1].currentElementUrl;
                            formelement.options[_.toInteger(i) - 1].currentElementUrl = `${pathObject.path}[${(pathObject.index - 1)}]`;
                            formelement.options[_.toInteger(i) - 1].url = _.replace(formelement.options[_.toInteger(i) - 1].url, oldCurrentElementUrl, formelement.options[_.toInteger(i) - 1].currentElementUrl);
                        }
                    }
                });
                $this.ref.detectChanges();
            }
        });
    }
    //Update DicomNetworkConnection reference index
    updateReferences(o, removedDicomNetworkConnectionIndex) {
        for (let i in o) {
            if(i === "dicomNetworkConnectionReference"){
                for(let index in o[i]){
                    let extracedIndex = this.extractIndexFromPath(o[i][index]);
                    if(extracedIndex > removedDicomNetworkConnectionIndex){
                        o[i][index] = `/dicomNetworkConnection/${(parseInt(extracedIndex) - 1)}`
                    }
                }
            }
            if (o[i] !== null && typeof(o[i])=="object") {
                this.updateReferences(o[i],removedDicomNetworkConnectionIndex);
            }
        }
    }
    isReferenceUsed(o, index){
        let check = { // We need object so i can use as call by reference
            used:false
        };
        this._isReferenceUsed(o,index,check);
        return check.used;
    }
    _isReferenceUsed(o,index,check){
        for (let i in o) {
            if(i === "dicomNetworkConnectionReference"){
                for(let reffIndex in o[i]){
                    if(this.extractIndexFromPath(o[i][reffIndex]) == index){
                        check.used = true;
                    }
                }
            }
            if (o[i] !== null && typeof(o[i])=="object") {
                this._isReferenceUsed(o[i],index,check);
            }
        }
    }

    clone(formelement){
/*        console.log("formelement",formelement);
        let value = (<FormArray>this.form.controls[formelement.key]).getRawValue();
        (<FormArray>this.form.controls[formelement.key]).insert(this.form.controls[formelement.key].value.length, new FormControl(value));*/
        let $this = this;
        let globalForm = this.formcomp.getForm();
        let value = globalForm.value;
        this.dialogRef = this.dialog.open(CloneSelectorComponent, {
            height: 'auto',
            width: '500px'
        });
        this.dialogRef.componentInstance.toCloneElement = formelement;
        this.dialogRef.afterClosed().subscribe((selected) => {
            if (selected){
                if(formelement.key === "dicomNetworkAE"){

                }
                let cloneUrl = formelement.addUrl + '/' + selected.currentElementUrl;
                $this.router.navigateByUrl(cloneUrl);
            }
        });
    }
    addArrayElement(element: any, formpart: FormControl[], form: any){
        formpart = formpart || [];
        element = element || [];
        element.push('');
        let globalForm = this.formcomp.getForm();
        formpart.push(new FormControl(''));
        let valueObject = globalForm.value;
        this.form = this.formservice.toFormGroup(this.formelements);
        this.form.patchValue(valueObject);
        this.formcomp.setForm(this.form);
        this.formcomp.setFormModel(valueObject);
        this.ref.detectChanges();
    }
    removeArrayElement(element: any, i: number, form: any){
        if (element.value.length > i){
            //Remove from react form

            (<FormArray>this.form.controls[element.key]).removeAt(i);
            //reflect the changes to the dome
            _.forEach(this.formelements, (m, j) => {
                if (m.key === element.key){
                    this.formelements[j].value = this.form.value[element.key];
                }
            });
            this.ref.detectChanges();
        }
    }
    checkboxChange(e, formelement){
        if (e.target.checked && !_.hasIn(this.form.controls[formelement.key].value, e.target.defaultValue)){
            (<FormArray>this.form.controls[formelement.key]).insert(this.form.controls[formelement.key].value.length, new FormControl(e.target.defaultValue));
        }else{
            (<FormArray>this.form.controls[formelement.key]).removeAt(_.indexOf(this.form.controls[formelement.key].value, e.target.defaultValue));
        }
    }
    navigateTo(e){
        if (e != '-'){
            this.router.navigateByUrl(e);
        }
    }

    toggleTab(orderId){
        if (this.form.valid){
            let globalForm = this.formcomp.getForm();
            let valueObject = globalForm.value;

            _.forEach(this.formelements, (m, i) => {
               if (Math.floor(m.order) === orderId + 1){
                   if (m.show === true){
                       m.show = false;
                   }else{
                       m.show = true;
                   }
               } else{
                   m.show = false;
               }
            });
            this.form = this.formservice.toFormGroup(this.formelements);
            this.form.patchValue(valueObject);
            this.formcomp.setForm(this.form);
            this.formcomp.setFormModel(valueObject);
        }
        // this.activetab = 'tab_'+(orderId-1);
    }
    onValueChange(e, formelement, formcontrol,i){
        if(e && e != ""){
            if(formelement.controlType === "arrayelement"){
                // (<FormArray>this.form.controls[formelement.key]).insert(i, new FormControl(e))
                formcontrol[i].setValue(e);
                formelement.value[i] = e;
            }else{
                if(formelement.controlType === "dynamiccheckbox"){
                    this.form.setControl(formelement.key,this._fb.array(e));
                }else{
                    formcontrol.setValue(e);
                    formelement.value = e;
                    console.log("in value change",e);
                }
            }
        }
        formelement.showPicker = false;
        formelement.showTimePicker = false;
        formelement.showDurationPicker = false;
        formelement.showSchedulePicker = false;
        formelement.showCharSetPicker = false;
    }
    dropdownChange(formelement,formcontrols){
        if(formelement.options && formelement.options.length > 0){
            let oneOptActive = false;
            //Check active attriubte on change
            _.forEach(formelement.options,(m,i)=>{
                if(m.value === formcontrols.value){
                    oneOptActive = true;
                    m.active = true;
                }else{
                    m.active = false;
                }
            });
            if(!oneOptActive && formcontrols.value === ""){
                formcontrols.setValue("");
            }
        }
    }
    onFocuse(formelement,i=null) {
        if(formelement.format){
            if(formelement.format === 'dcmTag' || formelement.format === 'dcmTransferSyntax' || formelement.format === 'dcmSOPClass'){
                if(i != null){
                    formelement.showPicker = formelement.showPicker || {};
                    formelement.showPicker[i] = true;
                }else{
                    formelement.showPicker = true;
                }
            }
            if(formelement.format === 'dcmTime'){
                if(i != null){
                    formelement.showTimePicker = formelement.showTimePicker || {};
                    formelement.showTimePicker[i] = true;
                }else{
                    formelement.showTimePicker = true;
                }
            }
            if(formelement.format === 'dcmDuration' || formelement.format === 'dcmPeriod'){
                if(i != null){
                    formelement.showDurationPicker = formelement.showDurationPicker || {};
                    formelement.showDurationPicker[i] = true;
                }else{
                    formelement.showDurationPicker = true;
                }
            }
            if(formelement.format === 'dcmCharset' || formelement.format === 'hl7Charset'){
                if(i != null){
                    formelement.showCharSetPicker = formelement.showCharSetPicker || {};
                    formelement.showCharSetPicker[i] = true;
                }else{
                    formelement.showCharSetPicker = true;
                }
            }
            if(formelement.format === 'dcmSchedule'){
                if(i != null){
                    formelement.showSchedulePicker = formelement.showSchedulePicker || {};
                    formelement.showSchedulePicker[i] = true;
                }else{
                    formelement.showSchedulePicker = true;
                }
            }
        }
    }
    onMouseEnter(formelement,i=null){
        if(formelement.format){
            if(formelement.format === 'dcmTag' || formelement.format === 'dcmTransferSyntax' || formelement.format === 'dcmSOPClass'){
                if(i != null){
                    formelement.showPickerTooltipp = formelement.showPickerTooltipp || {};
                    formelement.showPickerTooltipp[i] = true;
                }else{
                    formelement.showPickerTooltipp = true;
                }
            }
        }
    }
    onMouseLeave(formelement){
        formelement.showPickerTooltipp = false;
    }
}
