package io.swagger.models;

import java.util.ArrayList;
import java.util.List;

public class ResponseRESTer {
    String status;//响应状态
    List<ParameterRESTer> headers;//响应头文件
    List<String> examples;
    ResponseRESTer(){
        status="";
        headers=new ArrayList<>();
        examples=new ArrayList<>();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<ParameterRESTer> getHeaders() {
        return headers;
    }

    public void setHeaders(List<ParameterRESTer> headers) {
        this.headers = headers;
    }

    public List<String> getExamples() {
        return examples;
    }

    public void setExamples(List<String> examples) {
        this.examples = examples;
    }
}
