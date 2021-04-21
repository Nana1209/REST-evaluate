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
        patameters=new ArrayList<>();
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

    public List<ParameterRESTer> getPatameters() {
        return patameters;
    }

    public void setPatameters(List<ParameterRESTer> patameters) {
        this.patameters = patameters;
    }
}
