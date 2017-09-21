import {Injectable} from "@angular/core";
import {AppService} from "../app.service";
import * as _ from 'lodash';

@Injectable()
export class HttpErrorHandler {
    constructor(private mainservice:AppService){}

    public handleError(error){
        if (error._body && error._body != '') {
            try{
                let msgObject = JSON.parse(error._body);
                let msg = "Error";
                if(_.hasIn(msgObject,"msa-3")){
                    msg = msgObject["msa-3"];
                }
                if(_.hasIn(msgObject,"err-8")){
                    msg = msgObject["err-8"];
                }
                if(_.hasIn(msgObject,"errorMessage")){
                    msg = msgObject["errorMessage"];
                }
                this.mainservice.setMessage({
                    'title': 'Error ' + error.status,
                    'text': msg,
                    'status': 'error'
                });

            }catch (e){
                this.mainservice.setMessage({
                    'title': 'Error ' + error.status,
                    'text': error.statusText + '!',
                    'status': 'error',
                    'detailError': error._body
                });
            }
        }else{
            this.mainservice.setMessage({
                'title': 'Error ' + error.status,
                'text': error.statusText,
                'status': 'error'
            });
        }
    }
}
