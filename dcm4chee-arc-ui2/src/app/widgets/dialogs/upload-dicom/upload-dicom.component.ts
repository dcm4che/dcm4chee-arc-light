import {Component, OnInit} from '@angular/core';
// import {FileUploader} from 'ng2-file-upload';
import {MatDialogRef} from '@angular/material';
import {Http} from '@angular/http';
import {UploadDicomService} from './upload-dicom.service';
import * as _ from 'lodash';
import {AppService} from "../../../app.service";
import {J4careHttpService} from "../../../helpers/j4care-http.service";
import {StudiesService} from "../../../studies/studies.service";
import {HttpErrorHandler} from "../../../helpers/http-error-handler";
import {KeycloakService} from "../../../helpers/keycloak-service/keycloak.service";
import {j4care} from "../../../helpers/j4care.service";
import {DcmWebApp} from "../../../models/dcm-web-app";

@Component({
  selector: 'app-upload-dicom',
  templateUrl: './upload-dicom.component.html'
})
export class UploadDicomComponent implements OnInit{


    private _aes;
    private _selectedAe;
    file;
    fileList: File[];
    xmlHttpRequest;
    percentComplete: any;
    webApps;
    selectedWebApp;
/*    public vendorUpload: FileUploader = new FileUploader({
        url: ``,
        // allowedMimeType:['application/octet-stream','application/zip']
        // headers: [{name: 'Content-Type', value: `multipart/related`}]
        // ,disableMultipart: true
    });*/
    constructor(
        public dialogRef: MatDialogRef<UploadDicomComponent>,
        private $http:J4careHttpService,
        private service: UploadDicomService,
        public mainservice:AppService,
        private studieService:StudiesService,
        private httpErrorHandler:HttpErrorHandler,
        private _keycloakService: KeycloakService
    ) {
        this.service.progress$.subscribe(
            data => {
                console.log('progress = ' + data);
            });
    }

    ngOnInit() {
        /*        this.vendorUpload = new FileUploader({
         url:`/devices/${this._deviceName}/vendordata`,
         allowedMimeType:['application/octet-stream','application/zip']
         });*/
        this.percentComplete = {};
/*        this.vendorUpload.onAfterAddingFile = (item) => {
            item.method = 'POST';
            console.log('this.vendorUpload.optionss', this.vendorUpload.options);
            console.log('item', item);
        };
        this.vendorUpload.onBeforeUploadItem = (item) => {
            this.addFileNameHeader(item.file.name);
        };*/
        this.getWebApps();
    }
    addFileNameHeader(fileName) {
        // var boundary=Math.random().toString().substr(2);
/*        console.log('this.vendorUpload.progress', this.vendorUpload.progress);
        console.log('this.vendorUpload.optionss', this.vendorUpload.options);*/
        // this.vendorUpload.setOptions({headers: [{
        //     name: 'Content-Type', value: `multipart/related`
        // }]});

    }

    fileChange(event) {
        let $this = this;
        let boundary = Math.random().toString().substr(2);
        let filetype;
        let token;
        this.fileList = event.target.files;

        if (this.fileList) {
            this._keycloakService.getToken().subscribe((response) => {
                if(!this.mainservice.global.notSecure){
                    token = response.token;
                }
                _.forEach(this.fileList, (file, i) => {
    /*                {
                        mode:"determinate",
                            value:0,
                        show:false
                    }*/
                    if(file.type && file.type != "application/dicom"){
                        $this.mainservice.setMessage({
                            'title': 'Error',
                            'text': `Filetype "${file.type}" not allowed!`,
                            'status': 'error'
                        });
                        $this.fileList = [];
                        event = null;
                        $this.file = null;
                    }else{

                        console.log("file",file);
                        console.log("filetype",file.type);
                        this.percentComplete[file.name] = {};
                        this.percentComplete[file.name]['value'] = 0;
                        $this.percentComplete[file.name]['showTicker'] = false;
                        $this.percentComplete[file.name]['showLoader'] = true;

                        let xmlHttpRequest = new XMLHttpRequest();
                        //Some AJAX-y stuff - callbacks, handlers etc.
                        let url;
                        if(this.selectedWebApp){
                            url = this.service.getUrlFromWebApp(this.selectedWebApp);
                        }else{
                            url = `../aets/${$this._selectedAe}/rs/studies`;
                        }
                        xmlHttpRequest.open('POST', url, true);
                        let dashes = '--';
                        let crlf = '\r\n';
                        //Post with the correct MIME type (If the OS can identify one)
                        if (file.type == '') {
                            filetype = 'application/dicom';
                        } else {
                            filetype = file.type;
                        }
                        const postDataStart = dashes + boundary + crlf + 'Content-Disposition: form-data;' + 'name=\"file\";' + 'filename=\"' + encodeURIComponent(file.name) + '\"' + crlf + 'Content-Type: ' + filetype + crlf + crlf;
                        const postDataEnd = crlf + dashes + boundary + dashes;
                        xmlHttpRequest.setRequestHeader('Content-Type', 'multipart/related;type="application/dicom";boundary=' + boundary);
                        xmlHttpRequest.setRequestHeader('Accept', 'application/dicom+json');
                        if(!this.mainservice.global.notSecure) {
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
                                    console.log('in response', JSON.parse(xmlHttpRequest.response));
                                    $this.percentComplete[file.name]['showLoader'] = false;
                                    $this.percentComplete[file.name]['showTicker'] = true;
                                } else {
                                    console.log('in respons error', xmlHttpRequest.status);
                                    console.log('statusText', xmlHttpRequest.statusText);
                                    $this.percentComplete[file.name]['showLoader'] = false;
                                    $this.percentComplete[file.name]['value'] = 0;
                                    $this.percentComplete[file.name]['status'] = xmlHttpRequest.status + ' ' + xmlHttpRequest.statusText;
                                    let jsonFormat = JSON.parse(xmlHttpRequest.response);
                                    $this.httpErrorHandler.handleError(jsonFormat || xmlHttpRequest.response);
                                }
                            }
                        };
                        xmlHttpRequest.upload.onloadstart = function (e) {
                            $this.percentComplete[file.name]['value'] = 0;
                        };
                        xmlHttpRequest.upload.onloadend = function (e) {
                            if (xmlHttpRequest.status === 200){
                                $this.percentComplete[file.name]['showLoader'] = false;
                                $this.percentComplete[file.name]['value'] = 100;
                            }
                        };
                        //Send the binary data
                        xmlHttpRequest.send(new Blob([new Blob([postDataStart]),file, new Blob([postDataEnd])]));
                        // };
                    }
                });
            });
        }

    }
    uploadFile(dialogRef){

/*        this.vendorUpload.setOptions({url: `../aets/${this._selectedAe}/rs/studies`});
        this.vendorUpload.uploadAll();*/
        // dialogRef.close("ok");
    }
    close(dialogRef){
        dialogRef.close(null);
    }
/*    onChange(newValue) {
        this._selectedAe = newValue.title;
    }*/
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
            this.webApps.forEach((webApp:DcmWebApp)=>{
               if(webApp.dicomAETitle === this._selectedAe || (this.selectedWebApp && this.selectedWebApp.dcmWebAppName === webApp.dcmWebAppName))
                 this.selectedWebApp = webApp;
            });
        },(err)=>{
            j4care.log("Something went wrong on getting webApps", err);
            this.httpErrorHandler.handleError(err);
        });
    }
}
