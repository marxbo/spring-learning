package com.ma.mvcframework.v1.servlet;

import com.ma.mvcframework.annotation.Autowired;
import com.ma.mvcframework.annotation.Controller;
import com.ma.mvcframework.annotation.Service;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

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
     * 扫描包下的所有类全限定名
     */
    private List<String> classNames = new ArrayList<>();

    /**
     * IOC容器
     */
    private Map<String, Object> ioc = new HashMap<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doPost(req, resp);
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        // 1、加载application.properties配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));

        // 2、扫描包下的所有类，将类的全限定名放入classNames集合列表
        doScanner(contextConfig.getProperty("scan.package"));

        //  ================== IOC（Inversion of Control，控制反转）部分 ==================
        /**
         * IOC = DI + AOP
         * IOC不是一种技术而是一种设计思想，它的作用/目的：解耦。
         * IOC：对对象控制权的转移，从程序代码本身反转到了外部容器。
         */
        // 3、初始化IOC容器，将组件注解类实例化并加入IoC容器
        doInstance();

        // AOP（Aspect Oriented Programming，面向切面编程）

        //  ================== DI（Dependency Injection， 依赖注入）部分 ==================
        /**
         * DI：对象之间依赖关系由容器在运行期决定。
         */
        // 4、完成依赖注入
        doAutowired();

        //  ================== MVC部分 ==================
        // 5、初始化HandlerMapping
        //doInitHandlerMapping();

        System.out.println("Spring framework is init...");
    }

    /**
     * 4、完成依赖注入
     */
    private void doAutowired() {
        if (ioc.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
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
                    beanName = f.getType().getSimpleName();
                }
                f.setAccessible(true);
                try {
                    f.set(entry.getValue(), ioc.get(beanName));
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
        if (classNames.isEmpty()) {
            return;
        }

        for (String className : classNames) {
            try {
                // 必须加<?>，否则clazz.getAnnotation()返回的是Annotation对象
                Class<?> clazz = Class.forName(className);
                // clazz.isAnnotationPresent()判断该类上是否加了指定注解
                if (clazz.isAnnotationPresent(Controller.class)) {
                    // 默认类名首字母小写  注：clazz.getSimpleName()获取类名；clazz.getName()获取类的全限定名
                    String beanName = this.toLowerFirstCase(clazz.getSimpleName());
                    // key=>类名；value=>实例
                    ioc.put(beanName, clazz.newInstance());
                } else if (clazz.isAnnotationPresent(Service.class)) {
                    // 获取@Service注解的value属性
                    Service service = clazz.getAnnotation(Service.class);
                    System.out.println(service);
                    System.out.println(service.annotationType());
                    // 1、自定义的beanName
                    String beanName = service.value();

                    // 2、默认类名首字母小写
                    if ("".equals(beanName.trim())) {
                        beanName = toLowerFirstCase(clazz.getSimpleName());
                    }
                    ioc.put(beanName, clazz.newInstance());

                    // 3、一个接口不允许有多个别名相同的实现类
                    for (Class<?> i : clazz.getInterfaces()) {
                        if (ioc.containsKey(toLowerFirstCase(i.getSimpleName()))) {
                            throw new Exception("The \"" + i.getName() + "\" is exists, please use alies");
                        }
                        ioc.put(toLowerFirstCase(clazz.getSimpleName()), clazz.newInstance());
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
                classNames.add(className);
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

}
