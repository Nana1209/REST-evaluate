package io.swagger.models;

import io.swagger.handler.ValidatorController;
import io.swagger.handler.fileHandle;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class HSModel extends RESTModel{
    public HSModel(String html) throws IOException {
        //解析api html
        Document document = Jsoup.parse(html);
        Element docTit = document.getElementsByClass("docTit").get(0);
        name=docTit.text();
        Element api_table=document.getElementsByClass("api_table").get(0);//接口列表
        Elements trs=api_table.getElementsByTag("tr");
        paths=new HashMap<>();
        baseURLs=new ArrayList<>();
        for(int j=1;j<trs.size();j++){
            //获取每个端点的html
            Element tr=trs.get(j);
            String url=tr.getElementsByTag("td").get(1).getElementsByTag("a").attr("href");
            url="https://www.hs.net"+url;
            String endpointHtml= ValidatorController.getUrlContents(url);
            //解析端点html
            Document epdocument = Jsoup.parse(endpointHtml);
            Elements apiTables = epdocument.getElementsByClass("api_table");

            PathRESTer path=new PathRESTer();
            List<OperationRESTer> operations=new ArrayList<>();
            OperationRESTer operation=new OperationRESTer();
            ResponseRESTer response=new ResponseRESTer();
            response.status="200";
            String supportType="";
            List<ParameterRESTer> reqParameters=new ArrayList<>();//请求参数
            List<ResponseRESTer> responses=new ArrayList<>();//响应
            List<ParameterRESTer> resParameters=new ArrayList<>();//响应参数
            //解析端点的组件
            for(int i=0;i<apiTables.size();i++){
                Element apiTable=apiTables.get(i);
                switch (i){
                    case 0://解析server
                        Elements servers=apiTable.getElementsByTag("tr");
                        for(Element server :servers){
                            Element td=server.getElementsByTag("td").get(1);
                            baseURLs.add(td.text());
                        }
                        break;
                    case 1://解析path
                        String epurl=apiTable.text();
                        String pathname=epurl.substring(baseURLs.get(0).length());
                        path.pathName=pathname;
                        break;
                    case 2:
                        break;
                    case 3:
                        operation.method=apiTable.text();
                        break;
                    case 4:
                        supportType=apiTable.text();
                        break;
                    case 5:
                        Elements trss=apiTable.getElementsByTag("tr");
                        for(int k=1;k<trss.size();k++){//遍历解析每一个请求参数
                            Elements para=trss.get(k).getElementsByTag("td");
                            ParameterRESTer parameterRESTer=new ParameterRESTer();
                            parameterRESTer.name=para.get(0).text();
                            parameterRESTer.required=para.get(2).text().equals("否")?false:true;
                            parameterRESTer.in="header";
                            reqParameters.add(parameterRESTer);
                        }
                        break;
                    case 6:
                        Elements trsres=apiTable.getElementsByTag("tr");
                        for(int k=1;k<trsres.size();k++){//遍历解析每一个响应参数
                            Elements para=trsres.get(k).getElementsByTag("td");
                            ParameterRESTer parameterRESTer=new ParameterRESTer();
                            parameterRESTer.name=para.get(0).text();
                            parameterRESTer.in="header";
                            resParameters.add(parameterRESTer);
                        }
                        break;
                }
            }
            Elements codes = epdocument.getElementsByClass("code");
            String resExample=codes.get(1).text();
            List<String > examples=new ArrayList<>();
            examples.add(resExample);
            operation.parameters=reqParameters;
            response.headers=resParameters;
            response.examples=examples;
            responses.add(response);
            operation.responses=responses;
            operations.add(operation);
            path.operations=operations;
            paths.put(path.pathName,path);
        }
    }
}
