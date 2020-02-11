import { Component, OnInit } from '@angular/core';
import { MatDialogRef } from "@angular/material/dialog";
import {DomSanitizer} from "@angular/platform-browser";
import {HttpClient} from "@angular/common/http";
import {Observable} from "rxjs/Observable";
import {J4careHttpService} from "../../../helpers/j4care-http.service";
import {j4care} from "../../../helpers/j4care.service";

@Component({
    selector: 'app-media-viewer',
    templateUrl: './media-viewer.component.html',
    styleUrls: ['./media-viewer.component.scss']
})
export class MediaViewerComponent implements OnInit {
    title;
    url;
    contentType;
    pdfUrl;
    iframe;
    constructor(
        public dialogRef: MatDialogRef<MediaViewerComponent>,
        public sanitizer: DomSanitizer,
        public $http:J4careHttpService,
        public $nativeHttp:HttpClient
    ){}

    ngOnInit() {
        if(this.contentType.indexOf("xml") > -1){
            this.loadXML();
        }else{
            this.setPDFUrl();
        }
    }

    onVideoMouseEnter(e){
        console.log("e",e);
        e.target.setAttribute("controls",true);
    }

    onVideoMouseLeave(e){
        console.log("e",e);
        e.target.setAttribute("controls",false);

    }
    setPDFUrl(){
        this.pdfUrl = this.sanitizer.bypassSecurityTrustResourceUrl(this.url);
    }
    printIframe(){
        this.iframe.contentWindow.focus();// focus on contentWindow is needed on some ie versions
        this.iframe.contentWindow.print();
    }

    loadXML(){
        try{
            let loadXMLDoc = (filename) => {
                let xhttp = new XMLHttpRequest();
                xhttp.open("GET", filename, false);
                xhttp.send("");
                return xhttp.responseXML;
            };

            let xml = loadXMLDoc(this.url);
            let xsl = loadXMLDoc('./assets/xsl/cda_nojs.xsl');
            let xsltProcessor;
            let resultDocument;
            if (document.implementation && document.implementation.createDocument) {
                xsltProcessor = new XSLTProcessor();
                xsltProcessor.importStylesheet(xsl);
                resultDocument = xsltProcessor.transformToFragment(xml, document);

                this.iframe = document.createElement("iframe");
                this.iframe.style.width = "100%";
                this.iframe.style.height = "100%";
                document.getElementById('container').appendChild(this.iframe);
                let iframeDoc = this.iframe.contentDocument || this.iframe.contentWindow.document;
                iframeDoc.body.appendChild(resultDocument);
                let userSelection = iframeDoc.getElementsByClassName('cda-render');

                for(let i = 0; i < userSelection.length; i++) {
                    userSelection[i].addEventListener("click", (e)=> {
                        e.preventDefault();
                        iframeDoc.getElementById(e.target.hash.replace(/#/g, '')).scrollIntoView();
                    })
                }
            }
        }catch (e) {
            j4care.log("Error on loading xml",e);

        }
    }
}
