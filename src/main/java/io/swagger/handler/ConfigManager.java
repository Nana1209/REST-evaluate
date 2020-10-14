package io.swagger.handler;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
public class ConfigManager {
    Properties properties=new Properties();
    InputStream is=null;
    private static ConfigManager configManager=null;
    private ConfigManager(){
        try{
            is=ConfigManager.class.getClassLoader().getResourceAsStream("globalrule.properties");
            properties.load(is);
            is.close();
        }catch (IOException e){
            e.printStackTrace();
        }
    }
    public synchronized static ConfigManager getInstance(){
        if(configManager==null){
            configManager=new ConfigManager();
        }
        return configManager;
    }
    public String getValue(String key){
        return properties.getProperty(key);
    }
}
