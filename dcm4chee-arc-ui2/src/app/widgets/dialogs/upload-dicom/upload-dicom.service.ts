import { Injectable } from '@angular/core';
import {Observable} from 'rxjs';
import * as _ from "lodash";
@Injectable()
export class UploadDicomService {
    progress$;
    progressObserver;
    constructor () {
        this.progress$ = Observable.create(observer => {
            this.progressObserver = observer;
        }).share();
    }

    makeFileRequest(url: string, params: string[], files: File[]) {
        return Observable.create(observer => {
            let formData: FormData = new FormData(),
                xhr: XMLHttpRequest = new XMLHttpRequest();
            // formData.append('Content-Type', new Blob(['some plain text'], {type : 'Application/dicom'}));

            for (let i = 0; i < files.length; i++) {
                let fileObj = files[i];
                formData.append( '{ size : ' + fileObj.size + ' }' , files[i].name);
            }
/*            for (let i = 0; i < files.length; i++) {
                formData.append("uploads[]", files[i], files[i].name);
            }*/
            // xhr.setRequestHeader("Accept","Application/dicom");
            xhr.onreadystatechange = () => {
                if (xhr.readyState === 4) {
                    if (xhr.status === 200) {
                        console.log('responseheader', xhr.getResponseHeader('Content-Type'));
                        observer.next(JSON.parse(xhr.response));
                        observer.complete();
                    } else {
                        observer.error(xhr.response);
                    }
                }

            };
            let $this = this;
            xhr.upload.onprogress = (event) => {
                $this.progress$ = Math.round(event.loaded / event.total * 100);

                $this.progressObserver.next($this.progress$);
            };

            xhr.open('POST', url, true);
            xhr.send(formData);
        });
    }
    getUrlFromWebApp(webApp){
        try{
            let networkConnectionKey = "dicomNetworkConnection";
            if(!_.hasIn(webApp, networkConnectionKey) && _.hasIn(webApp, "dicomNetworkConnectionReference")){
                networkConnectionKey = "dicomNetworkConnectionReference";
            }
            let protocol = "http:";
            if(_.hasIn(webApp, `${networkConnectionKey}[0].dicomTLSCipherSuite`) && _.isArray(webApp[networkConnectionKey][0].dicomTLSCipherSuite) && webApp[networkConnectionKey][0].dicomTLSCipherSuite.length > 0){
                protocol = "https:";
            }
            return `${protocol}//${webApp[networkConnectionKey][0].dicomHostname}:${webApp[networkConnectionKey][0].dicomPort}${webApp.dcmWebServicePath}/studies`;
        }catch (e){
            return null;
        }
    }
}
