import { Component, OnInit } from '@angular/core';
//import {MatLegacyDialogRef as MatDialogRef} from "@angular/material/legacy-dialog";
import {CreateAeComponent} from "../create-ae/create-ae.component";
import {MatDialogRef} from "@angular/material/dialog";

@Component({
  selector: 'app-create-webapp',
  templateUrl: './create-webapp.component.html',
  styleUrls: ['./create-webapp.component.scss']
})
export class CreateWebappComponent implements OnInit {

  constructor(
      public dialogRef: MatDialogRef<CreateWebappComponent>,
  ) { }

  ngOnInit(): void {
  }

}
