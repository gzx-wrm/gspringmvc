package com.gzx.mvc.servlet;

import com.gzx.mvc.annotation.Controller;
import com.gzx.mvc.annotation.RequestMapping;
import com.gzx.mvc.annotation.RestController;
import com.gzx.spring.context.ApplicationContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DispatcherServlet extends HttpServlet {

    private Map<String, Object> controllerMapping = new ConcurrentHashMap<>();

    public DispatcherServlet(ApplicationContext applicationContext) {
        // 1. 读取容器内的对象，找到controller注解标注的类，注册到mapping中
        ConcurrentHashMap<String, Object> singletonBeanMap = applicationContext.singletonBeanMap;
        for (Object bean : singletonBeanMap.values()) {
            if (!bean.getClass().isAnnotationPresent(Controller.class) && !bean.getClass().isAnnotationPresent(RestController.class)) {
                continue;
            }
            // 将带有Controller注解的类注册到mapping中，这里的path只考虑类上的RequestMapping了
            if (!bean.getClass().isAnnotationPresent(RequestMapping.class)) {
                continue;
            }
            RequestMapping requestMapping = bean.getClass().getAnnotation(RequestMapping.class);
            if (controllerMapping.containsKey(requestMapping.value())) {
                throw new RuntimeException("path: " + requestMapping.value() + " has already been registered!");
            }
            controllerMapping.put(requestMapping.value(), bean);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            Object res = dispatch(req, resp);
            if (res instanceof String) {
                resp.getOutputStream().write(((String) res).getBytes());
            }
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public Object dispatch(HttpServletRequest request, HttpServletResponse response) throws InvocationTargetException, IllegalAccessException {
        // 1. 获取请求的url
        String uri = request.getRequestURI();
        // 2. 找到对应的controller做处理，这里就简化操作了
        for (String path : controllerMapping.keySet()) {
            if (!uri.startsWith(path)) {
                continue;
            }
            String subUri = uri.substring(path.length());
            subUri = subUri.startsWith("/") ? subUri : "/" + subUri;
            // 3. 找到controller中对应的方法做处理
            Object controller = controllerMapping.get(path);
            if (controller == null) {
                throw new RuntimeException("path: " + path + " has not been registered!");
            }
            Method[] methods = controller.getClass().getDeclaredMethods();
            for (Method method : methods) {
                if (!method.isAnnotationPresent(RequestMapping.class)) {
                    continue;
                }
                RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
                if (requestMapping.value().equals(subUri)) {
                    // 这里就默认方法的参数是servlet request和servlet response了
                    Object res = method.invoke(controller, request, response);
                    return res;
                }
            }
        }
        return null;
    }
}
