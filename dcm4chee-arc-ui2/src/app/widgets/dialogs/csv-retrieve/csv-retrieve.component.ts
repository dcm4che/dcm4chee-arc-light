import {Component, Input, OnInit} from '@angular/core';
import {FormBuilder, FormGroup, Validators} from "@angular/forms";
import {MatDialogRef} from "@angular/material";
import {CsvRetrieveService} from "./csv-retrieve.service";

@Component({
  selector: 'csv-retrieve',
  templateUrl: './csv-retrieve.component.html',
  styleUrls: ['./csv-retrieve.component.scss']
})
export class CsvRetrieveComponent implements OnInit {

  form: FormGroup;
  csvFile:File;
  @Input() aes;
   constructor(
      public dialogRef: MatDialogRef<CsvRetrieveComponent>,
      private _fb: FormBuilder,
      private service:CsvRetrieveService
  ) { }

  ngOnInit() {
    this.form = this._fb.group({
        aet:['', Validators.required],
        externalAET:['', Validators.required],
        field:[1,  Validators.minLength(1)],
        destinationAET:['', Validators.required],
        priority:NaN,
        batchID:''
    });
      console.log("aes",this.aes);
  }
    submit(){
        console.log("form",this.form.value);
        console.log("this.scvFile",this.csvFile);
        this.service.uploadCSV(this.form.value, this.csvFile);
    }
    onFileChange(e){
        console.log("e",e.target.files[0]);
        this.csvFile = e.target.files[0];
    }
}
