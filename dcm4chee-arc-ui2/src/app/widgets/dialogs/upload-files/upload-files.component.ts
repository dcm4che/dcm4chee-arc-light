import { Component, OnInit } from '@angular/core';
import {MdDialogRef} from "@angular/material";
import * as _ from 'lodash';
import {AppService} from "../../../app.service";

@Component({
  selector: 'app-upload-files',
  templateUrl: './upload-files.component.html'
})
export class UploadFilesComponent implements OnInit {


    private _aes;
    private _selectedAe;
    private _dicomObject;
    file;
    fileList: File[];
    xmlHttpRequest;
    percentComplete: any;
    constructor(
        public dialogRef: MdDialogRef<UploadFilesComponent>,
        public mainservice:AppService
    ) {
    }

    ngOnInit() {
        this.percentComplete = {};
    }

    fileChange(event) {
        let $this = this;
        let boundary = Math.random().toString().substr(2);
        let filetype;
        this.fileList = event.target.files;
        if (this.fileList) {
            _.forEach(this.fileList, (file, i) => {
                let transfareSyntax;
                switch (file.type) {
                    case "image/jpeg":
                        transfareSyntax = "1.2.840.10008.1.2.4.50";
                        break;
                    case "video/mpeg":
                        transfareSyntax = "1.2.840.10008.1.2.4.100";
                        break;
                    case "application/pdf":
                        transfareSyntax = "1.2.840.10008.5.1.4.1.1.104.1";
                        break;
                }
                //TODO if file.type is pdf than don't handle it as binary-file
                if(transfareSyntax){
                    console.log("file", file);
                    console.log("filetype", file.type);
                    this.percentComplete[file.name] = {};
                    this.percentComplete[file.name]['value'] = 0;
                    let reader = new FileReader();
                    // reader.readAsBinaryString(file);
                    reader.readAsArrayBuffer(file);
                    reader.onload = function (e) {

                        let xmlHttpRequest = new XMLHttpRequest();
                        //Some AJAX-y stuff - callbacks, handlers etc.
                        xmlHttpRequest.open('POST', `../aets/${$this._selectedAe}/rs/studies`, true);
                        let dashes = '--';
                        let crlf = '\r\n';
                        //Post with the correct MIME type (If the OS can identify one)
                        let studyObject = _.cloneDeep($this._dicomObject.attrs);
                        if(transfareSyntax === "1.2.840.10008.5.1.4.1.1.104.1"){
                            studyObject["00420011"] = {
                                "vr": "OB",
                                "Value": "file/" + file.name
                            };
                            studyObject["00080016"] =  {
                                "vr":"UI",
                                "Value":[
                                    "1.2.840.10008.5.1.4.1.1.104.1"
                                ]
                            }
                        }
                        studyObject["7FE00010"] = {
                            "vr": "OB",
                            "BulkDataURI": "file/" + file.name
                        }
                        const dataView = new DataView(e.target['result']);
                        const jsonData = dashes + boundary + crlf + 'Content-Type: application/dicom+json' + crlf + crlf + JSON.stringify(studyObject) + crlf;
                        const postDataStart = jsonData + dashes + boundary + crlf + 'Content-Type: ' + file.type + ';transfer-Syntax:' + transfareSyntax + crlf + 'Content-Location: file/' + file.name + crlf + crlf;
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
                        xmlHttpRequest.setRequestHeader('Content-Type', 'multipart/related;type=application/dicom+json;boundary=' + boundary + ';');
                        xmlHttpRequest.setRequestHeader('Accept', 'application/dicom+json');
                        xmlHttpRequest.upload.onprogress = function (e) {
                            if (e.lengthComputable) {
                                $this.percentComplete[file.name]['value'] = (e.loaded / e.total) * 100;
                            }
                        };
                        xmlHttpRequest.onreadystatechange = () => {
                            if (xmlHttpRequest.readyState === 4) {
                                if (xmlHttpRequest.status === 200) {
                                    console.log('in response', JSON.parse(xmlHttpRequest.response));
                                } else {
                                    console.log('in respons error', xmlHttpRequest.status);
                                    console.log('statusText', xmlHttpRequest.statusText);
                                    $this.percentComplete[file.name]['value'] = 0;
                                    $this.percentComplete[file.name]['status'] = xmlHttpRequest.status + ' ' + xmlHttpRequest.statusText;
                                }
                            }
                        };
                        xmlHttpRequest.upload.onloadstart = function (e) {
                            $this.percentComplete[file.name]['value'] = 0;
                        };
                        xmlHttpRequest.upload.onloadend = function (e) {
                            if (xmlHttpRequest.status === 200) {
                                $this.percentComplete[file.name]['value'] = 100;
                            }
                        };
                        //Send the binary data
                        xmlHttpRequest.send(payload);
                    };
                }else{
                    $this.mainservice.setMessage({
                        'title': 'Error',
                        'text': `Filetype "${file.type}" not allowed!`,
                        'status': 'error'
                    });
                    $this.fileList = [];
                    $this.file = null;
                }
            });
        }
    }
    close(dialogRef){
        if (this.xmlHttpRequest){
            this.xmlHttpRequest.abort();
        }
        dialogRef.close(null);
    }
    onChange(newValue) {
        this._selectedAe = newValue.title;
    }

    get dicomObject() {
        return this._dicomObject;
    }

    set dicomObject(value) {
        this._dicomObject = value;
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
