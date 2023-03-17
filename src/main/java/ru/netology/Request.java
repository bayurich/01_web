package ru.netology;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Request {
    private String method;
    private String path;
    private String query;
    //private List<String> headers = new ArrayList<>();
    private Map<String, String> headers = new HashMap<>();
    private byte[] body;

    public Request(String method, String path, Map<String, String> headers, byte[] body) {
        this.method = method;
        this.body = body;

        String[] pathAndQuery = path.split("\\?", 2);
        this.path = pathAndQuery[0];
        if (pathAndQuery.length > 1) {
            this.query = pathAndQuery[1];
        }

        var queryHeaders = getQueryParams();
        for (NameValuePair nameValuePair : queryHeaders) {
            headers.put(nameValuePair.getName(), nameValuePair.getValue());
        }
        this.headers = headers;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public String getBodyAsString() {
        return (body == null || body.length == 0) ? "" : new String(body);
    }


    private List<NameValuePair> getQueryParams() {
        return URLEncodedUtils.parse(query, StandardCharsets.UTF_8, '&');
    }
}
