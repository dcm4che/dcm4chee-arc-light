import { Injectable } from '@angular/core';
import {Http} from '@angular/http';
import {J4careHttpService} from "../../helpers/j4care-http.service";

@Injectable()
export class ControlService {

  constructor(private $http:J4careHttpService) { }

  reloadArchive(){
      return this.$http.post('/dcm4chee-arc/ctrl/reload', {});
  }
}
