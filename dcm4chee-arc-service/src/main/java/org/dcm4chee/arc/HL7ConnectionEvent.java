/*
 * **** BEGIN LICENSE BLOCK *****
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
 * Portions created by the Initial Developer are Copyright (C) 2015-2018
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
 * **** END LICENSE BLOCK *****
 *
 */

package org.dcm4chee.arc;

import org.dcm4che3.hl7.HL7Exception;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.UnparsedHL7Message;

import java.net.Socket;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2018
 */
public class HL7ConnectionEvent {
    public enum Type {
        MESSAGE_RECEIVED, MESSAGE_PROCESSED, MESSAGE_SENT, MESSAGE_RESPONSE
    }

    private final Type type;
    private final HL7Application hl7Application;
    private final Connection connection;
    private final Socket socket;
    private final UnparsedHL7Message hl7Message;
    private final UnparsedHL7Message hl7ResponseMessage;
    private final Exception exception;

    private HL7ConnectionEvent(HL7ConnectionEvent.Type type,
                               Connection connection, HL7Application hl7Application, Socket socket,
                               UnparsedHL7Message hl7Message, UnparsedHL7Message hl7ResponseMessage,
                               Exception exception) {
        this.type = type;
        this.hl7Application = hl7Application;
        this.connection = connection;
        this.socket = socket;
        this.hl7Message = hl7Message;
        this.hl7ResponseMessage = hl7ResponseMessage;
        this.exception = exception;
    }

    public static HL7ConnectionEvent onMessageReceived(Connection conn, Socket s, UnparsedHL7Message msg) {
        return new HL7ConnectionEvent(Type.MESSAGE_RECEIVED, conn, null, s, msg, null, null);
    }

    public static HL7ConnectionEvent onMessageProcessed(Connection conn, Socket s, UnparsedHL7Message msg,
                                                        UnparsedHL7Message rsp, HL7Exception ex) {
        return new HL7ConnectionEvent(Type.MESSAGE_PROCESSED, conn, null, s, msg, rsp, ex);
    }

    public static HL7ConnectionEvent onMessageSent(HL7Application hl7App, Socket s, UnparsedHL7Message msg, Exception ex) {
        return new HL7ConnectionEvent(Type.MESSAGE_SENT, null, hl7App, s, msg, null, ex);
    }

    public static HL7ConnectionEvent onMessageResponse(HL7Application hl7App, Socket s, UnparsedHL7Message msg,
                                                       UnparsedHL7Message rsp, Exception ex) {
        return new HL7ConnectionEvent(Type.MESSAGE_RESPONSE, null, hl7App, s, msg, rsp, ex);
    }

    public Type getType() {
        return type;
    }

    public HL7Application getHL7Application() {
        return hl7Application;
    }

    public Connection getConnection() {
        return connection;
    }

    public Socket getSocket() {
        return socket;
    }

    public UnparsedHL7Message getHL7Message() {
        return hl7Message;
    }

    public UnparsedHL7Message getHL7ResponseMessage() {
        return hl7ResponseMessage;
    }

    public Exception getException() {
        return exception;
    }

    @Override
    public String toString() {
        return "HL7ConnectionEvent{" +
                "type=" + type +
                ", hl7Application=" + hl7Application +
                ", connection=" + connection +
                ", socket=" + socket +
                ", hl7Message=" + hl7Message +
                ", hl7ResponseMessage=" + hl7ResponseMessage +
                ", exception=" + exception +
                '}';
    }
}
