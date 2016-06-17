package org.dcm4chee.arc.audit;

import org.dcm4che3.util.StringUtils;

/**
 * Created by vrinda on 17.06.2016.
 */

class AuditInfo {
    static final int CALLING_HOST = 0;
    static final int CALLING_AET = 1;
    static final int CALLED_AET = 2;
    static final int CALLED_HOST = 3;
    static final int STUDY_UID = 4;
    static final int ACC_NUM = 5;
    static final int P_ID = 6;
    static final int P_NAME = 7;
    static final int OUTCOME = 8;
    static final int STUDY_DATE = 9;
    static final int Q_POID = 10;
    static final int Q_STRING = 11;

    private final String[] fields;

    AuditInfo(BuildAuditInfo i) {
        fields = new String[] {
                i.callingHost,
                i.callingAET,
                i.calledAET,
                i.calledHost,
                i.studyUID,
                i.accNum,
                i.pID,
                i.pName,
                i.outcome,
                i.studyDate,
                i.queryPOID,
                i.queryString
        };
    }

    AuditInfo(String s) {
        fields = StringUtils.split(s, '\\');
    }
    String getField(int field) {
        return StringUtils.maskEmpty(fields[field], null);
    }
    @Override
    public String toString() {
        return StringUtils.concat(fields, '\\');
    }
}
