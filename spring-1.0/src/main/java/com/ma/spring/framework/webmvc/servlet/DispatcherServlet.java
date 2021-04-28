package com.ma.spring.framework.webmvc.servlet;

import com.ma.mvcframework.annotation.*;
import com.ma.spring.framework.annotation.*;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 核心控制器
 *
 * @author ma
 * @date 2021/4/14 21:34
 */
public class DispatcherServlet extends HttpServlet {

    /**
     * application.properties配置文件
     */
    private Properties contextConfig = new Properties();

    /**
     * 扫描包下的所有类全限定名（享元模式，缓存）
     */
    private List<String> classNames = new ArrayList<>();

    /**
     * IOC容器
     */
    private Map<String, Object> ioc = new HashMap<>();

    /**
     * 为什么不用Map？答：用Map的话key只能是URL，Handler本身功能就是URL和Method对应关系，已经具备Map的功能。
     */
    private List<Handler> handlerMapping = new ArrayList<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            // 6、请求委派（委派模式）
            this.doDispatch(req,resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 Exection, Detail : " + Arrays.toString(e.getStackTrace()));
        }
    }

    /**
     * 6、请求委派（委派模式）
     *
     * @param req 请求
     * @param resp 响应
     */
    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        Handler handler = this.getHandler(req);
        if (handler == null) {
        //if (!this.handlerMapping.containsKey(url)) {
            resp.getWriter().write("404 Not Found!!");
            return;
        }

        // 形参列表
        Class<?>[] paramTypes = handler.getParamTypes();
        // 实参列表
        Object[] paramValues = new Object[paramTypes.length];
        // 传参列表
        Map<String, String[]> paramMap = req.getParameterMap();

        // 匹配方法的参数，填充传递的参数
        for (Map.Entry<String, Integer> entry : handler.paramIndexMapping.entrySet()) {
            if (!paramMap.containsKey(entry.getKey())) {
                continue;
            }
            String value = Arrays.toString(paramMap.get(entry.getKey()))
                    // 去掉中括号
                    .replaceAll("\\[|\\]", "")
                    // 去掉空白字符（等价于[\f\n\r\t\v]）
                    .replaceAll("\\s+", "");
            Integer paramIndex = entry.getValue();
            paramValues[paramIndex] = this.convert(paramTypes[paramIndex], value);
        }

        // 设置request和response参数
        if (handler.paramIndexMapping.containsKey(HttpServletRequest.class.getSimpleName())) {
            Integer index = handler.paramIndexMapping.get(HttpServletRequest.class.getSimpleName());
            paramValues[index] = req;
        }
        if (handler.paramIndexMapping.containsKey(HttpServletResponse.class.getSimpleName())) {
            Integer index = handler.paramIndexMapping.get(HttpServletResponse.class.getSimpleName());
            paramValues[index] = resp;
        }

        // 调用映射方法
        Object returnValue = handler.method.invoke(handler.controller, paramValues);
        if (returnValue == null || returnValue instanceof Void) {
            return;
        }
        resp.getWriter().write(returnValue.toString());
    }

    /**
     * 根据请求路径匹配请求处理器
     *
     * @param req 请求
     * @return 请求处理器
     */
    private Handler getHandler(HttpServletRequest req) {
        // 绝对路径
        String url = req.getRequestURI();
        // 去掉上下文的相对路径
        String contextPath = req.getContextPath();
        url = url.replaceAll(contextPath, "").replaceAll("/+", "/");

        // 匹配请求处理器
        for (Handler handler : this.handlerMapping) {
            Matcher matcher = handler.getPattern().matcher(url);
            if (!matcher.matches()) {
                continue;
            }
            return handler;
        }
        return null;
    }

    /**
     * 类型转化
     *
     * @param type 类型
     * @param value 转化前的值
     * @return 转化后的值
     */
    private Object convert(Class<?> type, String value) {
        if (Integer.class == type) {
            return Integer.valueOf(value);
        } else if (Double.class == type) {
            return Double.valueOf(value);
        }
        return value;
    }

    @Override
    public void init(ServletConfig config) {
        // 1、加载application.properties配置文件
        this.doLoadConfig(config.getInitParameter("contextConfigLocation"));

        // 2、扫描包下的所有类，将类的全限定名放入classNames集合列表
        this.doScanner(contextConfig.getProperty("scan.package"));

        //  ================== IOC（Inversion of Control，控制反转）部分 ==================
        /**
         * IOC = DI + AOP
         * IOC不是一种技术而是一种设计思想，它的作用/目的：解耦。
         * IOC：对对象控制权的转移，从程序代码本身反转到了外部容器。
         */
        // 3、初始化IOC容器，将组件注解类实例化并加入IoC容器
        this.doInstance();

        // AOP（Aspect Oriented Programming，面向切面编程）

        //  ================== DI（Dependency Injection， 依赖注入）部分 ==================
        /**
         * DI：对象之间依赖关系由容器在运行期决定。
         */
        // 4、完成依赖注入
        this.doAutowired();

        //  ================== MVC部分 ==================
        // 5、初始化HandlerMapping
        this.doInitHandlerMapping();

        System.out.println("Spring framework is init...");
    }

    /**
     * 5、初始化HandlerMapping
     */
    private void doInitHandlerMapping() {
        if (this.ioc.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : this.ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(Controller.class)) {
                continue;
            }
            // Controller类注解上的映射路径：@RequestMapping("/demo")
            String baseUrl = "";
            if (clazz.isAnnotationPresent(RequestMapping.class)) {
                baseUrl = clazz.getAnnotation(RequestMapping.class).value();
            }

            for (Method method : clazz.getMethods()) {
                // 方法注解的映射路径：@RequestMapping("/query")
                if (!method.isAnnotationPresent(RequestMapping.class)) {
                    continue;
                }
                // 解决RequestMapping参数不加/情况
                String regex = ("/" + baseUrl + "/" + method.getAnnotation(RequestMapping.class).value())
                        .replaceAll("/+", "/");
                Pattern pattern = Pattern.compile(regex);
                // this.handlerMapping.put(url, method);
                this.handlerMapping.add(new Handler(pattern, entry.getValue(), method));
                System.out.println("Mapped: " + pattern + " => " + method);
            }
        }
    }

    /**
     * 4、完成依赖注入
     */
    private void doAutowired() {
        if (this.ioc.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : this.ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            // 把所有的包括private/protected/default/public 修饰字段都取出来
            Field[] fields = clazz.getDeclaredFields();
            for (Field f : fields) {
                if (!f.isAnnotationPresent(Autowired.class)) {
                    continue;
                }
                Autowired autowired = f.getAnnotation(Autowired.class);
                String beanName = autowired.value();
                if ("".equals(beanName.trim())) {
                    // field.getType() => com.ma.demo.service.DemoService
                    // field.getDeclaringClass() => com.ma.demo.controller.DemoController
                    // field.getClass() => java.lang.reflect.Field
                    beanName = this.toLowerFirstCase(f.getType().getSimpleName());
                }
                // 暴力访问
                f.setAccessible(true);
                try {
                    f.set(entry.getValue(), this.ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 3、初始化IOC容器，将组件注解类实例化并加入IoC容器
     * 组件注解：@Component及其子注解@Service、@Controller、@Repository...
     */
    private void doInstance() {
        if (this.classNames.isEmpty()) {
            return;
        }

        for (String className : this.classNames) {
            try {
                // 必须加<?>，否则clazz.getAnnotation()返回的是Annotation对象
                Class<?> clazz = Class.forName(className);
                // clazz.isAnnotationPresent()判断该类上是否加了指定注解
                if (clazz.isAnnotationPresent(Controller.class)) {
                    // 默认类名首字母小写  注：clazz.getSimpleName()获取类名；clazz.getName()获取类的全限定名
                    String beanName = this.toLowerFirstCase(clazz.getSimpleName());
                    // key=>类名；value=>实例
                    this.ioc.put(beanName, clazz.newInstance());
                } else if (clazz.isAnnotationPresent(Service.class)) {
                    // 获取@Service注解的value属性
                    Service service = clazz.getAnnotation(Service.class);
                    // 1、自定义的beanName
                    String beanName = service.value();

                    // 2、默认类名首字母小写
                    if ("".equals(beanName.trim())) {
                        beanName = this.toLowerFirstCase(clazz.getSimpleName());
                    }
                    this.ioc.put(beanName, clazz.newInstance());

                    // 3、一个接口不允许有多个别名相同的实现类
                    for (Class<?> i : clazz.getInterfaces()) {
                        if (this.ioc.containsKey(this.toLowerFirstCase(i.getSimpleName()))) {
                            throw new Exception("The \"" + i.getName() + "\" is exists, please use alies");
                        }
                        this.ioc.put(this.toLowerFirstCase(i.getSimpleName()), clazz.newInstance());
                    }
                } else {
                    continue;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 2、递归扫描包下的所有类，将类的全限定名放入classNames集合列表
     *
     * @param scanPackage 扫描的包路径
     */
    private void doScanner(String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource(scanPackage.replaceAll("\\.", "/"));
        // URL.getFile()路径带参数，URL.getPath()路径不带参数
        File classPath = new File(url.getFile());
        for (File file : classPath.listFiles()) {
            if (file.isDirectory()) {
                doScanner(scanPackage + "." + file.getName());
            } else {
                if (!file.getName().endsWith(".class")) {
                    continue;
                }
                // 全限定名 = 包名 + 类名
                // replace第一个参数为字符串，replaceAll第一个参数为正则，故不需加转义。
                String className = scanPackage + "." + file.getName().replace(".class", "");
                this.classNames.add(className);
            }
        }
    }

    /**
     * 1、加载application.properties配置文件
     *
     * @param contextConfigLocation Servlet初始化配置-application.properties配置文件路径
     */
    private void doLoadConfig(String contextConfigLocation) {
        /**
         * 获取类路径下资源：
         * 1、Class类的getResourceAsStream(String path)方法：以/开头相对classes根路径，不加则相对当前类的class文件。
         *      InputStream is = this.getClass().getResourceAsStream("/xxx.txt");
         * 2、Class类的getResourceAsStream(String path)方法：不能以/开头，因为本身就是相对classes路径。
         *      InputStream is = this.getClass().getClassLoader().getResourceAsStream("xxx.txt");
         */
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            // 读取格式为"key=value"的.properties文件
            contextConfig.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 首字母转小写
     *
     * @param simpleName 类名
     * @return 首字母小写的类名
     */
    private String toLowerFirstCase(String simpleName) {
        if (Character.isLowerCase(simpleName.charAt(0))) {
            return simpleName;
        }
        char[] charArray = simpleName.toCharArray();
        charArray[0] += 32;
        return new String(charArray);
    }


    /**
     * 请求处理器：记录Controller中的RequestMapping和Method的对应关系
     */
    public class Handler {
        /**  */
        private Pattern pattern;
        /** 控制器单例对象 */
        private Object controller;
        /** 映射方法 */
        private Method method;
        /** 形参类型列表 */
        private Class<?>[] paramTypes;

        public Pattern getPattern() {
            return pattern;
        }

        public Method getMethod() {
            return method;
        }

        public Object getController() {
            return controller;
        }

        public Class<?>[] getParamTypes() {
            return paramTypes;
        }

        /**
         * 形参列表：key-形参名，value-参数索引
         */
        private Map<String,Integer> paramIndexMapping;

        public Handler(Pattern pattern, Object controller, Method method) {
            this.pattern = pattern;
            this.controller = controller;
            this.method = method;

            paramTypes = method.getParameterTypes();

            paramIndexMapping = new HashMap<>();
            putParamIndexMapping(method);
        }

        /**
         * 形参列表映射
         *
         * @param method
         */
        private void putParamIndexMapping(Method method) {
            // 形参注解（一个参数可以有多个注解）
            Annotation[][] pas = method.getParameterAnnotations();
            for (int i = 0; i < paramTypes.length; i++) {
                for (Annotation paramAnnotation : pas[i]) {
                    // 判断注解类型是否为@RequestParam的2种方法
                    // RequestParam.class.isInstance(paramAnnotation) {
                    if (paramAnnotation instanceof RequestParam) {
                        String paramName = ((RequestParam) paramAnnotation).value();
                        if (!"".equals(paramName.trim())) {
                            paramIndexMapping.put(paramName, i);
                        }
                    }
                }
            }

            // 提取方法参数中的request和response参数
            for (int i = 0; i < this.paramTypes.length; i++) {
                Class<?> paramType = paramTypes[i];
                if (paramType == HttpServletRequest.class
                    || paramType == HttpServletResponse.class) {
                    paramIndexMapping.put(paramType.getSimpleName(), i);
                }
            }
        }

    }

}
