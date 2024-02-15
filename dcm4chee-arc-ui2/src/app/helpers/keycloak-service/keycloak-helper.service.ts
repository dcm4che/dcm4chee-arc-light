import { Injectable } from '@angular/core';
import {map, switchMap} from "rxjs/operators";
import {KeycloakService} from "./keycloak.service";
import {J4careHttpService} from "../j4care-http.service";
import * as _ from 'lodash-es';

@Injectable({
  providedIn: 'root'
})
export class KeycloakHelperService {

  constructor(
      private keycloakService:KeycloakService,
      private $http:J4careHttpService
  ) { }

  changeLanguageToUserProfile(languageCode){
    let userInfoTemp:any;
    return this.keycloakService.getUserInfo().pipe(
        map(userInfo=>{
          userInfoTemp = userInfo;
          return userInfo;
        }),
        switchMap(userInfo=>this.$http.get(`${KeycloakService.keycloakConfig.url}/admin/realms/dcm4che/users/${userInfoTemp.userProfile.id}?userProfileMetadata=true`)),
        map(userProfileMetadata=>{
          _.set(userProfileMetadata,"attributes.language[0]",languageCode);
          return userProfileMetadata;
        }),
        switchMap(userProfileMetadata=>this.$http.put(`${KeycloakService.keycloakConfig.url}/admin/realms/dcm4che/users/${userInfoTemp.userProfile.id}`,userProfileMetadata))
    );
  }
}
