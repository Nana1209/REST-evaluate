package io.swagger.models;

import java.util.ArrayList;
import java.util.List;

public class OperationRESTer {
    protected String method;
    protected List<ParameterRESTer> parameters;//请求参数
    protected List<ResponseRESTer> responses;//响应
    OperationRESTer(){
        method="";
        parameters=new ArrayList<>();
        responses=new ArrayList<>();
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public List<ParameterRESTer> getParameters() {
        return parameters;
    }

    public void setParameters(List<ParameterRESTer> parameters) {
        this.parameters = parameters;
    }

    public List<ResponseRESTer> getResponses() {
        return responses;
    }

    public void setResponses(List<ResponseRESTer> responses) {
        this.responses = responses;
    }
}
