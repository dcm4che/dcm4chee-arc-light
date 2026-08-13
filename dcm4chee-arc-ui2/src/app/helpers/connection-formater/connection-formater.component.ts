import { Component, OnInit } from '@angular/core';
import {Input} from '@angular/core';
import * as _ from 'lodash-es';

@Component({
    selector: 'connection-formater',
    template: `
        @for (connblock of dicomNetworkConnection; track connblock) {
            <div>
            <span>
                {{ connblock.dicomHostname }}
                @if (connblock.dicomPort) {
                    :
                }
                {{ connblock.dicomPort }}
            </span>
                @if (connblock.dicomTLSCipherSuite) {
                    <i title="{{connblock.dicomTLSCipherSuite}}" class="material-icons connection_tls" i18n="@@vpn_key">vpn_key</i>
                }
            </div>
        }
    `,
    standalone: true
})
export class ConnectionFormaterComponent implements OnInit {
    @Input() dicomNetworkConnection;

    constructor() { }
    ngOnInit() {
       _.forEach(this.dicomNetworkConnection, (m, i) => {
           if (m.dicomTLSCipherSuite) {
               if (m.dicomTLSCipherSuite[0] && m.dicomTLSCipherSuite[1]) {
                   m.dicomTLSCipherSuite = m.dicomTLSCipherSuite[0] + '\n' + m.dicomTLSCipherSuite[1];
               }
           }
       });
    }

}
