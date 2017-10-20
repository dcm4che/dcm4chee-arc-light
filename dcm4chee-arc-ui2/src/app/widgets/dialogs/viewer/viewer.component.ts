import { Component, OnInit } from '@angular/core';
import {j4care} from "../../../helpers/j4care.service";
import {AppService} from "../../../app.service";
import {MdDialogRef} from "@angular/material";

@Component({
  selector: 'app-viewer',
  templateUrl: './viewer.component.html',
  styleUrls: ['./viewer.component.css']
})
export class ViewerComponent implements OnInit {

    private _url;
    private _views;
    private _contentType;
    renderedUrl;
    constructor(
        public dialogRef: MdDialogRef<ViewerComponent>,
        private j4care:j4care,
        private mainservice:AppService
    ) { }

    ngOnInit() {
        console.log("url",this._url);
        console.log("_views",this._views);
        //, frameNumber: inst.view
        // this.j4care.download(this._url);
        let $this = this;
        let xhr = new XMLHttpRequest();

        xhr.open("GET", this._url, true);   // Make sure file is in same server
        xhr.overrideMimeType('text/plain; charset=x-user-defined');
        let token = this.mainservice.global.authentication.token;
        xhr.setRequestHeader('Authorization', `Bearer ${token}`);
        xhr.send(null);

        xhr.onreadystatechange = function() {
            if (xhr.readyState == 4){
                if ((xhr.status == 200) || (xhr.status == 0)){
                    $this.renderedUrl = `data:image/jpeg;base64,` + encode64(xhr.responseText);
                }else{
                    alert("Something misconfiguration : " +
                        "\nError Code : " + xhr.status +
                        "\nError Message : " + xhr.responseText);
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

    get contentType() {
        return this._contentType;
    }

    set contentType(value) {
        this._contentType = value;
    }
}
