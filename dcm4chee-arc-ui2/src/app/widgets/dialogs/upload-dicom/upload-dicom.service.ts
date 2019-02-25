import { Injectable } from '@angular/core';
import {Observable} from 'rxjs';

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
            return `${location.protocol}//${webApp.dicomNetworkConnection[0].dicomHostname}:${webApp.dicomNetworkConnection[0].dicomPort}${webApp.dcmWebServicePath}/studies`;
        }catch (e){
            return null;
        }
    }
}
