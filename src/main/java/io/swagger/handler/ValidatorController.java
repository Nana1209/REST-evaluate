package io.swagger.handler;

import static io.swagger.models.global.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonschema.core.report.ListProcessingReport;
import com.github.fge.jsonschema.core.report.LogLevel;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import io.swagger.models.HttpMethod;
import io.swagger.models.Path;
import io.swagger.models.auth.SecuritySchemeDefinition;
import io.swagger.oas.inflector.models.RequestContext;
import io.swagger.oas.inflector.models.ResponseContext;

import io.swagger.parser.SwaggerParser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.swagger.parser.util.SwaggerDeserializationResult;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.util.Json;
import io.swagger.util.Yaml;
import io.swagger.models.SchemaValidationError;
import io.swagger.models.ValidationResponse;
import io.swagger.v3.parser.util.OpenAPIDeserializer;
import org.apache.commons.lang3.StringUtils;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import javax.ws.rs.core.Response;
import java.awt.peer.ChoicePeer;
import java.io.*;
import java.net.InetAddress;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ValidatorController{

    static final String SCHEMA_FILE = "schema3.json";
    static final String SCHEMA_URL = "https://raw.githubusercontent.com/OAI/OpenAPI-Specification/master/schemas/v3.0/schema.json";

    static final String SCHEMA2_FILE = "schema.json";
    static final String SCHEMA2_URL = "http://swagger.io/v2/schema.json";

    static final String INVALID_VERSION = "Deprecated Swagger version.  Please visit http://swagger.io for information on upgrading to Swagger/OpenAPI 2.0 or OpenAPI 3.0";

    static Logger LOGGER = LoggerFactory.getLogger(ValidatorController.class);
    static long LAST_FETCH = 0;
    static long LAST_FETCH_V3 = 0;
    static ObjectMapper JsonMapper = Json.mapper();
    static ObjectMapper YamlMapper = Yaml.mapper();
    private JsonSchema schemaV2;
    private JsonSchema schemaV3;

    static boolean rejectLocal = StringUtils.isBlank(System.getProperty("rejectLocal")) ? true : Boolean.parseBoolean(System.getProperty("rejectLocal"));
    static boolean rejectRedirect = StringUtils.isBlank(System.getProperty("rejectRedirect")) ? true : Boolean.parseBoolean(System.getProperty("rejectRedirect"));

    private float pathNum;//路径数
    private int endpointNum;//端点数
    private int score=100; //评分机制
    public Map<String,String> evaluations=new HashMap<String, String>();
    private float pathEvaData[] =new float[10];//记录实现各规范的path数 [0 no_,1 lowercase,2 noVersion,3 noapi,4 noCRUD,5 noSuffix,6 noend/,7 sumHierarchy,8 maxHierarchy]
    private float avgHierarchy;//路径平均层级数
    private List<String> hierarchies=new ArrayList<>();//所有路径层级数统计

    private Map<String,Object> validateResult=new HashMap<>();
    private Map<String,Object> pathDetail=new HashMap<>();

    private boolean hasPagePara = false;//是否有分页相关属性
    private String fileName;
    private String category=null;//类别信息
    private int opGet;//get操作数
    private int opPost;//post操作数
    private int opDelete;//delete操作数
    private int opPut;//put操作数
    private int opHead;//head操作数
    private int opOptions;
    private int opPatch;
    private  List<String> security=new ArrayList<>();//支持的安全方案
    private  List<String> CRUDlist=new ArrayList<>();//出现的动词列表
    private List<String> suffixlist=new ArrayList<>();//出现的后缀列表
    private List<String> pathlist=new ArrayList<>();//路径
    private List<String> querypara=new ArrayList<>();//过滤、限制、分页查询参数
    private List<List<String>> CRUDPathOperations=new ArrayList<>();//出现动词的路径使用的操作

    public Map<String, Object> getValidateResult() {
        return validateResult;
    }

    public List<List<String>> getCRUDPathOperations() {
        return CRUDPathOperations;
    }

    public List<String> getQuerypara() {
        return querypara;
    }

    public List<String> getPathlist() {
        return pathlist;
    }

    public List<String> getCRUDlist() {
        return CRUDlist;
    }

    public List<String> getSuffixlist() {
        return suffixlist;
    }

    public List<String> getSecurity() {
        return security;
    }

    public int getOpTrace() {
        return opTrace;
    }

    private int opTrace;//3.0规范特有

    public int getOpGet() {
        return opGet;
    }

    public int getOpPost() {
        return opPost;
    }

    public int getOpDelete() {
        return opDelete;
    }

    public int getOpPut() {
        return opPut;
    }

    public int getOpHead() {
        return opHead;
    }

    public int getOpOptions() {
        return opOptions;
    }

    public int getOpPatch() {
        return opPatch;
    }

    public String getCategory() {
        return category;
    }

    public List<String> getHierarchies() {
        return hierarchies;
    }

    public int getEndpointNum() {
        return endpointNum;
    }

    public void setEndpointNum(int endpointNum) {
        this.endpointNum = endpointNum;
    }

    public float getPathNum() {
        return pathNum;
    }

    public void setPathNum(int pathNum) {
        this.pathNum = pathNum;
    }

    public float getAvgHierarchy() {
        return avgHierarchy;
    }

    public void setAvgHierarchy(float avgHierarchy) {
        this.avgHierarchy = avgHierarchy;
    }

    public boolean isHasPagePara() {
        return hasPagePara;
    }
    public float[] getPathEvaData() {
        return pathEvaData;
    }
    public void setHasPagePara(boolean hasPagePara) {
        this.hasPagePara = hasPagePara;
    }



    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public ResponseContext validateByUrl(RequestContext request , String url) {

        this.fileName=url;

        if(url == null) {
            return new ResponseContext()
                    .status(Response.Status.BAD_REQUEST)
                    .entity( "No specification supplied in either the url or request body.  Try again?" );
        }

        ValidationResponse validationResponse = null;
        try {
            validationResponse = debugByUrl(request, url);
        }catch (Exception e){
            return new ResponseContext().status(Response.Status.INTERNAL_SERVER_ERROR).entity( "Failed to process URL" );
        }

        //System.out.println("message:"+validationResponse.getMessages());

        return processValidationResponse(validationResponse);
    }

    /**
    *@Description: 直接检测说明文档（String）
    *@Param: [request, content]
    *@return: io.swagger.oas.inflector.models.ResponseContext
    *@Author: zhouxinyu
    *@date: 2020/5/16
    */
    public ResponseContext validateByString(RequestContext request, String content){
        if(content == null) {
            return new ResponseContext()
                    .status(Response.Status.BAD_REQUEST)
                    .entity( "No specification supplied.  Try again?" );
        }

        ValidationResponse validationResponse = null;
        try {
            validationResponse = debugByContent(request, content);
        }catch (Exception e){
            return new ResponseContext().status(Response.Status.INTERNAL_SERVER_ERROR).entity( "Failed to get content" );
        }

       // System.out.println("message:"+validationResponse.getMessages());

        return processValidationResponse(validationResponse);
    }

    public static void readToBuffer(StringBuffer buffer, String filePath) throws IOException {
        InputStream is = new FileInputStream(filePath);
        String line; // 用来保存每行读取的内容
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        line = reader.readLine(); // 读取第一行
        while (line != null) { // 如果 line 为空说明读完了
            buffer.append(line); // 将读到的内容添加到 buffer 中
            buffer.append("\n"); // 添加换行符
            line = reader.readLine(); // 读取下一行
        }
        reader.close();
        is.close();
    }
    public static String readFile(String filePath) throws IOException {
        StringBuffer sb = new StringBuffer();
        readToBuffer(sb, filePath);
        return sb.toString();
    }

    public ResponseContext validateByContent(RequestContext request, JsonNode inputSpec) {
        if(inputSpec == null) {
            return new ResponseContext()
                    .status(Response.Status.BAD_REQUEST)
                    .entity( "No specification supplied in either the url or request body.  Try again?" );
        }
        String inputAsString = Json.pretty(inputSpec);

        ValidationResponse validationResponse = null;
        try {
            validationResponse = debugByContent(request ,inputAsString);
        }catch (Exception e){
            return new ResponseContext().status(Response.Status.INTERNAL_SERVER_ERROR).entity( "Failed to process URL" );
        }


        return processValidationResponse(validationResponse);
    }


    private ResponseContext processValidationResponse(ValidationResponse validationResponse) {
        if (validationResponse == null){
            return new ResponseContext().status(Response.Status.INTERNAL_SERVER_ERROR).entity( "Failed to process specification" );
        }

        boolean valid = true;
        boolean upgrade = false;
        List messages = new ArrayList<>();

        if (validationResponse.getMessages() != null) {
            for (String message : validationResponse.getMessages()) {
                if (message != null) {
                    messages.add(message);
                    if(message.endsWith("is unsupported")) {
                        valid = true;
                    }else{
                        valid = false;
                    }
                }
            }
        }
        if (validationResponse.getSchemaValidationMessages() != null) {
            for (SchemaValidationError error : validationResponse.getSchemaValidationMessages()) {
                if (error != null) {
                    messages.add(error.getMessage());
                    if (error.getLevel() != null && error.getLevel().toLowerCase().contains("error")) {
                        valid= false;
                    }
                    if (INVALID_VERSION.equals(error.getMessage())) {
                        upgrade = true;
                    }
                }
            }
        }

        if (upgrade == true ){
            return new ResponseContext()
                    .contentType("image/png")
                    .entity(this.getClass().getClassLoader().getResourceAsStream("upgrade.png"));
        }else if (valid == true ){
            return new ResponseContext()
                    .contentType("image/png")
                    .entity(this.getClass().getClassLoader().getResourceAsStream("valid.png"));
        } else{
            return new ResponseContext()
                    .contentType("image/png")
                    .entity(this.getClass().getClassLoader().getResourceAsStream("invalid.png"));
        }
    }
    public ResponseContext reviewByUrl(RequestContext request , String url) {

        if(url == null) {
            return new ResponseContext()
                    .status(Response.Status.BAD_REQUEST)
                    .entity( "No specification supplied in either the url or request body.  Try again?" );
        }

        ValidationResponse validationResponse = null;
        try {
            validationResponse = debugByUrl(request, url);
        }catch (Exception e){
            return new ResponseContext().status(Response.Status.INTERNAL_SERVER_ERROR).entity( "Failed to process specification" );
        }

        return new ResponseContext()
                .entity(validationResponse);
        //return processDebugValidationResponse(validationResponse);

    }


    public ResponseContext reviewByContent(RequestContext request, JsonNode inputSpec) {
        if(inputSpec == null) {
            return new ResponseContext()
                    .status(Response.Status.BAD_REQUEST)
                    .entity( "No specification supplied in either the url or request body.  Try again?" );
        }
        String inputAsString = Json.pretty(inputSpec);

        ValidationResponse validationResponse = null;
        try {
            validationResponse = debugByContent(request ,inputAsString);
        }catch (Exception e){
            return new ResponseContext().status(Response.Status.INTERNAL_SERVER_ERROR).entity( "Failed to process specification" );
        }

        return new ResponseContext()
                .entity(validationResponse);
        //return processDebugValidationResponse(validationResponse);
    }

    private ResponseContext processDebugValidationResponse(ValidationResponse validationResponse) {
        if (validationResponse == null){
            return new ResponseContext().status(Response.Status.INTERNAL_SERVER_ERROR).entity( "Failed to process specification" );
        }

        List messages = new ArrayList<>();
        if (validationResponse.getMessages() != null) {
            for (String message : validationResponse.getMessages()) {
                if (message != null) {
                    messages.add(message);
                }

            }
        }
        if (validationResponse.getSchemaValidationMessages() != null) {
            for (SchemaValidationError error : validationResponse.getSchemaValidationMessages()) {
                if (error != null) {
                    messages.add(error.getMessage());
                }
            }
        }

        return new ResponseContext()
                .entity(messages);
    }

    public ValidationResponse debugByUrl( RequestContext request, String url) throws Exception {
        ValidationResponse output = new ValidationResponse();
        String content;

        if(StringUtils.isBlank(url)) {
            ProcessingMessage pm = new ProcessingMessage();
            pm.setLogLevel(LogLevel.ERROR);
            pm.setMessage("No valid URL specified");
            output.addValidationMessage(new SchemaValidationError(pm.asJson()));
            return output;
        }

        // read the spec contents, bail if it fails
        try {
            content = getUrlContents(url, ValidatorController.rejectLocal, ValidatorController.rejectRedirect); //获取url提供的swagger文档，返回响应entity
        } catch (Exception e) {
            ProcessingMessage pm = new ProcessingMessage();
            pm.setLogLevel(LogLevel.ERROR);
            pm.setMessage("Can't read from file " + url);
            output.addValidationMessage(new SchemaValidationError(pm.asJson()));
            return output;
        }

        return debugByContent(request, content);
    }

    public ValidationResponse debugByContent(RequestContext request, String content) throws Exception {

        ValidationResponse output = new ValidationResponse();

        // convert to a JsonNode

        JsonNode spec = readNode(content); //解析json/yaml格式，生成树结构
        if (spec == null) {
            ProcessingMessage pm = new ProcessingMessage();
            pm.setLogLevel(LogLevel.ERROR);
            pm.setMessage("Unable to read content.  It may be invalid JSON or YAML");
            output.addValidationMessage(new SchemaValidationError(pm.asJson()));
            return output;
        }

        boolean isVersion2 = false;

        // get the version, return deprecated if version 1.x
        String version = getVersion(spec);
        validateResult.put("openapiVersion",version);
        if (version != null && (version.startsWith("\"1") || version.startsWith("1"))) {
            ProcessingMessage pm = new ProcessingMessage();
            pm.setLogLevel(LogLevel.ERROR);
            pm.setMessage(INVALID_VERSION);
            output.addValidationMessage(new SchemaValidationError(pm.asJson()));
            return output;
        }
        else if (version != null && (version.startsWith("\"2") || version.startsWith("2"))) {
            isVersion2 = true;
            SwaggerDeserializationResult result = null;
            try {
                result = readSwagger(content);  //根据content构建swagger model
            } catch (Exception e) {
                LOGGER.debug("can't read Swagger contents", e);

                ProcessingMessage pm = new ProcessingMessage();
                pm.setLogLevel(LogLevel.ERROR);
                pm.setMessage("unable to parse Swagger: " + e.getMessage());
                output.addValidationMessage(new SchemaValidationError(pm.asJson()));
                return output;
            }
            if (result != null) {
                for (String message : result.getMessages()) {
                    output.addMessage(message);
                }

                //路径（命名）检测
                Set paths = result.getSwagger().getPaths().keySet();
                pathlist= new ArrayList<>(paths);
                pathEvaluate(paths,result);

                //安全解决方案
                System.out.println(result.getSwagger().getSecurity());
                Map<String, SecuritySchemeDefinition> securityDefinitions = result.getSwagger().getSecurityDefinitions()==null?null:result.getSwagger().getSecurityDefinitions();
                if(securityDefinitions!=null){
                    for (String key : securityDefinitions.keySet()) {
                        this.security.add(securityDefinitions.get(key).getType());
                        evaluations.put("securityType",securityDefinitions.get(key).getType());
                        System.out.println("securityType ：" + securityDefinitions.get(key).getType());
                    }
                }
                validateResult.put("securityList",getSecurity());

                //基本信息统计
                basicInfoGet(result);

                //属性研究,swagger解析出属性:path-> operation -> parameter
                for(String pathName : result.getSwagger().getPaths().keySet()){
                    Map<String, io.swagger.models.parameters.Parameter> parametersInSwagger = result.getSwagger().getParameters();
                    Path path = result.getSwagger().getPath(pathName);
                    List<io.swagger.models.Operation> operations=getAllOperationsInAPath(path);

                    if (parametersInSwagger==null){


                        for(io.swagger.models.Operation operation : operations){
                            List<io.swagger.models.parameters.Parameter> parasInOprlevel = operation.getParameters();
                            if(parasInOprlevel!=null){
                                for(io.swagger.models.parameters.Parameter parameter:parasInOprlevel){
                                    //检查是否使用分页参数（查询参数方式）
                                    if(isPagePara(parameter.getName()) && parameter.getIn().equals("query")){
                                        this.querypara.add(parameter.getName());
                                        setHasPagePara(true);
                                        System.out.println(parameter.getName()+" is page parameter. ");

                                    }
                                }
                                evaluations.put("hasPageParameter",String.valueOf(isHasPagePara()));
                            }
                        }

                    }else{
                        for(String key:parametersInSwagger.keySet()){
                            //检查是否使用分页参数（查询参数方式）
                            Parameter parameter = (Parameter) parametersInSwagger.get(key);
                            if(isPagePara(parameter.getName()) && parameter.getIn().equals("query")){
                                this.querypara.add(parameter.getName());
                                setHasPagePara(true);
                                System.out.println(parameter.getName()+" is page parameter. ");

                            }
                        }
                        evaluations.put("hasPageParameter",String.valueOf(isHasPagePara()));
                    }

                }
                validateResult.put("hasPagePara",isHasPagePara());
                validateResult.put("pageParaList",getQuerypara());

                //类别信息获取
                setCategory(result);

                //动态检测，提取url
                /*

                List<Scheme> schemes = result.getSwagger().getSchemes();
                if(schemes==null){
                    schemes.add(Scheme.HTTP);
                }
                String host=result.getSwagger().getHost()==null?"":result.getSwagger().getHost();
                String basepath=result.getSwagger().getBasePath()==null || result.getSwagger().getBasePath().equals("/")?"":result.getSwagger().getBasePath();
                for(Scheme scheme:schemes){
                    for (Iterator it = paths.iterator(); it.hasNext(); ) {
                        String path = (String) it.next();
                        String url=scheme.toValue()+"://"+host+basepath+path;
                        System.out.println(url);
                        Header[] headers=getUrlHeaders(url,false,false);
                        headerEvaluate(url,headers);

                    }
                }*/

            }//if result!=null
        }
        else if (version == null || (version.startsWith("\"3") || version.startsWith("3"))) {
            SwaggerParseResult result = null;
            try {
                result = readOpenApi(content);
            } catch (Exception e) {
                LOGGER.debug("can't read OpenAPI contents", e);

                ProcessingMessage pm = new ProcessingMessage();
                pm.setLogLevel(LogLevel.ERROR);
                pm.setMessage("unable to parse OpenAPI: " + e.getMessage());
                output.addValidationMessage(new SchemaValidationError(pm.asJson()));
                return output;
            }
            if (result != null) {
                for (String message : result.getMessages()) {
                    output.addMessage(message);
                    System.out.println(message);
                }

                //基本信息获取
                basicInfoGet(result);

                //路径命名验证
                Set paths = result.getOpenAPI().getPaths().keySet();
                pathlist= new ArrayList<>(paths);
                pathEvaluate(paths,result);


                //System.out.println(result.getOpenAPI().getSecurity());
                //获取API security方案类型（apiKey，OAuth，http等）
                Components component = result.getOpenAPI().getComponents();
                if (component!=null){
                    Map<String, SecurityScheme> securitySchemes = result.getOpenAPI().getComponents().getSecuritySchemes();
                    if(securitySchemes!=null){
                        for (String key : securitySchemes.keySet()) {
                            this.security.add(securitySchemes.get(key).getType().toString());
                            evaluations.put("securityType",securitySchemes.get(key).getType().toString());
                            System.out.println("securityType ：" + securitySchemes.get(key).getType().toString());
                        }
                    }else {
                        evaluations.put("securityType","null");
                    }

                }else{
                    evaluations.put("securityType","null");
                }
                validateResult.put("securityList",getSecurity());

                //属性研究
                //openAPI完全按照说明文档进行解析，大部分属性信息在路径中
                if (component!=null) {
                    Map<String, Parameter> parametersInComponent = result.getOpenAPI().getComponents().getParameters();
                    if (parametersInComponent != null) {
                        for (String paraName : parametersInComponent.keySet()) {
                            //检查是否使用分页参数（查询参数方式）
                            if (isPagePara(paraName) && parametersInComponent.get(paraName).getIn().equals("query")) {
                                this.querypara.add(paraName);
                                setHasPagePara(true);
                                System.out.println(paraName + " is page parameter. ");
                            }
                        }
                        evaluations.put("hasPageParameter", String.valueOf(isHasPagePara()));
                    }
                }

                for(String pathName : result.getOpenAPI().getPaths().keySet()){
                    //path-》operation-》parameters

                    List<Parameter> parasInPathlevel = result.getOpenAPI().getPaths().get(pathName).getParameters();
                    OpenAPIDeserializer deserializer = new OpenAPIDeserializer();
                    List<Operation> operationsInAPath = deserializer.getAllOperationsInAPath(result.getOpenAPI().getPaths().get(pathName));
                    //this.endpointNum+=operationsInAPath.size();//统计端点数
                    if(parasInPathlevel==null){


                        for(Operation operation:operationsInAPath){
                            List<Parameter> parasInOprlevel=operation.getParameters();
                            if(parasInOprlevel!=null){
                                for(Parameter parameter: parasInOprlevel){
                                    //检查是否使用分页参数（查询参数方式）
                                    if(isPagePara(parameter.getName()) && parameter.getIn().equals("query")){
                                        this.querypara.add(parameter.getName());
                                        setHasPagePara(true);
                                        System.out.println(parameter.getName()+" is page parameter. ");
                                    }
                                }
                                evaluations.put("hasPageParameter",String.valueOf(isHasPagePara()));
                            }
                        }
                    }


                }
                validateResult.put("hasPagePara",isHasPagePara());
                validateResult.put("pageParaList",getQuerypara());

                //类别信息获取
                setCategory(result);

                //动态检测，获取URL
                /*错误太多，跳过
                List<Server> servers = result.getOpenAPI().getServers();
                if(servers.size()==1 && servers.get(0).getUrl()=="/"){

                }else {
                    for (Server server : servers) {
                        String serverURL = server.getUrl();
                        if(serverURL.contains("{")){
                            ServerVariables serverVaris = server.getVariables();
                            List<String> varsInURL = extractMessageByRegular(serverURL);//找到路径中出现的参数
                            for(String varInURL:varsInURL){
                                ServerVariable serverVar = serverVaris.get(varInURL);
                                List<String> varValues = serverVar.getEnum();//提取对应的参数枚举值
                                String varValue=varValues.get(0);
                                serverURL=serverURL.replace("{"+varInURL+"}",varValue);//将{参数}替换为枚举值第一个值
                            }
                            System.out.println(serverURL);



                        }

                        for (Iterator it = paths.iterator(); it.hasNext(); ) {
                            String path = (String) it.next();
                            String url = serverURL + path;
                            Header[] headers = getUrlHeaders(url, false, false);
                            if (headers != null) {
                                headerEvaluate(url, headers);
                            }

                        }


                    }
                }*/

                //类别信息提取
                //categorySet(result);
                
            }
        }
        evaluations.put("endpointNum",String.valueOf(this.endpointNum));//将端点数填入评估结果
        // do actual JSON schema validation
        JsonSchema schema = getSchema(isVersion2);
        ProcessingReport report = schema.validate(spec);
        ListProcessingReport lp = new ListProcessingReport();
        lp.mergeWith(report);

        java.util.Iterator<ProcessingMessage> it = lp.iterator();
        while (it.hasNext()) {
            ProcessingMessage pm = it.next();
            output.addValidationMessage(new SchemaValidationError(pm.asJson()));
        }

        return output;
    }

    /**
    *@Description: 规范3.0中提取基本信息（路径数，端点数，操作数（get，post，delete，put，，，）
    *@Param: [result]
    *@return: void
    *@Author: zhouxinyu
    *@date: 2020/7/7
    */
    private void basicInfoGet(SwaggerParseResult result) {
        setPathNum(result.getOpenAPI().getPaths().keySet().size());//提取路径数
        validateResult.put("pathNum",getPathNum());

        for(String pathName : result.getOpenAPI().getPaths().keySet()){
            OpenAPIDeserializer deserializer = new OpenAPIDeserializer();
            List<Operation> operationsInAPath = deserializer.getAllOperationsInAPath(result.getOpenAPI().getPaths().get(pathName));
            this.endpointNum+=operationsInAPath.size();//统计端点数
            PathItem path = result.getOpenAPI().getPaths().get(pathName);
            if (path.getGet() != null) {
                this.opGet++;
            }
            this.opPost = path.getPost() != null ? this.opPost+1 : this.opPost;
            this.opDelete = path.getDelete() != null ? this.opDelete+1 : this.opDelete;
            this.opPut = path.getPut() != null ? this.opPut+1 : this.opPut;
            this.opHead = path.getHead() != null ? this.opHead+1 : this.opHead;
            this.opPatch = path.getPatch() != null ? this.opPatch+1 : this.opPatch;
            this.opOptions = path.getOptions() != null ? this.opOptions+1 : this.opOptions;
            this.opTrace = path.getTrace() != null ? this.opTrace+1 : this.opTrace;
        }
        validateResult.put("endpointNum",this.getPathNum());
        validateResult.put("opGET",this.getOpGet());
        validateResult.put("opPOST",this.getOpPost());
        validateResult.put("opDELETE",getOpDelete());
        validateResult.put("opPUT",getOpPut());
        validateResult.put("opHEAD",getOpHead());
        validateResult.put("opPATCH",getOpPatch());
        validateResult.put("opOPTIONS",getOpOptions());
        validateResult.put("opTRACE",getOpTrace());

    }

    /**
    *@Description: 规范2.0中提取基本信息（路径数，端点数，操作数（get，post，delete，put，，，）
    *@Param: [result]
    *@return: void
    *@Author: zhouxinyu
    *@date: 2020/7/7
    */
    private void basicInfoGet(SwaggerDeserializationResult result) {
        setPathNum(result.getSwagger().getPaths().keySet().size());//提取路径数
        validateResult.put("pathNum",getPathNum());
        for(String pathName : result.getSwagger().getPaths().keySet()) {
            Path path = result.getSwagger().getPath(pathName);
            List<io.swagger.models.Operation> operations = getAllOperationsInAPath(result.getSwagger().getPath(pathName));
            this.endpointNum += operations.size();//统计端点数
            if (path.getGet() != null) {
                this.opGet++;
            }
            this.opPost = path.getPost() != null ? this.opPost+1 : this.opPost;
            this.opDelete = path.getDelete() != null ? this.opDelete+1 : this.opDelete;
            this.opPut = path.getPut() != null ? this.opPut+1 : this.opPut;
            this.opHead = path.getHead() != null ? this.opHead+1 : this.opHead;
            this.opPatch = path.getPatch() != null ? this.opPatch+1 : this.opPatch;
            this.opOptions = path.getOptions() != null ? this.opOptions+1 : this.opOptions;
        }
        validateResult.put("endpointNum",this.getPathNum());
        validateResult.put("opGET",this.getOpGet());
        validateResult.put("opPOST",this.getOpPost());
        validateResult.put("opDELETE",getOpDelete());
        validateResult.put("opPUT",getOpPut());
        validateResult.put("opHEAD",getOpHead());
        validateResult.put("opPATCH",getOpPatch());
        validateResult.put("opOPTIONS",getOpOptions());
        validateResult.put("opTRACE",getOpTrace());
    }

    /**
    *@Description: 获取API类别信息 2.0规范
    *@Param: [result]
    *@return: void
    *@Author: zhouxinyu
    *@date: 2020/7/5
    */
    private void setCategory(SwaggerDeserializationResult result) {
        if(result.getSwagger().getInfo()!=null){
            Map<String, Object> extension = result.getSwagger().getInfo().getVendorExtensions();
            if(extension!=null && extension.size()!=0){
                String cateInfo = extension.get("x-apisguru-categories").toString();
                if(cateInfo!=null){
                    this.category=cateInfo;
                }
            }
        }

        validateResult.put("category",getCategory());
        return;
    }

    /**
    *@Description: 获取API类别信息 3.0规范
    *@Param: [result]
    *@return: void
    *@Author: zhouxinyu
    *@date: 2020/7/5
    */
    private void setCategory(SwaggerParseResult result) {
        if(result.getOpenAPI().getInfo()!=null){
            Map<String, Object> extension = result.getOpenAPI().getInfo().getExtensions();
            if(extension!=null && extension.size()!=0){
                String cateInfo = extension.get("x-apisguru-categories").toString();
                if(cateInfo!=null){
                    this.category=cateInfo;
                }
            }
        }

        validateResult.put("category",getCategory());
        return;
    }

    /**
    *@Description: 头文件检测，检测结果加入evaluations
    *@Param: [headers]
    *@return: void
    *@Author: zhouxinyu
    *@date: 2020/5/20
    */
    private void headerEvaluate(String url,Header[] headers) {
        boolean hasCacheScheme=false;
        boolean hasEtag=false;
        boolean hasCacheControl=false;
        boolean hasContentType=false;
        boolean hasLastModified=false;
        boolean hasExpires=false;
        String contentType="";
        for(Header header:headers){
            if(header.getName().equals("etag")){
                System.out.println(url+" response has Etag");
                hasEtag=true;
            }
            if(header.getName().equals("last-modified")){
                System.out.println(url+" response has last-modified");
                hasLastModified=true;
            }
            if(header.getName().equals("expires")){
                System.out.println(url+" response has expires");
                hasExpires=true;
            }
            if(header.getName().equals("cache-control")){
                System.out.println(url+" response has cache-control");
                hasCacheControl=true;
            }
            if(header.getName().equals("content-type")){
                System.out.println(url+" response has content-type");
                //evaluations.put("content-type",header.getValue());
                contentType=header.getValue();
                hasContentType=true;
            }
        }
        hasCacheScheme=hasCacheControl || hasEtag || hasExpires || hasLastModified;
        evaluations.put("hasEtag",String.valueOf(hasEtag));
        evaluations.put("hasLastModified",String.valueOf(hasLastModified));
        evaluations.put("hasExpires",String.valueOf(hasExpires));
        evaluations.put("hasCacheControl",String.valueOf(hasCacheControl));
        evaluations.put("hasCacheScheme",String.valueOf(hasCacheScheme));
        evaluations.put("hasContentType",hasContentType==true?contentType:"false");
    }

    /**
    *@Description: 是否有页面过滤机制
    *@Param: [name]
    *@return: boolean
    *@Author: zhouxinyu
    *@date: 2020/5/27
    */
    public boolean isPagePara(String name) {
        if(name==null) return  false;
        //String pageNames[]=PAGEPARANAMES;
        String pageNames[]=ConfigManager.getInstance().getValue("PAGEPARANAMES").split(",",-1);//配置文件获取功能性（页面过滤）查询属性检查列表
        boolean result = false;
        for(int i=0; i< pageNames.length; i++){
            if (name.toLowerCase().indexOf(pageNames[i]) >=0) {
                result = true;
                break;
            }
        }
        return result;
    }

    public List<io.swagger.models.Operation> getAllOperationsInAPath(Path pathObj) {
        List<io.swagger.models.Operation> operations = new ArrayList();
        addToOperationsList(operations, pathObj.getGet());
        addToOperationsList(operations, pathObj.getPut());
        addToOperationsList(operations, pathObj.getPost());
        addToOperationsList(operations, pathObj.getPatch());
        addToOperationsList(operations, pathObj.getDelete());
        addToOperationsList(operations, pathObj.getOptions());
        addToOperationsList(operations, pathObj.getHead());
        return operations;
    }

    private void addToOperationsList(List<io.swagger.models.Operation> operationsList, io.swagger.models.Operation operation) {
        if (operation != null) {
            operationsList.add(operation);
        }
    }

    /**
    *@Description: 路径（命名）验证,v2.0
    *@Param: [paths, result]
    *@return: void
    *@Author: zhouxinyu
    *@date: 2020/8/12
    */
    private void pathEvaluate(Set paths, SwaggerDeserializationResult result) {
        //setPathNum(paths.size());//提取路径数
        evaluations.put("pathNum",Float.toString(getPathNum()));//向评估结果中填入路径数
        for (Iterator it = paths.iterator(); it.hasNext(); ) {
            Map<String,Object> pathResult=new HashMap<>();
            String p = (String) it.next();
            //evaluateToScore()

            if(!(p.indexOf("_") < 0)){
                //System.out.println(p+" has _");
                //this.score=this.score-20>0?this.score-20:0;
                pathResult.put("no_",false);
            }else {
                this.pathEvaData[0]++;//Integer是Object子类，是对象，可以为null。int是基本数据类型，必须初始化，默认为0
                pathResult.put("no_",true);
            }

            if(p!=p.toLowerCase()){
                //System.out.println(p+"need to be lowercase");
                //this.score=this.score-20>0?this.score-20:0;
                pathResult.put("lowercase",false);
            }else {
                this.pathEvaData[1]++;
                pathResult.put("lowercase",true);
            }

            //Pattern pattern1 = Pattern.compile(VERSIONPATH_REGEX);
            Pattern pattern1 = Pattern.compile(ConfigManager.getInstance().getValue("VERSIONPATH_REGEX"));
            Matcher m1 = pattern1.matcher(p); // 获取 matcher 对象
            if(m1.find()){
                System.out.println("version shouldn't in paths "+p);
                //this.score=this.score-5>0?this.score-5:0;
                pathResult.put("noVersion",false);
            }else {
                this.pathEvaData[2]++;
                pathResult.put("noVersion",true);
            }

            if(p.toLowerCase().indexOf("api")>=0){
                System.out.println("api shouldn't in path "+p);
                //this.score=this.score-10>0?this.score-10:0;
                pathResult.put("noapi",false);
            }else {
                this.pathEvaData[3]++;
                pathResult.put("noapi",true);
            }

            //this.pathlist.add(p);
            Pattern pp = Pattern.compile("(\\{[^\\}]*\\})");
            Matcher m = pp.matcher(p);
            String pathclear = "";//去除属性{}之后的路径
            int endtemp=0;
            while(m.find()){
                pathclear+=p.substring(endtemp,m.start());
                endtemp=m.end();
            }
            pathclear+=p.substring(endtemp);
            pathclear=pathclear.toLowerCase();
            //String crudnames[]=CRUDNAMES;
            String crudnames[]=ConfigManager.getInstance().getValue("CRUDNAMES").split(",",-1);

            String dellistString=ConfigManager.getInstance().getValue("DELLIST");
            String str1[] = dellistString.split(";",-1);
            String delList[][]=new String[str1.length][];
            for(int i = 0;i < str1.length;i++) {

                String str2[] = str1[i].split(",");
                delList[i] = str2;
            }
            //String delList[][]=DELLIST;
            boolean isCrudy = false;
            List<String> verblist=new ArrayList<>();
            for(int i=0; i< crudnames.length; i++){
                // notice it should start with the CRUD name
                String temp=fileHandle.delListFromString(pathclear,delList[i]);
                if (temp.indexOf(crudnames[i]) >=0) {
                    isCrudy = true;
                    verblist.add(crudnames[i]);

                    CRUDPathOperation(p,crudnames[i], result);
                    break;
                }
            }
            this.CRUDlist.addAll(verblist);
            pathResult.put("CRUDlist",verblist);
            if(isCrudy){
                System.out.println("CRUD shouldn't in path "+p);
                //this.score=this.score-20>0?this.score-20:0;
                pathResult.put("noCRUD",false);

            }else{
                this.pathEvaData[4]++;
                pathResult.put("noCRUD",true);
            }

            //文件扩展名不应该包含在API的URL命名中
            //String suffix[]=SUFFIX_NAMES;
            String suffix[]=ConfigManager.getInstance().getValue("SUFFIX_NAMES").split(",",-1);
            boolean isSuffix = false;
            List<String> slist=new ArrayList<>();
            for(int i=0; i< suffix.length; i++){
                if (p.toLowerCase().indexOf(suffix[i]) >=0) {
                    isSuffix = true;
                    slist.add(suffix[i]);

                    break;
                }
            }
            this.suffixlist.addAll(slist);
            pathResult.put("suffixList",slist);
            if(isSuffix){
                System.out.println("suffix shouldn't in path "+p);
                //this.score=this.score-20>0?this.score-20:0;
                pathResult.put("noSuffix",false);
            }else {
                this.pathEvaData[5]++;
                pathResult.put("noSuffix",true);
            }



            //使用正斜杠分隔符“/”来表示一个层次关系，尾斜杠不包含在URL中
            int hierarchyNum=0;
            if(p.endsWith("/") && p.length()>1){
                //System.out.println(p+" :尾斜杠不包含在URL中");
                //this.score=this.score-20>0?this.score-20:0;
                hierarchyNum=substringCount(p,"/")-1;
                this.hierarchies.add(Integer.toString(hierarchyNum));
                this.pathEvaData[7]+=hierarchyNum;//层级总数，算平均层级数
                this.pathEvaData[8]=hierarchyNum>=this.pathEvaData[8]?hierarchyNum:this.pathEvaData[8];//最大层级数
                pathResult.put("noend/",false);

            }else{
                pathResult.put("noend/",true);
                this.pathEvaData[6]++;
                //建议嵌套深度一般不超过3层
                hierarchyNum=substringCount(p,"/");
                this.hierarchies.add(Integer.toString(hierarchyNum));
                this.pathEvaData[7]+=hierarchyNum;//层级总数，算平均层级数
                this.pathEvaData[8]=hierarchyNum>=this.pathEvaData[8]?hierarchyNum:this.pathEvaData[8];//最大层级数

            }
            pathResult.put("hierarchies",hierarchyNum);
            if(hierarchyNum>3){
                //System.out.println(p+": 嵌套深度建议不超过3层");
                //this.score=this.score-5>0?this.score-5:0;
            }else {

            }
            pathDetail.put(p,pathResult);

        }
        validateResult.put("pathEvaData",getPathEvaData());
        setAvgHierarchy(this.pathEvaData[7]/(float)paths.size());//计算平均层级数
        validateResult.put("avgHierarchies",getAvgHierarchy());
        evaluations.put("avgHierarchy",Float.toString(getAvgHierarchy()));//向评估结果中填入平均层级数
        evaluations.put("maxHierarchy",Float.toString(pathEvaData[8]));//最大层级数
        evaluations.put("noUnderscoreRate",Float.toString(pathEvaData[0]/getPathNum()));//不出现下划线实现率
        evaluations.put("lowcaseRate",Float.toString(pathEvaData[1]/getPathNum()));//小写实现率
        evaluations.put("noVersionRate",Float.toString(pathEvaData[2]/getPathNum()));//不出现版本信息实现率
        evaluations.put("noapiRate",Float.toString(pathEvaData[3]/getPathNum()));//不出现"api"实现率
        evaluations.put("noCRUDRate",Float.toString(pathEvaData[4]/getPathNum()));//不出现动词实现率
        evaluations.put("noSuffixRate",Float.toString(pathEvaData[5]/getPathNum()));//不出现格式后缀实现率
        evaluations.put("noEndSlashRate",Float.toString(pathEvaData[6]/getPathNum()));//没有尾斜杠实现率

        validateResult.put("path",pathDetail);
    }
    /**
    *@Description: 路径（命名）验证,v3.0
    *@Param: [paths]路径名集合
    *@return: void
    *@Author: zhouxinyu
    *@date: 2020/5/16
    */
    private void pathEvaluate(Set paths, SwaggerParseResult result) {
        //setPathNum(paths.size());//提取路径数
        evaluations.put("pathNum",Float.toString(getPathNum()));//向评估结果中填入路径数
        for (Iterator it = paths.iterator(); it.hasNext(); ) {
            Map<String,Object> pathResult=new HashMap<>();
            String p = (String) it.next();
            //evaluateToScore()

            if(!(p.indexOf("_") < 0)){
                //System.out.println(p+" has _");
                //this.score=this.score-20>0?this.score-20:0;
                pathResult.put("no_",false);
            }else {
                this.pathEvaData[0]++;//Integer是Object子类，是对象，可以为null。int是基本数据类型，必须初始化，默认为0
                pathResult.put("no_",true);
            }

            if(p!=p.toLowerCase()){
                //System.out.println(p+"need to be lowercase");
                //this.score=this.score-20>0?this.score-20:0;
                pathResult.put("lowercase",false);
            }else {
                this.pathEvaData[1]++;
                pathResult.put("lowercase",true);
            }

            //Pattern pattern1 = Pattern.compile(VERSIONPATH_REGEX);
            Pattern pattern1 = Pattern.compile(ConfigManager.getInstance().getValue("VERSIONPATH_REGEX"));
            Matcher m1 = pattern1.matcher(p); // 获取 matcher 对象
            if(m1.find()){
                System.out.println("version shouldn't in paths "+p);
                //this.score=this.score-5>0?this.score-5:0;
                pathResult.put("noVersion",false);
            }else {
                this.pathEvaData[2]++;
                pathResult.put("noVersion",true);
            }

            if(p.toLowerCase().indexOf("api")>=0){
                System.out.println("api shouldn't in path "+p);
                //this.score=this.score-10>0?this.score-10:0;
                pathResult.put("noapi",false);
            }else {
                this.pathEvaData[3]++;
                pathResult.put("noapi",true);
            }

            //this.pathlist.add(p);
            Pattern pp = Pattern.compile("(\\{[^\\}]*\\})");
            Matcher m = pp.matcher(p);
            String pathclear = "";//去除属性{}之后的路径
            int endtemp=0;
            while(m.find()){
                pathclear+=p.substring(endtemp,m.start());
                endtemp=m.end();
            }
            pathclear+=p.substring(endtemp);
            pathclear=pathclear.toLowerCase();
            //String crudnames[]=CRUDNAMES;
            String crudnames[]=ConfigManager.getInstance().getValue("CRUDNAMES").split(",",-1);

            String dellistString=ConfigManager.getInstance().getValue("DELLIST");
            String str1[] = dellistString.split(";",-1);
            String delList[][]=new String[str1.length][];
            for(int i = 0;i < str1.length;i++) {

                String str2[] = str1[i].split(",");
                delList[i] = str2;
            }
            //String delList[][]=DELLIST;
            boolean isCrudy = false;
            List<String> verblist=new ArrayList<>();
            for(int i=0; i< crudnames.length; i++){
                // notice it should start with the CRUD name
                String temp=fileHandle.delListFromString(pathclear,delList[i]);
                if (temp.indexOf(crudnames[i]) >=0) {
                    isCrudy = true;
                    verblist.add(crudnames[i]);

                    CRUDPathOperation(p,crudnames[i], result);
                    break;
                }
            }
            this.CRUDlist.addAll(verblist);
            pathResult.put("CRUDlist",verblist);
            if(isCrudy){
                System.out.println("CRUD shouldn't in path "+p);
                //this.score=this.score-20>0?this.score-20:0;
                pathResult.put("noCRUD",false);

            }else{
                this.pathEvaData[4]++;
                pathResult.put("noCRUD",true);
            }

            //文件扩展名不应该包含在API的URL命名中
            //String suffix[]=SUFFIX_NAMES;
            String suffix[]=ConfigManager.getInstance().getValue("SUFFIX_NAMES").split(",",-1);
            boolean isSuffix = false;
            List<String> slist=new ArrayList<>();
            for(int i=0; i< suffix.length; i++){
                if (p.toLowerCase().indexOf(suffix[i]) >=0) {
                    isSuffix = true;
                    slist.add(suffix[i]);

                    break;
                }
            }
            this.suffixlist.addAll(slist);
            pathResult.put("suffixList",slist);
            if(isSuffix){
                System.out.println("suffix shouldn't in path "+p);
                //this.score=this.score-20>0?this.score-20:0;
                pathResult.put("noSuffix",false);
            }else {
                this.pathEvaData[5]++;
                pathResult.put("noSuffix",true);
            }



            //使用正斜杠分隔符“/”来表示一个层次关系，尾斜杠不包含在URL中
            int hierarchyNum=0;
            if(p.endsWith("/") && p.length()>1){
                //System.out.println(p+" :尾斜杠不包含在URL中");
                //this.score=this.score-20>0?this.score-20:0;
                hierarchyNum=substringCount(p,"/")-1;
                this.hierarchies.add(Integer.toString(hierarchyNum));
                this.pathEvaData[7]+=hierarchyNum;//层级总数，算平均层级数
                this.pathEvaData[8]=hierarchyNum>=this.pathEvaData[8]?hierarchyNum:this.pathEvaData[8];//最大层级数
                pathResult.put("noend/",false);

            }else{
                pathResult.put("noend/",true);
                this.pathEvaData[6]++;
                //建议嵌套深度一般不超过3层
                hierarchyNum=substringCount(p,"/");
                this.hierarchies.add(Integer.toString(hierarchyNum));
                this.pathEvaData[7]+=hierarchyNum;//层级总数，算平均层级数
                this.pathEvaData[8]=hierarchyNum>=this.pathEvaData[8]?hierarchyNum:this.pathEvaData[8];//最大层级数

            }
            pathResult.put("hierarchies",hierarchyNum);
            if(hierarchyNum>3){
                //System.out.println(p+": 嵌套深度建议不超过3层");
                //this.score=this.score-5>0?this.score-5:0;
            }else {

            }
            pathDetail.put(p,pathResult);
        }
        validateResult.put("pathEvaData",getPathEvaData());
        setAvgHierarchy(this.pathEvaData[7]/(float)paths.size());//计算平均层级数
        validateResult.put("avgHierarchies",getAvgHierarchy());
        evaluations.put("avgHierarchy",Float.toString(getAvgHierarchy()));//向评估结果中填入平均层级数
        evaluations.put("maxHierarchy",Float.toString(pathEvaData[8]));//最大层级数
        evaluations.put("noUnderscoreRate",Float.toString(pathEvaData[0]/getPathNum()));//不出现下划线实现率
        evaluations.put("lowcaseRate",Float.toString(pathEvaData[1]/getPathNum()));//小写实现率
        evaluations.put("noVersionRate",Float.toString(pathEvaData[2]/getPathNum()));//不出现版本信息实现率
        evaluations.put("noapiRate",Float.toString(pathEvaData[3]/getPathNum()));//不出现"api"实现率
        evaluations.put("noCRUDRate",Float.toString(pathEvaData[4]/getPathNum()));//不出现动词实现率
        evaluations.put("noSuffixRate",Float.toString(pathEvaData[5]/getPathNum()));//不出现格式后缀实现率
        evaluations.put("noEndSlashRate",Float.toString(pathEvaData[6]/getPathNum()));//没有尾斜杠实现率

        validateResult.put("path",pathDetail);
    }

    /**
    *@Description: 路径中出现的动词以及所使用的操作 v3.0
    *@Param: [p, crudname, result]
    *@return: void
    *@Author: zhouxinyu
    *@date: 2020/8/12
    */
    private void CRUDPathOperation(String p, String crudname, SwaggerParseResult result) {
        List<String> pathOP=new ArrayList<>();
        pathOP.add(p);
        pathOP.add(crudname);
        PathItem path =result.getOpenAPI().getPaths().get(p);
        for(PathItem.HttpMethod op : path.readOperationsMap().keySet()){
            pathOP.add(op.name());
        }
        CRUDPathOperations.add(pathOP);
    }

    /**
    *@Description: 路径中出现的动词以及所使用的操作 v2.0
    *@Param:
    *@return:
    *@Author: zhouxinyu
    *@date: 2020/8/12
    */
    private void CRUDPathOperation(String p, String crudname, SwaggerDeserializationResult result) {
        List<String> pathOP=new ArrayList<>();
        pathOP.add(p);
        pathOP.add(crudname);
        Path path=result.getSwagger().getPaths().get(p);

        for(HttpMethod op : path.getOperationMap().keySet()){
            pathOP.add(op.name());
        }
        CRUDPathOperations.add(pathOP);
    }

    //正则表达式提取字符串{}内字符串
    public static List<String> extractMessageByRegular(String msg){

        List<String> list=new ArrayList<String>();
        Pattern p = Pattern.compile("(\\{[^\\}]*\\})");
        Matcher m = p.matcher(msg);
        while(m.find()){
            list.add(m.group().substring(1, m.group().length()-1));
        }
        return list;
    }

    /**
    *@Description: 计算字符串中子串数
    *@Param: [s, subs]
    *@return: int
    *@Author: zhouxinyu
    *@date: 2020/5/16
    */
    public int substringCount(String s, String subs) {
        //String src = "Little monkey like to eat bananas, eat more into the big monkey, and finally become fat monkey";
        //String dest = "monkey";
        int count = 0;
        int index = 0;

        while ((index = s.indexOf(subs)) != -1){
            s = s.substring(index + subs.length());
            //System.out.println(src);
            count++;
        }
        //System.out.print(count);
        return count;
    }

    /**
    *@Description: 获取指定url的响应头内容
    *@Param: [urlString, rejectLocal, rejectRedirect]
    *@return: org.apache.http.Header[]
    *@Author: zhouxinyu
    *@date: 2020/5/18
    */
    public Header[] getUrlHeaders(String urlString, boolean rejectLocal, boolean rejectRedirect) throws IOException {
        if(urlString.contains("{")){
            return  null;
        }else {
            URL url = new URL(urlString);


            if (rejectLocal) {
                InetAddress inetAddress = InetAddress.getByName(url.getHost());
                if (inetAddress.isAnyLocalAddress() || inetAddress.isLoopbackAddress() || inetAddress.isLinkLocalAddress()) {
                    throw new IOException("Only accepts http/https protocol");
                }
            }
            final CloseableHttpClient httpClient = getCarelessHttpClient(rejectRedirect);

            RequestConfig.Builder requestBuilder = RequestConfig.custom();
            requestBuilder = requestBuilder
                    .setConnectTimeout(5000)
                    .setSocketTimeout(5000);


            HttpGet getMethod = new HttpGet(urlString);
            getMethod.setConfig(requestBuilder.build());
            getMethod.setHeader("Accept", "application/json, */*");


            if (httpClient != null) {
                final CloseableHttpResponse response = httpClient.execute(getMethod);

                try {

                    HttpEntity entity = response.getEntity();
                    StatusLine line = response.getStatusLine();
                    if (line.getStatusCode() > 299 || line.getStatusCode() < 200) {
                        return null;
                        //throw new IOException("failed to read swagger with code " + line.getStatusCode());
                    }
                    Header[] headers = response.getAllHeaders();
                    //return EntityUtils.toString(entity, "UTF-8");
                    return headers;
                } finally {
                    response.close();
                    httpClient.close();
                }
            } else {
                throw new IOException("CloseableHttpClient could not be initialized");
            }
        }
    }

    private JsonSchema getSchema(boolean isVersion2) throws Exception {
        if (isVersion2) {
            return getSchemaV2();
        } else {
            return getSchemaV3();
        }
    }

    private JsonSchema getSchemaV3() throws Exception {
        if (schemaV3 != null && (System.currentTimeMillis() - LAST_FETCH_V3) < 600000) {
            return schemaV3;
        }

        try {
            LOGGER.debug("returning online schema v3");
            LAST_FETCH_V3 = System.currentTimeMillis();
            schemaV3 = resolveJsonSchema(getUrlContents(SCHEMA_URL), true);
            return schemaV3;
        } catch (Exception e) {
            LOGGER.warn("error fetching schema v3 from GitHub, using local copy");
            schemaV3 = resolveJsonSchema(getResourceFileAsString(SCHEMA_FILE), true);
            LAST_FETCH_V3 = System.currentTimeMillis();
            return schemaV3;
        }
    }

    private JsonSchema getSchemaV2() throws Exception {
        if (schemaV2 != null && (System.currentTimeMillis() - LAST_FETCH) < 600000) {
            return schemaV2;
        }

        try {
            LOGGER.debug("returning online schema");
            LAST_FETCH = System.currentTimeMillis();
            schemaV2 = resolveJsonSchema(getUrlContents(SCHEMA2_URL));
            return schemaV2;
        } catch (Exception e) {
            LOGGER.warn("error fetching schema from GitHub, using local copy");
            schemaV2 = resolveJsonSchema(getResourceFileAsString(SCHEMA2_FILE));
            LAST_FETCH = System.currentTimeMillis();
            return schemaV2;
        }
    }

    private JsonSchema resolveJsonSchema(String schemaAsString) throws Exception {
        return resolveJsonSchema(schemaAsString, false);
    }
    private JsonSchema resolveJsonSchema(String schemaAsString, boolean removeId) throws Exception {
        JsonNode schemaObject = JsonMapper.readTree(schemaAsString);
        if (removeId) {
            ObjectNode oNode = (ObjectNode) schemaObject;
            if (oNode.get("id") != null) {
                oNode.remove("id");
            }
            if (oNode.get("$schema") != null) {
                oNode.remove("$schema");
            }
            if (oNode.get("description") != null) {
                oNode.remove("description");
            }
        }
        JsonSchemaFactory factory = JsonSchemaFactory.byDefault();
        return factory.getJsonSchema(schemaObject);

    }
    private CloseableHttpClient getCarelessHttpClient(boolean disableRedirect) {
        CloseableHttpClient httpClient = null;

        try {
            SSLContextBuilder builder = new SSLContextBuilder();
            builder.loadTrustMaterial(null, new TrustStrategy() {
                public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    return true;
                }
            });
            SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build(), NoopHostnameVerifier.INSTANCE);
            HttpClientBuilder httpClientBuilder = HttpClients
                    .custom()
                    .setSSLSocketFactory(sslsf);
            if (disableRedirect) {
                httpClientBuilder.disableRedirectHandling();
            }
            httpClientBuilder.setUserAgent("swagger-validator");
            httpClient = httpClientBuilder.build();
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            LOGGER.error("can't disable SSL verification", e);
        }

        return httpClient;
    }

    public String getUrlContents(String urlString) throws IOException {
        return getUrlContents(urlString, false, false);
    }
    private String getUrlContents(String urlString, boolean rejectLocal, boolean rejectRedirect) throws IOException {
        LOGGER.trace("fetching URL contents");
        URL url = new URL(urlString);
        if(rejectLocal) {
            InetAddress inetAddress = InetAddress.getByName(url.getHost());
            if(inetAddress.isAnyLocalAddress() || inetAddress.isLoopbackAddress() || inetAddress.isLinkLocalAddress()) {
                throw new IOException("Only accepts http/https protocol");
            }
        }
        final CloseableHttpClient httpClient = getCarelessHttpClient(rejectRedirect);

        RequestConfig.Builder requestBuilder = RequestConfig.custom();
        requestBuilder = requestBuilder
                .setConnectTimeout(5000)
                .setSocketTimeout(5000);

        HttpGet getMethod = new HttpGet(urlString);
        getMethod.setConfig(requestBuilder.build());
        getMethod.setHeader("Accept", "application/json, */*");


        if (httpClient != null) {
            final CloseableHttpResponse response = httpClient.execute(getMethod);

            try {

                HttpEntity entity = response.getEntity();
                StatusLine line = response.getStatusLine();
                if(line.getStatusCode() > 299 || line.getStatusCode() < 200) {
                    throw new IOException("failed to read swagger with code " + line.getStatusCode());
                }
                return EntityUtils.toString(entity, "UTF-8");
            } finally {
                response.close();
                httpClient.close();
            }
        } else {
            throw new IOException("CloseableHttpClient could not be initialized");
        }
    }

    private SwaggerParseResult readOpenApi(String content) throws IllegalArgumentException {
        OpenAPIV3Parser parser = new OpenAPIV3Parser();
        return parser.readContents(content, null, null);

    }

    private SwaggerDeserializationResult readSwagger(String content) throws IllegalArgumentException {
        SwaggerParser parser = new SwaggerParser();
        return parser.readWithInfo(content);
    }

    private JsonNode readNode(String text) {
        try {
            if (text.trim().startsWith("{")) {
                return JsonMapper.readTree(text);
            } else {
                return YamlMapper.readTree(text);
            }
        } catch (IOException e) {
            return null;
        }
    }


    private String getVersion(JsonNode node) {
        if (node == null) {
            return null;
        }

        JsonNode version = node.get("openapi");
        if (version != null) {
            return version.toString();
        }

        version = node.get("swagger");
        if (version != null) {
            return version.toString();
        }
        version = node.get("swaggerVersion");
        if (version != null) {
            return version.toString();
        }

        LOGGER.debug("version not found!");
        return null;
    }

    public String getResourceFileAsString(String fileName) {
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(fileName);
        if (is != null) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            return reader.lines().collect(Collectors.joining(System.lineSeparator()));
        }
        return null;
    }

    /**
    *@Description: 将结果写进文件
    *@Param: [fileName] 生成的文件名
    *@return: boolean
    *@Author: zhouxinyu
    *@date: 2020/6/20
    */
    public boolean resultToFile(String fileName){
        Boolean bool = false;
        String filenameTemp = "E:\\test\\resultOpenAPI\\"+fileName+".json";//文件路径+名称+文件类型
        File file = new File(filenameTemp);
        try {
            //如果文件不存在，则创建新的文件
            if(!file.exists()){
                file.createNewFile();

                System.out.println("success create file,the file is "+filenameTemp);
                //创建文件成功后，写入内容到文件里
                ObjectMapper mapper = new ObjectMapper();
                try {
                    mapper.writeValue(file, this.evaluations);
                    bool = true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return bool;
    }




}
