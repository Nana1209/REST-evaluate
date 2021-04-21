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
}
