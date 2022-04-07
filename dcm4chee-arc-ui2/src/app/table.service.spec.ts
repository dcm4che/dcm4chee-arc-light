import { TestBed } from '@angular/core/testing';

import { TableService } from './table.service';
import {inject} from "@angular/core";
import {AppService} from "./app.service";
import {TableSchemaElement} from "./models/dicom-table-schema-element";
class TableServiceDependenc{
}

describe('TableService', () => {
  let service: TableService;

  beforeEach(() => {
    TestBed.configureTestingModule({
        providers: [
                TableService,
                {provide:AppService, useClass:TableServiceDependenc}
            ]
  });
    service = TestBed.inject(TableService);
  });

  it('Should get table schema elements', ()=>{
      expect(service.getTableSchema([
          {
              key:"dicomDeviceName"
          },{
              key:"updateTime",
              overwrite:<TableSchemaElement>{
                  widthWeight:2
              }
          }
      ])).toEqual([
          new TableSchemaElement({
              type:"value",
              title:$localize `:@@device_name:Device name`,
              pathToValue:"dicomDeviceName",
              description: $localize `:@@device_name:Device name`,
              widthWeight:1,
              calculatedWidth:"20%"
          }),
          new TableSchemaElement({
              type:"value",
              title:$localize `:@@updated_time:Updated time`,
              pathToValue:"updatedTime",
              description:$localize `:@@updated_time:Updated time`,
              widthWeight:2,
              calculatedWidth:"20%"
          })
      ]);
      expect(service.getTableSchema([
          {
              key:"StorageID",
              overwrite:<TableSchemaElement>{
                  pathToValue:"status.Value.0"
              }
          },{
              key:"batchID",
              overwrite:<TableSchemaElement>{
                  widthWeight:2
              }
          }
      ])).toEqual([
          new TableSchemaElement({
              type:"value",
              title: $localize`:@@storage_id:Storage ID`,
              pathToValue:"status.Value.0",
              description: $localize`:@@storage_id:Storage ID`,
              widthWeight:1,
              calculatedWidth:"20%"
          }),
          new TableSchemaElement({
              type:"value",
              title:$localize `:@@batch_id:Batch ID`,
              pathToValue:"batchID",
              description: $localize `:@@batch_id:Batch ID`,
              widthWeight:2,
              calculatedWidth:"20%",
              cssClass:"hideOn800px"
          })
      ]);
  });
});
