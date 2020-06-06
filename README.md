# Dubbo源码学习2.6.x-蓝星花  

## 架构图

![Architecture](http://dubbo.apache.org/img/architecture.png)


## 源码模块划分
common : 通用逻辑模块，提供工具类和通用模型  
remoting: 远程模块，为消费者和服务提供者提供通信能力    
rpc: 与remoting相似，本模块提供各种通信协议，以及动态代理   
cluster: 集群容错模块，多个服务伪装一个服务提供方，（负载均衡，容错，路由）
registry：注册中心实现(multicast,zk,redis)  
monitor: 监控模块，监控dubbo接口的调用次数，时间等   
config:配置模块，实现了API配置，属性配置，XML配置，注解配置等功能  
container：容器模块   
fiter：拦截器模块，包含dubbo内置的过滤器   
plugin:插件模块,提供内置的插件   
demo:远程调用示例      
test：测试模块      
---

## 功能层次划分  
service: 业务层，开发者实现的业务代码
proxy:服务代理层
registry:注册层，dubbo服务的注册于发现
cluster:集群容错层
monitor:监控层
protocol:远程调用层
exchange 信息交换层
transport 网络传输层
serialize序列化层  

---

##### dubbo服务启动时发生了什么:
dubbo服务初始化启动时，通过Proxy组件调用具体协议（Protocol），把服务端要暴露的接口封装成Invoker
（真实类型是AbstractProxyInvoker），然后转换成Exporter，这个时候框架会打开服务端口等并记录服务
实例到内存中，最后通过Registry把服务元数据注册到注册中心，如果是消费者，在启动时会通过Registry注
册中心订阅服务端的元数据，就可以获取暴露的服务了。


##### dubbo消费者调用生产者服务时发生了什么：
每个消费者接口都会有一个interface代理类，这些代理类由Proxy持有管理着，触发Invoker的方法调用时，需
要使用Cluster，Cluster负责容错，如果调用失败会重试。在调用之前会通过Directory获取所有可调用的列表
，然后根据路由规则将列表过滤一遍，然后通过LoadBalance做负载均衡，选定一个可调用的Invoker，这个Invoker
在调用之前又会通过一个拦截器，这个拦截器通常是处理上下文，限流，计数等。


接着会通过Client做数据传输，生产者收到数据包，会使用Codec处理协议头以及一些半包、粘包等，处理完之后再对
完整的数据报文做反序列化处理。随后，这个Request会被分配到线程池中进行处理，Server会处理这些request，根
据请求查找对应的Export，经过拦截器之后原路返回。

---
## 面试题
##### 1.服务发布流程
1.DubboNamespaceHandler.init()  
2.ServiceBean.afterPropertiesSet() -> export()
3.ServiceConfig.doExport() -> doExportUrls() -> doExportUrlsFor1Protocol()   
4.SPI扩展  ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension(); 
  ProxyFactory 
  Protocol
  Exchanger
  Transporter
  Dispatcher
  ExecutorRepository
  ThreadPool

   

##### 2.服务引用流程
1.DubboNamespaceHandler.init()  
2.ReferenceBean.afterPropertiesSet() -> getObject()  
3.ReferenceConfig.init() -> createProxy(map) -> refprotocol.refer(interfaceClass, url)
4.DubboProtocol.refer() 
5.new DubboInvoker<T>(serviceType, url, getClients(url), invokers)
6.Exchangers.connect(url, requestHandler);
4.Transporters.connect()
5.NettyClient -> new AbstractClient() -> connect()   


##### 3.超时实现原理
1.ReentrantLock,ConditionObject
2.DefaultFuture.class -> get()


##### 4.重试实现原理
1.FailoverClusterInvoke.class->doInvoke()  


##### 5.负载实现原理
1.RandomLoadBalance
2.RoundRobinLoadBalance
3.LeastActiveLoadBalance(RpcStatus维护了个map)
4.ConsistentHashLoadBalance 







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

到这里我们大致知道了dubbo如何读取xml配置文件，定义成spring的BeanDefinition对象。

---
##### ServiceBean是何时暴露服务的
```html
public class ServiceBean<T> extends ServiceConfig<T> implements BeanNameAware,// setBeanName()
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
