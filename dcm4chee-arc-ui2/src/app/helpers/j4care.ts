export interface J4careDateTime {
    FullYear:string;
    Month:string;
    Date:string;
    Hours:string;
    Minutes:string;
    Seconds:string;
    dateObject?:Date;
}
export type J4careDateTimeMode = "range" | "leftOpen" | "rightOpen" | "single";

export interface RangeObject {
    firstDateTime:J4careDateTime;
    secondDateTime:J4careDateTime;
    mode:J4careDateTimeMode;
}