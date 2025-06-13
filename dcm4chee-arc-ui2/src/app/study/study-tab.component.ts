import { Component, OnInit } from '@angular/core';
import {PermissionDirective} from '../helpers/permissions/permission.directive';
import {RouterLink, RouterLinkActive} from '@angular/router';
import {CommonModule} from '@angular/common';

@Component({
    selector: 'study-tab',
    template: `
        <ul class="nav nav-tabs" role="tablist">
            <li [permission]="{id:'tab-study-patient',param:'visible'}" role="presentation" routerLinkActive="active"><a role="tab"
                                                                                                                         routerLink="/study/patient"
                                                                                                                         routerLinkActive="active"
                                                                                                                         i18n="@@patients">Patients</a>
            </li>
            <li [permission]="{id:'tab-study-study',param:'visible'}" role="presentation" routerLinkActive="active"><a role="tab"
                                                                                                                       routerLink="/study/study"
                                                                                                                       routerLinkActive="active"
                                                                                                                       i18n="@@studies">Studies</a>
            </li>
            <li [permission]="{id:'tab-study-series',param:'visible'}" role="presentation" routerLinkActive="active"><a role="tab"
                                                                                                                        routerLink="/study/series"
                                                                                                                        routerLinkActive="active"
                                                                                                                        i18n="@@series">Series</a>
            </li>
            <li [permission]="{id:'tab-study-mwl',param:'visible'}" role="presentation" routerLinkActive="active"><a role="tab"
                                                                                                                     routerLink="/study/mwl"
                                                                                                                     routerLinkActive="active"
                                                                                                                     i18n="@@mwl">MWL</a>
            </li>
            <li [permission]="{id:'tab-study-mpps',param:'visible'}" role="presentation" routerLinkActive="active"><a role="tab"
                                                                                                                      routerLink="/study/mpps"
                                                                                                                      routerLinkActive="active"
                                                                                                                      i18n="@@mpps">MPPS</a>
            </li>
            <li [permission]="{id:'tab-study-uwl',param:'visible'}" role="presentation" routerLinkActive="active"><a role="tab"
                                                                                                                     routerLink="/study/uwl"
                                                                                                                     routerLinkActive="active"
                                                                                                                     i18n="@@work_items">Work
                Items</a></li>
            <li [permission]="{id:'tab-study-diff',param:'visible'}" role="presentation" routerLinkActive="active"><a role="tab"
                                                                                                                      routerLink="/study/diff"
                                                                                                                      routerLinkActive="active"
                                                                                                                      i18n="@@navigation.tab.diffs">Compare</a>
            </li>
        </ul>
    `,
    imports: [
        PermissionDirective,
        RouterLinkActive,
        RouterLink,
        CommonModule
    ],
    standalone: true
})
export class StudyTabComponent{

  constructor() { }
}
