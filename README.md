# Apache Dubbo 源码学习

## 架构图

![Architecture](http://dubbo.apache.org/img/architecture.png)


---
# 服务暴露，服务提供者

## 1.Dubbo-config模块
1.1 dubbo-config-spring

> 1.自定义spring标签   
> 2.解析DubboNamespaceHandler   
> 3.spi是什么  

(ExecutorService) ExtensionLoader.getExtensionLoader(ThreadPool.class).getAdaptiveExtension().getExecutor(url);


##### DubboNamespaceHandler.java
```html
 @Override
    public void init() {
        registerBeanDefinitionParser("application", new DubboBeanDefinitionParser(ApplicationConfig.class, true));
        registerBeanDefinitionParser("module", new DubboBeanDefinitionParser(ModuleConfig.class, true));
        registerBeanDefinitionParser("registry", new DubboBeanDefinitionParser(RegistryConfig.class, true));
        registerBeanDefinitionParser("config-center", new DubboBeanDefinitionParser(ConfigCenterBean.class, true));
        registerBeanDefinitionParser("metadata-report", new DubboBeanDefinitionParser(MetadataReportConfig.class, true));
        registerBeanDefinitionParser("monitor", new DubboBeanDefinitionParser(MonitorConfig.class, true));
        registerBeanDefinitionParser("metrics", new DubboBeanDefinitionParser(MetricsConfig.class, true));
        registerBeanDefinitionParser("provider", new DubboBeanDefinitionParser(ProviderConfig.class, true));
        registerBeanDefinitionParser("consumer", new DubboBeanDefinitionParser(ConsumerConfig.class, true));
        registerBeanDefinitionParser("protocol", new DubboBeanDefinitionParser(ProtocolConfig.class, true));
        registerBeanDefinitionParser("service", new DubboBeanDefinitionParser(ServiceBean.class, true));
        registerBeanDefinitionParser("reference", new DubboBeanDefinitionParser(ReferenceBean.class, false));
        registerBeanDefinitionParser("annotation", new AnnotationBeanDefinitionParser());
    }
```  

---

##### DubboBeanDefinitionParser.java  
> 逻辑比较多，不过大体意思就是拿到xml中所有配置的基本信息，然后定义成spring中的BeanDefinition。保存类名，scope，属性，构造函数参数列表，
依赖Bean，是否是单例的，是否懒加载....后面对Bean的操作直接对BeanDefinition操作就好了。


```html
  private static BeanDefinition parse(Element element, ParserContext parserContext, Class<?> beanClass, boolean required) {
        RootBeanDefinition beanDefinition = new RootBeanDefinition();
        beanDefinition.setBeanClass(beanClass);
        beanDefinition.setLazyInit(false);
        String id = element.getAttribute("id");
        if (StringUtils.isEmpty(id) && required) {
            String generatedBeanName = element.getAttribute("name");
            if (StringUtils.isEmpty(generatedBeanName)) {
                if (ProtocolConfig.class.equals(beanClass)) {
                    generatedBeanName = "dubbo";
                } else {
                    generatedBeanName = element.getAttribute("interface");
                }
            }
            if (StringUtils.isEmpty(generatedBeanName)) {
                generatedBeanName = beanClass.getName();
            }
            id = generatedBeanName;
            int counter = 2;
            while (parserContext.getRegistry().containsBeanDefinition(id)) {
                id = generatedBeanName + (counter++);
            }
        }
        if (StringUtils.isNotEmpty(id)) {
            if (parserContext.getRegistry().containsBeanDefinition(id)) {
                throw new IllegalStateException("Duplicate spring bean id " + id);
            }
            parserContext.getRegistry().registerBeanDefinition(id, beanDefinition);
            beanDefinition.getPropertyValues().addPropertyValue("id", id);
        }
        if (ProtocolConfig.class.equals(beanClass)) {
            for (String name : parserContext.getRegistry().getBeanDefinitionNames()) {
                BeanDefinition definition = parserContext.getRegistry().getBeanDefinition(name);
                PropertyValue property = definition.getPropertyValues().getPropertyValue("protocol");
                if (property != null) {
                    Object value = property.getValue();
                    if (value instanceof ProtocolConfig && id.equals(((ProtocolConfig) value).getName())) {
                        definition.getPropertyValues().addPropertyValue("protocol", new RuntimeBeanReference(id));
                    }
                }
            }
        } else if (ServiceBean.class.equals(beanClass)) {
            String className = element.getAttribute("class");
            if (StringUtils.isNotEmpty(className)) {
                RootBeanDefinition classDefinition = new RootBeanDefinition();
                classDefinition.setBeanClass(ReflectUtils.forName(className));
                classDefinition.setLazyInit(false);
                parseProperties(element.getChildNodes(), classDefinition);
                beanDefinition.getPropertyValues().addPropertyValue("ref", new BeanDefinitionHolder(classDefinition, id + "Impl"));
            }
        } else if (ProviderConfig.class.equals(beanClass)) {
            parseNested(element, parserContext, ServiceBean.class, true, "service", "provider", id, beanDefinition);
        } else if (ConsumerConfig.class.equals(beanClass)) {
            parseNested(element, parserContext, ReferenceBean.class, false, "reference", "consumer", id, beanDefinition);
        }
        Set<String> props = new HashSet<>();
        ManagedMap parameters = null;
        for (Method setter : beanClass.getMethods()) {
            String name = setter.getName();
            if (name.length() > 3 && name.startsWith("set")
                    && Modifier.isPublic(setter.getModifiers())
                    && setter.getParameterTypes().length == 1) {
                Class<?> type = setter.getParameterTypes()[0];
                String beanProperty = name.substring(3, 4).toLowerCase() + name.substring(4);
                String property = StringUtils.camelToSplitName(beanProperty, "-");
                props.add(property);
                // check the setter/getter whether match
                Method getter = null;
                try {
                    getter = beanClass.getMethod("get" + name.substring(3), new Class<?>[0]);
                } catch (NoSuchMethodException e) {
                    try {
                        getter = beanClass.getMethod("is" + name.substring(3), new Class<?>[0]);
                    } catch (NoSuchMethodException e2) {
                        // ignore, there is no need any log here since some class implement the interface: EnvironmentAware,
                        // ApplicationAware, etc. They only have setter method, otherwise will cause the error log during application start up.
                    }
                }
                if (getter == null
                        || !Modifier.isPublic(getter.getModifiers())
                        || !type.equals(getter.getReturnType())) {
                    continue;
                }
                if ("parameters".equals(property)) {
                    parameters = parseParameters(element.getChildNodes(), beanDefinition);
                } else if ("methods".equals(property)) {
                    parseMethods(id, element.getChildNodes(), beanDefinition, parserContext);
                } else if ("arguments".equals(property)) {
                    parseArguments(id, element.getChildNodes(), beanDefinition, parserContext);
                } else {
                    String value = element.getAttribute(property);
                    if (value != null) {
                        value = value.trim();
                        if (value.length() > 0) {
                            if ("registry".equals(property) && RegistryConfig.NO_AVAILABLE.equalsIgnoreCase(value)) {
                                RegistryConfig registryConfig = new RegistryConfig();
                                registryConfig.setAddress(RegistryConfig.NO_AVAILABLE);
                                beanDefinition.getPropertyValues().addPropertyValue(beanProperty, registryConfig);
                            } else if ("provider".equals(property) || "registry".equals(property) || ("protocol".equals(property) && ServiceBean.class.equals(beanClass))) {
                                /**
                                 * For 'provider' 'protocol' 'registry', keep literal value (should be id/name) and set the value to 'registryIds' 'providerIds' protocolIds'
                                 * The following process should make sure each id refers to the corresponding instance, here's how to find the instance for different use cases:
                                 * 1. Spring, check existing bean by id, see{@link ServiceBean#afterPropertiesSet()}; then try to use id to find configs defined in remote Config Center
                                 * 2. API, directly use id to find configs defined in remote Config Center; if all config instances are defined locally, please use {@link ServiceConfig#setRegistries(List)}
                                 */
                                beanDefinition.getPropertyValues().addPropertyValue(beanProperty + "Ids", value);
                            } else {
                                Object reference;
                                if (isPrimitive(type)) {
                                    if ("async".equals(property) && "false".equals(value)
                                            || "timeout".equals(property) && "0".equals(value)
                                            || "delay".equals(property) && "0".equals(value)
                                            || "version".equals(property) && "0.0.0".equals(value)
                                            || "stat".equals(property) && "-1".equals(value)
                                            || "reliable".equals(property) && "false".equals(value)) {
                                        // backward compatibility for the default value in old version's xsd
                                        value = null;
                                    }
                                    reference = value;
                                } else if (ONRETURN.equals(property) || ONTHROW.equals(property) || ONINVOKE.equals(property)) {
                                    int index = value.lastIndexOf(".");
                                    String ref = value.substring(0, index);
                                    String method = value.substring(index + 1);
                                    reference = new RuntimeBeanReference(ref);
                                    beanDefinition.getPropertyValues().addPropertyValue(property + METHOD, method);
                                } else {
                                    if ("ref".equals(property) && parserContext.getRegistry().containsBeanDefinition(value)) {
                                        BeanDefinition refBean = parserContext.getRegistry().getBeanDefinition(value);
                                        if (!refBean.isSingleton()) {
                                            throw new IllegalStateException("The exported service ref " + value + " must be singleton! Please set the " + value + " bean scope to singleton, eg: <bean id=\"" + value + "\" scope=\"singleton\" ...>");
                                        }
                                    }
                                    reference = new RuntimeBeanReference(value);
                                }
                                beanDefinition.getPropertyValues().addPropertyValue(beanProperty, reference);
                            }
                        }
                    }
                }
            }
        }
        NamedNodeMap attributes = element.getAttributes();
        int len = attributes.getLength();
        for (int i = 0; i < len; i++) {
            Node node = attributes.item(i);
            String name = node.getLocalName();
            if (!props.contains(name)) {
                if (parameters == null) {
                    parameters = new ManagedMap();
                }
                String value = node.getNodeValue();
                parameters.put(name, new TypedStringValue(value, String.class));
            }
        }
        if (parameters != null) {
            beanDefinition.getPropertyValues().addPropertyValue("parameters", parameters);
        }
        return beanDefinition;
    }
```
到这里我们大致知道了dubbo如何读取xml配置文件，定义成spring的BeanDefinition对象。

---
##### ServiceBean是何时暴露服务的
```html
public class ServiceBean<T> extends ServiceConfig<T> implements
        BeanNameAware,// setBeanName()
        ApplicationContextAware, // setApplicationContext
        InitializingBean, // afterPropertiesSet()
        DisposableBean, // destory()
        ApplicationEventPublisherAware {
        
}

@Override
public void setApplicationContext(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
    SpringExtensionFactory.addApplicationContext(applicationContext);
}


@Override
public void afterPropertiesSet() throws Exception {
    if (StringUtils.isEmpty(getPath())) {
        if (StringUtils.isNotEmpty(beanName)
                && StringUtils.isNotEmpty(getInterface())
                && beanName.startsWith(getInterface())) {
            setPath(beanName);
        }
    }
}

@Override
public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
    this.applicationEventPublisher = applicationEventPublisher;
}


@Override
public void exported() {
    super.exported(); // 将本地服务暴露给外部调用
    // Publish ServiceBeanExportedEvent
    publishExportEvent();
}
    
```

---

##### ServiceConfig.export具体实现
````html
public synchronized void export() {
    if (!shouldExport()) {
        return;
    }

    if (bootstrap == null) {
        bootstrap = DubboBootstrap.getInstance();
        bootstrap.init();
    }

    checkAndUpdateSubConfigs();

    //init serviceMetadata
    serviceMetadata.setVersion(version);
    serviceMetadata.setGroup(group);
    serviceMetadata.setDefaultGroup(group);
    serviceMetadata.setServiceType(getInterfaceClass());
    serviceMetadata.setServiceInterfaceName(getInterface());
    serviceMetadata.setTarget(getRef());

    // 延迟加载，
    if (shouldDelay()) {
        DELAY_EXPORT_EXECUTOR.schedule(this::doExport, getDelay(), TimeUnit.MILLISECONDS);
    } else {
        doExport();
    }
}

protected synchronized void doExport() {
    if (unexported) {
        throw new IllegalStateException("The service " + interfaceClass.getName() + " has already unexported!");
    }
    if (exported) {
        return;
    }
    exported = true;

    if (StringUtils.isEmpty(path)) {
        path = interfaceName;
    }
    doExportUrls();
    // dispatch a ServiceConfigExportedEvent since 2.7.4
    dispatch(new ServiceConfigExportedEvent(this));
}


private void doExportUrls() {
    ServiceRepository repository = ApplicationModel.getServiceRepository();
    ServiceDescriptor serviceDescriptor = repository.registerService(getInterfaceClass());
    repository.registerProvider(
            getUniqueServiceName(),
            ref,
            serviceDescriptor,
            this,
            serviceMetadata
    );

    // 拼接注册url
    // registry://172.23.2.101:2181/com.alibaba.dubbo.registry.RegistryService?application=oic-dubbo-provider
    // &dubbo=2.6.1&logger=slf4j&pid=15258&register=true&registry=zookeeper&timestamp=1528958780785
    
    List<URL> registryURLs = ConfigValidationUtils.loadRegistries(this, true);

    // 将protocols列表中的每个protocol根据url暴露出去,主要是doExportUrlsFor1Protocol方法
    for (ProtocolConfig protocolConfig : protocols) {
        String pathKey = URL.buildKey(getContextPath(protocolConfig)
                .map(p -> p + "/" + path)
                .orElse(path), group, version);
        // In case user specified path, register service one more time to map it to path.
        repository.registerService(pathKey, interfaceClass);
        // TODO, uncomment this line once service key is unified
        serviceMetadata.setServiceKey(pathKey);
        doExportUrlsFor1Protocol(protocolConfig, registryURLs);
    }
}


````

---
然后这个方法前期就一堆塞参数到map，最后也是跟上面生成registryUrl差不多，只不过多加了一些module,provider和自己的一些参数，
拼成一个更长的url。下面这个就是我上面那个服务生成的完整url:
```html
dubbo://10.8.0.28:12000/com.tyyd.oic.service.PushMessageService?accepts=1000&anyhost=true&application=oic-dubbo-provider
&bind.ip=10.8.0.28&bind.port=12000&buffer=8192&charset=UTF-8&default.service.filter=dubboCallDetailFilter&dubbo=2.6.1
&generic=false&interface=com.tyyd.oic.service.PushMessageService&iothreads=9&logger=slf4j
&methods=deletePushMessage,getPushMessage,batchPushMessage,addPushMessage,updatePushMessage,qryPushMessage
&payload=8388608&pid=15374&queues=0&retries=0&revision=1.0.0&serialization=hessian2&side=provider
&threadpool=fixed&threads=100&timeout=6000&timestamp=1528959454516&version=1.0.0
```

url地址包含了版本号，接口名，方法列表，序列化方法，过期时间等这个接口bean所有需要用到的上下文信息，并且地址头也由registry改
成了dubbo。因为包含了所有上下文的信息，所以这个url的用处很大

```html
private void doExportUrlsFor1Protocol(ProtocolConfig protocolConfig, List<URL> registryURLs) {
    String name = protocolConfig.getName();
    if (StringUtils.isEmpty(name)) {
        name = DUBBO;
    }
    .....
    
    // 转成Invoke创建exporter
     if (!SCOPE_LOCAL.equalsIgnoreCase(scope)) {
        if (CollectionUtils.isNotEmpty(registryURLs)) {
            for (URL registryURL : registryURLs) {
                //if protocol is only injvm ,not register
                if (LOCAL_PROTOCOL.equalsIgnoreCase(url.getProtocol())) {
                    continue;
                }
                url = url.addParameterIfAbsent(DYNAMIC_KEY, registryURL.getParameter(DYNAMIC_KEY));
                URL monitorUrl = ConfigValidationUtils.loadMonitor(this, registryURL);
                if (monitorUrl != null) {
                    url = url.addParameterAndEncoded(MONITOR_KEY, monitorUrl.toFullString());
                }
                if (logger.isInfoEnabled()) {
                    if (url.getParameter(REGISTER_KEY, true)) {
                        logger.info("Register dubbo service " + interfaceClass.getName() + " url " + url + " to registry " + registryURL);
                    } else {
                        logger.info("Export dubbo service " + interfaceClass.getName() + " to url " + url);
                    }
                }

                // For providers, this is used to enable custom proxy to generate invoker
                String proxy = url.getParameter(PROXY_KEY);
                if (StringUtils.isNotEmpty(proxy)) {
                    registryURL = registryURL.addParameter(PROXY_KEY, proxy);
                }

                Invoker<?> invoker = PROXY_FACTORY.getInvoker(ref, (Class) interfaceClass, registryURL.addParameterAndEncoded(EXPORT_KEY, url.toFullString()));
                DelegateProviderMetaDataInvoker wrapperInvoker = new DelegateProviderMetaDataInvoker(invoker, this);

                Exporter<?> exporter = protocol.export(wrapperInvoker);
                exporters.add(exporter);
            }
        } else {
            if (logger.isInfoEnabled()) {
                logger.info("Export dubbo service " + interfaceClass.getName() + " to url " + url);
            }
            Invoker<?> invoker = PROXY_FACTORY.getInvoker(ref, (Class) interfaceClass, url);
            DelegateProviderMetaDataInvoker wrapperInvoker = new DelegateProviderMetaDataInvoker(invoker, this);

            Exporter<?> exporter = protocol.export(wrapperInvoker);
            exporters.add(exporter);
        }
        /**
         * @since 2.7.0
         * ServiceData Store
         */
        WritableMetadataService metadataService = WritableMetadataService.getExtension(url.getParameter(METADATA_KEY, DEFAULT_METADATA_STORAGE_TYPE));
        if (metadataService != null) {
            metadataService.publishServiceDefinition(url);
        }
    }
}
```

---
##### ProxyFactory.java -> JavassistProxyFactory
proxyFactory接口写了SPI标签，所以这里默认使用的就是javassistProxyFactory。

```html
@SPI("javassist")
public interface ProxyFactory {

    /**
     * create proxy.
     *
     * @param invoker
     * @return proxy
     */
    @Adaptive({PROXY_KEY})
    <T> T getProxy(Invoker<T> invoker) throws RpcException;

    /**
     * create proxy.
     *
     * @param invoker
     * @return proxy
     */
    @Adaptive({PROXY_KEY})
    <T> T getProxy(Invoker<T> invoker, boolean generic) throws RpcException;

    /**
     * create invoker.
     *
     * @param <T>
     * @param proxy
     * @param type
     * @param url
     * @return invoker
     */
    @Adaptive({PROXY_KEY})
    <T> Invoker<T> getInvoker(T proxy, Class<T> type, URL url) throws RpcException;

}
```

---
##### Protocol.java -> DubboProtocol

```html
@SPI("dubbo")
public interface Protocol {

    int getDefaultPort();

    /**
     * Export service for remote invocation: <br>
     * 1. Protocol should record request source address after receive a request:
     * RpcContext.getContext().setRemoteAddress();<br>
     * 2. export() must be idempotent, that is, there's no difference between invoking once and invoking twice when
     * export the same URL<br>
     * 3. Invoker instance is passed in by the framework, protocol needs not to care <br>
     *
     * @param <T>     Service type
     * @param invoker Service invoker
     * @return exporter reference for exported service, useful for unexport the service later
     * @throws RpcException thrown when error occurs during export the service, for example: port is occupied
     */
    @Adaptive
    <T> Exporter<T> export(Invoker<T> invoker) throws RpcException;

 
    @Adaptive
    <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException;

    void destroy();

   
    default List<ProtocolServer> getServers() {
        return Collections.emptyList();
    }

}
```

---
##### Exchanger.java -> HeaderExchanger
```html
SPI(HeaderExchanger.NAME)
public interface Exchanger {

    /**
     * bind.
     *
     * @param url
     * @param handler
     * @return message server
     */
    @Adaptive({Constants.EXCHANGER_KEY})
    ExchangeServer bind(URL url, ExchangeHandler handler) throws RemotingException;

    /**
     * connect.
     *
     * @param url
     * @param handler
     * @return message channel
     */
    @Adaptive({Constants.EXCHANGER_KEY})
    ExchangeClient connect(URL url, ExchangeHandler handler) throws RemotingException;

}
```

---
##### Transporter -> nettyTransporter
```html
@SPI("netty")
public interface Transporter {

    /**
     * Bind a server.
     */
    @Adaptive({Constants.SERVER_KEY, Constants.TRANSPORTER_KEY})
    RemotingServer bind(URL url, ChannelHandler handler) throws RemotingException;

    /**
     * Connect to a server.
     */
    @Adaptive({Constants.CLIENT_KEY, Constants.TRANSPORTER_KEY})
    Client connect(URL url, ChannelHandler handler) throws RemotingException;
}
```

---
##### Dispatcher -> AllDispatcher
```html
@SPI(AllDispatcher.NAME)
public interface Dispatcher {

    /**
     * dispatch the message to threadpool.
     */
    @Adaptive({Constants.DISPATCHER_KEY, "dispather", "channel.handler"})
    ChannelHandler dispatch(ChannelHandler handler, URL url);

}
```

---
##### ExecutorRepository -> DefaultExecutorRepository
```html
@SPI("default")
public interface ExecutorRepository {

    /**
     * Called by both Client and Server. TODO, consider separate these two parts.
     * When the Client or Server starts for the first time, generate a new threadpool according to the parameters specified.
     *
     * @param url
     * @return
     */
    ExecutorService createExecutorIfAbsent(URL url);

    ExecutorService getExecutor(URL url);

    /**
     * Modify some of the threadpool's properties according to the url, for example, coreSize, maxSize, ...
     *
     * @param url
     * @param executor
     */
    void updateThreadpool(URL url, ExecutorService executor);

    /**
     * Returns a scheduler from the scheduler list, call this method whenever you need a scheduler for a cron job.
     * If your cron cannot burden the possible schedule delay caused by sharing the same scheduler, please consider define a dedicate one.
     *
     * @return
     */
    ScheduledExecutorService nextScheduledExecutor();

    ScheduledExecutorService getServiceExporterExecutor();

    /**
     * Get the default shared threadpool.
     *
     * @return
     */
    ExecutorService getSharedExecutor();

}
```

---

##### ThreadPool -> FixedThreadPool
```html
@SPI("fixed")
public interface ThreadPool {

    /**
     * Thread pool
     *
     * @param url URL contains thread parameter
     * @return thread pool
     */
    @Adaptive({THREADPOOL_KEY})
    Executor getExecutor(URL url);

}


public class FixedThreadPool implements ThreadPool {
    @Override
    public Executor getExecutor(URL url) {
        String name = url.getParameter(THREAD_NAME_KEY, DEFAULT_THREAD_NAME);
        int threads = url.getParameter(THREADS_KEY, DEFAULT_THREADS);
        int queues = url.getParameter(QUEUES_KEY, DEFAULT_QUEUES);
        return new ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS,
                queues == 0 ? new SynchronousQueue<Runnable>() :
                        (queues < 0 ? new LinkedBlockingQueue<Runnable>()
                                : new LinkedBlockingQueue<Runnable>(queues)),
                new NamedInternalThreadFactory(name, true), new AbortPolicyWithReport(name, url));
    }
}
```

---
# 服务消费者
<dubbo:reference id="xx" interface="com.demo.xx" version = "1.0.0" />
registerBeanDefinitionParser("reference", new DubboBeanDefinitionParser(ReferenceBean.class, false));

##### ReferenceBean[和serviceBean差不多]
```html
@Override
public Object getObject() {
    return get();
}

public synchronized T get() {
    if (destroyed) {
        throw new IllegalStateException("The invoker of ReferenceConfig(" + url + ") has already destroyed!");
    }
    if (ref == null) {
        init();
    }
    return ref;
}

public synchronized void init() {
    if (initialized) {
        return;
    }
    // TODO: 2019/11/11  ApplicationModel,consumer也会注入进来
    // TODO: 2019/11/11  ApplicationModel里面，这个类里面有所有的provider和comsumer
    ServiceRepository repository = ApplicationModel.getServiceRepository();
    ServiceDescriptor serviceDescriptor = repository.registerService(interfaceClass);
    repository.registerConsumer(
            serviceMetadata.getServiceKey(),
            attributes,
            serviceDescriptor,
            this,
            null,
            serviceMetadata);
    // TODO: 2019/11/11 这才是关键.....
    ref = createProxy(map);
}


private T createProxy(Map<String, String> map) {
    if (shouldJvmRefer(map)) {
    
    }
    List<Invoker<?>> invokers = new ArrayList<Invoker<?>>();
    URL registryURL = null;
    for (URL url : urls) {
        // TODO: 2019/11/11 这是关键 refer，有用到spi dubboInvoker
        invokers.add(REF_PROTOCOL.refer(interfaceClass, url));
        if (UrlUtils.isRegistry(url)) {
            registryURL = url; // use last registry url
        }
    }
}

```
