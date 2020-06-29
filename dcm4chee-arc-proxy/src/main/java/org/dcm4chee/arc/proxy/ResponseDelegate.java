/*
 * ** BEGIN LICENSE BLOCK *****
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
 * Portions created by the Initial Developer are Copyright (C) 2016-2020
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
 * ** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.proxy;

import javax.ws.rs.core.*;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.util.*;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jan 2017
 */

public class ResponseDelegate extends Response {
    private final Response delegate;

    public ResponseDelegate(Response delegate) {
        this.delegate = delegate;
    }

    @Override
    public int getStatus() {
        return delegate.getStatus();
    }

    @Override
    public StatusType getStatusInfo() {
        return delegate.getStatusInfo();
    }

    @Override
    public Object getEntity() {
        return delegate.readEntity(InputStream.class);
    }

    @Override
    public <T> T readEntity(Class<T> aClass) {
        return delegate.readEntity(aClass);
    }

    @Override
    public <T> T readEntity(GenericType<T> genericType) {
        return delegate.readEntity(genericType);
    }

    @Override
    public <T> T readEntity(Class<T> aClass, Annotation[] annotations) {
        return delegate.readEntity(aClass, annotations);
    }

    @Override
    public <T> T readEntity(GenericType<T> genericType, Annotation[] annotations) {
        return delegate.readEntity(genericType, annotations);
    }

    @Override
    public boolean hasEntity() {
        return delegate.hasEntity();
    }

    @Override
    public boolean bufferEntity() {
        return delegate.bufferEntity();
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public MediaType getMediaType() {
        return delegate.getMediaType();
    }

    @Override
    public Locale getLanguage() {
        return delegate.getLanguage();
    }

    @Override
    public int getLength() {
        return delegate.getLength();
    }

    @Override
    public Set<String> getAllowedMethods() {
        return delegate.getAllowedMethods();
    }

    @Override
    public Map<String, NewCookie> getCookies() {
        return delegate.getCookies();
    }

    @Override
    public EntityTag getEntityTag() {
        return delegate.getEntityTag();
    }

    @Override
    public Date getDate() {
        return delegate.getDate();
    }

    @Override
    public Date getLastModified() {
        return delegate.getLastModified();
    }

    @Override
    public URI getLocation() {
        return delegate.getLocation();
    }

    @Override
    public Set<Link> getLinks() {
        return delegate.getLinks();
    }

    @Override
    public boolean hasLink(String s) {
        return delegate.hasLink(s);
    }

    @Override
    public Link getLink(String s) {
        return delegate.getLink(s);
    }

    @Override
    public Link.Builder getLinkBuilder(String s) {
        return delegate.getLinkBuilder(s);
    }

    @Override
    public MultivaluedMap<String, Object> getMetadata() {
        return delegate.getMetadata();
    }

    @Override
    public MultivaluedMap<String, Object> getHeaders() {
        return delegate.getHeaders();
    }

    @Override
    public MultivaluedMap<String, String> getStringHeaders() {
        return delegate.getStringHeaders();
    }

    @Override
    public String getHeaderString(String s) {
        return delegate.getHeaderString(s);
    }
}
