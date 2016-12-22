import {Component, OnInit, Input} from '@angular/core';

@Component({
  selector: 'iod-form-generator',
  templateUrl: './iod-form-generator.component.html',
  styles: []
})
export class IodFormGeneratorComponent implements OnInit {

    @Input() object;
    constructor() { }

    ngOnInit() {
    }

}
