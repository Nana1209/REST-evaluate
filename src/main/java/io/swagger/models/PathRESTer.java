package io.swagger.models;

import java.util.ArrayList;
import java.util.List;

public class PathRESTer {
    protected String pathName;
    protected List<OperationRESTer> operations;
    protected List<ParameterRESTer> patameters;
    PathRESTer(){
        pathName="";
        operations=new ArrayList<>();
    }
}
