package com.sqlong.framework.servlet;

import com.sqlong.framework.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class SqlongDispatcherServlet extends HttpServlet {

    // 定义配置文件
    private Properties contextConfig = new Properties();

    private List<String> classNames = new ArrayList<>();

    // IoC容器
    private Map<String, Object> ioc = new HashMap<>();

    // MVC映射容器
    private Map<String, Method> handleMapping = new HashMap<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // 6、委派处理请求, URL 》 Method 》 Response
        try {
            doDispatch(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 Exception, Detail :" + Arrays.toString(e.getStackTrace()));
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        // 请求url 绝对路径
        String url = req.getRequestURI();
        //
        String contextPath = req.getContextPath();
        // 相对路径
        url = url.replaceAll(contextPath, "").replaceAll("/+", "/");
        if (!this.handleMapping.containsKey(url)) {
            resp.getWriter().write("404 Not Found!!!");
            return;
        }

        // 请求参数
        Map<String, String[]> params = req.getParameterMap();

        // MVC容器映射方法
        Method method = this.handleMapping.get(url);

        // 反射拿方法所在的类名
        String beanName = toLowerFirstCase(method.getDeclaringClass().getSimpleName());

        // 形参列表
        Class<?>[] parameterTypes = method.getParameterTypes();
        // 实参列表
        Object[] paramValues = new Object[parameterTypes.length];

        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            if (parameterType == HttpServletRequest.class) {
                paramValues[i] = req;
            } else if (parameterType == HttpServletResponse.class) {
                paramValues[i] = resp;
            } else if (parameterType == String.class) {
                // 参数注解无法从参数类型（非运行时的）getAnnotation，需要从方法（运行时的）getParameterAnnotations();
                // SqlongRequestParam sqlongRequestParam = (SqlongRequestParam) parameterType.getAnnotation(SqlongRequestParam.class);
                Annotation[][] parameterAnnotations = method.getParameterAnnotations();
                for (int j = 0; j < parameterAnnotations.length; j++) {
                    for(Annotation a : parameterAnnotations[j]) {
                        if (a instanceof SqlongRequestParam) {
                            String paramName = ((SqlongRequestParam) a).value().trim();
                            if (!"".equals(paramName)) {
                                String paramValue = Arrays.toString(params.get(paramName))
                                        .replaceAll("\\[|\\]", "")
                                        .replaceAll("\\s", ",");
                                paramValues[i] = paramValue;
                            }
                        }
                    }
                }
            }
        }

        // 执行方法
        method.invoke(ioc.get(beanName), paramValues);
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

        System.out.println("Sqlong Servlet Init OK ================================");
    }

    private void doInitHandMapping() {
        if (ioc.isEmpty()) return;

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();

            if (!clazz.isAnnotationPresent(SqlongController.class)) continue;

            String baseUrl = "";
            if (clazz.isAnnotationPresent(SqlongRequestMapping.class)) {
                baseUrl = clazz.getAnnotation(SqlongRequestMapping.class).value().trim();
            }

            // 只取Public的方法
            for (Method method : clazz.getMethods()) {
                if (!method.isAnnotationPresent(SqlongRequestMapping.class)) continue;
                // 正则解释器模式
                String url = ("/" + baseUrl + "/" + method.getAnnotation(SqlongRequestMapping.class).value().trim()).replaceAll("/+", "/");
                handleMapping.put(url, method);
                System.out.println("Mapped: " + url + ", " + method);
            }
        }

    }

    private void doAutowired() {
        if (ioc.isEmpty()) return;

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            // 一个类的所有属性
            for (Field field : entry.getValue().getClass().getDeclaredFields()) {
                if (!field.isAnnotationPresent(SqlongAutowired.class)) continue;

                // 注解上给的值
                String beanName = field.getAnnotation(SqlongAutowired.class).value().trim();

                // 没有给值，就直接拿类型名，试用于 Impl + Service
//                if ("".equals(beanName)) {
//                    beanName = field.getType().getName();
//                }

                // 没有给值，就直接拿类型名，试用于 Service
                if ("".equals(beanName)) {
                    beanName = toLowerFirstCase(field.getType().getSimpleName());
                }

                // getDeclaredFields能拿到(public、private、default、protected)所有的属性，field.set只能设置public的，所以设置强制赋值
                field.setAccessible(true);

                try {
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }

    }

    private void doInstance() {
        if (classNames.isEmpty()) return;

        for (String className : classNames) {
            try {
                Class<?> clazz = Class.forName(className);

                // 注解过的才去拿控制权
                if (clazz.isAnnotationPresent(SqlongController.class)) {
                    // value
                    Object instance = clazz.newInstance();

                    // key
                    String beanName = toLowerFirstCase(clazz.getSimpleName()); // 处理首字母小写
                    ioc.put(beanName, instance);
                } else if (clazz.isAnnotationPresent(SqlongService.class)) {
                    // 1、默认的类名首字母小写
                    Object instance = clazz.newInstance();

                    // 2、不同包名下的相同的类，可以自己在注解使用时自己起名字
                    String beanName = clazz.getAnnotation(SqlongService.class).value();
                    if (beanName.trim().equals("")) {
                        // 注解给的名字是空
                        beanName = toLowerFirstCase(clazz.getSimpleName()); // 处理首字母小写
                    }

                    // 1 + 2 情况注入
                    ioc.put(beanName, instance);

                    // 3、如果时注解的类实现了多个接口（IShowService、IShow2Service、ShowServiceImpl）
                    for (Class<?> i : clazz.getInterfaces()) {
                        // 一个接口被多个类实现，已经扫描过的类及接口全名已经加载进去，不能再加
                        if (ioc.containsKey(i.getName())) {
                            throw new Exception("The " + i.getName() + "is exists");
                        }
                        // i.getName() 接口全类名com.sqlong.IxxxxService
                        ioc.put(i.getName(), instance);
                    }
                } else {
                    continue;
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private String toLowerFirstCase(String simpleName) {
        char[] chars = simpleName.toCharArray();
        chars[0] += 32;
        return String.copyValueOf(chars);
    }

    private void doScanner(String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));
        File classPath = new File(url.getFile());

        for (File file : classPath.listFiles()) {
            if (file.isDirectory()) {
                // 是文件夹
                doScanner(scanPackage + "." + file.getName());
            } else {
                if (!file.getName().endsWith(".class")) continue;
                // 文件类名file.getName()可能会重复，所以加上包名
                String className = scanPackage + "." + file.getName().replace(".class", "");
                classNames.add(className);
            }
        }

    }

    private void doLoadConfig(String contextConfigLocation) {
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation.replace("classpath:", ""));
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
