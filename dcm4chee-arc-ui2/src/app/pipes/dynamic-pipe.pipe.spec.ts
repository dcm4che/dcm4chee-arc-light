import { DynamicPipePipe } from './dynamic-pipe.pipe';
import {Injector} from "@angular/core";
import {DynamicPipe} from "../helpers/dicom-studies-table/dicom-studies-table.interfaces";

const attr = {
    "00080016": {
        "vr": "UI",
        "Value": [
            "1.2.840.10008.5.1.4.1.1.2"
        ]
    },
    "00080018": {
        "vr": "UI",
        "Value": [
            "1.2.840.113704.7.7.1.1762134868.1376.807379226.78"
        ]
    },
    "00080054": {
        "vr": "AE",
        "Value": [
            "DCM4CHEE"
        ]
    },
    "00080056": {
        "vr": "CS",
        "Value": [
            "ONLINE"
        ]
    },
    "00081190": {
        "vr": "UR",
        "Value": [
            "http://shefki-lifebook:8080/dcm4chee-arc/aets/DCM4CHEE/rs/studies/2.16.376.1.1.511752826.1.2.21313.5230164/series/2.16.376.1.1.511752826.1.2.21459.3526228/instances/1.2.840.113704.7.7.1.1762134868.1376.807379226.78"
        ]
    },
    "0020000D": {
        "vr": "UI",
        "Value": [
            "2.16.376.1.1.511752826.1.2.21313.5230164"
        ]
    },
    "0020000E": {
        "vr": "UI",
        "Value": [
            "2.16.376.1.1.511752826.1.2.21459.3526228"
        ]
    },
    "00200013": {
        "vr": "IS",
        "Value": [
            0
        ]
    },
    "00280010": {
        "vr": "US",
        "Value": [
            340
        ]
    },
    "00280011": {
        "vr": "US",
        "Value": [
            340
        ]
    },
    "00280100": {
        "vr": "US",
        "Value": [
            16
        ]
    }
};

describe('DynamicPipePipe', () => {
  it('create an instance', () => {
    let injector:Injector;
    const pipe = new DynamicPipePipe(injector);
    expect(pipe).toBeTruthy();

    // expect(pipe.transform(attr, new DynamicPipe("ContentDescriptionPipe",undefined))).toBe("");
  });
});
