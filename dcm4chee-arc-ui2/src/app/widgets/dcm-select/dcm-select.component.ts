import {Component, Input, OnInit, TemplateRef, ContentChild} from '@angular/core';

@Component({
  selector: 'dcm-select',
  templateUrl: './dcm-select.component.html',
  styleUrls: ['./dcm-select.component.scss']
})
export class DcmSelectComponent implements OnInit {
    @ContentChild(TemplateRef) templ;
  @Input() options;
  @Input() placeholder;
  selectOpen = false;
  search = '';
  selectedElement;
  constructor() { }

  ngOnInit() {
  }
}
