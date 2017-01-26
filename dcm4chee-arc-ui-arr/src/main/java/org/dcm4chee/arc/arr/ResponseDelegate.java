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
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
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

package org.dcm4chee.arc.arr;

import javax.ws.rs.core.*;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.util.*;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jan 2017
 */

public class ResponseDelegate extends Response {
    private final Response response;

    public ResponseDelegate(Response response) {
        this.response = response;
    }

    @Override
    public int getStatus() {
        return response.getStatus();
    }

    @Override
    public StatusType getStatusInfo() {
        return response.getStatusInfo();
    }

    @Override
    public Object getEntity() {
        return response.readEntity(InputStream.class);
    }

    @Override
    public <T> T readEntity(Class<T> aClass) {
        return response.readEntity(aClass);
    }

    @Override
    public <T> T readEntity(GenericType<T> genericType) {
        return response.readEntity(genericType);
    }

    @Override
    public <T> T readEntity(Class<T> aClass, Annotation[] annotations) {
        return response.readEntity(aClass, annotations);
    }

    @Override
    public <T> T readEntity(GenericType<T> genericType, Annotation[] annotations) {
        return response.readEntity(genericType, annotations);
    }

    @Override
    public boolean hasEntity() {
        return response.hasEntity();
    }

    @Override
    public boolean bufferEntity() {
        return response.bufferEntity();
    }

    @Override
    public void close() {
        response.close();
    }

    @Override
    public MediaType getMediaType() {
        return response.getMediaType();
    }

    @Override
    public Locale getLanguage() {
        return response.getLanguage();
    }

    @Override
    public int getLength() {
        return response.getLength();
    }

    @Override
    public Set<String> getAllowedMethods() {
        return response.getAllowedMethods();
    }

    @Override
    public Map<String, NewCookie> getCookies() {
        return response.getCookies();
    }

    @Override
    public EntityTag getEntityTag() {
        return response.getEntityTag();
    }

    @Override
    public Date getDate() {
        return response.getDate();
    }

    @Override
    public Date getLastModified() {
        return response.getLastModified();
    }

    @Override
    public URI getLocation() {
        return response.getLocation();
    }

    @Override
    public Set<Link> getLinks() {
        return response.getLinks();
    }

    @Override
    public boolean hasLink(String s) {
        return response.hasLink(s);
    }

    @Override
    public Link getLink(String s) {
        return response.getLink(s);
    }

    @Override
    public Link.Builder getLinkBuilder(String s) {
        return response.getLinkBuilder(s);
    }

    @Override
    public MultivaluedMap<String, Object> getMetadata() {
        return response.getMetadata();
    }

    @Override
    public MultivaluedMap<String, Object> getHeaders() {
        return response.getHeaders();
    }

    @Override
    public MultivaluedMap<String, String> getStringHeaders() {
        return response.getStringHeaders();
    }

    @Override
    public String getHeaderString(String s) {
        return response.getHeaderString(s);
    }

    public static ResponseBuilder fromResponse(Response response) {
        return Response.fromResponse(response);
    }

    public static ResponseBuilder status(StatusType status) {
        return Response.status(status);
    }

    public static ResponseBuilder status(Status status) {
        return Response.status(status);
    }

    public static ResponseBuilder status(int status) {
        return Response.status(status);
    }

    public static ResponseBuilder ok() {
        return Response.ok();
    }

    public static ResponseBuilder ok(Object entity) {
        return Response.ok(entity);
    }

    public static ResponseBuilder ok(Object entity, MediaType type) {
        return Response.ok(entity, type);
    }

    public static ResponseBuilder ok(Object entity, String type) {
        return Response.ok(entity, type);
    }

    public static ResponseBuilder ok(Object entity, Variant variant) {
        return Response.ok(entity, variant);
    }

    public static ResponseBuilder serverError() {
        return Response.serverError();
    }

    public static ResponseBuilder created(URI location) {
        return Response.created(location);
    }

    public static ResponseBuilder accepted() {
        return Response.accepted();
    }

    public static ResponseBuilder accepted(Object entity) {
        return Response.accepted(entity);
    }

    public static ResponseBuilder noContent() {
        return Response.noContent();
    }

    public static ResponseBuilder notModified() {
        return Response.notModified();
    }

    public static ResponseBuilder notModified(EntityTag tag) {
        return Response.notModified(tag);
    }

    public static ResponseBuilder notModified(String tag) {
        return Response.notModified(tag);
    }

    public static ResponseBuilder seeOther(URI location) {
        return Response.seeOther(location);
    }

    public static ResponseBuilder temporaryRedirect(URI location) {
        return Response.temporaryRedirect(location);
    }

    public static ResponseBuilder notAcceptable(List<Variant> variants) {
        return Response.notAcceptable(variants);
    }
}
