import { Component } from '@angular/core';
import {Http} from "@angular/http";
import {MdDialogRef} from "@angular/material";
import {AppService} from "../../../app.service";
import {SlimLoadingBarService} from "ng2-slim-loading-bar";
import * as _ from "lodash";
import {CreateExporterService} from "./create-exporter.service";

@Component({
  selector: 'app-create-exporter',
  templateUrl: './create-exporter.component.html'
})
export class CreateExporterComponent {
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
        dcmStgCmtSCP : undefined
    };
    externalAeConnections;
    externalAe;
    externalAeObject;
    queue;
    private _aes;
    private _devices;
    _ = _;
    constructor(
        public $http:Http,
        public dialogRef: MdDialogRef<CreateExporterComponent>,
        public mainservice:AppService,
        public cfpLoadingBar:SlimLoadingBarService,
        private service:CreateExporterService
    ) {
        this.cfpLoadingBar.complete();
        let $this = this;
        this.service.getQueue().subscribe(queue => {
            $this.queue = queue;
        });
    }

    get aes() {
        return this._aes;
    }

    set aes(value) {
        this._aes = value;
    }

    onChange(newValue, model) {
        _.set(this, model,newValue);
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
/*    setDcmUri(e){
        this.dcmExporter.dcmURI = "dicom:" + this.externalAeConnections.dicomAETitle;
    };*/
    setDcmStgCmtSCP(e){
            this.dcmExporter.dcmStgCmtSCP = e.dicomAETitle;
        // if(e.dicomDeviceName){
            let $this = this;
            this.externalAeObject = e;

            /*           this.service.getDevice(e.dicomDeviceName).subscribe(device => {
                           // $this.selectedDeviceObject = device;
                           if(_.hasIn(device,'dicomNetworkConnection') && _.size(device.dicomNetworkConnection) > 0){
                               $this.externalAeConnections = [];
                               _.forEach(device.dicomNetworkConnection,(m, i)=>{
                                   $this.externalAeConnections.push('dicom:' + e.dicomAETitle);
                               });
                               $this.externalAe = e.dicomAETitle;
                               $this.dcmExporter.dcmExporterID = e.dicomAETitle;
                               $this.dcmExporter.dicomDescription = 'Export to ' + e.dicomAETitle;
                               if(_.size($this.externalAeConnections) === 1){
                                   $this.dcmExporter.dcmURI = $this.externalAeConnections[0];
                               }
                               $this.showexternalae = false;
                               if($this.externalAe && $this.selectedDeviceObject)
                                   $this.showexporter = true;
                           }else{
                               $this.mainservice.setMessage({
                                   "title": "Error ",
                                   "text": "The selected External AE" + e + " doesn't have any connections defined!",
                                   "status": "error"
                               });
                           }
                           $this.cfpLoadingBar.stop();
                       },(err) => {
                           $this.mainservice.setMessage({
                               "title": "Error " + err.status,
                               "text": err.statusText,
                               "status": "error"
                           });
                           $this.cfpLoadingBar.complete();
                       });*/
/*        }else{
            this.mainservice.setMessage({
                "title": "Error ",
                "text": "The selected AE is not connected to any device",
                "status": "error"
            });
        }*/
    };
    selectDevice(e){
        // this.getDevice(e, this.selectedDeviceObject);
        this.selectedDevice = e;
        let $this = this;
        $this.cfpLoadingBar.start();
        this.service.getDevice(e).subscribe(device => {
            $this.selectedDeviceObject = device;
            $this.showselectdevice = false;
            if($this.externalAe && $this.selectedDeviceObject)
                $this.showexporter = true;
            $this.cfpLoadingBar.stop();
        },(err) => {
            $this.mainservice.setMessage({
                "title": "Error " + err.status,
                "text": err.statusText,
                "status": "error"
            });
            $this.cfpLoadingBar.complete();
        });
    }

    validAeForm(){
        if(!this.dcmExporter.dcmExporterID || this.dcmExporter.dcmExporterID === ''){
            return false;
        }
        if(!this.dcmExporter.dcmURI || this.dcmExporter.dcmURI === ''){
            return false;
        }
        if(!this.dcmExporter.dcmQueueName || this.dcmExporter.dcmQueueName === ''){
            return false;
        }
        if(!this.dcmExporter.dicomAETitle || this.dcmExporter.dicomAETitle === ''){
            return false;
        }
        return true;
    }
}
