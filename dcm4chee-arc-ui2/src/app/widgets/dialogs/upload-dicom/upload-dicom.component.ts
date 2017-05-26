import { Component, OnInit } from '@angular/core';
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
        let $this = this;
        let boundary = Math.random().toString().substr(2);
        let filetype;
        let fileList: File[] = event.target.files;
        if(fileList.length > 0) {
/*            this.service.makeFileRequest(`../aets/${this._selectedAe}/rs/studies`, [], fileList).subscribe(() => {
                console.log('sent');
            });*/
/*            let reader = new FileReader();
            reader.readAsDataURL(fileList[0]);

            reader.onload = function(e) {

                var xmlHttpRequest = new XMLHttpRequest();
                //Some AJAX-y stuff - callbacks, handlers etc.
                xmlHttpRequest.open("POST", `../aets/${$this._selectedAe}/rs/studies`, true);
                var dashes = '--';
                var crlf = "\r\n";

                //Post with the correct MIME type (If the OS can identify one)
                if ( fileList[0].type == '' ){
                    filetype = 'application/dicom';
                } else {
                    filetype = fileList[0].type;
                }

                //Build a HTTP request to post the file
                var data = dashes + boundary + crlf + "Content-Disposition: form-data;" + "name=\"file\";" + "filename=\"" + encodeURIComponent(fileList[0].name) + "\"" + crlf + "Content-Type: " + filetype + crlf + crlf + e.target["result"] + crlf + dashes + boundary + dashes;

                xmlHttpRequest.setRequestHeader("Content-Type", "multipart/related;boundary=" + boundary);

                //Send the binary data
                xmlHttpRequest.send(data);
            };*/

            let reader = new FileReader();
            // reader.readAsBinaryString(fileList[0]);
            reader.readAsArrayBuffer(fileList[0]);

            reader.onload = function(e) {
                var xmlHttpRequest = new XMLHttpRequest();
                //Some AJAX-y stuff - callbacks, handlers etc.
                xmlHttpRequest.open("POST", `../aets/${$this._selectedAe}/rs/studies`, true);
                var dashes = '--';
                var crlf = "\r\n";
                //Post with the correct MIME type (If the OS can identify one)
                if ( fileList[0].type == '' ){
                    filetype = 'application/dicom';
                } else {
                    filetype = fileList[0].type;
                }
                const dataView = new DataView(e.target["result"]);
                const AUDIO_CONTENT_DISPOSITION ="Content-Type: " + filetype;
                const postDataStart = [
                    crlf,
                    dashes,
                    boundary,
                    crlf,
                    AUDIO_CONTENT_DISPOSITION,
                    crlf
                ].join('');
                const postDataEnd = [crlf, dashes, boundary, dashes, crlf].join('');
/*                var array = new Int8Array(e.target["result"]);
                let content = JSON.stringify(array, null, '  ');
                console.log("content",content);*/
                let content = e.target["result"];
                //Build a HTTP request to post the file
                // var data = dashes + boundary + crlf + "Content-Type: " + filetype + crlf + crlf + content + crlf + dashes + boundary + dashes;
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
                xmlHttpRequest.setRequestHeader("Content-Type", "multipart/related;type=application/dicom;boundary=" + boundary+";");
                xmlHttpRequest.setRequestHeader("Accept", "application/dicom+json");

                //Send the binary data
                xmlHttpRequest.send(payload);
            };
/*            var xmlHttpRequest = new XMLHttpRequest();
            xmlHttpRequest.open("POST", `../aets/${$this._selectedAe}/rs/studies`, true);
            xmlHttpRequest.setRequestHeader("Content-Type", "multipart/related;boundary=" + boundary+";Type:application/dicom");
            var dashes = '--';
            var crlf = "\r\n";
            if ( fileList[0].type == '' ){
                filetype = 'application/dicom';
            } else {
                filetype = fileList[0].type;
            }
            this.parseFile(fileList[0],{
                binary:true,
                success:(file)=>{
                    console.log("successfile",file);
                },
                error_callback: (error)=>{
                    alert(error);
                },
                chunk_read_callback:(filePart)=>{
                    console.log("filepart",filePart);
                    var data = dashes + boundary + crlf + "name=\"file\";" + "filename=\"" + encodeURIComponent(fileList[0].name) + "\"" + crlf + "Content-Type: " + filetype + crlf + crlf + filePart + crlf + dashes + boundary + dashes;
                    xmlHttpRequest.send(data);
                }
            });*/
        }

    }
    //Copyed from     https://gist.github.com/alediaferia/cfb3a7503039f9278381
    /*
     * Valid options are:
     * - chunk_read_callback: a function that accepts the read chunk
     as its only argument. If binary option
     is set to true, this function will receive
     an instance of ArrayBuffer, otherwise a String
     * - error_callback:      an optional function that accepts an object of type
     FileReader.error
     * - success:             an optional function invoked as soon as the whole file has been
     read successfully
     * - binary:              If true chunks will be read through FileReader.readAsArrayBuffer
     *                        otherwise as FileReader.readAsText. Default is false.
     * - chunk_size:          The chunk size to be used, in bytes. Default is 64K.
     */
    parseFile(file, options) {
        let opts       = typeof options === 'undefined' ? {} : options;
        let fileSize   = file.size;
        let chunkSize  = typeof opts['chunk_size'] === 'undefined' ?  64 * 1024 : parseInt(opts['chunk_size']); // bytes
        let binary     = typeof opts['binary'] === 'undefined' ? false : opts['binary'] == true;
        let offset     = 0;
        let self       = this; // we need a reference to the current object
        let readBlock  = null;
        let chunkReadCallback = typeof opts['chunk_read_callback'] === 'function' ? opts['chunk_read_callback'] : function() {};
        let chunkErrorCallback = typeof opts['error_callback'] === 'function' ? opts['error_callback'] : function() {};
        let success = typeof opts['success'] === 'function' ? opts['success'] : function() {};

        let onLoadHandler = function(evt) {
            if (evt.target.error == null) {
                offset += evt.target.result.length;
                chunkReadCallback(evt.target.result);
            } else {
                chunkErrorCallback(evt.target.error);
                return;
            }
            if (offset >= fileSize) {
                success(file);
                return;
            }

            readBlock(offset, chunkSize, file);
        }

        readBlock = function(_offset, length, _file) {
            var r = new FileReader();
            var blob = _file.slice(_offset, length + _offset);
            r.onload = onLoadHandler;
            if (binary) {
                r.readAsArrayBuffer(blob);
            } else {
                r.readAsText(blob);
            }
        }

        readBlock(offset, chunkSize, file);
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
