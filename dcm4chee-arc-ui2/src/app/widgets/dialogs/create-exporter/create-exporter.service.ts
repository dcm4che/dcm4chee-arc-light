import { Injectable } from '@angular/core';
import {Http} from '@angular/http';

@Injectable()
export class CreateExporterService {

    constructor(private $http: Http) { }

    getDevice(devicename){
        return this.$http.get('../devices/' + devicename).map(device => device.json());
    }
    getQueue(){
        return this.$http.get('../queue').map(queue => queue.json());
    }
}
