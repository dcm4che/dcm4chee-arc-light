import { Component, OnInit } from '@angular/core';
import {j4care} from "../../../helpers/j4care.service";
import {AppService} from "../../../app.service";
import {MatDialogRef} from "@angular/material";
import {J4careHttpService} from "../../../helpers/j4care-http.service";
import {HttpErrorHandler} from "../../../helpers/http-error-handler";
import {KeycloakService} from "../../../helpers/keycloak-service/keycloak.service";

@Component({
  selector: 'app-viewer',
  templateUrl: './viewer.component.html',
  styleUrls: ['./viewer.component.css']
})
export class ViewerComponent implements OnInit {

    private _url;
    private _views;
    private _view;
    private _contentType;
    renderedUrl;
    xhr = new XMLHttpRequest();
    showLoader;
    constructor(
        public dialogRef: MatDialogRef<ViewerComponent>,
        private j4care:j4care,
        private mainservice:AppService,
        private $http:J4careHttpService,
        public httpErrorHandler:HttpErrorHandler,
        private _keycloakService: KeycloakService
    ) { }

    ngOnInit() {
        console.log("url",this._url);
        console.log("_views",this._views);
        //, frameNumber: inst.view
        // this.j4care.download(this._url);
        this.loadImage();
    }
    loadImage(){
        let token;
        let $this = this;
        this._keycloakService.getToken().subscribe((response)=>{
            if(!this.mainservice.global.notSecure){
                token = response.token;
            }
            this.showLoader = true;
            let url = this._url;
            if(this._contentType != 'video/mpeg'){
                url = this._url + `&frameNumber=${this._view}`;
            }
            if(!this._contentType){
                this._contentType = 'image/jpeg';
            }
            $this.xhr.open("GET", url, true);   // Make sure file is in same server
            $this.xhr.overrideMimeType('text/plain; charset=x-user-defined');
            if(!this.mainservice.global.notSecure) {
                $this.xhr.setRequestHeader('Authorization', `Bearer ${token}`);
            }
            $this.xhr.send(null);
            $this.xhr.onloadstart = (res)=>{
                console.log("onloade res",res);
            };

            $this.xhr.onreadystatechange = function() {
                if ($this.xhr.readyState == 4){
                    if (($this.xhr.status == 200) || ($this.xhr.status == 0)){
                        $this.renderedUrl = `data:${$this.contentType};base64,` + encode64($this.xhr.responseText);
                        $this.showLoader = false;
                    }else{
                        $this.httpErrorHandler.handleError($this.xhr);
                        $this.showLoader = false;
                        $this.dialogRef.close(null);
                    }
                }
            };
            function encode64(inputStr){
                var b64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";
                var outputStr = "";
                var i = 0;

                while (i<inputStr.length){
                    var byte1 = inputStr.charCodeAt(i++) & 0xff;
                    var byte2 = inputStr.charCodeAt(i++) & 0xff;
                    var byte3 = inputStr.charCodeAt(i++) & 0xff;

                    var enc1 = byte1 >> 2;
                    var enc2 = ((byte1 & 3) << 4) | (byte2 >> 4);

                    var enc3, enc4;
                    if (isNaN(byte2)){
                        enc3 = enc4 = 64;
                    } else{
                        enc3 = ((byte2 & 15) << 2) | (byte3 >> 6);
                        if (isNaN(byte3)){
                            enc4 = 64;
                        } else {
                            enc4 = byte3 & 63;
                        }
                    }
                    outputStr +=  b64.charAt(enc1) + b64.charAt(enc2) + b64.charAt(enc3) + b64.charAt(enc4);
                }
                return outputStr;
            }
        });
    }
    changeImage(mode){
        if(mode === "prev" && this._view > 1){
            this.view--;
        }
        if(mode === "next" && this._view < this._views.length){
            this.view++;
        }
        this.loadImage();
    }
    get url() {
      return this._url;
    }

    set url(value) {
      this._url = value;
    }

    get views() {
        return this._views;
    }

    set views(value) {
        this._views = value;
    }

    get view() {
        return this._view;
    }

    set view(value) {
        this._view = value;
    }

    get contentType() {
        return this._contentType;
    }

    set contentType(value) {
        this._contentType = value;
    }
}
