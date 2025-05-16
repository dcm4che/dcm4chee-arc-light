import {Component, Input, OnInit, TemplateRef, ContentChild} from '@angular/core';

@Component({
    selector: 'dcm-select',
    templateUrl: './dcm-select.component.html',
    styleUrls: ['./dcm-select.component.scss'],
    standalone: false
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
