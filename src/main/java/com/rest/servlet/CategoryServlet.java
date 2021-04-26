package com.rest.servlet;

import io.swagger.handler.ConfigManager;
import org.json.JSONException;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

public class CategoryServlet extends HttpServlet {
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
// 设置request的编码
        request.setCharacterEncoding("UTF-8");
        // 获取信息
        String category = request.getParameter("category");
        System.out.println(category);
        String categoryResult[]=null;
        if(category!=null){
            categoryResult= ConfigManager.getInstance().getValue(category.toUpperCase()).split(",",-1);
            System.out.println(categoryResult);
        }

        //System.out.println(url);
        //System.out.println("context"+context);

        Map<String, Object> result=null;
        JSONObject object = new JSONObject();
        try {
            //fileHandle.MaptoJsonObj(result,object);
            if(categoryResult!=null){
                object.put("categoryResult",categoryResult);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
        System.out.println(object);
        response.getWriter().print(object);
    }
}
