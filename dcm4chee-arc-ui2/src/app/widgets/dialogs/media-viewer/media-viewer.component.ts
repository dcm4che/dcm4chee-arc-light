import { Component, OnInit } from '@angular/core';
import {MatDialogRef} from "@angular/material";
import {DomSanitizer} from "@angular/platform-browser";

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
    constructor(
        public dialogRef: MatDialogRef<MediaViewerComponent>,
        public sanitizer: DomSanitizer
    ){}

    ngOnInit() {
        this.setPDFUrl();
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


}
