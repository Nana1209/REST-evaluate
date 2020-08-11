package io.swagger.models;

public interface global {
    String PAGEPARANAMES[]={"limit", "offset","page", "range","pagesize","pagestartindex","before","after"};
    String VERSIONPATH_REGEX ="v(ers?|ersion)?[0-9.]+";
    String CRUDNAMES[]={"get",  "create","add","update","put","post", "remove","delete", "new",  "set","push", "read","drop" ,"modify"   };//根据统计结果排序
    String DELLIST[][]={
            {"target","budget","widget","gadget"},
            {},
            {"address","addon","addition"},
            {"updates"},
            {"compute","output","input","reputation","dispute"},
            {"postal","posts","postgresql"},
            {},
            {"deleted"},
            {"news","renewal"},
            {"setting","setup","asset","settle","setlist","sets","dataset","preset"},
            {},
            {"thread","readme","spread","readouts","reader"},
            {"dropped"},{}};
    String SUFFIX_NAMES[]={ ".json", ".html", ".js",".php",".xml",".gif",".jpg", ".txt",".png",    ".java", ".jsp",  ".asp"};

}
