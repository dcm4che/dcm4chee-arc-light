import { Injectable } from '@angular/core';
import {AppService} from '../../app.service';
import {J4careHttpService} from "../../helpers/j4care-http.service";

@Injectable()
export class UPSService {

    constructor(public $http:J4careHttpService, public mainservice: AppService) {
    }

    search() {
        return this.$http.get('../upstpls');
    };
}
