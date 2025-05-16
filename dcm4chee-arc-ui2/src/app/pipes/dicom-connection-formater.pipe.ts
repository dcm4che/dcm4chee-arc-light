import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
    name: 'dicomConnectionFormater',
    standalone: false
})
export class DicomConnectionFormaterPipe implements PipeTransform {

  transform(value: any, args?: any): any {
      if (value[0]){
          let stringConn = value.map(val => {
              let concat = val.dicomHostname + ':' + val.dicomPort;
              if (val.dicomTLSCipherSuite){
                  concat = concat + '<i class="material-icons connection_tls" title="' + val.dicomTLSCipherSuite.join(',\n') + '">vpn_key</i>';
              }
              return concat;
          });
          return stringConn.join('<br>');
      }else{
          return null;
      }
  }

}
