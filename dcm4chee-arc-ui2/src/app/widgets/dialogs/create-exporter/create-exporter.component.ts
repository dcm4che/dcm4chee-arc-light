import {Component, OnInit} from '@angular/core';
import {Http} from '@angular/http';
import {MatDialogRef} from '@angular/material';
import {AppService} from '../../../app.service';
import * as _ from 'lodash';
import {CreateExporterService} from './create-exporter.service';
import {HttpErrorHandler} from "../../../helpers/http-error-handler";
import {J4careHttpService} from "../../../helpers/j4care-http.service";
import {LoadingBarService} from "@ngx-loading-bar/core";
import {DeviceConfiguratorService} from "../../../configuration/device-configurator/device-configurator.service";

@Component({
  selector: 'app-create-exporter',
  templateUrl: './create-exporter.component.html'
})
export class CreateExporterComponent implements OnInit{
    showselectdevice = true;
    showexternalae = true;
    showexporter = false;
    selectedDeviceObject;
    selectedDevice;
    dcmExporter = {
        dcmExporterID : undefined,
        dcmURI : undefined,
        dcmQueueName : undefined,
        dicomAETitle : undefined,
        dicomDescription : '',
        dcmStgCmtSCP : undefined,
        dcmInstanceAvailability : "ONLINE",
        dcmExportPriority : 4
    };
    externalAeConnections;
    externalAe;
    externalAeObject;
    queue;
    schema = {
        "title": "Exporter Descriptor",
        "description": "Exporter Descriptor",
        "type": "object",
        "required": [
            "dcmExporterID",
            "dcmURI",
            "dcmQueueName",
            "dcmExportPriority",
            "dcmInstanceAvailability",
            "dicomAETitle"
        ],
        "properties": {
            "dcmExporterID": {
                "title": "Exporter ID",
                "description": "Exporter ID",
                "type": "string"
            },
            "dcmURI": {
                "title": "URI",
                "description": "RFC2079: Uniform Resource Identifier",
                "type": "string"
            },
            "dcmQueueName": {
                "title": "Queue Name",
                "description": "JMS Queue Name",
                "type": "string",
                "enum" : [
                    "Export1",
                    "Export2",
                    "Export3",
                    "Export4",
                    "Export5"
                ]
            },
            "dcmExportPriority": {
                "title": "Export Priority",
                "description": "JMS Priority Level for processing the Export Task from 0 (lowest) to 9 (highest).",
                "type": "integer",
                "default" : 4,
                "minimum": 0,
                "maximum": 9
            },
            "dcmInstanceAvailability": {
                "title": "Instance Availability",
                "description": "Instance Availability.",
                "type": "string",
                "default": "ONLINE",
                "enum": [
                    "ONLINE",
                    "NEARLINE",
                    "OFFLINE"
                ]
            },
            "dicomAETitle": {
                "title": "Application Entity (AE) title",
                "description": "Application Entity (AE) title",
                "type": "string",
                "format": "dcmArchiveAETitle"
            }
        }
    };
    formObj;
    archive;
    private _aes;
    private _devices;
    _ = _;
    constructor(
        public $http:J4careHttpService,
        public dialogRef: MatDialogRef<CreateExporterComponent>,
        public mainservice: AppService,
        public cfpLoadingBar: LoadingBarService,
        private service: CreateExporterService,
        private httpErrorHandler:HttpErrorHandler,
        public deviceConfigService:DeviceConfiguratorService,
    ) {
    }
    ngOnInit(){
        this.cfpLoadingBar.complete();
        let $this = this;
        this.service.getQueue().subscribe(queue => {
            $this.queue = queue;
        });
        this.getArchiveDevice(2);
    }
    get aes() {
        return this._aes;
    }

    set aes(value) {
        this._aes = value;
    }

    onChange(newValue, model) {
        _.set(this, model, newValue);
    }

    get devices() {
        return this._devices;
    }

    set devices(value) {
        this._devices = value;
    };
    setAe(e){
        this.dcmExporter.dicomAETitle = e;
    }
    setQueue(e){
        this.dcmExporter.dcmQueueName = e;
    }
    setDcmStgCmtSCP(e){
            this.dcmExporter.dcmStgCmtSCP = e.dicomAETitle;
            this.externalAeObject = e;
    };
    getSchema(){
        this.service.getExporterDescriptorSchema().subscribe(schema=>{
            this.schema = schema;
            this.formObj = this.deviceConfigService.convertSchemaToForm(this.archive, this.schema, {}, 'attr');
        },err=>{
            this.formObj = this.deviceConfigService.convertSchemaToForm(this.archive, this.schema, {}, 'attr');
        })
    }
    selectDevice(e){
        // this.getDevice(e, this.selectedDeviceObject);
        this.selectedDevice = e;
        let $this = this;
        $this.cfpLoadingBar.start();
        this.service.getDevice(e).subscribe(device => {
            $this.selectedDeviceObject = device;
            $this.showselectdevice = false;
            if ($this.externalAe && $this.selectedDeviceObject)
                $this.showexporter = true;
            $this.cfpLoadingBar.complete();
        }, (err) => {
            $this.httpErrorHandler.handleError(err);
            $this.cfpLoadingBar.complete();
        });
    }

    getArchiveDevice(retries){
        if(!this.mainservice.archiveDeviceName){
            if(retries){
                console.log("retry",retries);
                setTimeout(()=>{
                    this.getArchiveDevice(retries-1);
                },400);
            }
        }else{
            this.service.getDevice(this.mainservice.archiveDeviceName).subscribe((res)=>{
                this.archive = res;
                this.getSchema();
            },(err)=>{
                if(retries)
                    this.getArchiveDevice(retries-1);
                else{
                    this.httpErrorHandler.handleError(err);
                }
            });
        }
    }
    submitFunction(e){
        console.log("e",e);
        // this.deviceConfigService.addChangesToDevice(e, '', this.archive);
        // this.deviceConfiguratiorComponent.submitFunction(e);
        this.dialogRef.close({
            device:this.archive,
            exporter:e
        });
    }
    validAeForm(){
        if (!this.dcmExporter.dcmExporterID || this.dcmExporter.dcmExporterID === ''){
            return false;
        }
        if (!this.dcmExporter.dcmURI || this.dcmExporter.dcmURI === ''){
            return false;
        }
        if (!this.dcmExporter.dcmQueueName || this.dcmExporter.dcmQueueName === ''){
            return false;
        }
        if (!this.dcmExporter.dicomAETitle || this.dcmExporter.dicomAETitle === ''){
            return false;
        }
        return true;
    }
}
