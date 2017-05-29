import {Component, OnInit, OnDestroy} from '@angular/core';
import {FileUploader} from "ng2-file-upload";
import {MdDialogRef} from "@angular/material";
import {Headers, RequestOptions, Http} from "@angular/http";
import {Observable} from "rxjs";
import {UploadDicomService} from "./upload-dicom.service";
import {request} from "http";
import {unescape} from "querystring";

@Component({
  selector: 'app-upload-dicom',
  templateUrl: './upload-dicom.component.html'
})
export class UploadDicomComponent implements OnInit{


    private _aes;
    private _selectedAe;
    xmlHttpRequest;
    percentComplete =  {
        mode:"determinate",
        value:0,
        show:false
    };
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
        let $this = this;
        this.percentComplete.show = true;
        let boundary = Math.random().toString().substr(2);
        let filetype;
        let fileList: File[] = event.target.files;
        if(fileList.length > 0) {
            let reader = new FileReader();
            // reader.readAsBinaryString(fileList[0]);
            reader.readAsArrayBuffer(fileList[0]);

            reader.onload = function(e) {

                $this.xmlHttpRequest = new XMLHttpRequest();
                //Some AJAX-y stuff - callbacks, handlers etc.
                $this.xmlHttpRequest.open("POST", `../aets/${$this._selectedAe}/rs/studies`, true);
                var dashes = '--';
                var crlf = "\r\n";
                //Post with the correct MIME type (If the OS can identify one)
                if ( fileList[0].type == '' ){
                    filetype = 'application/dicom';
                } else {
                    filetype = fileList[0].type;
                }
                const dataView = new DataView(e.target["result"]);
                const postDataStart = dashes + boundary + crlf + "Content-Disposition: form-data;" + "name=\"file\";" + "filename=\"" + encodeURIComponent(fileList[0].name) + "\"" + crlf + "Content-Type: " + filetype + crlf + crlf;
                const postDataEnd = crlf + dashes + boundary + dashes;
                const size = postDataStart.length + dataView.byteLength + postDataEnd.length;
                const uint8Array = new Uint8Array(size);
                let i = 0;
                for (; i < postDataStart.length; i++) {
                    uint8Array[i] = postDataStart.charCodeAt(i) & 0xFF;
                }

                for (let j = 0; j < dataView.byteLength; i++, j++) {
                    uint8Array[i] = dataView.getUint8(j);
                }

                for (let j = 0; j < postDataEnd.length; i++, j++) {
                    uint8Array[i] = postDataEnd.charCodeAt(j) & 0xFF;
                }
                const payload = uint8Array.buffer;
                $this.xmlHttpRequest.setRequestHeader("Content-Type", "multipart/related;type=application/dicom;boundary=" + boundary+";");
                $this.xmlHttpRequest.setRequestHeader("Accept", "application/dicom+json");
                $this.xmlHttpRequest.upload.onprogress = function (e) {
                    if (e.lengthComputable) {
                        $this.percentComplete.value  = (e.loaded / e.total)*100;
                    }
                }
                $this.xmlHttpRequest.upload.onloadstart = function (e) {
                    $this.percentComplete.value = 0;
                    $this.percentComplete.mode = "determinate";
                }
                $this.xmlHttpRequest.upload.onloadend = function (e) {
                    $this.percentComplete.value  = 100;
                }
                //Send the binary data
                $this.xmlHttpRequest.send(payload);
            };
        }

    }
    uploadFile(dialogRef){

        this.vendorUpload.setOptions({url: `../aets/${this._selectedAe}/rs/studies`});
        this.vendorUpload.uploadAll();
        // dialogRef.close("ok");
    }
    close(dialogRef){
        if(this.xmlHttpRequest){
            this.xmlHttpRequest.abort();
        }
        dialogRef.close(null)
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
