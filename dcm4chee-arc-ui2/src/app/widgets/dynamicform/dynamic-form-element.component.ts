/**
 * Created by shefki on 9/20/16.
 */
import {
    Component, Input, ElementRef, OnInit, ComponentFactoryResolver, ChangeDetectionStrategy,
    ViewContainerRef, ApplicationRef, NgZone, ChangeDetectorRef, Inject
} from "@angular/core";
import {FormGroup, FormControl, FormArray} from "@angular/forms";
import {DynamicFormComponent} from "./dynamic-form.component";
import {FormService} from "../../helpers/form/form.service";
import {FormElement} from "../../helpers/form/form-element";
import * as _ from "lodash";
import {Router} from "@angular/router";
import {DeviceConfiguratorService} from "../../device-configurator/device-configurator.service";
import {CloneSelectorComponent} from "../dialogs/clone-selector/clone-selector.component";
import {MdDialogRef, MdDialog, MdDialogConfig} from "@angular/material";
import {FileUploader} from "ng2-file-upload";
import {UploadFilesComponent} from "../dialogs/upload-files/upload-files.component";
import {ConfirmComponent} from "../dialogs/confirm/confirm.component";
import {Http} from "@angular/http";

@Component({
    selector:'df-element',
    templateUrl:'./dynamic-form-element.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class DynamicFormElementComponent{

    @Input() formelement:FormElement<any>;
    @Input() formelements:FormElement<any>[];
    @Input() form:FormGroup;
    @Input() partSearch:string;
    dialogRef: MdDialogRef<any>;
    // activetab = "tab_1";
    constructor(
        private formservice:FormService,
        private formcomp:DynamicFormComponent,
        dcl: ComponentFactoryResolver,
        elementRef: ElementRef,
        private router:Router,
        private deviceConfiguratorService:DeviceConfiguratorService,
        public dialog: MdDialog,
        public config: MdDialogConfig,
        public viewContainerRef: ViewContainerRef,
        public $http:Http,
        private ref: ChangeDetectorRef

    ){
        // dcl.resolveComponentFactory(DynamicFormComponent);

    }
    get isValid(){
        return this.form.controls[this.formelement.key].valid;
    }
    confirm(confirmparameters){
        this.config.viewContainerRef = this.viewContainerRef;
        this.dialogRef = this.dialog.open(ConfirmComponent, this.config);
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
    deleteFile(deviceName,formelement){
        let $this = this;
        this.confirm({
            content:'Are you sure you want to delete the vendor data of this device?'
        }).subscribe(result => {
            if(result){
                console.log("delete file form device",deviceName);
                $this.$http.delete(`../devices/${deviceName}/vendordata`).subscribe((res)=>{
                    console.log("deleted successfully");
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
                    console.log("formelements",$this.formelements);
                    console.log("formelement",$this.formelement);
                    // $this.formelements = [];
                    // formelement.controlType = "fileupload";
                    let test = {
                        controlType: "filedownload",
                        description: "Device specific vendor configuration information",
                        deviceName: "Testdevi2",
                        downloadUrl: "../devices/Testdevi2/vendordata",
                        key: "dicomVendorData",
                        label: "Vendor Device Data",
                        order: 5.02,
                        show: true
                    };
                    let test2 = {
                        controlType:"fileupload",
                        description:"Device specific vendor configuration information",
                        deviceName:"Testdevi2",
                        key:"dicomVendorData",
                        label:"Vendor Device Data",
                        modus:"upload",
                        order:5.02,
                        show:true
                    }
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
                    })
                    // $this.router.navigateByUrl(`/device/edit/${deviceName}`);
                });
            }
        });
    }
    uploadVendor(deviceName){
        let $this = this;
        this.dialogRef = this.dialog.open(UploadFilesComponent, {
            height:'auto',
            width:'500px'
        });
        this.dialogRef.componentInstance.deviceName = deviceName;
        this.dialogRef.afterClosed().subscribe((selected)=>{
            if(selected){
/*                var globalForm = $this.formcomp.getForm();
                var valueObject = globalForm.value;
                valueObject.dicomVendorData = true;
                $this.formcomp.setFormModel(valueObject);
                $this.form = $this.formservice.toFormGroup($this.formelements);
                $this.formcomp.setForm($this.form);*/
                console.log($this.formelements);
                // $this.router.navigateByUrl(`/device/edit/${deviceName}`);
                $this.deviceConfiguratorService.device = {};
                $this.deviceConfiguratorService.schema = {};
                $this.router.navigateByUrl('blank').then(() => {
                    $this.router.navigateByUrl(`/device/edit/${deviceName}`);
                });
                // window.location.reload();
                // $this.router.navigateByUrl('/DummyComponent', true);
                // location.reload();
            }
        });
    }
    addElement(element:any, formpart:FormControl[]){
        var globalForm = this.formcomp.getForm();
        var valueObject = globalForm.value;
        element.push(element[0]);
        formpart["options"].push(formpart["options"][0]);
        this.form = this.formservice.toFormGroup(this.formelements);
        this.formcomp.setForm(this.form);
        this.formcomp.setFormModel(valueObject);
    }
    removeObject(formelement,controls){
        let $this = this;
        this.confirm({
            content:'Are you sure you want to remove this extension and all of its child objects?'
        }).subscribe(result => {
            if(result){
                $this.deviceConfiguratorService.removeExtensionFromDevice(formelement.devicereff);
                _.forEach($this.formelements,(m,i)=>{
                    if(m["controlType"] === "button" && m["devicereff"] === formelement.devicereff){
                        m.value = 0;
                    }
                });
                $this.ref.detectChanges();
            }
        });
    }
    clone(formelement){
/*        console.log("formelement",formelement);
        let value = (<FormArray>this.form.controls[formelement.key]).getRawValue();
        (<FormArray>this.form.controls[formelement.key]).insert(this.form.controls[formelement.key].value.length, new FormControl(value));*/
        let $this = this;
        var globalForm = this.formcomp.getForm();
        let value = globalForm.value;
        this.dialogRef = this.dialog.open(CloneSelectorComponent, {
            height:'auto',
            width:'500px'
        });
        this.dialogRef.componentInstance.toCloneElement = formelement;
        /*        this.dialogRef.afterClosed().subscribe(result => {
         if(result){
         console.log("result", result);
         }else{
         console.log("false");
         }
         });*/
        this.dialogRef.afterClosed().subscribe((selected)=>{
            if(selected){
                $this.router.navigateByUrl(formelement.addUrl+selected.forCloneUrl);
            }
        });
    }
    addArrayElement(element:any, formpart:FormControl[], form:any){
        formpart = formpart || [];
        element = element || [];
        element.push("");
        var globalForm = this.formcomp.getForm();
        formpart.push(new FormControl(""));
        var valueObject = globalForm.value;
        this.form = this.formservice.toFormGroup(this.formelements);
        this.form.patchValue(valueObject);
        this.formcomp.setForm(this.form);
        this.formcomp.setFormModel(valueObject);
        this.ref.detectChanges();
    }
    removeArrayElement(element:any, i:number, form:any){
        if(element.value.length > i){
            //Remove from react form

            (<FormArray>this.form.controls[element.key]).removeAt(i);
            //reflect the changes to the dome
            _.forEach(this.formelements,(m,j)=>{
                if(m.key === element.key){
                    this.formelements[j].value = this.form.value[element.key];
                }
            });
            this.ref.detectChanges();
        }
    }
    checkboxChange(e, formelement){
        if(e.target.checked && !_.hasIn(this.form.controls[formelement.key].value, e.target.defaultValue)){
            (<FormArray>this.form.controls[formelement.key]).insert(this.form.controls[formelement.key].value.length, new FormControl(e.target.defaultValue));
        }else{
            (<FormArray>this.form.controls[formelement.key]).removeAt(_.indexOf(this.form.controls[formelement.key].value,e.target.defaultValue));
        }
    }
    navigateTo(e){
        if(e != '-'){
            this.router.navigateByUrl(e);
        }
    }

    toggleTab(orderId){
        if(this.form.valid){
            var globalForm = this.formcomp.getForm();
            var valueObject = globalForm.value;

            _.forEach(this.formelements,(m,i)=>{
               if(Math.floor(m.order) === orderId+1){
                   if(m.show === true){
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
}
