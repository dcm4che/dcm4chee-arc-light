import { CustomDatePipe } from './custom-date.pipe';

describe('CustomDatePipe', () => {
    it('create an instance', () => {
        const pipe = new CustomDatePipe();
        expect(pipe).toBeTruthy();
    });
    it("Should format the date (or) time based on the UI Config date time format",()=>{
        const pipe = new CustomDatePipe();
        //new Date("2022-04-11T16:12:52.123")
        expect(pipe.transform("19950405",{
            dateFormat:"yyyy-MM-dd",
            timeFormat:"HH:mm",
            dateTimeFormat:"yyyy-MM-dd HH:mm"
        })).toBe("1995-04-05");

        expect(pipe.transform("095238",{
            dateFormat:"yyyy-MM-dd",
            timeFormat:"HH:mm",
            dateTimeFormat:"yyyy-MM-dd HH:mm"
        })).toBe("09:52");
        expect(pipe.transform("19950405095237",{
            dateFormat:"yyyy-MM-dd",
            timeFormat:"HH:mm",
            dateTimeFormat:"yyyy-MM-dd HH:mm"
        })).toBe("1995-04-05 09:52");

        expect(pipe.transform("095237.0",{
            dateFormat:"yyyy-MM-dd",
            timeFormat:"HH:mm:ss",
            dateTimeFormat:"yyyy-MM-dd HH:mm"
        })).toBe("09:52:37");

        expect(pipe.transform("20220309130523",{
            dateFormat:"yyyy-MM-dd",
            timeFormat:"HH:mm:ss",
            dateTimeFormat:"yyyy-MM-dd HH:mm"
        })).toBe("2022-03-09 13:05");

        expect(pipe.transform("20220309050602.308",{
            dateFormat:"yyyy-MM-dd",
            timeFormat:"HH:mm:ss",
            dateTimeFormat:"yyyy-MM-dd HH:mm"
        })).toBe("2022-03-09 05:06");
    })
});
