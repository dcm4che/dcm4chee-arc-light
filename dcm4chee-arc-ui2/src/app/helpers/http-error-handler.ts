import {Injectable} from "@angular/core";
import {AppService} from "../app.service";

@Injectable()
export class HttpErrorHandler {
    constructor(private mainservice:AppService){}

    public handleError(error){
        if (error._body && error._body != '') {
            try{
                this.mainservice.setMessage({
                    'title': 'Error ' + error.status,
                    'text': JSON.parse(error._body).errorMessage,
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
