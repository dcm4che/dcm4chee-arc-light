import {Injectable} from "@angular/core";
import {AppService} from "../app.service";
import * as _ from 'lodash';
import {WindowRefService} from "./window-ref.service";

@Injectable()
export class HttpErrorHandler {
    constructor(private mainservice:AppService){}

    public handleError(error){
        if ((error._body && error._body != '') || _.hasIn(error,"message") || _.hasIn(error,"error") || _.hasIn(error, "[00000902].Value[0]")) {
            try{
                if(_.hasIn(error,"message") || _.hasIn(error,"error.errorMessage")){
                    if(_.hasIn(error,"error.errorMessage")){
                        this.mainservice.setMessage({
                            'title': 'Error ' + (error.status||''),
                            'text': _.get(error,"error.errorMessage"),
                            'status': 'error'
                        });
                    }else{
                        this.mainservice.setMessage({
                            'title': 'Error ' + (error.status||''),
                            'text': error["message"],
                            'status': 'error'
                        });
                    }
                }else{
                    if(_.hasIn(error, "[00000902].Value[0]")){
                        this.mainservice.setMessage({
                            'title': 'Error ' + (error.status||''),
                            'text': `${_.get(error,"[00000902].Value[0]")}<br>${(_.hasIn(error,'["00081198"].Value["0"]["00081197"].Value["0"]') ? 'Failure Reason:' + _.get(error,'["00081198"].Value["0"]["00081197"].Value["0"]'):'')}`,
                            'status': 'error'
                        });
                    }else{
                        if( _.hasIn(error,"error")){
                            this.mainservice.showError(error.error);
                        }else{
                            let msg = "Error";
                            let msgObject = JSON.parse(error._body);
                            if(_.hasIn(msgObject,"msa-3")){
                                msg = msgObject["msa-3"];
                            }
                            if(_.hasIn(msgObject,"err-8")){
                                msg = msgObject["erSr-8"];
                            }
                            if(_.hasIn(msgObject,"errorMessage")){
                                msg = msgObject["errorMessage"];
                            }
                            this.mainservice.setMessage({
                                'title': 'Error ' + (error.status||''),
                                'text': msg,
                                'status': 'error'
                            });
                        }
                    }
                }

            }catch (e){
                if(error.status === 0 && error.statusText === ""){
                    /*if(_.hasIn(error,"_body.target.__zone_symbol__xhrURL") && _.get(error,"_body.target.__zone_symbol__xhrURL") === "rs/realm"){
                        WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";
                    }else {
                        this.mainservice.setMessage({
                            'title': 'Error ' + (error.status||''),
                            'text': `Request didn't work (${_.get(error,"_body.target.__zone_symbol__xhrURL") || ''})`,
                            'status': 'error'
                        });
                    }*/
                }else{
                    this.mainservice.setMessage({
                        'title': 'Error ' + error.status,
                        'text': error.statusText + '!',
                        'status': 'error',
                        'detailError': error._body
                    });
                }
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
