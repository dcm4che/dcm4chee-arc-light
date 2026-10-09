import { Service } from '@angular/core';
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {j4care} from "../../helpers/j4care.service";
import {AppService} from "../../app.service";

@Service()
export class DnsLookupService {
    constructor(
        private $http:J4careHttpService,
        private appService:AppService
    ) { }
    dnsLookup(host: string) {
        return this.$http.get(`${j4care.addLastSlash(this.appService.baseUrl)}host/${host.trim()}`)
    }
}
