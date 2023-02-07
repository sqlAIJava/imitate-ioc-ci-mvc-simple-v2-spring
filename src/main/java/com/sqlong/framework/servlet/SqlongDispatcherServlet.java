package com.sqlong.framework.servlet;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

public class SqlongDispatcherServlet extends HttpServlet {

    // 定义配置文件
    private Properties contextConfig = new Properties();

    private List<String> classNames = new ArrayList<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // 6、委派处理请求, URL 》 Method 》 Response
        doDispatch();
    }

    private void doDispatch() {
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        // 1、加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));

        // 2、扫描相关类
        doScanner(contextConfig.getProperty("scanPackage"));

        // 3、初始化Ioc容器，将扫描到相关的类示例化，保存到Ioc容器中
        doInstance();

        // 暂时忽略AOP

        // 4、完成依赖注入DI
        doAutowired();

        // 5、初始化HandMapping MVC
        doInitHandMapping();

        System.out.println("Sqlong Servlet Init OK");
    }

    private void doInitHandMapping() {
    }

    private void doAutowired() {
    }

    private void doInstance() {
    }

    private void doScanner(String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));
        File classPath = new File(url.getFile());

        for (File file : classPath.listFiles()) {
            if (file.isDirectory()) {
                // 是文件夹
                doScanner(scanPackage + "." + file.getName());
            } else {
                // 文件类名file.getName()可能会重复，所以加上包名
                String className = scanPackage + "." + file.getName().replace(".class", "");
                classNames.add(className);
            }
        }

    }

    private void doLoadConfig(String contextConfigLocation) {
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            contextConfig.load(inputStream);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
