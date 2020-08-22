package com.rest.servlet;

import io.swagger.handler.ValidatorController;
import io.swagger.oas.inflector.models.RequestContext;

import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

//@WebServlet(name = "HelloServlet",urlPatterns = "/hello")
public class ValidateServlet extends javax.servlet.http.HttpServlet {
    protected void doPost(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response) throws javax.servlet.ServletException, IOException {
        // 设置request的编码
        request.setCharacterEncoding("UTF-8");
        // 获取信息
        String url = request.getParameter("url");
        String context = request.getParameter("context");
        //System.out.println(url);
        //System.out.println(context);
        ValidatorController validator = new ValidatorController();
        String pathNum="";
        String pathList="";
        String endpointNum="";
        String avgHierarchy="";
        List<List<String>> CRUDPathOperations=new ArrayList<>();
        List<String> CRUDList=new ArrayList<>();
        if(context!=null){
            validator.validateByString(new RequestContext(), context);
            pathNum=Float.toString(validator.getPathNum());
            pathList=validator.getPathlist().toString();
            avgHierarchy=Float.toString(validator.getAvgHierarchy());
            endpointNum=Float.toString(validator.getEndpointNum());
            CRUDPathOperations=validator.getCRUDPathOperations();
            CRUDList=validator.getCRUDlist();
        }
        System.out.println("list"+CRUDList);
        // 设置response的编码
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
        out.close();
    }

    protected void doGet(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response) throws javax.servlet.ServletException, IOException {
        PrintWriter out = response.getWriter();
        out.println("<h1>hello servlet</h1>");
    }
}
