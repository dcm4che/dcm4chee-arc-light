import { Component } from '@angular/core';
import {PermissionDirective} from '../helpers/permissions/permission.directive';
import {RouterLink, RouterLinkActive} from '@angular/router';

@Component({
    selector: 'config-tab',
    template: `
        <ul class="nav nav-tabs" role="tablist">
            <li [permission]="{id:'tab-configuration->devices',param:'visible'}" role="presentation" routerLinkActive="active"><a
                    aria-controls="device_modus" routerLink="/device/devicelist" role="tab" routerLinkActive="active" i18n="@@devices">Devices</a>
            </li>
            <li [permission]="{id:'tab-configuration->ae_list',param:'visible'}" role="presentation" routerLinkActive="active"><a
                    aria-controls="aet_modus" routerLink="/device/aelist" role="tab" routerLinkActive="active" i18n="@@ae_list">AE list</a>
            </li>
            <li [permission]="{id:'tab-configuration->web_apps_list',param:'visible'}" role="presentation" routerLinkActive="active"><a
                    aria-controls="aet_modus" routerLink="/device/webappslist" role="tab" routerLinkActive="active" i18n="@@web_apps_list">Web
                Apps list</a></li>
            <li [permission]="{id:'tab-configuration->hl7_applications',param:'visible'}" role="presentation" routerLinkActive="active"><a
                    aria-controls="aet_modus" routerLink="/device/hl7applications" role="tab" routerLinkActive="active"
                    i18n="@@hl7_applications">Hl7 Applications</a></li>
            <li [permission]="{id:'tab-monitoring->control',param:'visible'}" role="presentation" routerLinkActive="active"><a role="tab"
                                                                                                                               routerLink="/monitoring/control"
                                                                                                                               routerLinkActive="active"
                                                                                                                               i18n="@@control">Control</a>
            </li>
        </ul>
    `,
    imports: [
        PermissionDirective,
        RouterLink,
        RouterLinkActive
    ],
    standalone: true
})
export class ConfigTabComponent{}
