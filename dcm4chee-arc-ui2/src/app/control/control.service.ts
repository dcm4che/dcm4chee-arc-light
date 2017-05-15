import { Injectable } from '@angular/core';
import {Http} from "@angular/http";

@Injectable()
export class ControlService {

  constructor(private $http:Http) { }

  reloadArchive(){
      return this.$http.post("/dcm4chee-arc/ctrl/reload",{})
  }
}
