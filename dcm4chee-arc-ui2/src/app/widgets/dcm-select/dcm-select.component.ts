import {Component, Input, OnInit, TemplateRef, ContentChild} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {CommonModule, NgTemplateOutlet} from '@angular/common';
import {ClickOutsideDirective} from '../../helpers/click-outside.directive';
import {SearchPipe} from '../../pipes/search.pipe';

@Component({
    selector: 'dcm-select',
    templateUrl: './dcm-select.component.html',
    styleUrls: ['./dcm-select.component.scss'],
    imports: [
        FormsModule,
        NgTemplateOutlet,
        ClickOutsideDirective,
        CommonModule,
        SearchPipe
    ],
    standalone: true
})
export class DcmSelectComponent implements OnInit {
    @ContentChild(TemplateRef, {static: true}) templ;
  @Input() options;
  @Input() placeholder;
  selectOpen = false;
  search = '';
  selectedElement;
  constructor() { }

  ngOnInit() {
  }
}
