import { Component, OnInit } from '@angular/core';
import {MatDialogRef} from "@angular/material";
import * as _ from 'lodash';
import {AppService} from "../../../app.service";
import {J4careHttpService} from "../../../helpers/j4care-http.service";
import {StudiesService} from "../../../studies/studies.service";
import {UploadDicomService} from "../upload-dicom/upload-dicom.service";

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
    selectedSopClass;
    modality;
    description;
    showFileList = false;
    isImage = false;
    webApps;
    selectedWebApp;
    seriesNumber = 0;
    instanceNumber = 1;
    imageType = [
        {
            title:"Screenshots",
            description:"Secondary Capture Image Storage",
            value:"1.2.840.10008.5.1.4.1.1.7",
            modality:"OT"
        },
        {
            title:"Photographs",
            description:"VL Photographic Image Storage",
            value:"1.2.840.10008.5.1.4.1.1.77.1.4",
            modality:"XC"
        }
    ]
    constructor(
        public dialogRef: MatDialogRef<UploadFilesComponent>,
        public mainservice:AppService,
        public $http:J4careHttpService,
        private studieService:StudiesService,
        private uploadDicomService:UploadDicomService
    ) {
    }

    ngOnInit() {
        this.percentComplete = {};
        this.selectedSopClass = this.imageType[0];
        this.getWebApps();
    }
    fileChange(event){
        this.fileList = event.target.files;
        if(this.fileList[0] && this.fileList[0].type === "image/jpeg"){
            this.isImage = true;
        }
    }
    upload() {
        let $this = this;
        let boundary = Math.random().toString().substr(2);
        let filetype;
        let descriptionPart;
        let token;
        this.showFileList = true;
        // this.fileList = this.file;
        this.$http.refreshToken().subscribe((response) => {
            if(!this.mainservice.global.notSecure){
                if (response && response.length != 0) {
                    $this.$http.resetAuthenticationInfo(response);
                    token = response['token'];
                } else {
                    token = this.mainservice.global.authentication.token;
                }
            }
            if (this.fileList) {
                _.forEach(this.fileList, (file, i) => {
                    let transfareSyntax;
                    switch (file.type) {
                        case "image/jpeg":
                            transfareSyntax = "1.2.840.10008.1.2.4.50";
                            $this.modality = $this.selectedSopClass.modality;
                            descriptionPart = "Image";
                            break;
                        case "video/mpeg":
                            transfareSyntax = "1.2.840.10008.1.2.4.100";
                            descriptionPart = "Video";
                            $this.modality = "XC";
                            break;
                        case "application/pdf":
                            transfareSyntax = "";
                            descriptionPart = "PDF";
                            $this.modality = "DOC";
                            break;
                    }
                    if (transfareSyntax || transfareSyntax === "") {
                        this.percentComplete[file.name] = {};
                        // this.percentComplete[file.name]['value'] = 0;

                        $this.percentComplete[file.name]['showTicker'] = false;
                        $this.percentComplete[file.name]['showLoader'] = true;
                        /*                    let reader = new FileReader();
                                            // reader.readAsBinaryString(file);
                                            reader.readAsArrayBuffer(file);
                                            reader.onload = function (e) {*/

                        $this.xmlHttpRequest = new XMLHttpRequest();
                        //Some AJAX-y stuff - callbacks, handlers etc.
                        let url = this.uploadDicomService.getUrlFromWebApp(this.selectedWebApp);
                        $this.xmlHttpRequest.open('POST', url, true);
                        let dashes = '--';
                        let crlf = '\r\n';
                        //Post with the correct MIME type (If the OS can identify one)
                        let studyObject = _.pickBy($this._dicomObject.attrs, (o, i) => {
                            return (i.toString().indexOf("777") === -1);
                        })
                        if (!$this.description || $this.description === "") {
                            $this.description = "Imported " + descriptionPart;
                        }
                        studyObject["0008103E"] = {
                            "vr": "LO",
                            "Value": [
                                $this.description
                            ]
                        };
                        studyObject["00200013"] = {
                            "vr": "IS",
                            "Value": [
                                this.instanceNumber || 1
                            ]
                        };
                        studyObject["00200011"] = {
                            "vr": "IS",
                            "Value": [
                                this.seriesNumber || 0
                            ]
                        };
                        studyObject["0020000E"] = {
                            "vr": "UI",
                            "Value": [
                                `${studyObject["0020000D"].Value[0]}.${(this.seriesNumber || 0)}`
                            ]
                        };
                        studyObject["00080018"] = {
                            "vr": "UI",
                            "Value": [
                                `${studyObject["0020000D"].Value[0]}.${(this.seriesNumber || 0)}.${(this.instanceNumber || 1)}`
                            ]
                        };

                        if (file.type === "application/pdf") {
                            studyObject["00420011"] = {
                                "vr": "OB",
                                "BulkDataURI": "file/" + file.name
                            };
                            studyObject["00080016"] = {
                                "vr": "UI",
                                "Value": [
                                    "1.2.840.10008.5.1.4.1.1.104.1"
                                ]
                            }
                            studyObject["00280301"] = {
                                "vr": "CS",
                                "Value": [
                                    "YES"
                                ]
                            };
                            studyObject["00420012"] = {
                                "vr": "LO",
                                "Value": [
                                    "application/pdf"
                                ]
                            };
                            studyObject["00420010"] = {
                                "vr": "ST",
                                "Value": [
                                    $this.description
                                ]
                            };
                        } else {
                            if (file.type === "video/mpeg") {
                                studyObject["00080016"] = {
                                    "vr": "UI",
                                    "Value": [
                                        "1.2.840.10008.5.1.4.1.1.77.1.4.1"
                                    ]
                                }
                            } else {
                                studyObject["00080016"] = {
                                    "vr": "UI",
                                    "Value": [
                                        $this.selectedSopClass.value
                                    ]
                                }
                            }
                            studyObject["7FE00010"] = {
                                "vr": "OB",
                                "BulkDataURI": "file/" + file.name
                            }
                            transfareSyntax = ';transfer-syntax=' + transfareSyntax;
                        }
                        if(file.type === "image/jpeg"){
                            studyObject["00080008"] = {
                                "vr": "CS",
                                "Value": [
                                    "ORIGINAL",
                                    "PRIMARY"
                                ]
                            };
                            if(this.selectedSopClass.value === '1.2.840.10008.5.1.4.1.1.7'){
                                studyObject["00080064"] = {
                                    "vr": "CS",
                                    "Value": [
                                        "WSD"
                                    ]
                                };
                                studyObject["00200020"] = {
                                    "vr": "CS"
                                };
                            }
                        }
                        studyObject["00080060"] = {
                            "vr": "CS",
                            "Value": [
                                $this.modality
                            ]
                        };

                        // const dataView = new DataView(e.target['result']);

                        let object = [{}];
                        Object.keys(studyObject).forEach(key=>{
                            if(([
                                "00080054",
                                "00080056",
                                "00080061",
                                "00080062",
                                "00081190",
                                "00201200",
                                "00201206",
                                "00201208"
                            ].indexOf(key) === -1))
                                object[0][key] = studyObject[key];
                        });
                        const jsonData = dashes + boundary + crlf + 'Content-Type: application/dicom+json' + crlf + crlf + JSON.stringify(object) + crlf;

                        const postDataStart = jsonData + dashes + boundary + crlf + 'Content-Type: ' + file.type + transfareSyntax + crlf + 'Content-Location: file/' + file.name + crlf + crlf;
                        const postDataEnd = crlf + dashes + boundary + dashes;

                        $this.xmlHttpRequest.setRequestHeader('Content-Type', 'multipart/related;type="application/dicom+json";boundary=' + boundary);
                        $this.xmlHttpRequest.setRequestHeader('Accept', 'application/dicom+json');
                        if(!this.mainservice.global.notSecure) {
                            $this.xmlHttpRequest.setRequestHeader('Authorization', `Bearer ${token}`);
                        }
                        $this.xmlHttpRequest.upload.onprogress = function (e) {
                            if (e.lengthComputable) {
                                $this.percentComplete[file.name]['value'] = (e.loaded / e.total) * 100;
                            }
                        };
                        $this.xmlHttpRequest.onreadystatechange = () => {
                            if ($this.xmlHttpRequest.readyState === 4) {
                                if ($this.xmlHttpRequest.status === 200) {
                                    console.log('in response', JSON.parse($this.xmlHttpRequest.response));
                                    $this.percentComplete[file.name]['showLoader'] = false;
                                    $this.percentComplete[file.name]['showTicker'] = true;
                                } else {
                                    $this.percentComplete[file.name]['showLoader'] = false;
                                    console.log('in respons error', $this.xmlHttpRequest.status);
                                    console.log('statusText', $this.xmlHttpRequest.statusText);
                                    $this.percentComplete[file.name]['value'] = 0;
                                    $this.percentComplete[file.name]['status'] = $this.xmlHttpRequest.status + ' ' + $this.xmlHttpRequest.statusText;
                                }
                            }
                            // $this.percentComplete[file.name]['showLoader'] = true;
                        };
                        $this.xmlHttpRequest.upload.onloadstart = function (e) {
                            $this.percentComplete[file.name]['value'] = 1;
                        };
                        $this.xmlHttpRequest.upload.onloadend = function (e) {
                            if ($this.xmlHttpRequest.status === 200) {
                                $this.percentComplete[file.name]['showLoader'] = false;
                                $this.percentComplete[file.name]['value'] = 100;
                            }
                        };
                        //Send the binary data
                        // $this.xmlHttpRequest.send(payload);
                        $this.xmlHttpRequest.send(new Blob([new Blob([postDataStart]), file, new Blob([postDataEnd])]));
                        // };
                    } else {
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
        });
    }
    close(dialogRef){
        if (this.xmlHttpRequest){
            this.xmlHttpRequest.abort();
        }
        dialogRef.close(null);
    }
    onChange(newValue) {
        this._selectedAe = newValue.dicomAETitle;
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
    getWebApps(){
        this.studieService.getWebApps().subscribe((res)=>{
            this.webApps = res;
            this.webApps.forEach(webApp=>{
                if(webApp.dicomAETitle === this._selectedAe)
                    this.selectedWebApp = webApp;
            });
        },(err)=>{

        });
    }
}
