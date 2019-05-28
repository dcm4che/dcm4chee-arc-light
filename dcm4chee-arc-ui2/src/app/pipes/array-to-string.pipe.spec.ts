import { ArrayToStringPipe } from './array-to-string.pipe';

describe('ArrayToStringPipe', () => {
    const array1 = [
        "test1",
        "test2"
    ];
    const array2 = [
        {
            test:"selam",
            test2:"test2",
        },
        {
            test:"selam2",
            test2:"test22"
        }
    ];
    it('create an instance', () => {
        const pipe = new ArrayToStringPipe();
        expect(pipe).toBeTruthy();
    });
    it("Should convert array to string",()=>{
        const pipe = new ArrayToStringPipe();
        expect(pipe.transform(array1,", ")).toBe("test1, test2");
        expect(pipe.transform(array2,", ")).toBe("selam, selam2");
        expect(pipe.transform(array2,"|")).toBe("selam|selam2");
    })
});
