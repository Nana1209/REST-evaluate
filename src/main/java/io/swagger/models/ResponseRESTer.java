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
}
