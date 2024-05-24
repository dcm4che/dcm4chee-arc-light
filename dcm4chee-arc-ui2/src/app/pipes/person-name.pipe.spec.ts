/* tslint:disable:no-unused-variable */

import { PersonNamePipe } from './person-name.pipe';

describe('Pipe: PersonName', () => {
  it('create an instance', () => {
    let pipe = new PersonNamePipe();
    expect(pipe).toBeTruthy();
  });
  it("Should format person name based on UI Config person format",()=>{
    const pipe = new PersonNamePipe();
    //{FAMILY-NAME}^{GIVEN-NAME}^{MIDDLE-NAME}^{NAME-PREFIX}^{NAME-SUFFIX}
    expect(pipe.transform({
        Alphabetic: "COTTA^ANNA"
    },"{NAME-PREFIX} {GIVEN-NAME} {MIDDLE-NAME} {FAMILY-NAME} {NAME-SUFFIX} ( {P_FAMILY-NAME} {I_FAMILY-NAME} )"))
        .toBe("ANNA COTTA");
    expect(pipe.transform({
        Alphabetic: "Buc^Jérôme"
    },"{NAME-PREFIX} {GIVEN-NAME} {MIDDLE-NAME} {FAMILY-NAME} {NAME-SUFFIX} ( {P_FAMILY-NAME} {I_FAMILY-NAME} )"))
        .toBe("Jérôme Buc");

    expect(pipe.transform({
        Alphabetic: "قباني^لنزا"
    },"{NAME-PREFIX} {GIVEN-NAME} {MIDDLE-NAME} {FAMILY-NAME} {NAME-SUFFIX} ( {P_FAMILY-NAME} {I_FAMILY-NAME} )"))
        .toBe("لنزا قباني");


    expect(pipe.transform({
          Alphabetic: "Wang^XiaoDong",
          Ideographic: "王^小东"
    },"{NAME-PREFIX} {GIVEN-NAME} {MIDDLE-NAME} {FAMILY-NAME} {NAME-SUFFIX} ( {P_FAMILY-NAME} {I_FAMILY-NAME} )"))
        .toBe("XiaoDong Wang ( 王 )");


    expect(pipe.transform({
          Alphabetic: "Hong^Gildong",
          Ideographic: "洪^吉洞",
          Phonetic: "홍^길동"
    },"{NAME-PREFIX} {GIVEN-NAME} {MIDDLE-NAME} {FAMILY-NAME} {NAME-SUFFIX} ( {P_FAMILY-NAME} {I_FAMILY-NAME} )"))
        .toBe("Gildong Hong ( 홍 洪 )");

    expect(pipe.transform({
          Alphabetic: "SB1^SB2^SB3^SB4^SB5",
          Ideographic: "ID1^ID2^ID3^ID4^ID5" ,
          Phonetic: "PH1^PH2^PH3^PH4^PH5"
    },"{NAME-PREFIX} {GIVEN-NAME} {MIDDLE-NAME} {FAMILY-NAME} {NAME-SUFFIX} ( {P_FAMILY-NAME} {I_FAMILY-NAME} )"))
        .toBe("SB4 SB2 SB3 SB1 SB5 ( PH1 ID1 )");

    expect(pipe.transform({
          Alphabetic: "Esadi^Shefki^Xhevair^Dr.^PhD",
    },"{NAME-PREFIX} {GIVEN-NAME} {MIDDLE-NAME} {FAMILY-NAME} {NAME-SUFFIX}"))
        .toBe("Dr. Shefki Xhevair Esadi PhD");

    expect(pipe.transform("Esadi^Shefki^Xhevair^Dr.^PhD","{NAME-PREFIX} {GIVEN-NAME} {MIDDLE-NAME} {FAMILY-NAME} {NAME-SUFFIX}"))
        .toBe("Dr. Shefki Xhevair Esadi PhD");

    expect(pipe.transform("=Esadi^Shefki^Xhevair^Dr.^PhD","{NAME-PREFIX} {GIVEN-NAME} {MIDDLE-NAME} {FAMILY-NAME} {NAME-SUFFIX}"))
        .toBe("Dr. Shefki Xhevair Esadi PhD");

    expect(pipe.transform("Esadi^Shefki^Xhevair^Dr.^PhD",))
        .toBe("Dr. Shefki Xhevair Esadi, PhD");

    expect(pipe.transform("=SB1^SB2^SB3^SB4^SB5","{NAME-PREFIX} {GIVEN-NAME} {MIDDLE-NAME} {FAMILY-NAME} {NAME-SUFFIX} ( {P_FAMILY-NAME} {I_FAMILY-NAME} )"))
        .toBe("( SB1 )");
    expect(pipe.transform("==SB1^SB2^SB3^SB4^SB5","{NAME-PREFIX} {GIVEN-NAME} {MIDDLE-NAME} {FAMILY-NAME} {NAME-SUFFIX} ( {P_FAMILY-NAME} {I_FAMILY-NAME} )"))
        .toBe("( SB1 )");
  });
});
