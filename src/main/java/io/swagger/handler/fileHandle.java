package io.swagger.handler;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.oas.inflector.models.RequestContext;
import io.swagger.oas.inflector.models.ResponseContext;
import io.swagger.util.Json;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
public class fileHandle {
    private List<List<String>> basicInfos=new ArrayList<>();//name,path,endpoint
    private List<List<String>> hierarchys=new ArrayList<>();//name,hierarchy..
    private List<List<String>> validations=new ArrayList<>();//name,no_,lowcase,nosuffix,noCRUD,noAPI,noversion,noend/
    private List<List<String>> categories=new ArrayList<>();//name,category

    public static void main(String[] args) throws Exception {
        //    在此目录中找文件
        //String baseDIR = "D:\\REST API\\openapi-directory-master\\APIs";
        //    找扩展名为txt的文件
        //String fileName = "openapi.yaml";

        //File imagFile = findFiles(baseDIR, fileName);
        //System.out.println(imagFile.getPath());
        fileHandle test=new fileHandle();
        test.validateFiles("E:\\test\\openapi-versionClear");
        return ;
    }

    /**
     * 递归查找文件
     * @param baseDirName  查找的文件夹路径
     * @param targetFileName  需要查找的文件名
     */
    public static File findFiles(String baseDirName, String targetFileName) throws IOException {

        File baseDir = new File(baseDirName);		// 创建一个File对象
        if (!baseDir.exists() || !baseDir.isDirectory()) {	// 判断目录是否存在
            System.out.println("文件查找失败：" + baseDirName + "不是一个目录！");
        }
        String tempName = null;
        //判断目录是否存在
        File tempFile;
        File[] files = baseDir.listFiles();
        for (int i = 0; i < files.length; i++) {
            tempFile = files[i];
            if(tempFile.isDirectory()){
                findFiles(tempFile.getAbsolutePath(), targetFileName);
            }else if(tempFile.isFile()){
                tempName = tempFile.getName();
                if(tempName.equals(targetFileName)){
                    System.out.println("find!"+tempFile.getAbsoluteFile().toString());
                    String pathname="E:\\test\\openapi\\"+tempFile.getAbsolutePath().toString().replace('\\','-').substring(42);
                    File dest=new File(pathname);
                    Files.copy(tempFile.getAbsoluteFile().toPath(),dest.toPath());
                    return tempFile.getAbsoluteFile();
                }
            }
        }
        return null;
    }

    public boolean resultAnalysis(String filePath) throws IOException {
        boolean result=false;
        File baseDir = new File(filePath);		// 创建一个File对象
        if (!baseDir.exists() || !baseDir.isDirectory()) {	// 判断目录是否存在
            System.out.println("文件查找失败：" + filePath + "不是一个目录！");
            return false;
        }
        List<String> files = new ArrayList<String>();
        File file = new File(filePath);
        File[] fileList = file.listFiles();

        for (int i = 0; i < fileList.length; i++) {
            if (fileList[i].isFile()) {
                JsonNode jsonNode= Json.mapper().readTree(fileList[i].toString());
                jsonNode.get("pathNum");
                files.add(fileList[i].toString());
                //文件名，不包含路径
                //String fileName = tempList[i].getName();
            }
        }

        return result;
    }

    /**
    *@Description: 检验文件夹内说明文件
    *@Param: [pathName] 检验指定文件夹
    *@return: void
    *@Author: zhouxinyu
    *@date: 2020/6/22
    */
    public void validateFiles(String pathName) throws Exception {
        //File file = new File("E:\\test\\openapi");
        File file = new File(pathName);
        File[] tempList = file.listFiles();
        for(File f : tempList){
            ValidatorController validator = new ValidatorController();
            String path=f.getPath();
            String name=f.getName();
            String content=validator.readFile(path);
            System.out.println(name+" start!");
            ResponseContext response = validator.validateByString(new RequestContext(), content);
            //ResponseContext response = validator.validateByUrl(new RequestContext(), url);
            /*基本信息（路径、端点、get，post，delete，put，head，patch）*/
            List<String> basicInfo=new ArrayList<>();
            basicInfo.add(name);
            basicInfo.add(Float.toString(validator.getPathNum()));
            basicInfo.add(Float.toString(validator.getEndpointNum()));
            basicInfo.add(Float.toString(validator.getOpGet()));
            basicInfo.add(Float.toString(validator.getOpPost()));
            basicInfo.add(Float.toString(validator.getOpDelete()));
            basicInfo.add(Float.toString(validator.getOpPut()));
            basicInfo.add(Float.toString(validator.getOpHead()));
            basicInfo.add(Float.toString(validator.getOpPatch()));
            basicInfo.add(Float.toString(validator.getOpOptions()));
            basicInfo.add(Float.toString(validator.getOpTrace()));
            System.out.println(basicInfo.toString());
            basicInfos.add(basicInfo);

            /*层级信息*/
            /*List<String> hierarchyInfo=new ArrayList<>();
            hierarchyInfo.add(name);
            hierarchyInfo.addAll(validator.getHierarchies());
            hierarchys.add(hierarchyInfo);*/
            //System.out.println(validator.evaluations.toString());

            /*命名验证结果*/
            /*List<String> validation=new ArrayList<>();
            validation.add(name);
            validation.add(validator.evaluations.get("noUnderscoreRate"));
            validation.add(validator.evaluations.get("lowcaseRate"));
            validation.add(validator.evaluations.get("noSuffixRate"));
            validation.add(validator.evaluations.get("noCRUDRate"));
            validation.add(validator.evaluations.get("noapiRate"));
            validation.add(validator.evaluations.get("noVersionRate"));
            validation.add(validator.evaluations.get("noEndSlashRate"));
            validations.add(validation);*/

            //validator.resultToFile(name);

            //类别信息统计
            /*List<String> cate=new ArrayList<>();
            if(validator.getCategory()!=null){
                cate.add(name);
                cate.add(validator.getCategory());
                categories.add(cate);
            }*/

            System.out.println(name+" end!");

        }
        //基本信息（路径、端点）
        createCSVFile(basicInfos,"result","basicInfo-openAPIv3.0");
        //层级信息
        //createCSVFile(hierarchys,"result","hierarchy-openAPIv2.0");
        //命名验证结果
        //createCSVFile(validations,"result","validationRate-openAPIv2.0");
        //类别信息x-apisguru-categories
        //createCSVFile(categories,"result","category-openAPIv2.0");


    }

    public File createCSVFile(List<List<String>> exportData, String outPutPath, String fileName) {
        File csvFile = null;
        BufferedWriter csvFileOutputStream = null;
        try {
            File file = new File(outPutPath);
            if (!file.exists()) {
                if (file.mkdirs()) {
                    System.out.println("创建成功");
                } else {
                    System.out.println("创建失败");
                }
            }
            //定义文件名格式并创建
            csvFile = File.createTempFile(fileName, ".csv", new File(outPutPath));
            csvFileOutputStream = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(csvFile), StandardCharsets.UTF_8), 1024);
            for (List<String> exportDatum : exportData) {
                writeRow(exportDatum, csvFileOutputStream);
                csvFileOutputStream.newLine();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (csvFileOutputStream != null) {
                    csvFileOutputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return csvFile;
    }
    /**
    *@Description: 写一行进csv文件
    *@Param: [row, csvWriter]
    *@return: void
    *@Author: zhouxinyu
    *@date: 2020/6/22
    */
    private void writeRow(List<String> row, BufferedWriter csvWriter) throws IOException {
        int i=0;
        for (String data : row) {
            //csvWriter.write(DelQuota(data));
            csvWriter.write(data);
            if (i!=row.size()-1){
                csvWriter.write(",");
            }
            i++;
        }
    }
    /**
    *@Description: 剔除特殊字符
    *@Param: [str]
    *@return: java.lang.String
    *@Author: zhouxinyu
    *@date: 2020/6/22
    */
    public String DelQuota(String str) {
        String result = str;
        String[] strQuota = {"~", "!", "@", "#", "$", "%", "^", "&", "*", "(", ")", "`", ";", "'", ",", ".", "/", ":", "/,", "<", ">", "?"};
        for (int i = 0; i < strQuota.length; i++) {
            if (result.indexOf(strQuota[i]) > -1)
                result = result.replace(strQuota[i], "");
        }
        return result;
    }
}
