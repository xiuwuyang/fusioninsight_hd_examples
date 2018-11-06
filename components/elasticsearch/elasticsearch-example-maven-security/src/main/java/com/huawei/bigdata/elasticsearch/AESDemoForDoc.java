package com.huawei.bigdata.elasticsearch;

import com.google.gson.Gson;
import com.sun.scenario.effect.impl.sw.sse.SSEBlend_SRC_OUTPeer;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.PropertyConfigurator;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/*
 *场景描述：
 * 获取客户端，连接Elasticsearch集群
 * 查询集群健康状态
 * 检查指定索引是否存在
 * 创建指定分片数目的索引
 * 更新文档索引
 * 批量写入数据
 * 查询文档索引信息
 * 删除索引
 */
public class AESDemoForDoc {
    private static final Logger LOG = LoggerFactory.getLogger(AESDemoForDoc.class);
    private static String isSecureMode;
    private static String esServerHost;
    private static int MaxRetryTimeoutMillis;
    private static String index;
    private static String type;
    private static int id;
    private static int shardNum;
    private static int replicaNum;
    private static int ConnectTimeout;
    private static int SocketTimeout;
    private static String schema = "https";
    private static RestClientBuilder builder = null;
    private static RestClient restClient = null;

    static {
        //日志配置文件
        PropertyConfigurator.configure(AESDemoForDoc.class.getClassLoader().getResource("conf/log4j.properties").getPath());
    }

    public static void main(String[] args) throws Exception {
        LOG.info("Start to do es test !");

        //加载es-example.properties配置文件中的内容，包括与主机的连接信息，索引信息等
        Properties properties = new Properties();
        String path =  AESDemoForDoc.class.getClassLoader().getResource("conf/es-example.properties").getPath();

        try {
            properties.load(new FileInputStream(new File(path)));
        } catch (Exception e) {
            throw new Exception("Failed to load properties file : " + path);
        }
        //在es-example.properties中必须为ip1：port1，ip2：port2，ip3：port3 ....
        esServerHost = properties.getProperty("EsServerHost");
        MaxRetryTimeoutMillis = Integer.valueOf(properties.getProperty("MaxRetryTimeoutMillis"));
        ConnectTimeout = Integer.valueOf(properties.getProperty("ConnectTimeout"));
        SocketTimeout = Integer.valueOf(properties.getProperty("SocketTimeout"));
        isSecureMode = properties.getProperty("isSecureMode");
        index = properties.getProperty("index");
        type = properties.getProperty("type");
        id = Integer.valueOf(properties.getProperty("id"));
        shardNum = Integer.valueOf(properties.getProperty("shardNum"));
        replicaNum = Integer.valueOf(properties.getProperty("replicaNum"));

        LOG.info("EsServerHost:" + esServerHost);
        LOG.info("MaxRetryTimeoutMillis:" + MaxRetryTimeoutMillis);
        LOG.info("ConnectTimeout:" + ConnectTimeout);
        LOG.info("SocketTimeout:" + SocketTimeout);
        LOG.info("index:" + index);
        LOG.info("shardNum:" + shardNum);
        LOG.info("replicaNum:" + replicaNum);
        LOG.info("isSecureMode:" + isSecureMode);
        LOG.info("type:" + type);
        LOG.info("id:" + id);

        //安全登录
        if ((isSecureMode).equals("true")) {
            //加载krb5.conf：客户端与Kerberos对接的配置文件，配置到JVM系统参数中
            String krb5ConfFile = AESDemoForDoc.class.getClassLoader().getResource("conf/krb5.conf").getPath();
            LOG.info("krb5ConfFile: " + krb5ConfFile);
            System.setProperty("java.security.krb5.conf", krb5ConfFile);
            //加载jaas文件进行认证，并配置到JVM系统参数中
            String jaasPath = AESDemoForDoc.class.getClassLoader().getResource("conf/jaas.conf").getPath();
            LOG.info("jaasPath: " + jaasPath);
            System.setProperty("java.security.auth.login.config", jaasPath);
            System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");
            //添加ES安全指示
            System.setProperty("es.security.indication", "true");
            LOG.info("es.security.indication is  " + System.getProperty("es.security.indication"));
        }else if((isSecureMode).equals("false")){
            System.setProperty("es.security.indication", "false");
            schema="http";
        }


        //************* 获取客户端，连接Elasticsearch集群 ************
        //我们需要获取RestClient类 通过设置IP和端口连接到特定Elasticsearch集群
        //RestClient实例可以通过相应的RestClientBuilder类构建，该类通过RestClient＃builder（HttpHost ...）静态方法创建。
        // 唯一必需的参数是客户端将与之通信的一个或多个主机，作为HttpHost的实例提供。
        //HttpHost保存与主机的HTTP连接所需的所有变量。这包括远程主机名，端口和方案。
        List<HttpHost> hosts=new ArrayList<HttpHost>();
        String[] hostArray1 = esServerHost.split(",");

        for(String host:hostArray1) {
            String[] ipPort= host.split(":");
            HttpHost hostNew =new HttpHost(ipPort[0],Integer.valueOf(ipPort[1]),schema);
            hosts.add(hostNew);
        }
        HttpHost[] httpHosts = hosts.toArray(new HttpHost[] {});
        builder = RestClient.builder(httpHosts);
        // 设置请求的回调函数
        //1.设置连接超时时间，单位毫秒。
        //2.设置请求获取数据的超时时间，单位毫秒。如果访问一个接口，多少时间内无法返回数据，就直接放弃此次调用。
        //3.设置同一请求最大超时重试时间（以毫秒为单位）。
        builder = builder.setRequestConfigCallback(new RestClientBuilder.RequestConfigCallback() {
            @Override
            public RequestConfig.Builder customizeRequestConfig(RequestConfig.Builder requestConfigBuilder) {
                return requestConfigBuilder.setConnectTimeout(ConnectTimeout).setSocketTimeout(SocketTimeout);
            }
        }).setMaxRetryTimeoutMillis(MaxRetryTimeoutMillis);

        //设置默认请求标头，它将与每个请求一起发送。请求时标头将始终覆盖任何默认标头。
        Header[] defaultHeaders = new Header[] { new BasicHeader("Accept", "application/json"),
                new BasicHeader("Content-type", "application/json") };
        builder.setDefaultHeaders(defaultHeaders);
        //根据配置好的RestClientBuilder创建新的RestClient。
        restClient = builder.build();
        LOG.info("The RestClient has been created !");
        restClient.setHosts(httpHosts);


        //*********** 查询集群健康状态 *************
        Response rsp = null;
        //在请求中添加pretty参数，可以让ES输出为容易阅读的JSON格式数据
        Map<String, String> params = Collections.singletonMap("pretty", "true");

        try {
            rsp = restClient.performRequest("GET", "/_cluster/health" , params);
            if(HttpStatus.SC_OK == rsp.getStatusLine().getStatusCode()) {
                LOG.info("QueryEsClusterHealth successful.");
            }else {
                LOG.error("QueryEsClusterHealth failed.");
            }
            LOG.info("QueryEsClusterHealth response entity is : " + EntityUtils.toString(rsp.getEntity()));
        }catch (Exception e) {
            LOG.error("QueryEsClusterHealth failed, exception occurred.",e);
        }

        //**************检查指定索引是否存在************
        //检查指定的索引是否存在于Elasticsearch集群中。
        //以下代码中的变量index，即需要检查的索引名称。
        //在样例工程的conf目录下的“es-example.properties”配置文件中设置，已经给予默认值“huawei”。
        boolean isExist = true;
        Response rsp1 = null;
        try {
            rsp1 = restClient.performRequest("HEAD", "/" + index , params);
            if(HttpStatus.SC_OK == rsp1.getStatusLine().getStatusCode()) {
                LOG.info("Check index successful,index is exist : " + index);
            }
            if(HttpStatus.SC_NOT_FOUND == rsp.getStatusLine().getStatusCode()){
                LOG.info("index is not exist : " + index);
                isExist =  false;
            }

        } catch (Exception e) {
            LOG.error("Check index failed, exception occurred.",e);
        }


        if(isExist){
            //**************删除索引*************
            Response rsp2 = null;
            try {
                ///删除指定index
                rsp2 = restClient.performRequest("DELETE", "/" + index , params);
                //删除指定index、type、id下的文档信息。
                //rsp2 = restClient.performRequest("DELETE", "/" + index+ "/" + type+ "/" + id, params);
                if(HttpStatus.SC_OK == rsp2.getStatusLine().getStatusCode()) {
                    LOG.info("Delete successful.");
                }
                else {
                    LOG.error("Delete failed.");
                }
                LOG.info("Delete response entity is : " + EntityUtils.toString(rsp2.getEntity()));
            }catch (Exception e) {
                LOG.error("Delete failed, exception occurred.",e);
            }
        }


        //*************创建指定分片数目的索引************
        //创建指定primary shard和replica数目的索引。
        //以下代码中的变量index、shardNum、replicaNum ，即需要创建的索引名称、索引的primary shard和replica数目。
        //在样例工程的conf目录下的“es-example.properties”配置文件中设置，已经给予默认值 “huawei”，“3”，“1”，可自行修改。
        Response rsp3 = null;
        String jsonString = "{" + "\"settings\":{" + "\"number_of_shards\":\"" + shardNum + "\","
                + "\"number_of_replicas\":\"" + replicaNum + "\"" + "}}";

        HttpEntity entity = new NStringEntity(jsonString, ContentType.APPLICATION_JSON);
        try {

            rsp3 = restClient.performRequest("PUT", "/" + index, params, entity);
            if(HttpStatus.SC_OK == rsp3.getStatusLine().getStatusCode()) {
                LOG.info("CreateIndexWithShardNum successful.");
            }else {
                LOG.error("CreateIndexWithShardNum failed.");
            }
            LOG.info("CreateIndexWithShardNum response entity is : " + EntityUtils.toString(rsp3.getEntity()));
        }catch (Exception e) {
            LOG.error("CreateIndexWithShardNum failed, exception occurred.",e);
        }



        //*********更新文档索引************
        //往指定索引中插入数据，更新索引。
        //以下代码中的变量index，type，id，即需要插入数据进行更新的索引名称、类型、ID。
        //在样例工程的conf目录下的“es-example.properties”配置文件中设置，已经给予默认值“huawei”，“doc”，“1”。
        //String变量“jsonString”即为插入的数据内容，用户可以自定义数据内容。
        String jsonString1 = "{" + "\"name\":\"Happy\"," + "\"author\":\"Alex Yang \","
                + "\"pubinfo\":\"Beijing,China. \"," + "\"pubtime\":\"2016-07-16\","
                + "\"desc\":\"Elasticsearch is a highly scalable open-source full-text search and analytics engine.\""
                + "}";

        HttpEntity entity1 = new NStringEntity(jsonString1, ContentType.APPLICATION_JSON);
        Response rsp4 = null;
        try {
            rsp4 = restClient.performRequest("POST", "/" + index + "/" + type +"/" + id , params, entity1);
            if(HttpStatus.SC_OK == rsp4.getStatusLine().getStatusCode()||HttpStatus.SC_CREATED == rsp4.getStatusLine().getStatusCode()) {
                LOG.info("PutData successful.");
            }else {
                LOG.error("PutData failed.");
            }
            LOG.info("PutData response entity is : " + EntityUtils.toString(rsp4.getEntity()));
        } catch (Exception e) {
            LOG.error("PutData failed, exception occurred.",e);
        }


        //****************批量写入数据**********************
        //批量写入数据到指定的索引中。esMap中添加的数据即为需要写入索引的数据信息
        Long idNumber = 0L;
        Long oneCommit = 5L;//请根据实际需要修改每个循环中提交的索引文档的数量。
        Long totalRecordNumber = 10L;//请根据实际需要修改此批量请求方法中需要提交的索引文档总数。
        Long circleNumber = totalRecordNumber/oneCommit;
        StringEntity entity2 = null;
        Gson gson = new Gson();
        Map<String,Object> esMap = new HashMap<String,Object>();
        String str = "{ \"index\" : { \"_index\" : \"" + index + "\", \"_type\" :  \"" + type + "\"} }";

        for (int i = 1; i <=circleNumber; i++) {
            StringBuffer buffer = new StringBuffer();
            for (int j = 1; j <= oneCommit; j++) {
                esMap.clear();
                idNumber = Long.valueOf(idNumber.longValue() + 1L);
                esMap.put("id_number", idNumber);
                esMap.put("name", "Linda");
                esMap.put("age", ThreadLocalRandom.current().nextInt(18, 100));
                esMap.put("height", (float) ThreadLocalRandom.current().nextInt(140, 220));
                esMap.put("weight", (float) ThreadLocalRandom.current().nextInt(70, 200));
                esMap.put("cur_time", new Date().getTime());

                String strJson = gson.toJson(esMap);
                buffer.append(str).append("\n");
                buffer.append(strJson).append("\n");
            }
            entity2 = new StringEntity(buffer.toString(), ContentType.APPLICATION_JSON);
            entity2.setContentEncoding("UTF-8");
            Response rsp5 = null;
            try {
                rsp5 = restClient.performRequest("PUT", "/_bulk" ,params ,entity2);
                if(HttpStatus.SC_OK == rsp5.getStatusLine().getStatusCode()) {
                    LOG.info("Bulk successful.");
                    LOG.info("Already input documents : " + oneCommit*i);
                }
                else {
                    LOG.error("Bulk failed.");
                }
                LOG.info("Bulk response entity is : " + EntityUtils.toString(rsp5.getEntity()));
            }catch (Exception e) {
                LOG.error("Bulk failed, exception occurred.",e);
            }
        }

        //******查询文档索引信息*********
        //查询指定index、type、id下的文档信息。
        Response rsp6 = null;
        try {
            rsp6 = restClient.performRequest("GET", "/" + index+ "/" + type+ "/" + id, params);
            if(HttpStatus.SC_OK == rsp.getStatusLine().getStatusCode()) {
                LOG.info("QueryData successful.");
            }
            else {
                LOG.error("QueryData failed.");
            }
            LOG.info("QueryData response entity is : " + EntityUtils.toString(rsp6.getEntity()));
        }catch (Exception e) {
            LOG.error("QueryData failed, exception occurred.",e);
        }

        //在进行完Elasticsearch操作后，需要调用“restClient.close()”关闭所申请的资源。
        if( restClient!=null) {
            try {
                restClient.close();
                LOG.info("Close the client successful in main.");
            } catch (Exception e1) {
                LOG.error("Close the client failed in main.",e1);
            }
        }
    }
}