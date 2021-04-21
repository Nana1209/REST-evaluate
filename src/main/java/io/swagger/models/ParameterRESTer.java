package io.swagger.models;

public class ParameterRESTer {
    protected String name;
    protected String in;
    protected Boolean required;
    ParameterRESTer(){
        name="";
        in="";
        required=false;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIn() {
        return in;
    }

    public void setIn(String in) {
        this.in = in;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }
}
