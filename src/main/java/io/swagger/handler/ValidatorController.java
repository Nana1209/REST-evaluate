package io.swagger.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonschema.core.report.ListProcessingReport;
import com.github.fge.jsonschema.core.report.LogLevel;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import io.swagger.models.*;
import io.swagger.models.auth.SecuritySchemeDefinition;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.RefParameter;
import io.swagger.models.parameters.SerializableParameter;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.MapProperty;
import io.swagger.models.properties.ObjectProperty;
import io.swagger.models.properties.Property;
import io.swagger.oas.inflector.models.RequestContext;
import io.swagger.oas.inflector.models.ResponseContext;

import io.swagger.parser.SwaggerParser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.servers.ServerVariable;
import io.swagger.v3.oas.models.servers.ServerVariables;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.swagger.parser.util.SwaggerDeserializationResult;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.util.Json;
import io.swagger.util.Yaml;
import io.swagger.v3.parser.util.OpenAPIDeserializer;
import net.sf.json.JSONException;
import org.apache.commons.lang3.StringUtils;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.apache.http.util.EntityUtils;
/*import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;*/
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
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
import net.sf.json.JSONObject;
import net.sf.json.JSONArray;
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

    private Map<String,Object> validateResult=new HashMap<>(); //检测结果json



    private Map<String,Object> pathDetail=new HashMap<>();

    private boolean hasPagePara = false;//是否有分页相关属性

    private boolean apiInServer=false;//域名中是否有“api”

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
    private List<String> querypara=new ArrayList<>();//过滤、条件限制、分页查询参数
    private List<List<String>> CRUDPathOperations=new ArrayList<>();//出现动词的路径使用的操作

    String contentType="";
    private boolean hasCacheScheme=false;//是否有缓存机制
    private boolean hasStrongCacheStatic=false;//是否有强制缓存机制cache-control、expires、date-静态检测
    private boolean hasEtagStatic=false;//是否有强制缓存机制cache-control、expires、date-静态检测
    boolean versionInQueryPara=false;//查询属性中是否有版本信息（版本信息不应该出现在查询属性中）
    private boolean versionInHead=false;//头文件中是否有版本信息
    private boolean securityInHeadPara=false;//头文件（属性）中是否有安全验证机制
    private boolean hasAccept=false;//头文件（属性）中是否有accept
    private boolean hasVersionInHost=false;//服务器信息/域名中是否有版本信息
    private Map<String,Integer> status=new HashMap<>();//状态码使用情况的统计
    private int[] statusUsage;//状态码使用情况（端点级别 是否使用各类状态码
    private int dotCountInServer;//server中版本号的.数，用来判断是否语义版本号
    private int dotCountInPath;//path中版本号的.数，用来判断是否语义版本号
    private boolean semanticVersion=false;//是否使用语义版本号
    private boolean hateoas=false;//是否实现HATEOAS原则
    private boolean hasResponseContentType=false;//响应头文件中是否有contenetType

    public boolean isHasResponseContentType() {
        return hasResponseContentType;
    }

    public boolean isHateoas() {
        return hateoas;
    }

    public boolean isSemanticVersion() {
        return semanticVersion;
    }

    public int getDotCountInServer() {
        return dotCountInServer;
    }

    public int getDotCountInPath() {
        return dotCountInPath;
    }

    public boolean isHasStrongCacheStatic() {
        return hasStrongCacheStatic;
    }

    public boolean isHasEtagStatic() {
        return hasEtagStatic;
    }

    public int[] getStatusUsage() {
        return statusUsage;
    }

    public Map<String, Integer> getStatus() {
        return status;
    }

    public boolean isApiInServer() {
        return apiInServer;
    }

    public boolean isHasVersionInHost() {
        return hasVersionInHost;
    }

    public boolean isHasAccept() {
        return hasAccept;
    }

    public boolean isHasCacheScheme() {
        return hasCacheScheme;
    }

    public boolean isVersionInQueryPara() {
        return versionInQueryPara;
    }

    public boolean isVersionInHead() {
        return versionInHead;
    }

    public boolean isSecurityInHeadPara() {
        return securityInHeadPara;
    }

    public Map<String, Object> getValidateResult() {
        return validateResult;
    }
    public Map<String, Object> getPathDetail() {
        return pathDetail;
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
    public void dynamicValidateByContent(String content) throws IOException, JSONException {
        JsonNode spec = readNode(content); //解析json/yaml格式，生成树结构
        String version = getVersion(spec);
        if (version != null && (version.startsWith("\"2") || version.startsWith("2"))){
            SwaggerDeserializationResult result = null;
            try {
                result = readSwagger(content);  //根据content构建swagger model
            } catch (Exception e) {
                LOGGER.debug("can't read Swagger contents", e);

                ProcessingMessage pm = new ProcessingMessage();
                pm.setLogLevel(LogLevel.ERROR);
                pm.setMessage("unable to parse Swagger: " + e.getMessage());
            }
            //动态检测，提取url

            List<Scheme> schemes = result.getSwagger().getSchemes();
            if(schemes==null){
                schemes.add(Scheme.HTTP);
            }
            String host=result.getSwagger().getHost()==null?"":result.getSwagger().getHost();
            String basepath=result.getSwagger().getBasePath()==null || result.getSwagger().getBasePath().equals("/")?"":result.getSwagger().getBasePath();
            Set paths = result.getSwagger().getPaths().keySet();
            for(Scheme scheme:schemes){
                for (Iterator it = paths.iterator(); it.hasNext(); ) {
                    String pathString = (String) it.next();
                    Path path=result.getSwagger().getPath(pathString);
                    Map<String,io.swagger.models.Operation> operations=getAllOperationsMapInAPath(path);
                    for(String method : operations.keySet()){//对于每一个操作,创建一个请求
                        if(operations.get(method)!=null){
                            io.swagger.models.Operation operation=operations.get(method);
                            Map<String,String> headers=new HashMap<>();//请求头文件
                            Map<String,Object> entity=new HashMap<>();//请求体

                            List<io.swagger.models.parameters.Parameter> parameters= operation.getParameters();
                            Map<String,String> queryParas=new HashMap<>();//查询参数
                            if(parameters!=null){
                                for(io.swagger.models.parameters.Parameter parameter:parameters){
                                    /*//Swagger解析时，RefParameter会直接连接到对应属性，并生成对应的实例
                                    if(parameter.getClass().getName()=="RefParameter"){
                                        RefParameter refpara=(RefParameter)parameter;
                                        String ref=refpara.get$ref();
                                    }*/
                                    if(parameter.getRequired()==true){//必需属性
                                        try {
                                            SerializableParameter spara = (SerializableParameter) parameter;//这个子类才能获取到类型、枚举等值,包括header、querty、path、cookie、Form属性

                                            String paraType=spara.getType();
                                            String paraName = parameter.getName();
                                            String paraValue="";//填充后的值
                                            String paraIn=parameter.getIn();

                                            //生成属性值
                                            List<String> paraEnum=spara.getEnum();
                                            if(paraEnum!=null){
                                                paraValue=paraEnum.get(0);
                                            }else {
                                                paraValue=getDefaultFromType(paraType).toString();
                                            }

                                            //根据属性位置给请求填充属性
                                            if(paraIn=="path") {//路径属性
                                                pathString=pathString.replace("{"+paraName+"}",paraValue);
                                            }else if(paraIn=="query"){//查询属性
                                                queryParas.put(paraName,paraValue);
                                                //pathString+="?"+paraName+"="+paraValue;
                                            }else if(paraIn=="header"){
                                                headers.put(paraName,paraValue);
                                            }else if(paraIn=="cookie"){
                                                headers.put("cookie",paraValue);
                                            }

                                        }catch (ClassCastException e){//消息体属性无法反射到SerializableParameter
                                            BodyParameter bodypara=(BodyParameter) parameter;
                                            if(bodypara.getExamples()!=null){//有例子直接使用例子值
                                                for(String k:bodypara.getExamples().keySet()){
                                                    entity.put(k,bodypara.getExamples().get(k));
                                                }
                                            }else if(bodypara.getSchema()!=null){//schema内容描述
                                                Map<String, Property> properties=bodypara.getSchema().getProperties();
                                                if(properties!=null){//schema中直接有描述
                                                    entity=parsePropertiesToEntity(properties);
                                                }else if(bodypara.getSchema().getReference()!=null){//引用schema
                                                    //从definition中获得描述信息
                                                    String ref=bodypara.getSchema().getReference();
                                                    String[] refsplits=ref.split("/");
                                                    if(refsplits[1].equals("definitions")){
                                                        Map<String, Model> defs=result.getSwagger().getDefinitions();
                                                        Model def=defs.get(refsplits[2]);
                                                        Map<String, Property> propertiesFromDef=def.getProperties();
                                                        entity=parsePropertiesToEntity(propertiesFromDef);
                                                    }
                                                }
                                            }
                                        }

                                    }
                                }
                            }
                            if(queryParas.size()!=0){//拼接查询属性到url中
                                String querPart="";
                                for(String paraname:queryParas.keySet()){
                                    querPart+=paraname+"="+queryParas.get(paraname)+"&";
                                }
                                querPart="?"+querPart;
                                querPart=querPart.substring(0,querPart.length()-1);
                                pathString+=querPart;
                            }
                            String url=scheme.toValue()+"://"+host+basepath+pathString;
                            //System.out.println(url);
                            Request request=new Request(method,url,headers,entity);
                            dynamicValidateByURL(request,false,false);
                        }
                    }


                }
            }
        }else if (version == null || (version.startsWith("\"3") || version.startsWith("3"))) {
            SwaggerParseResult result = null;
            try {
                result = readOpenApi(content);
            } catch (Exception e) {
                LOGGER.debug("can't read OpenAPI contents", e);

                ProcessingMessage pm = new ProcessingMessage();
                pm.setLogLevel(LogLevel.ERROR);
                pm.setMessage("unable to parse OpenAPI: " + e.getMessage());
            }
            Set paths = result.getOpenAPI().getPaths().keySet();
            //动态检测，获取URL
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
                    }else {
                        for (Iterator it = paths.iterator(); it.hasNext(); ) {
                            String path = (String) it.next();
                            String url = serverURL + path;
                            //dynamicValidateByURL(url, false, false);
                        }
                    }


                }
            }
        }
    }

    /**
     * 解析properties成为Map对象，最终这个Map会转成json作为请求消息体
     * @param properties objectProperty存在嵌套，Array、Map
     * @return
     */
    private Map<String, Object> parsePropertiesToEntity(Map<String, Property> properties) {
        Map<String, Object> result=new HashMap<>();
        for(String proName:properties.keySet()){
            Property pro=properties.get(proName);
            if(pro.getExample()!=null){//检查说明中有无例子，有的话直接使用例子
                result.put(proName,pro.getExample());
            }else{
                //根据property的类别进行值的生成
                if(pro.getType()=="object"/*pro.getClass().getName()=="ObjectProperty"*/){//只有ObjectProperty存在嵌套
                    ObjectProperty obPro=(ObjectProperty)pro;
                    result.put(proName,parsePropertiesToEntity(obPro.getProperties()));
                }else if(pro.getType()=="array"){
                    ArrayProperty arrPro=(ArrayProperty)pro;
                    String itemType=arrPro.getItems().getType();
                    List<Object> items=new ArrayList<>();
                    items.add(getDefaultFromType(itemType));//返回基本类型的默认值
                    result.put(proName,items);

                }else if(pro.getType()=="map"){
                    MapProperty mapPro=(MapProperty)pro;
                    String proType=mapPro.getAdditionalProperties().getType();
                    Map<String,Object> pros=new HashMap<>();
                    pros.put(proType,getDefaultFromType(proType));
                    result.put(proName,pros);
                }else{
                    result.put(proName,getDefaultFromType(pro.getType()));
                }
            }

        }
        return result;
    }

    /**
     * 根据基本类型返回默认值
     * @param itemType
     * @return
     */
    private Object getDefaultFromType(String itemType) {
        if(itemType=="string"){
            return "string";
        }else if(itemType=="integer"){
            return 0;
        }else if(itemType=="boolean"){
            return true;
        }else{
            return "default";
        }
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
                /*20201218 状态码统计实验，为加速实验，注释掉其他检测
                for (String message : result.getMessages()) {
                    output.addMessage(message);
                }
                validateResult.put("name",result.getSwagger().getInfo().getTitle());

                //路径（命名）检测
                Set paths = result.getSwagger().getPaths().keySet();
                pathlist= new ArrayList<>(paths);
                pathEvaluate(paths,result);
                //pathSemanticsEvaluate(paths);



                //安全解决方案
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
*/
                //域名检测
                String serverurl=result.getSwagger().getHost()+result.getSwagger().getBasePath();
                serverEvaluate(serverurl);
                validateResult.put("apiInServer",this.apiInServer);
                //属性研究,swagger解析出属性:全局属性，路径级别属性，操作级别属性（path-> operation -> parameter）
                List<io.swagger.models.parameters.Parameter> parameters= new ArrayList<>();
                Map<String, io.swagger.models.parameters.Parameter> parametersInSwagger = result.getSwagger().getParameters();//提取全局属性,加入到属性列表中
                if(parametersInSwagger!=null){
                    for(io.swagger.models.parameters.Parameter parameter:parametersInSwagger.values()){
                        parameters.add(parameter);
                    }
                }
                int opCount=0;
                int x2s=0,x3s=0,x4s=0,x5s=0;
                for(String pathName : result.getSwagger().getPaths().keySet()){
                    Path path = result.getSwagger().getPath(pathName);

                    if(path.getParameters()!=null) {
                        parameters.addAll(path.getParameters());//提取路径级别属性，加入属性列表中
                    }
                    //提取操作级别属性
                    List<io.swagger.models.Operation> operations=getAllOperationsInAPath(path);
                    for(io.swagger.models.Operation operation : operations){
                        boolean x2=false;
                        boolean x3=false;
                        boolean x4=false;
                        boolean x5=false;
                        //统计状态码使用情况
                        opCount++;
                        Map<String, io.swagger.models.Response> responses=operation.getResponses();
                        if(responses!=null){
                            for(String s:responses.keySet()){
                                if(s.startsWith("2")){
                                    x2=true;
                                }else if(s.startsWith("3")){
                                    x3=true;
                                }else if(s.startsWith("4")){
                                    x4=true;
                                }else if(s.startsWith("5")){
                                    x5=true;
                                }
                                if(status.containsKey(s)){
                                    status.put(s,status.get(s)+1);
                                }else{
                                    status.put(s,1);
                                }
                                io.swagger.models.Response response =responses.get(s);
                                if(response.getHeaders()!=null){
                                    for(String headerName:response.getHeaders().keySet()){
                                        headerName=headerName.toLowerCase();
                                        if(headerName.equals("cache-control") || headerName.equals("expires") || headerName.equals("date") ){

                                            hasStrongCacheStatic=true;
                                        }else if(headerName.equals("etag") || headerName.equals("last-modified")){
                                            hasEtagStatic=true;
                                        }else if(headerName.equals("content-type") ){
                                            this.hasResponseContentType=true;
                                        }
                                    }
                                }
                                Model responseSchema = response.getResponseSchema();
                                //检测是否实现hateoas原则
                                if(responseSchema!=null){
                                    Map<String, Property> properties = responseSchema.getProperties();
                                    for(String proname:properties.keySet()){
                                        if(proname.toLowerCase().contains("link")){
                                            this.hateoas=true;
                                        }
                                    }
                                }
                            }
                        }
                        x2s+=x2?1:0;
                        x3s+=x3?1:0;
                        x4s+=x4?1:0;
                        x5s+=x5?1:0;


                        //加入操作级别属性到属性列表中
                        if(operation.getParameters()!=null)
                            parameters.addAll(operation.getParameters());
                    }
                }
                /*
                //status.put("opcount",opCount);
                statusUsage= new int[]{opCount, x2s, x3s, x4s, x5s};

                if(parameters.size()!=0){
                    for(io.swagger.models.parameters.Parameter parameter:parameters){
                        if(parameter instanceof BodyParameter){

                        }
                        String paraName=parameter.getName().toLowerCase();
                        if(parameter.getIn().equals("query")){//查询属性
                            if(isPagePara(paraName)){//判断查询属性中是否有功能性属性
                                this.querypara.add(paraName);
                                setHasPagePara(true);
                                System.out.println(paraName+" is page parameter. ");
                            }else if(paraName.contains("version")){//版本信息
                                this.versionInQueryPara=true;
                                System.out.println("Query-parameter shouldn't has version parameter: "+paraName);
                            }
                        }else if(parameter.getIn().equals("header")){
                            if(parameter.getName().contains("version")){
                                this.versionInHead=true;
                            }else if(paraName.contains("key") || paraName.contains("token") || paraName.contains("authorization") ){
                                this.securityInHeadPara=true;
                            }else if(paraName.equals("accept")){
                                this.hasAccept=true;
                            }
                        }
                    }
                }*/
                validateResult.put("hasPagePara",isHasPagePara());
                validateResult.put("pageParaList",getQuerypara());
                validateResult.put("noVersionInQueryPara",!this.versionInQueryPara);
                validateResult.put("hasSecurityInHeadPara",this.securityInHeadPara);
                validateResult.put("hasVersionInHead",this.versionInHead);
                validateResult.put("hasAccpet",this.hasAccept);
                evaluations.put("hasPageParameter",String.valueOf(isHasPagePara()));

/*
                //类别信息获取
                setCategory(result);*/



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

                Components component = result.getOpenAPI().getComponents();
/*20201221 响应状态码实验，注释其他

                //类别信息获取
                setCategory(result);

                validateResult.put("name",result.getOpenAPI().getInfo().getTitle());
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

*/
                //域名检测
                List<Server> servers=result.getOpenAPI().getServers();
                if(servers!=null){
                    for(Server server:servers){
                        String serverurl=server.getUrl();
                        serverEvaluate(serverurl);

                    }
                }
                validateResult.put("apiInServer",this.apiInServer);

                //属性研究
                //openAPI完全按照说明文档进行解析，大部分属性信息在路径中
                List<Parameter> parameters=new ArrayList<>();
                if (component!=null) {
                    Map<String, Parameter> parametersInComponent = result.getOpenAPI().getComponents().getParameters();//全局属性
                    if (parametersInComponent != null) {
                        for (Parameter parameter : parametersInComponent.values()) {
                            parameters.add(parameter);//全局属性加入属性列表
                        }
                    }
                }

                int opCount=0;
                int x2s=0,x3s=0,x4s=0,x5s=0;
                for(String pathName : result.getOpenAPI().getPaths().keySet()){
                    //path-》operation-》parameters
                    if(result.getOpenAPI().getPaths().get(pathName).getParameters()!=null)
                        parameters.addAll(result.getOpenAPI().getPaths().get(pathName).getParameters());//路径级别属性加入属性列表
                    OpenAPIDeserializer deserializer = new OpenAPIDeserializer();
                    List<Operation> operationsInAPath = deserializer.getAllOperationsInAPath(result.getOpenAPI().getPaths().get(pathName));//获取所有操作
                    this.endpointNum+=operationsInAPath.size();//统计端点数

                    for(Operation operation:operationsInAPath){
                        boolean x2=false;
                        boolean x3=false;
                        boolean x4=false;
                        boolean x5=false;
                        opCount++;
                        //操作级别属性加入属性列表
                        if(operation.getParameters()!=null)
                            parameters.addAll(operation.getParameters());

                        if(operation.getResponses()!=null){
                            for(String s:operation.getResponses().keySet()){
                                if(s.startsWith("2")){
                                    x2=true;
                                }else if(s.startsWith("3")){
                                    x3=true;
                                }else if(s.startsWith("4")){
                                    x4=true;
                                }else if(s.startsWith("5")){
                                    x5=true;
                                }

                                if(status.containsKey(s)){
                                    status.put(s,status.get(s)+1);
                                }else{
                                    status.put(s,1);
                                }
                                ApiResponse response=operation.getResponses().get(s);
                                if(response.getLinks()!=null){//检测是否实现hateoas原则
                                    this.hateoas=true;
                                }
                                if(response.getHeaders()!=null){
                                    for(String headerName:response.getHeaders().keySet()){
                                        headerName=headerName.toLowerCase();
                                        if(headerName.equals("cache-control") || headerName.equals("expires") || headerName.equals("date") ){

                                            hasStrongCacheStatic=true;
                                        }else if(headerName.equals("etag") || headerName.equals("last-modified")){
                                            hasEtagStatic=true;
                                        }else if(headerName.equals("content-type") ){
                                            this.hasResponseContentType=true;
                                        }
                                    }
                                }
                            }
                        }
                        x2s+=x2?1:0;
                        x3s+=x3?1:0;
                        x4s+=x4?1:0;
                        x5s+=x5?1:0;
                    }
                }

                status.put("opcount",opCount);
                statusUsage= new int[]{opCount, x2s, x3s, x4s, x5s};
/*
                if(parameters.size()!=0){//对属性进行检测
                    for(Parameter parameter:parameters){
                        String paraName=parameter.getName().toLowerCase();
                        if( parameter.getIn().equals("query")){//查询属性
                            if(isPagePara(paraName)){//功能性属性
                                this.querypara.add(paraName);
                                setHasPagePara(true);
                                System.out.println(paraName+" is page parameter. ");
                            }else if(paraName.contains("version")){//版本信息
                                this.versionInQueryPara=true;
                                System.out.println("Query-parameter shouldn't has version parameter: "+paraName);
                            }
                        }else if(parameter.getIn().equals("header")){//头文件属性
                            if(paraName.contains("version")){
                                this.versionInHead=true;
                            }else if(paraName.contains("key") || paraName.contains("token") || paraName.contains("authorization") ){
                                this.securityInHeadPara=true;
                            }else if(paraName.equals("accept")){
                                this.hasAccept=true;
                            }
                        }
                    }

                }*/
                validateResult.put("hasPagePara",isHasPagePara());
                validateResult.put("pageParaList",getQuerypara());
                validateResult.put("noVersionInQueryPara",!this.versionInQueryPara);
                validateResult.put("hasSecurityInHeadPara",this.securityInHeadPara);
                validateResult.put("hasVersionInHead",this.versionInHead);
                validateResult.put("hasAccpet",this.hasAccept);


                
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
     * 检测域名内容（“api”以及版本信息）
     * @param serverurl 服务器信息（OAS3）或域名+baseurl（OAS2）
     */
    private void serverEvaluate(String serverurl) {
        Pattern pattern1 = Pattern.compile(ConfigManager.getInstance().getValue("VERSIONPATH_REGEX"));
        Matcher m1 = pattern1.matcher(serverurl); // 获取 matcher 对象
        Pattern pattern2=Pattern.compile("v(ers?|ersion)?[0-9.]+(-?(alpha|beta|rc)([0-9.]+\\+?[0-9]?|[0-9]?))?");
        Matcher m2=pattern2.matcher(serverurl);
        if(serverurl.contains("api")){//检测域名中包含“api”
            this.apiInServer=true;
            System.out.println(serverurl+"has api");
        }else if(m2.find()){
            //System.out.println("version shouldn't in paths "+p);
            //this.score=this.score-5>0?this.score-5:0;
            this.hasVersionInHost=true;
            String version=m2.group();
            /*int dotCount=0;
            for(int i=0;i<version.length();i++){
                if(version.charAt(i)=='.'){
                    dotCount++;
                }
            }
            this.dotCountInServer=dotCount;*/
            if(version.contains(".") || version.contains("alpha") || version.contains("beta") || version.contains("rc")){
                this.semanticVersion=true;
            }
        }
    }

    private void pathSemanticsEvaluate(Set paths) {
        for (Iterator it = paths.iterator(); it.hasNext(); ) {
            String p = (String) it.next();
            p=p.replace('/',' ');
        }
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
        validateResult.put("endpointNum",this.getEndpointNum());
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
        validateResult.put("endpointNum",this.getEndpointNum());
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
    private void headerEvaluate(String url, Header[] headers,Map<String,Object> pathResult) {
        //Map<String,Object> pathResult=new HashMap<>();
        boolean hasCacheScheme=false;
        boolean hasEtag=false;
        boolean hasCacheControl=false;
        boolean hasContentType=false;
        boolean hasLastModified=false;
        boolean hasExpires=false;
        String contentType="";
        for(Header header:headers){
            if(header.getName().equals("etag")){
                System.out.println(url+" response has etag");
                hasEtag=true;

            }else if(header.getName().equals("last-modified")){
                System.out.println(url+" response has last-modified");
                hasLastModified=true;
            }else if(header.getName().equals("expires")){
                System.out.println(url+" response has expires");
                hasExpires=true;
            }else if(header.getName().equals("cache-control")){
                System.out.println(url+" response has cache-control");
                hasCacheControl=true;
            }else if(header.getName().equals("content-type")){

                //evaluations.put("content-type",header.getValue());
                contentType=header.getValue();
                hasContentType=true;
                System.out.println(url+" response has content-type:"+contentType);
            }
        }
        hasCacheScheme=hasCacheControl || hasEtag || hasExpires || hasLastModified;
        /*evaluations.put("hasEtag",String.valueOf(hasEtag));
        evaluations.put("hasLastModified",String.valueOf(hasLastModified));
        evaluations.put("hasExpires",String.valueOf(hasExpires));
        evaluations.put("hasCacheControl",String.valueOf(hasCacheControl));
        evaluations.put("hasCacheScheme",String.valueOf(hasCacheScheme));
        evaluations.put("hasContentType",hasContentType==true?contentType:"false");*/
        pathResult.put("hasCacheScheme",hasCacheScheme);
        pathResult.put("hasEtag",hasEtag);
        pathResult.put("hasExpires",hasExpires);
        pathResult.put("hasLastModified",hasLastModified);
        pathResult.put("hasCacheControl",hasCacheControl);
        pathResult.put("hasContentType",hasContentType);
        pathResult.put("contentType",contentType);

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

    /**
     * 获取一个路径中的所有操作list
     * @param pathObj
     * @return
     */
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

    /**
     * 获取一个路径中的所有操作Map
     * @param pathObj
     * @return
     */
    public Map<String,io.swagger.models.Operation> getAllOperationsMapInAPath(Path pathObj) {
        Map<String,io.swagger.models.Operation> operations = new HashMap<>();
        operations.put("get",pathObj.getGet());
        operations.put("put",pathObj.getPut());
        operations.put("delete",pathObj.getDelete());
        operations.put("post",pathObj.getPost());
        operations.put("patch",pathObj.getPatch());
        operations.put("options",pathObj.getOptions());
        operations.put("head",pathObj.getHead());
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
    private void pathEvaluate(Set paths, SwaggerDeserializationResult result) throws IOException {
        //setPathNum(paths.size());//提取路径数
        evaluations.put("pathNum",Float.toString(getPathNum()));//向评估结果中填入路径数
        for (Iterator it = paths.iterator(); it.hasNext(); ) {
            Map<String,Object> pathResult=new HashMap<>();
            String p = (String) it.next();
            //evaluateToScore()
/*1228注释
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
            }*/

            Pattern pattern1 = Pattern.compile(ConfigManager.getInstance().getValue("VERSIONPATH_REGEX"));
            Matcher m1 = pattern1.matcher(p); // 获取 matcher 对象
            Pattern pattern2=Pattern.compile("v(ers?|ersion)?[0-9.]+(-?(alpha|beta|rc)([0-9.]+\\+?[0-9]?|[0-9]?))?");
            Matcher m2=pattern2.matcher(p);
            if(m2.find()){
                System.out.println("version shouldn't in paths "+p);
                //this.score=this.score-5>0?this.score-5:0;
                String version=m2.group();
                /*int dotCount=0;
                for(int i=0;i<version.length();i++){
                    if(version.charAt(i)=='.'){
                        dotCount++;
                    }
                }
                this.dotCountInPath=dotCount;*/
                if(version.contains(".") || version.contains("alpha") || version.contains("beta") || version.contains("rc")){
                    this.semanticVersion=true;
                }
                pathResult.put("noVersion",false);
            }else {
                this.pathEvaData[2]++;
                pathResult.put("noVersion",true);
            }
/*1228注释
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
                if (temp.contains(crudnames[i])) {
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
            //层级之间的语义上下文关系
            List<String> splitPaths;
            String pathText=pathclear.replace("/"," ");
            splitPaths=StanfordNLP.getlemma(pathText);//词形还原
            if(splitPaths.size()>=2){
                WordNet wordNet=new WordNet();
                wordNet.hasRelation(splitPaths);//检测是否具有上下文关系
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
            pathDetail.put(p,pathResult);*/

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
    private void pathEvaluate(Set paths, SwaggerParseResult result) throws IOException {
        //setPathNum(paths.size());//提取路径数
        evaluations.put("pathNum",Float.toString(getPathNum()));//向评估结果中填入路径数
        for (Iterator it = paths.iterator(); it.hasNext(); ) {
            Map<String,Object> pathResult=new HashMap<>();
            String p = (String) it.next();
            //evaluateToScore()
/*
1228注释
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
*/

            Pattern pattern1 = Pattern.compile(ConfigManager.getInstance().getValue("VERSIONPATH_REGEX"));
            Matcher m1 = pattern1.matcher(p); // 获取 matcher 对象
            Pattern pattern2=Pattern.compile("v(ers?|ersion)?[0-9.]+(-?(alpha|beta|rc)([0-9.]+\\+?[0-9]?|[0-9]?))?");
            Matcher m2=pattern2.matcher(p);
            if(m2.find()){
                System.out.println("version shouldn't in paths "+p);
                //this.score=this.score-5>0?this.score-5:0;
                String version=m2.group();
                /*int dotCount=0;
                for(int i=0;i<version.length();i++){
                    if(version.charAt(i)=='.'){
                        dotCount++;
                    }
                }
                this.dotCountInPath=dotCount;*/
                if(version.contains(".") || version.contains("alpha") || version.contains("beta") || version.contains("rc")){
                    this.semanticVersion=true;
                }
                pathResult.put("noVersion",false);
            }else {
                this.pathEvaData[2]++;
                pathResult.put("noVersion",true);
            }
/*1228注释
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
            //层级之间的语义上下文关系
            List<String> splitPaths;
            String pathText=pathclear.replace("/"," ");
            splitPaths=StanfordNLP.getlemma(pathText);//词形还原
            if(splitPaths.size()>=2){
                WordNet wordNet=new WordNet();
                wordNet.hasRelation(splitPaths);//检测是否具有上下文关系
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
            pathDetail.put(p,pathResult);*/
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
    *@Description: 动态检测指定url的响应内容
    *@Param: [urlString, rejectLocal, rejectRedirect]
    *@return: org.apache.http.Header[]
    *@Author: zhouxinyu
    *@date: 2020/5/18
    */
    public void dynamicValidateByURL(Request request, boolean rejectLocal, boolean rejectRedirect) throws IOException, JSONException {
        Map<String,Object> pathResult=new HashMap<>();
        String urlString=request.getUrl();
        System.out.println(urlString);
        if(urlString.contains("{")){
            return  ;
        }else {
            URL url = new URL(urlString);


            if (rejectLocal) {
                InetAddress inetAddress = InetAddress.getByName(url.getHost());
                if (inetAddress.isAnyLocalAddress() || inetAddress.isLoopbackAddress() || inetAddress.isLinkLocalAddress()) {
                    throw new IOException("Only accepts http/https protocol");
                }
            }


            RequestConfig.Builder requestBuilder = RequestConfig.custom();//设置配置信息
            requestBuilder = requestBuilder
                    .setConnectTimeout(5000)//连接超时时间
                    .setSocketTimeout(5000);//socket超时时间

            String method=request.getMethod();
            System.out.println(method);
            request.getHeader().put("authorization","token 7d3d79e8be31ca6a367b1920acf5bd3bbd119881");

            if(method=="get"){
                HttpGet httpRequest = new HttpGet(urlString);//创建get请求,此时父类A的变量和静态方法会将子类的变量和静态方法隐藏。instanceA此时唯一可能调用的子类B的地方就是子类B中覆盖了父类A中的实例方法。
                httpRequest.setConfig(requestBuilder.build());//将上面的配置信息运用到GET请求中
                if(request.getHeader()!=null){
                    for(String name:request.getHeader().keySet()){
                        httpRequest.setHeader(name,request.getHeader().get(name));
                    }
                }
                httpRequest.setHeader("Accept", "application/json, */*");//设置请求头文件
                final CloseableHttpClient httpClient = getCarelessHttpClient(rejectRedirect);//创建HTTP客户端
                if (httpClient != null) {
                    final CloseableHttpResponse response = httpClient.execute(httpRequest);
                    dynamicValidateByResponse(response,urlString,pathResult);
                    httpClient.close();
                } else {
                    throw new IOException("CloseableHttpClient could not be initialized");
                }
            }else if(method=="post"){
                HttpPost httpRequest=new HttpPost(urlString);
                httpRequest.setConfig(requestBuilder.build());//将上面的配置信息运用到GET请求中
                if(request.getHeader()!=null){
                    for(String name:request.getHeader().keySet()){
                        httpRequest.setHeader(name,request.getHeader().get(name));
                    }
                }
                httpRequest.setHeader("Accept", "application/json, */*");//设置请求头文件
                final CloseableHttpClient httpClient = getCarelessHttpClient(rejectRedirect);//创建HTTP客户端
                if (httpClient != null) {
                    final CloseableHttpResponse response = httpClient.execute(httpRequest);
                    dynamicValidateByResponse(response,urlString,pathResult);
                    httpClient.close();
                } else {
                    throw new IOException("CloseableHttpClient could not be initialized");
                }
                //设置消息体
                JSONObject jsonObject=JSONObject.fromObject(request.getEntity());
                String string = jsonObject.toString();
                System.out.println("entity: "+string);
                StringEntity entity = new StringEntity(string, "UTF-8");
                httpRequest.setEntity(entity);
            }else if(method=="put"){
                HttpPut httpRequest=new HttpPut(urlString);
                httpRequest.setConfig(requestBuilder.build());//将上面的配置信息运用到GET请求中
                if(request.getHeader()!=null){
                    for(String name:request.getHeader().keySet()){
                        httpRequest.setHeader(name,request.getHeader().get(name));
                    }
                }
                httpRequest.setHeader("Accept", "application/json, */*");//设置请求头文件
                final CloseableHttpClient httpClient = getCarelessHttpClient(rejectRedirect);//创建HTTP客户端
                if (httpClient != null) {
                    final CloseableHttpResponse response = httpClient.execute(httpRequest);
                    dynamicValidateByResponse(response,urlString,pathResult);
                    httpClient.close();
                } else {
                    throw new IOException("CloseableHttpClient could not be initialized");
                }
            }else if(method=="delete"){
                HttpDelete httpRequest=new HttpDelete(urlString);
                httpRequest.setConfig(requestBuilder.build());//将上面的配置信息运用到GET请求中
                if(request.getHeader()!=null){
                    for(String name:request.getHeader().keySet()){
                        httpRequest.setHeader(name,request.getHeader().get(name));
                    }
                }
                httpRequest.setHeader("Accept", "application/json, */*");//设置请求头文件
                final CloseableHttpClient httpClient = getCarelessHttpClient(rejectRedirect);//创建HTTP客户端
                if (httpClient != null) {
                    final CloseableHttpResponse response = httpClient.execute(httpRequest);
                    dynamicValidateByResponse(response,urlString,pathResult);
                    httpClient.close();
                } else {
                    throw new IOException("CloseableHttpClient could not be initialized");
                }
            }else if(method=="head"){
                HttpHead httpRequest=new HttpHead(urlString);
                httpRequest.setConfig(requestBuilder.build());//将上面的配置信息运用到GET请求中
                if(request.getHeader()!=null){
                    for(String name:request.getHeader().keySet()){
                        httpRequest.setHeader(name,request.getHeader().get(name));
                    }
                }
                httpRequest.setHeader("Accept", "application/json, */*");//设置请求头文件
                final CloseableHttpClient httpClient = getCarelessHttpClient(rejectRedirect);//创建HTTP客户端
                if (httpClient != null) {
                    final CloseableHttpResponse response = httpClient.execute(httpRequest);
                    dynamicValidateByResponse(response,urlString,pathResult);
                    httpClient.close();
                } else {
                    throw new IOException("CloseableHttpClient could not be initialized");
                }
            }else if(method=="patch"){
                HttpPatch httpRequest=new HttpPatch(urlString);
                httpRequest.setConfig(requestBuilder.build());//将上面的配置信息运用到GET请求中
                if(request.getHeader()!=null){
                    for(String name:request.getHeader().keySet()){
                        httpRequest.setHeader(name,request.getHeader().get(name));
                    }
                }
                httpRequest.setHeader("Accept", "application/json, */*");//设置请求头文件
                final CloseableHttpClient httpClient = getCarelessHttpClient(rejectRedirect);//创建HTTP客户端
                if (httpClient != null) {
                    final CloseableHttpResponse response = httpClient.execute(httpRequest);
                    dynamicValidateByResponse(response,urlString,pathResult);
                    httpClient.close();
                } else {
                    throw new IOException("CloseableHttpClient could not be initialized");
                }
            }else if(method=="options"){
                HttpOptions httpRequest=new HttpOptions(urlString);
                httpRequest.setConfig(requestBuilder.build());//将上面的配置信息运用到GET请求中
                if(request.getHeader()!=null){
                    for(String name:request.getHeader().keySet()){
                        httpRequest.setHeader(name,request.getHeader().get(name));
                    }
                }
                httpRequest.setHeader("Accept", "application/json, */*");//设置请求头文件
                final CloseableHttpClient httpClient = getCarelessHttpClient(rejectRedirect);//创建HTTP客户端
                if (httpClient != null) {
                    final CloseableHttpResponse response = httpClient.execute(httpRequest);
                    dynamicValidateByResponse(response,urlString,pathResult);
                    httpClient.close();
                } else {
                    throw new IOException("CloseableHttpClient could not be initialized");
                }
            }
            else{
                HttpGet httpRequest = new HttpGet(urlString);//创建get请求
                httpRequest.setConfig(requestBuilder.build());//将上面的配置信息运用到GET请求中
                if(request.getHeader()!=null){
                    for(String name:request.getHeader().keySet()){
                        httpRequest.setHeader(name,request.getHeader().get(name));
                    }
                }
                httpRequest.setHeader("Accept", "application/json, */*");//设置请求头文件
                final CloseableHttpClient httpClient = getCarelessHttpClient(rejectRedirect);//创建HTTP客户端
                if (httpClient != null) {
                    final CloseableHttpResponse response = httpClient.execute(httpRequest);
                    dynamicValidateByResponse(response,urlString,pathResult);
                    httpClient.close();
                } else {
                    throw new IOException("CloseableHttpClient could not be initialized");
                }
            }


        }
        this.pathDetail.put(urlString,pathResult);//各url的动态检测结果
        return;
    }

    private void dynamicValidateByResponse(CloseableHttpResponse response,String urlString,Map<String,Object> pathResult) throws IOException {
        try {


            StatusLine line = response.getStatusLine();
            System.out.println("response status: "+line.getStatusCode());
            if (line.getStatusCode() > 299 || line.getStatusCode() < 200) {//成功状态
                return ;
                //throw new IOException("failed to read swagger with code " + line.getStatusCode());
            }
            Header[] headers = response.getAllHeaders();//获取头文件
            if(headers!=null){
                headerEvaluate(urlString,headers,pathResult);//对头文件进行检测
                //System.out.println("changesuccess?"+pathResult.size());
            }

            HttpEntity entity = response.getEntity();//获取响应体
            if(entity!=null){
                entityEvaluate(urlString,entity,pathResult);//检测响应体
            }

            //return EntityUtils.toString(entity, "UTF-8");

        } catch (JSONException e) {
            e.printStackTrace();
        } finally {
            response.close();

        }
    }

    private void entityEvaluate(String urlString, HttpEntity entity,Map<String,Object> pathResult) throws IOException, JSONException {
        String entityString=EntityUtils.toString(entity);
        //System.out.println(entityString);
        Boolean isHATEOAS=false;
       if(entity.getContentType().getValue().contains("application/json")) {//判断响应体格式是否为json
           //JSONObject object = JSONObject.parseObject(entityString);

           //String[] keySet = null;
           //解析为jsonObject或jsonArray
           JSONObject entityObject=null;
            ArrayList<JSONObject> jsonArray=new ArrayList<JSONObject>();
           if(entityString.startsWith("{")) {
               try {
                   entityObject = JSONObject.fromObject(entityString);
                   //keySet=JSONObject.getNames(entityObject);
               }catch (JSONException e){
                   System.out.println(e.toString());
                   return;
               }

           }else if(entityString.startsWith("[")){
               try {
                   JSONArray entityArray = JSONArray.fromObject(entityString);
                   for (int i = 0; i < entityArray.size(); i++) {
                       JSONObject object = entityArray.getJSONObject(i);
                       jsonArray.add(object);
                       //keySet = JSONObject.getNames(object);
                   }
               }catch (JSONException e){
                   System.out.println(e.toString());
                   return;
               }
           }
           //检测是否实现HATEOAS原则，即响应体中是否含有link
           if(entityObject!=null){
               for(Object key:entityObject.keySet()){
                   if(key.toString().contains("link")){
                       isHATEOAS=true;
                       System.out.println(urlString+" has HATEOAS "+key+":"+entityObject.getString(key.toString()));
                   }
               }
           }else if(jsonArray.size()!=0){
               for(JSONObject entityjson:jsonArray){
                   for(Object key:entityjson.keySet()){
                       if(key.toString().contains("link")){
                           isHATEOAS=true;
                           System.out.println(urlString+"has HATEOAS "+key+":"+entityjson.getString(key.toString()));
                       }
                   }
               }
           }

           //System.out.println("entityKeyset"+keySet.toString());
       }
       pathResult.put("isHATEOAS",isHATEOAS);
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
    public String getUrlContents(String urlString, boolean rejectLocal, boolean rejectRedirect) throws IOException {
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
