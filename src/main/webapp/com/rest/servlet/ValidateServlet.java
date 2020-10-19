package com.rest.servlet;
import com.google.common.primitives.Bytes;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import io.swagger.handler.ConfigManager;
import io.swagger.handler.ValidatorController;
import io.swagger.handler.fileHandle;
import io.swagger.oas.inflector.models.RequestContext;
import org.json.JSONException;
import org.json.JSONObject;

//import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

//@WebServlet(name = "HelloServlet",urlPatterns = "/hello")
public class ValidateServlet extends javax.servlet.http.HttpServlet {
    protected void doPost(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response) throws javax.servlet.ServletException, IOException {
        // 设置request的编码
        request.setCharacterEncoding("UTF-8");
        // 获取信息
        String url = request.getParameter("url");
        String context = request.getParameter("context");
        String category = request.getParameter("category");
        //ServletInputStream serIn=request.getInputStream();
        ValidatorController validator = new ValidatorController();
        if(context ==null) {
            if(url!=null){
                context=validator.getUrlContents(url, false, false); //获取url提供的swagger文档，返回响应entity
            }else {

                try {
                    //创建DiskFileItemFactory工厂对象
                    DiskFileItemFactory factory = new DiskFileItemFactory();
                    ServletFileUpload fileUpload = new ServletFileUpload(factory);


                    fileUpload.setHeaderEncoding("utf-8");
                    //            解析request，将form表单的各个字段封装为FileItem对象

                    List<FileItem> fileItems = fileUpload.parseRequest(request);
                    //获取上传文件流
                    for (FileItem fileItem : fileItems) {
                        if (fileItem.isFormField() == false) {
                            InputStream in = fileItem.getInputStream();
                            List<Byte> b = new ArrayList<Byte>();
                            //byte b[] = new byte[10240];
                            int len = 0;
                            int temp = 0;          //所有读取的内容都使用temp接收
                            while ((temp = in.read()) != -1) {    //当没有读取完时，继续读取
                                //b[len]=(byte)temp;
                                b.add((byte) temp);
                                len++;
                            }
                            byte[] bb = Bytes.toArray(b);
                            context = new String(bb, 0, len);
                            //System.out.println(context);
                            in.close();
                        } else {
                            //break;
                            String name = fileItem.getFieldName();
                            if (name.equals("category")) {
//                        如果字段值不为空
                                if (!fileItem.getString().equals("")) {
                                    category = fileItem.getString("utf-8");
                                }
                            }
                        }
                    }
                } catch (FileUploadException e) {
                    e.printStackTrace();
                }
            }
        }

        String categoryResult[]=null;
        if(category!=null){
            categoryResult= ConfigManager.getInstance().getValue(category.toUpperCase()).split(",",-1);
            System.out.println(categoryResult);
        }

        //System.out.println(url);
        //System.out.println("context"+context);

        Map<String, Object> result=null;
        if(context!=null){
            validator.validateByString(new RequestContext(), context);
            result=validator.getValidateResult();
        }
        JSONObject object = new JSONObject();
        try {
            fileHandle.MaptoJsonObj(result,object);
            if(categoryResult!=null){
                object.put("categoryResult",categoryResult);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
        System.out.println(object);
        response.getWriter().print(object);

        /*// 设置response的编码
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/html");
        // 获取PrintWriter对象
        PrintWriter out = response.getWriter();
        // 输出信息
        out.println("<HTML>");
        out.println("<HEAD><TITLE>登录信息</TITLE></HEAD>");
        out.println("<BODY>");
        out.println("路径数：" + pathNum + "<br>");
        out.println("路径：" + pathList + "<br>");
        out.println("端点数：" +endpointNum + "<br>");
        out.println("路径平均层级数：" + avgHierarchy + "<br>");
        out.println("动词：" + CRUDList.toString() + "<br>");
        out.println("</BODY>");
        out.println("</HTML>");
        // 释放PrintWriter对象
        out.flush();
        out.close();*/
    }

    protected void doGet(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response) throws javax.servlet.ServletException, IOException {
        JSONObject object = new JSONObject();
        try {
            object.put("name", "tom");
            object.put("age", 15);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        System.out.println(object);
        response.getWriter().print(object);
    }
}
