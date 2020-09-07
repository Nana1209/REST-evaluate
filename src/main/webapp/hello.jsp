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
<div align="center" id="showForm">
    <form method="post" action="/restapi" id="ajaxForm">
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
                    <input type="submit" value="检测"/>
                    <input type="reset" value="重置"/>
                </td>
            </tr>
        </table>
    </form>
</div>
<script src="https://cdn.bootcss.com/jquery/2.1.4/jquery.min.js"></script>
<script type="text/javascript">
    $(      //页面加载完执行
        $("#ajaxForm").on("submit",function () {    //表单提交时监听提交事件
            $(this).ajaxSubmit(options);    //当前表单执行异步提交，optons 是配置提交时、提交后的相关选项
            return false;   //  必须返回false，才能跳到想要的页面
        })
    )
    //配置 options 选项
    var options = {
        url: "/restapi",       //提交地址：默认是form的action,如果申明,则会覆盖
        type: "post",           //默认是form的method（get or post），如果申明，则会覆盖
        success: successFun,    //提交成功后的回调函数，即成功后可以定页面跳到哪里
        dataType: "json",       //接受服务端返回的类型
        clearForm: true,        //成功提交后，清除所有表单元素的值
        resetForm: true,        //成功提交后，重置所有表单元素的值
        timeout: 3000           //设置请求时间，超过该时间后，自动退出请求，单位(毫秒)
    }
    //设置提交成功后返回的页面
    function successFun(data,status) {
        $("#showForm").html(data);      //提交表单后从后台接收到的返回来的数据，我保存到了msg里
        // $("#showForm").html("或者这里可以直接写想要显示的内容")
    }
</script>
</body>

</html>
