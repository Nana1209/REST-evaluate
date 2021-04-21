package io.swagger.models;

import io.swagger.parser.util.SwaggerDeserializationResult;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RESTModel {
    protected String name;
    protected List<String> baseURLs;
    protected Map<String,Object> paths;
    protected Map<String,Object> securitySchemes;

    /**
     * 从swagger构造
     * @param result
     */
    public RESTModel(SwaggerDeserializationResult result){
        baseURLs=new ArrayList<>();
        paths=new HashMap<>();

        Swagger swagger=result.getSwagger();
        //解析baseURL
        String baseURL=swagger.getHost()+swagger.getBasePath();
        List<Scheme> schemes = swagger.getSchemes();
        if(schemes==null){
            schemes.add(Scheme.HTTP);
        }
        for(Scheme scheme:schemes){
            baseURL=scheme.toValue()+"://"+baseURL;
            baseURLs.add(baseURL);
        }
        //解析路径paths
        for(String path:swagger.getPaths().keySet()){
            paths.put(path,swagger.getPath(path));
        }
    }

    /**
     * 从openAPI构造
     * @param result
     */
    public RESTModel(SwaggerParseResult result){
        baseURLs=new ArrayList<>();
        paths=new HashMap<>();

        //解析baseurl
        OpenAPI openAPI=result.getOpenAPI();
        List<Server> servers=openAPI.getServers();
        if(servers!=null){
            for(Server server:servers){
                String serverurl=server.getUrl();//还有属性没有解决
                baseURLs.add(serverurl);
            }
        }
        //解析路径paths
        for(String path :result.getOpenAPI().getPaths().keySet()){
            paths.put(path,result.getOpenAPI().getPaths().get(path));
        }
    }

    public RESTModel() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setBaseURLs(List<String> baseURLs) {
        this.baseURLs = baseURLs;
    }

    public void setPaths(Map<String, Object> paths) {
        this.paths = paths;
    }

    public void setSecuritySchemes(Map<String, Object> securitySchemes) {
        this.securitySchemes = securitySchemes;
    }

    public List<String> getBaseURLs() {
        return baseURLs;
    }

    public Map<String, Object> getPaths() {
        return paths;
    }

    public Map<String, Object> getSecuritySchemes() {
        return securitySchemes;
    }
}
