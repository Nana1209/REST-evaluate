<%--
  Created by IntelliJ IDEA.
  User: Administrator
  Date: 2020/8/17
  Time: 19:48
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
    <title>Title</title>
</head>
<body>
<div align="center">
    <form method="post" action="/swagger-validator/restapi">
        <table>
            <tr>
                <td>API url：</td>
                <td><input type="text" name="url"/></td>
            </tr>
            <tr>
                <td>API context：</td>
                <td><textarea name="context"></textarea></td>
            </tr>


            <tr>
                <td></td>
                <td>
                    <input type="submit" value="登录"/>
                    <input type="reset" value="重置"/>
                </td>
            </tr>
        </table>
    </form>
</div>
</body>
</html>
