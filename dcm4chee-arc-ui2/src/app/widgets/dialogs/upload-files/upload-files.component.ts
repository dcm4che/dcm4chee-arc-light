import { Component, OnInit } from '@angular/core';
import {FileUploader} from "ng2-file-upload";
import {MdDialogRef} from "@angular/material";

@Component({
  selector: 'app-upload-files',
  templateUrl: './upload-files.component.html'
})
export class UploadFilesComponent implements OnInit {

    private _deviceName;
    public vendorUpload:FileUploader = new FileUploader({url: ``,allowedMimeType:['application/octet-stream','application/zip'],disableMultipart: true});
    constructor(public dialogRef: MdDialogRef<UploadFilesComponent>) { }

    ngOnInit() {
/*        this.vendorUpload = new FileUploader({
            url:`/devices/${this._deviceName}/vendordata`,
            allowedMimeType:['application/octet-stream','application/zip']
        });*/
        this.vendorUpload.onAfterAddingFile = (item) => {
            item.method = "PUT";
        };
        this.vendorUpload.onBeforeUploadItem = (item) =>{
            this.addFileNameHeader(item.file.name);
        };
    }
    addFileNameHeader(fileName) {
        this.vendorUpload.setOptions({headers: [{name: 'Content-Type', value: "application/zip"}]});
    }
    uploadFile(dialogRef){
        this.vendorUpload.setOptions({url: `../devices/${this._deviceName}/vendordata`});
        this.vendorUpload.uploadAll();
        dialogRef.close("ok");
    }

    get deviceName() {
        return this._deviceName;
    }

    set deviceName(value) {
        this._deviceName = value;
    }
}
