import { Component, OnInit } from '@angular/core';
import {MatDialogRef} from "@angular/material";
import * as _ from 'lodash';
import {AppService} from "../../../app.service";
import {J4careHttpService} from "../../../helpers/j4care-http.service";
import {StudiesService} from "../../../studies/studies.service";
import {UploadDicomService} from "../upload-dicom/upload-dicom.service";
import {KeycloakService} from "../../../helpers/keycloak-service/keycloak.service";
import {j4care} from "../../../helpers/j4care.service";
import {ComparewithiodPipe} from "../../../pipes/comparewithiod.pipe";
import {StudyDicom} from "../../../models/study-dicom";
import {StudyService} from "../../../study/study/study.service";
import {DcmWebApp} from "../../../models/dcm-web-app";

// declare var uuidv4: any;

// import * as uuidv4 from  'uuid/v4';


@Component({
  selector: 'app-upload-files',
    templateUrl: './upload-files.component.html',
    styles:[`        
        .upload label{
            float: left;
            width: 100%;
        }
    `]
})
export class UploadFilesComponent implements OnInit {


    private _aes;
    private _selectedAe;
    private _dicomObject;
    private _fromExternalWebApp;
    private _preselectedWebApp:DcmWebApp;
    mode;
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
    // instanceNumber = 1;
    moreAttributes = false;
    dropdown;
    iod;
    iodFileNameFromMode = {
        patient:"study",
        study:"series",
        mwl:"mwl"
    };
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
    ];
    constructor(
        public dialogRef: MatDialogRef<UploadFilesComponent>,
        public mainservice:AppService,
        public $http:J4careHttpService,
        private studyService:StudyService,
        private uploadDicomService:UploadDicomService,
        private _keycloakService: KeycloakService
    ) {
    }

    ngOnInit() {
        this.percentComplete = {};
        this.selectedSopClass = this.imageType[0];
        if(!this._fromExternalWebApp){
            this.getWebApps();
        }else{
            this.selectedWebApp = this._fromExternalWebApp;
        }
    }

    fileChange(event){
        this.fileList = event.target.files;
        if(this.fileList[0] && this.fileList[0].type === "image/jpeg"){
            this.isImage = true;
        }
/*        console.log("filtetypes",this.fileList);
        let type;
        let i = 0;
        _.forEach(this.fileList,file=>{
            if(!type){
                type = file.type;
                i++;
            }else{
                if(type != file.type){
                   i++;
                }
            }
        });
        if(i > 1){
            this.mainservice.showError("Mixed file types at once is not supported!");
            this.fileList = undefined;
            this.file = undefined;
            this.isImage = undefined;
        }
        //video / video.iod.json
        //image / sc.iod.json == screenshot or photograph vlPhotographic.iod.json
        //pdf   /encapsulatedPDF.iod.json*/

    }
    showMoreAttributes(){
        console.log("this.dicomObject",this.dicomObject);
        if(!this._dicomObject){
            this._dicomObject = {
                attrs:[]
            }
        }
/*        if(this.mode === "study"){
            if(this.seriesNumber && this.seriesNumber != 0){
                this._dicomObject.attrs['00200011'] = {
                    "vr": "IS",
                    "Value": [
                        this.seriesNumber || 0
                    ]
                };
            }
            if(this.description && this.description != ""){
                this._dicomObject.attrs['0008103E'] = {
                    "vr": "LO",
                    "Value": [
                        this.description
                    ]
                };
            }
        }*/
        this.studyService.initEmptyValue(this._dicomObject.attrs);

        this.studyService.getIod(this.iodFileNameFromMode[this.mode]).subscribe((iod) => {
            this._dicomObject.attrs = new ComparewithiodPipe().transform(this._dicomObject.attrs, iod);
            this.iod = this.studyService.replaceKeyInJson(iod, 'items', 'Value');
            console.log("iod",iod);
            console.log("dicomOjbect",this.dicomObject);
            this.dropdown = this.studyService.getArrayFromIod(iod);
            this.moreAttributes = !this.moreAttributes;
        });
    }
    onStudyChange(e:StudyDicom){
        console.log("e",e);
        console.log("this._dicomObject.attrs",this._dicomObject.attrs);
        // this._dicomObject.attrs = e.attrs;
    }
    upload() {
        let $this = this;
        let boundary = Math.random().toString().substr(2);
        let descriptionPart;
        let token;
        this.showFileList = true;
/*        $this.studyService.clearPatientObject(this.dicomObject.attrs);
        $this.studyService.convertStringToNumber(this.dicomObject.attrs);
        // StudiesService.convertDateToString($scope, "editstudyFiltered");

        //Add patient attributs again
        // angular.extend($scope.editstudyFiltered.attrs, patient.attrs);
        // $scope.editstudyFiltered.attrs.concat(patient.attrs);
        let local = {};
        // $this.studyService.appendPatientIdTo(patient.attrs, local);
        // local["00100020"] = patient.attrs["00100020"];
        _.forEach(this.dicomObject.attrs, (m, i)=>{
            if (this.iod[i]){
                local[i] = m;
            }
        });*/
        let seriesInstanceUID;
        this._keycloakService.getToken().subscribe((response) => {
            if(!this.mainservice.global.notSecure){
                token = response.token;
            }
            if(!this.seriesNumber && this.seriesNumber != 0){
                this.seriesNumber = 0;
            }
            if (this.fileList) {
                seriesInstanceUID = j4care.generateOIDFromUUID();
                _.forEach(this.fileList, (file, i) => {
                    let transfareSyntax;
                    switch (file.type) {
                        case "image/jpeg":
                            transfareSyntax = "1.2.840.10008.1.2.4.50";
                            $this.modality = $this.selectedSopClass.modality;
                            descriptionPart = "Image";
                            break;
                        case "video/mpeg":
                            transfareSyntax = "";
                            descriptionPart = "Video";
                            $this.modality = "XC";
                            break;
                        case "video/mp4":
                            transfareSyntax = "";
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

                        let xmlHttpRequest = new XMLHttpRequest();
                        //Some AJAX-y stuff - callbacks, handlers etc.
                        let url = this.uploadDicomService.getUrlFromWebApp(this.selectedWebApp);
                        if (url) {
                            this.percentComplete[file.name] = {};
                            $this.percentComplete[file.name]['showTicker'] = false;
                            $this.percentComplete[file.name]['showLoader'] = true;
                            xmlHttpRequest.open('POST', url, true);
                            let dashes = '--';
                            let crlf = '\r\n';
                            //Post with the correct MIME type (If the OS can identify one)
                            /*                        let studyObject = _.pickBy(local, (o, i) => {
                                                        return (i.toString().indexOf("777") === -1);
                                                    });          */
                            let studyObject = _.pickBy(this._dicomObject.attrs, (o, i) => {
                                return (i.toString().indexOf("777") === -1);
                            });
                            if (!$this.description || $this.description === "") {
                                $this.description = "Imported " + descriptionPart;
                            }
                            studyObject["0008103E"] = {
                                "vr": "LO",
                                "Value": [
                                    $this.description
                                ]
                            };
                            studyObject["00200013"] = { //"00200013":"Instance Number"
                                "vr": "IS",
                                "Value": [
                                    i + 1
                                ]
                            };
                            studyObject["00200011"] = { // "00200011":"Series Number"
                                "vr": "IS",
                                "Value": [
                                    this.seriesNumber || 0
                                ]
                            };
                            if (_.hasIn(studyObject, "0020000D.Value[0]")) {
                                studyObject["0020000E"] = { ///"0020000E":"Series Instance UID" //Decides if the file in the same series appear
                                    "vr": "UI",
                                    "Value": [
                                        seriesInstanceUID
                                    ]
                                };
                            }
                            studyObject["00080018"] = {
                                "vr": "UI",
                                "Value": [
                                    j4care.generateOIDFromUUID()
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
                                if (file.type.indexOf("video") > -1) {
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
                                // transfareSyntax = ';transfer-syntax=' + transfareSyntax;
                            }
                            if (file.type === "image/jpeg") {
                                studyObject["00080008"] = {
                                    "vr": "CS",
                                    "Value": [
                                        "ORIGINAL",
                                        "PRIMARY"
                                    ]
                                };
                                if (this.selectedSopClass.value === '1.2.840.10008.5.1.4.1.1.7') {
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
                            let object = [{}];
                            Object.keys(studyObject).forEach(key => {
                                if (([
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

                            const postDataStart = jsonData + dashes + boundary + crlf + 'Content-Type: ' + file.type + crlf + 'Content-Location: file/' + file.name + crlf + crlf;
                            const postDataEnd = crlf + dashes + boundary + dashes;

                            xmlHttpRequest.setRequestHeader('Content-Type', 'multipart/related;type="application/dicom+json";boundary=' + boundary);
                            xmlHttpRequest.setRequestHeader('Accept', 'application/dicom+json');
                            if (!this.mainservice.global.notSecure) {
                                xmlHttpRequest.setRequestHeader('Authorization', `Bearer ${token}`);
                            }
                            xmlHttpRequest.upload.onprogress = function (e) {
                                if (e.lengthComputable) {
                                    $this.percentComplete[file.name]['value'] = (e.loaded / e.total) * 100;
                                }
                            };
                            xmlHttpRequest.onreadystatechange = () => {
                                if (xmlHttpRequest.readyState === 4) {
                                    if (xmlHttpRequest.status === 200) {
                                        $this.percentComplete[file.name]['showLoader'] = false;
                                        $this.percentComplete[file.name]['showTicker'] = true;
                                        console.log('in response', JSON.parse(xmlHttpRequest.response));
                                    } else {
                                        $this.percentComplete[file.name]['showLoader'] = false;
                                        console.log('in respons error', xmlHttpRequest.status);
                                        console.log('statusText', xmlHttpRequest.statusText);
                                        $this.percentComplete[file.name]['value'] = 0;
                                        $this.percentComplete[file.name]['status'] = xmlHttpRequest.status + ' ' + xmlHttpRequest.statusText;
                                    }
                                }
                                // $this.percentComplete[file.name]['showLoader'] = true;
                            };
                            xmlHttpRequest.upload.onloadstart = function (e) {
                                $this.percentComplete[file.name]['value'] = 1;
                            };
                            xmlHttpRequest.upload.onloadend = function (e) {
                                if (xmlHttpRequest.status === 200) {
                                    $this.percentComplete[file.name]['showLoader'] = false;
                                    $this.percentComplete[file.name]['showTicker'] = true;
                                    $this.percentComplete[file.name]['value'] = 100;
                                }
                            };
                            xmlHttpRequest.send(new Blob([new Blob([postDataStart]), file, new Blob([postDataEnd])]));
                        }else{
                            this.mainservice.showError("A STOW-RS server is missing!")
                        }
                    } else {
                        $this.mainservice.setMessage({
                            'title': 'Error',
                            'text': `File type "${file.type}" not allowed!`,
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
    get fromExternalWebApp() {
        return this._fromExternalWebApp;
    }

    set fromExternalWebApp(value) {
        this._fromExternalWebApp = value;
    }

    get preselectedWebApp():DcmWebApp {
        return this._preselectedWebApp;
    }

    set preselectedWebApp(value:DcmWebApp) {
        this._preselectedWebApp = value;
    }

    getWebApps(){
        this.studyService.getWebApps({dcmWebServiceClass:"STOW_RS"}).subscribe((res)=>{
            this.webApps = res;
            this.webApps.forEach((webApp:DcmWebApp)=>{
                if(this._preselectedWebApp){
                    if(webApp.dcmWebAppName === this._preselectedWebApp.dcmWebAppName){
                        this.selectedWebApp = webApp;
                    }
                }else{
                    if(webApp.dicomAETitle === this._selectedAe)
                        this.selectedWebApp = webApp;
                }
            });
        },(err)=>{

        });
    }
}
