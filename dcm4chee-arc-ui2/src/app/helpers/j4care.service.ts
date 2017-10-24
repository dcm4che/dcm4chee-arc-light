import { Injectable } from '@angular/core';
import {AppService} from "../app.service";
import {J4careHttpService} from "./j4care-http.service";
declare var fetch;
declare var Headers;
@Injectable()
export class j4care {

    constructor(
        public mainservice:AppService,
        public httpJ4car:J4careHttpService
    ) {}
    static traverse(object,func){
        for(let key in object){
            if(object.hasOwnProperty(key)) {
                if(typeof object[key] === "object"){
                    this.traverse(object[key],func);
                }else{
                    object[key] = func.apply(object,[object[key],key]);
                }
            }
        }
        return object;
    }

    download(url){
        this.httpJ4car.refreshToken().subscribe((res)=>{
            let token;
            if(res.length && res.length > 0){
                this.httpJ4car.resetAuthenticationInfo(res);
                token = res.token;
            }else{
                token = this.mainservice.global.authentication.token;
            }
/*            let xhttp = new XMLHttpRequest();
            let filename = url.substring(url.lastIndexOf("/") + 1).split("?")[0];
            xhttp.onload = function() {
                let a = document.createElement('a');
                a.href = window.URL.createObjectURL(xhttp.response); // xhr.response is a blob
                let attr = document.createAttribute("download");
                // attr.value = "true";
                a.setAttributeNode(attr);
                // a.download = filename; // Set the file name.
                a.style.display = 'none';
                document.body.appendChild(a);
                a.click();
                a.remove();
            };
            xhttp.open("GET", url);
            xhttp.responseType = "blob";
            xhttp.setRequestHeader('Authorization', `Bearer ${token}`);
            xhttp.send();*/
            console.log("token",token);
            var myHeaders = new Headers();
            myHeaders.append('Authorization', `Bearer ${token}`);

            var myInit = { method: 'GET',
                headers: myHeaders,
                mode: 'cors',
                cache: 'default' };
            let a = document.createElement('a');;

            fetch(url,myInit)
                .then((response)=> {
                    console.log("response.url",response);
                    return response.body.getReader();
                })
                .then((myBlob)=> {
                    a.href = window.URL.createObjectURL(myBlob);
                    let attr = document.createAttribute("download");
                    a.setAttributeNode(attr);
                    // a.download = filename; // Set the file name.
                    a.style.display = 'none';
                    document.body.appendChild(a);
                    a.click();
                    a.remove();
                });
        });
    }
}
