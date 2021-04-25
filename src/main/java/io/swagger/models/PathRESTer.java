package io.swagger.models;

import java.util.ArrayList;
import java.util.List;

public class PathRESTer {
    protected String pathName;
    protected List<OperationRESTer> operations;
    protected List<ParameterRESTer> parameters;
    PathRESTer(){
        pathName="";
        operations=new ArrayList<>();
        parameters=new ArrayList<>();
    }

    public String getPathName() {
        return pathName;
    }

    public void setPathName(String pathName) {
        this.pathName = pathName;
    }

    public List<OperationRESTer> getOperations() {
        return operations;
    }

    public void setOperations(List<OperationRESTer> operations) {
        this.operations = operations;
    }

    public List<ParameterRESTer> getParameters() {
        return parameters;
    }

    public void setPatameters(List<ParameterRESTer> parameters) {
        this.parameters = parameters;
    }
}
