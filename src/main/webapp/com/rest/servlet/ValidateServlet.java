package com.rest.servlet;

import io.swagger.handler.ConfigManager;
import io.swagger.handler.ValidatorController;
import io.swagger.handler.fileHandle;
import io.swagger.oas.inflector.models.RequestContext;
import org.json.JSONException;
import org.json.JSONObject;

//import javax.servlet.annotation.WebServlet;
import java.io.IOException;
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

        String categoryResult[]= ConfigManager.getInstance().getValue(category.toUpperCase()).split(",",-1);

        System.out.println(categoryResult);
        //System.out.println(url);
        //System.out.println("context"+context);
        ValidatorController validator = new ValidatorController();
        Map<String, Object> result=null;
        if(context!=null){
            validator.validateByString(new RequestContext(), context);
            result=validator.getValidateResult();
        }
        JSONObject object = new JSONObject();
        try {
            fileHandle.MaptoJsonObj(result,object);
            object.put("categoryResult",categoryResult);
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
