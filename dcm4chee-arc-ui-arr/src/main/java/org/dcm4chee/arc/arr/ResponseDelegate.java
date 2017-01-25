package org.dcm4chee.arc.arr;

import javax.ws.rs.core.*;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.util.*;

/**
 * Created by vrinda on 25.01.2017.
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
