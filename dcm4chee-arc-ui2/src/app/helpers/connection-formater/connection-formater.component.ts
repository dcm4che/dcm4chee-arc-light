import { Component, OnInit } from '@angular/core';
import {Input} from "@angular/core/src/metadata/directives";
import * as _ from "lodash";

@Component({
  selector: 'connection-formater',
  template: `
        <div *ngFor="let connblock of dicomNetworkConnection">
            <span>
                {{connblock.dicomHostname}}
                    <ng-container *ngIf="connblock.dicomPort">:</ng-container>
                {{connblock.dicomPort}}
            </span>
            <i *ngIf="connblock.dicomTLSCipherSuite" title="{{connblock.dicomTLSCipherSuite}}" class="material-icons connection_tls" >vpn_key</i>
        </div>
    `
})
export class ConnectionFormaterComponent implements OnInit {
    @Input() dicomNetworkConnection;

    constructor() { }
    ngOnInit() {
        console.log("this.dicomNetworkConnection",this.dicomNetworkConnection);
       _.forEach(this.dicomNetworkConnection, (m, i)=>{
           if(m.dicomTLSCipherSuite){
               if(m.dicomTLSCipherSuite[0] && m.dicomTLSCipherSuite[1]){
                   m.dicomTLSCipherSuite = m.dicomTLSCipherSuite[0] +'\n'+ m.dicomTLSCipherSuite[1];
               }
           }
       });
        console.log("this.dicomNetworkConnection2",this.dicomNetworkConnection);
    }

}
