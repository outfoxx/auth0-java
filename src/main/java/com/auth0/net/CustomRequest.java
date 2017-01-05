package com.auth0.net;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import okhttp3.*;
import okhttp3.Request;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class CustomRequest<T> extends BaseRequest<T> implements CustomizableRequest<T> {
    private static final MediaType JSON = MediaType.parse("application/json");

    private final String url;
    private final String method;
    private final Class<T> tClazz;
    private final ObjectMapper mapper;
    private Map<String, String> headers;
    private Map<String, Object> parameters;

    public CustomRequest(OkHttpClient client, String url, String method, Class<T> tClazz) {
        super(client);
        this.url = url;
        this.method = method;
        this.tClazz = tClazz;
        this.mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.headers = new HashMap<>();
        this.parameters = new HashMap<>();
    }

    @Override
    protected Request createRequest() throws RequestFailedException {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .method(method, createBody());
        for (Map.Entry<String, String> e : headers.entrySet()) {
            builder.addHeader(e.getKey(), e.getValue());
        }
        builder.addHeader("Content-Type", "application/json");
        return builder.build();
    }

    @Override
    protected T parseResponse(Response response) throws RequestFailedException {
        if (!response.isSuccessful()) {
            parseResponseError(response);
            return null;
        }

        ResponseBody body = response.body();
        String payload = null;
        try {
            payload = body.string();
            return mapper.readValue(payload, tClazz);
        } catch (JsonParseException e) {
            //Parse failed, maybe it's not a JSON.
            throw new RequestFailedException(payload, e);
        } catch (IOException e) {
            throw new RequestFailedException("Failed to convert", e);
        } finally {
            body.close();
        }
    }


    @Override
    public void addHeader(String name, String value) {
        headers.put(name, value);
    }

    @Override
    public void addParameter(String name, Object value) {
        parameters.put(name, value);
    }

    private RequestBody createBody() throws RequestFailedException {
        if (parameters.isEmpty()) {
            return null;
        }
        try {
            byte[] jsonBody = mapper.writeValueAsBytes(parameters);
            return RequestBody.create(JSON, jsonBody);
        } catch (JsonProcessingException e) {
            throw new RequestFailedException("Couldn't create the request body.", e);
        }
    }

    public void parseResponseError(Response response) throws RequestFailedException {
        ResponseBody body = response.body();
        InputStream byteStream = body.byteStream();
        try {
            MapType mapType = mapper.getTypeFactory().constructMapType(HashMap.class, String.class, String.class);
            Map<String, Object> payload = mapper.readValue(byteStream, mapType);
            //create exception with payload
        } catch (IOException e) {
            //create exception without payload
            throw new RequestFailedException("Request failed", e);
        } finally {
            body.close();
        }
        //throw
        throw new RequestFailedException("Unknown error");
    }
}
