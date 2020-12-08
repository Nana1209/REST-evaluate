package io.swagger.models;

import java.util.HashMap;
import java.util.Map;

/**
 * 动态检测请求类
 */
public class Request {
    private String url;
    private Map<String,String> header;
    private Map<String ,Object> entity;
    private String method;
    public Request(String method,String url, Map<String, String> header, Map<String, Object> entity){
        this.method=method;
        this.url=url;
        this.header=header;
        this.entity=entity;
    }

    public String getMethod() {
        return method;
    }

    public String getUrl() {
        return url;
    }

    public Map<String, String> getHeader() {
        return header;
    }

    public Map<String, Object> getEntity() {
        return entity;
    }
}
