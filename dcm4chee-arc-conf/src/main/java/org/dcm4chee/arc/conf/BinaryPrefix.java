/*
 * *** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * J4Care.
 * Portions created by the Initial Developer are Copyright (C) 2013
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.conf;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jan 2016
 */
public enum BinaryPrefix {
    K(1000, 1),
    k(1000, 1),
    M(1000, 2),
    G(1000, 3),
    T(1000, 4),
    P(1000, 5),
    Ki(1024, 1),
    Mi(1024, 2),
    Gi(1024, 3),
    Ti(1024, 4),
    Pi(1024, 5);

    private final int base;
    private final int exponent;

    BinaryPrefix(int base, int exponent) {
        this.base = base;
        this.exponent = exponent;
    }

    public int getBase() {
        return base;
    }

    public int getExponent() {
        return exponent;
    }

    public long size() {
        return size(base, exponent);
    }

    public static long parse(String s) {
        int unitEnd = s.length();
        if (unitEnd > 0 && s.charAt(0) != '-')
            try {
                if (s.charAt(unitEnd-1) == 'B')
                    unitEnd--;

                int unitStart = unitEnd;
                while (unitStart > 0 && !Character.isDigit(s.charAt(unitStart-1)))
                    unitStart--;

                String val = s.substring(0, unitStart);
                long unitSize = unitStart < unitEnd ? valueOf(s.substring(unitStart, unitEnd)).size() : 1L;
                return (s.indexOf('.') >= 0)
                        ? (long) (Double.parseDouble(val) * unitSize)
                        : Long.parseLong(val) * unitSize;
            } catch (IllegalArgumentException e) {
            }
        throw new IllegalArgumentException(s);
    }

    public static String formatBinary(long size) {
        return format(size, Ki);
    }

    public static String formatDecimal(long size) {
        return format(size, k);
    }

    private static long size(int base, int exponent) {
        if (exponent == 0)
            return 1;

        long size = base;
        int i = exponent;
        while (--i > 0)
            size *= base;

        return size;
    }

    private static String format(long size, BinaryPrefix kiloPrefix) {
        if (size < 0)
            throw new IllegalArgumentException("size must be positive");

        StringBuilder sb = new StringBuilder();
        int base = kiloPrefix.base;
        long val = size;
        int exp = 0;
        while (val >= base && exp < 5) {
            val /= base;
            exp++;
        }
        long unitSize = size(base, exp);
        if (val * unitSize == size)
            sb.append(val);
        else
            sb.append(((double) size / unitSize));

        if (exp != 0)
            sb.append(BinaryPrefix.values()[kiloPrefix.ordinal() - 1 + exp]).append('B');

        return sb.toString();
    }
}
