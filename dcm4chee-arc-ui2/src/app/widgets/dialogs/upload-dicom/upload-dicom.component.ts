import { Component, OnInit } from '@angular/core';
import {FileUploader} from "ng2-file-upload";
import {MdDialogRef} from "@angular/material";
import {Headers, RequestOptions, Http} from "@angular/http";
import {Observable} from "rxjs";
import {UploadDicomService} from "./upload-dicom.service";
import {request} from "http";

@Component({
  selector: 'app-upload-dicom',
  templateUrl: './upload-dicom.component.html'
})
export class UploadDicomComponent implements OnInit {

    private _aes;
    private _selectedAe;
    public vendorUpload:FileUploader = new FileUploader({
        url: ``,
        // allowedMimeType:['application/octet-stream','application/zip']
        // headers: [{name: 'Content-Type', value: `multipart/related`}]
        // ,disableMultipart: true
    });
    constructor(
        public dialogRef: MdDialogRef<UploadDicomComponent>,
        private $http:Http,
        private service:UploadDicomService
    ) {
        this.service.progress$.subscribe(
            data => {
                console.log('progress = '+data);
            });
    }

    ngOnInit() {
        /*        this.vendorUpload = new FileUploader({
         url:`/devices/${this._deviceName}/vendordata`,
         allowedMimeType:['application/octet-stream','application/zip']
         });*/
        this.vendorUpload.onAfterAddingFile = (item) => {
            item.method = "POST";
            console.log("this.vendorUpload.optionss",this.vendorUpload.options);
            console.log("item",item);
        };
        this.vendorUpload.onBeforeUploadItem = (item) =>{
            this.addFileNameHeader(item.file.name);
        };
    }
    addFileNameHeader(fileName) {
        // var boundary=Math.random().toString().substr(2);
        console.log("this.vendorUpload.progress",this.vendorUpload.progress);
        console.log("this.vendorUpload.optionss",this.vendorUpload.options);
        // this.vendorUpload.setOptions({headers: [{
        //     name: 'Content-Type', value: `multipart/related`
        // }]});

    }
    fileChange(event) {

        let fileList: File[] = event.target.files;
        if(fileList.length > 0) {
/*            this.service.makeFileRequest(`../aets/${this._selectedAe}/rs/studies`, [], fileList).subscribe(() => {
                console.log('sent');
            });*/
            let reader = new FileReader();
            let fileContent = reader.readAsArrayBuffer(fileList[0]);
            let fileContentString = reader.readAsBinaryString(fileList[0]);
            console.log("fileContnet",fileContent);
/*            let file: File = fileList[0];
            let formData:FormData = new FormData();
            formData.append('uploadFile', file, file.name);
            let headers = new Headers();
            headers.append('Content-Type', undefined);
            headers.append('Accept', 'Application/dicom');
            let options = new RequestOptions({ headers: headers });
            this.$http.post(`../aets/${this._selectedAe}/rs/studies`, formData, options)
                .map(res => res.json())
                .catch(error => Observable.throw(error))
                .subscribe(
                    data => console.log('success'),
                    error => console.log(error)
                )*/
        }

    }
    uploadFile(dialogRef){

        this.vendorUpload.setOptions({url: `../aets/${this._selectedAe}/rs/studies`});
        this.vendorUpload.uploadAll();
        // dialogRef.close("ok");
    }
    onChange(newValue) {
        this._selectedAe = newValue.title;
    }
    get selectedAe() {
        return this._selectedAe;
    }

    set selectedAe(value) {
        this._selectedAe = value;
    }

    get aes() {
        return this._aes;
    }

    set aes(value) {
        this._aes = value;
    }
}
