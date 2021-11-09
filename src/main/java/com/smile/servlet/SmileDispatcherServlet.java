package com.smile.servlet;

import com.smile.annotation.*;

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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class SmileDispatcherServlet extends HttpServlet {

    private Map<String,Object> mapping = new HashMap<>();

    private Properties contextConfig = new Properties();

    private List<String> classNames = new ArrayList<>();

    private Map<String,Object> ioc = new HashMap<>();

    private Map<String,Method> handlerMapping = new HashMap<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req,resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatch(req,resp);
        } catch (Exception e) {
            resp.getWriter().write("500 Exception " + Arrays.toString(e.getStackTrace()));
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException, InvocationTargetException, IllegalAccessException {
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath,"").replaceAll("/+","/");
        if (!this.handlerMapping.containsKey(url)) {
            resp.getWriter().write("404 Not Found!");
            return;
        }
        Method method = (Method) this.handlerMapping.get(url);
        Map<String,String[]> params = req.getParameterMap();
//        method.invoke(this.mapping.get(method.getDeclaringClass().getName()),new Object[]{req,resp,params.get("name")[0]});
        Class<?>[] parameterTypes = method.getParameterTypes();
        Map<String,String[]> parameterMap = req.getParameterMap();
        Object[] paramValues = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Class parameterType = parameterTypes[i];
            if (parameterType == HttpServletRequest.class) {
                paramValues[i] = req;
                continue;
            } else if (parameterType == HttpServletResponse.class) {
                paramValues[i] = resp;
                continue;
            } else if (parameterType == String.class) {
                Annotation[][] pa = method.getParameterAnnotations();
                for (int j = 0; j < pa.length; j++) {
                    for (Annotation a : pa[i]) {
                        if (a instanceof SmileRequestParam) {
                            String paramName = ((SmileRequestParam)a).value();
                            if (!"".equals(paramName.trim())) {
                                String value = Arrays.toString(parameterMap.get(paramName))
                                        .replaceAll("\\[|\\]","")
                                        .replaceAll("\\s",",");
                                paramValues[i] = value;
                            }
                        }
                    }
                }
            }
        }

        String beanName = toLowerFirstCase(method.getDeclaringClass().getSimpleName());
        method.invoke(ioc.get(beanName),new Object[]{req,resp,params.get("name")[0]});

    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        doScanner(contextConfig.getProperty("scanPackage"));
//        doScanner("com.smile");
        doInstance();
        doAutowired();
        initHandlerMapping();
        System.out.println("Smile Spring framework is init.");
    }

    private void initHandlerMapping() {
       if (ioc.isEmpty()) {
           return;
       }
       for (Map.Entry<String,Object> entry: ioc.entrySet()) {
           Class<?> clazz = entry.getValue().getClass();
           if (!clazz.isAnnotationPresent(SmileController.class)) {
               continue;
           }
           String baseUrl = "";
           if (clazz.isAnnotationPresent(SmileRequestMapping.class)) {
               SmileRequestMapping requestMapping = clazz.getAnnotation(SmileRequestMapping.class);
               baseUrl = requestMapping.value();
           }
           for (Method method: clazz.getMethods()) {
               if (!method.isAnnotationPresent(SmileRequestMapping.class)) {
                   continue;
               }
               SmileRequestMapping requestMapping = method.getAnnotation(SmileRequestMapping.class);
               String url = ("/" + baseUrl + "/" + requestMapping.value()) .replaceAll("/+","/");
               handlerMapping.put(url,method);
               System.out.println("Mapped :" + url + "," + method);
           }
       }
    }

    private void doAutowired() {
        if (ioc.isEmpty()) {
            return;
        }
        for(Map.Entry<String,Object> entry: ioc.entrySet()) {
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field:fields) {
                if (!field.isAnnotationPresent(SmileAutowired.class)) {
                    continue;
                }
                SmileAutowired autowired = field.getAnnotation(SmileAutowired.class);
                String beanName = autowired.value().trim();
                if ("".equals(beanName)) {
                    beanName = field.getType().getName();
                }
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(),ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    private String toLowerFirstCase(String simpleName) {
        char[] chars = simpleName.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }



    private void doInstance() {
        if (classNames.isEmpty()) {
            return;
        }
        try {
            for (String className: classNames) {
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(SmileController.class)) {
                    Object instance = clazz.newInstance();
                    String beanName = toLowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName,instance);
                } else if(clazz.isAnnotationPresent(SmileService.class)) {
                    SmileService smileService = clazz.getAnnotation(SmileService.class);
                    String beanName = smileService.value();
                    if ("".equals(beanName.trim())) {
                        beanName = toLowerFirstCase(clazz.getSimpleName());
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName,instance);
                    for (Class<?> i : clazz.getInterfaces()) {
                        if (ioc.containsKey(i.getName())) {
                            throw new Exception("The \"" + i.getName() + "\" is exists !!" );
                        }
                        ioc.put(i.getName(),instance);
                    }
                } else {
                    continue;
                }
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void doScanner(String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.","/"));
        File classPath = new File(url.getFile());
        for (File file: classPath.listFiles()) {
            if (file.isDirectory()) {
                doScanner(scanPackage + "." + file.getName());
            } else {
                if (!file.getName().endsWith(".class")) {
                    continue;
                }
                String  className = (scanPackage + "." + file.getName().replace(".class",""));
                classNames.add(className);
            }
        }
    }

    private void doLoadConfig(String contextConfigLocation) {
        InputStream fis = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try{
            contextConfig.load(fis);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (null != fis) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
