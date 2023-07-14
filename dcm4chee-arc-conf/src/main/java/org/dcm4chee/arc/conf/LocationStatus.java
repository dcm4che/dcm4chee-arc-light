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
 * Portions created by the Initial Developer are Copyright (C) 2013-2021
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
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Jul 2023
 */
public enum LocationStatus {
    OK,                                     // 0
    TO_DELETE,                              // 1
    FAILED_TO_DELETE,                       // 2
    MISSING_OBJECT,                         // 3
    FAILED_TO_FETCH_METADATA,               // 4
    FAILED_TO_FETCH_OBJECT,                 // 5
    DIFFERING_OBJECT_SIZE,                  // 6
    DIFFERING_OBJECT_CHECKSUM,              // 7
    DIFFERING_S3_MD5SUM,                    // 8
    FAILED_TO_DELETE2,                      // 9
    ORPHANED,                               // 10
    VERIFY_QSTAR_ACCESS_STATE,              // 11
    QSTAR_ACCESS_STATE_NONE,                // 12
    QSTAR_ACCESS_STATE_EMPTY,               // 13
    QSTAR_ACCESS_STATE_UNSTABLE,            // 14
    QSTAR_ACCESS_STATE_OUT_OF_CACHE,        // 15
    QSTAR_ACCESS_STATE_OFFLINE,             // 16
    QSTAR_ACCESS_STATE_ERROR_STATUS,        // 17
    UNKNOWN_STORAGE,                        // 18
}
