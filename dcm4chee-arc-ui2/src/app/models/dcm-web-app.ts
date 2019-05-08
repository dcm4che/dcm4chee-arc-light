import {DicomNetworkConnection} from "../interfaces";

export type WebServiceClass = "QIDO_RS" | "STOW_RS" | "WADO_RS" | "WADO_URI" | "UPS_RS" | "DCM4CHEE_ARC";

export interface DcmWebApp{
    dcmWebAppName:string;
    dicomNetworkConnectionReference:(any[]);
    dicomDescription:string;
    dcmWebServicePath:string;
    dcmWebServiceClass:WebServiceClass;
    dicomAETitle:string;
    dicomApplicationCluster:any[]
    dicomInstalled:boolean;
}