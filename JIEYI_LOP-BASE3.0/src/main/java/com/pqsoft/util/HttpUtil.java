package com.pqsoft.util;

import com.alibaba.fastjson.JSON;
import com.google.common.base.Strings;
import com.pqsoft.skyeye.exception.ActionException;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.collections.map.MultiValueMap;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.*;

/**
 * HTTP工具类
 *
 * @author 钟林俊
 * @version V1.0 2016年4月21日 下午4:04:32
 */
public class HttpUtil {

    private static final Logger logger = Logger.getLogger(HttpUtil.class);
    private static final CloseableHttpClient httpClient = HttpClients.createDefault();
    private static final Integer TIMEOUT = 45000;

    /**
     * 向指定url以表单方式发送http post请求
     *
     * @deprecated
     * @see HttpUtil#formPost(String, Map)
     * @param url 请求地址
     * @param params 请求参数
     * @return 响应体的字符串表示
     * @throws ActionException
     * 30秒超时，请求发送失败，响应码非200都会抛ActionException
     */
    public static String httpPost(String url, Map<String, String> params){
        return httpPost(url, params, null, null);
    }

    /**
     * 向指定url以表单方式发送http post请求
     *
     * @deprecated
     * @see HttpUtil#formPost(String, Map, Map, Map)
     * @param url 请求地址
     * @param params 请求参数
     * @param cookies cookie容器
     * @param headers header容器
     * @return 响应体的字符串表示
     * @throws ActionException
     * 30秒超时，请求发送失败，响应码非200都会抛ActionException
     */
    public static String httpPost(String url, Map<String, String> params, Map<String, String> cookies, Map<String, String> headers) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        if(Strings.isNullOrEmpty(url)){
            throw new ActionException("请求地址不能为空！");
        }
        HttpPost httpPost = new HttpPost(URI.create(url));

        // 45秒超时
        RequestConfig.Builder builder = RequestConfig.custom();
        builder.setConnectTimeout(TIMEOUT);
        httpPost.setConfig(builder.build());
        logger.debug("CONNECTION TIMEOUT: " + TIMEOUT/1000 + " seconds");

        // 设置cookie
        HttpContext httpContext = new BasicHttpContext();
        httpContext.setAttribute(HttpClientContext.COOKIE_STORE, getCookieStore(cookies));

        // 设置header
        httpPost.setHeaders(getHeaders(headers));

        // 设置请求体
        UrlEncodedFormEntity formEntity = null;
        logger.debug("REQUEST PARAMETERS: " + params.toString());
        try {
            formEntity = new UrlEncodedFormEntity(getValuePairs(params), "utf-8");
        } catch (UnsupportedEncodingException e) {
            // ignored
        }
        httpPost.setEntity(formEntity);

        // 响应对象
        CloseableHttpResponse response;
        try {
            logger.debug("BEGIN TO SEND REQUEST TO " + url);
            response = httpClient.execute(httpPost, httpContext);
        } catch (IOException e) {
            logger.error("REQUEST FAILED!", e);
            throw new ActionException("请求超时，请重试！", e);
        }
        logger.debug("SUCCEED IN RECEIVING RESPONSE.");
        StatusLine statusLine = response.getStatusLine();
        int statusCode = statusLine.getStatusCode();
        logger.debug("RESPONSE CODE: " + statusCode);
        // 响应码非200
        // TODO 重定向302
        if (statusCode != HttpStatus.SC_OK) {
            throw new ActionException("请求发生错误：" + statusCode + " - " + statusLine.getReasonPhrase());
        }

        String result = null;
        try {
            result = EntityUtils.toString(response.getEntity(), "utf-8");
            logger.debug("RESPONSE ENTITY: " + result);
        } catch (IOException e) {
            logger.error("PARSE RESPONSE ENTITY FAILED!", e);
            throw new ActionException("解析请求响应失败！", e);
        } finally {
            IOUtils.closeQuietly(response);
            IOUtils.closeQuietly(httpClient);
        }
        return result;
    }

    /**
     * 向指定url发送get请求
     *
     * @param url 目标url
     * @return 响应体字符串
     */
    public static String httpGet(String url){
        return httpGet(url, null);
    }

    /**
     * 向指定url发送get请求
     * get请求的参数是拼接在url之后，有一定长度限制
     *
     * @param url 目标url
     * @param params 请求参数
     * @return 响应体字符串
     */
    public static String httpGet(String url, Map<String, String> params){
        return httpGet(url, params, null, null);
    }

    /**
     * 向指定url发送get请求（包含cookie和header，可空）
     * get请求的参数是拼接在url之后，有一定长度限制
     *
     * @param url 目标url
     * @param params 请求参数
     * @param cookies cookie集合
     * @param headers header集合
     * @return 响应体字符串
     */
    public static String httpGet(String url, Map<String, String> params, Map<String, String> cookies, Map<String, String> headers) {
        String queryString = assembleQueryString(params);

        // get请求对象
        HttpGet httpGet = createHttpGet(Strings.isNullOrEmpty(queryString) ? url : url + "?" + queryString);

        // 设置cookie
        HttpContext httpContext = loadCookies(cookies);

        // 设置header
        loadHeaders(httpGet, headers);

        // 发送get请求
        CloseableHttpResponse httpResponse = execute(httpGet, httpContext);

        // 获取响应体字符串
        return extractResponseEntity(httpResponse);
    }

    /**
     * 向指定url发送post请求，请求体为json串
     *
     * @param url 目标url
     * @param params 参数对象
     * @return 响应体字符串
     */
    public static String jsonPost(String url, Object params){
        return jsonPost(url, params, null, null);
    }

    /**
     * 向指定url发送post请求，请求体为json串（包含cookie和header，可空）
     *
     * @param url 目标url
     * @param params 参数对象
     * @param cookies cookie集合
     * @param headers header集合
     * @return 响应体的字符串表示
     */
    public static String jsonPost(String url, Object params, Map<String, String> cookies, Map<String, String> headers){
        // 创建post对象
        HttpPost httpPost = createHttpPost(url);

        // 设置cookie
        HttpContext httpContext = loadCookies(cookies);

        // 设置header
        loadHeaders(httpPost, headers, true);

        // 设置请求体
        loadStringEntity(httpPost, params);

        // 发送请求
        CloseableHttpResponse httpResponse = execute(httpPost, httpContext);

        // 获取响应体
        return extractResponseEntity(httpResponse);
    }

    /**
     * 向指定url发送post请求，请求体为表单参数
     *
     * @param url 请求地址
     * @param params 请求参数
     * @return 响应体的字符串表示
     * 30秒超时，请求发送失败，响应码非200都会抛ActionException
     */
    public static String formPost(String url, Map<String, String> params){
        return formPost(url, params, null, null);
    }

    /**
     * 向指定url发送post请求，请求体为表单参数（包含cookie和header，可空）
     *
     * @param url 请求地址
     * @param params 请求参数
     * @param cookies cookie容器
     * @param headers header容器
     * @return 响应体的字符串表示
     * @throws ActionException
     * 30秒超时，请求发送失败，响应码非200都会抛ActionException
     */
    public static String formPost(String url, Map<String, String> params, Map<String, String> cookies, Map<String, String> headers) {
        // 创建post请求对象
        HttpPost httpPost = createHttpPost(url);

        // 设置cookie
        HttpContext httpContext = loadCookies(cookies);

        // 设置header
        loadHeaders(httpPost, headers, false);

        // 设置请求体
        loadFormEntity(httpPost, params);

        // 响应对象
        CloseableHttpResponse response = execute(httpPost, httpContext);

        // 获取响应体字符串
        return extractResponseEntity(response);
    }

    /**
     * 从响应对象中获取响应体的字符串表示
     *
     * @param response 响应对象
     * @return 响应体字符串
     */
    private static String extractResponseEntity(CloseableHttpResponse response) {
        String result = null;
        try {
            result = EntityUtils.toString(response.getEntity(), "utf-8");
            if(result.length() <= 1000){
                logger.debug("RESPONSE ENTITY: " + result);
            }
        } catch (IOException e) {
            throw new ActionException("解析响应体失败！", e);
        } finally {
            IOUtils.closeQuietly(response);
//            IOUtils.closeQuietly(httpClient);
        }
        return result;
    }

    /**
     * 发送http请求
     *
     * @param httpRequest 请求对象
     * @param httpContext 请求上下文对象
     * @return 响应对象
     */
    private static CloseableHttpResponse execute(HttpRequestBase httpRequest, HttpContext httpContext) {
        CloseableHttpResponse response;
        try {
            logger.debug("BEGIN TO SEND REQUEST TO " + httpRequest.getURI());
            response = httpClient.execute(httpRequest, httpContext);
        } catch (IOException e) {
            throw new ActionException("远程服务器无响应，请稍后重试！", e);
        }
        logger.debug("SUCCEED IN RECEIVING RESPONSE.");
        StatusLine statusLine = response.getStatusLine();
        int statusCode = statusLine.getStatusCode();
        logger.debug("RESPONSE CODE: " + statusCode);
        // 响应码非200
        // TODO 重定向302
        if (statusCode != org.apache.http.HttpStatus.SC_OK) {
            throw new ActionException("请求发生错误：" + statusCode + " - " + statusLine.getReasonPhrase());
        }
        return response;
    }

    /**
     * 设置表单参数格式的请求体
     *
     * @param httpPost post请求对象
     * @param params 参数集合
     */
    private static void loadFormEntity(HttpPost httpPost, Map<String, String> params) {
        UrlEncodedFormEntity formEntity = null;
        logger.debug("REQUEST PARAMETERS: " + params.toString());
        try {
            formEntity = new UrlEncodedFormEntity(getValuePairs(params), "utf-8");
        } catch (UnsupportedEncodingException e) {
            // ignored
        }
        httpPost.setEntity(formEntity);
    }

    /**
     * 设置header
     *
     * @param httpRequest 请求对象
     * @param headers header集合
     */
    private static void loadHeaders(HttpRequestBase httpRequest, Map<String, String> headers){
        loadHeaders(httpRequest, headers, null);
    }

    /**
     * 设置header
     *
     * @param httpRequest 请求对象
     * @param headers header集合
     */
    private static void loadHeaders(HttpRequestBase httpRequest, Map<String, String> headers, Boolean isJson) {
        Map<String, String> headersMap = new HashMap<>();
        if(isJson != null){
            headersMap.put("Content-Type", "application/json;charset=utf-8");
            if(!isJson){
                headersMap.put("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");
            }
        }
        if(!MapUtils.isEmpty(headers)){
            headersMap.putAll(headers);
        }
        httpRequest.setHeaders(getHeadersNew(headersMap));
    }

    /**
     * 创建post请求对象
     *
     * @param url 目标url
     * @return post请求对象
     */
    private static HttpPost createHttpPost(String url) {
        if(Strings.isNullOrEmpty(url)){
            throw new ActionException("请求地址不能为空！");
        }
        HttpPost httpPost = new HttpPost(URI.create(url));
        // 30秒超时
        setTimeout(httpPost, TIMEOUT);

        return httpPost;
    }

    /**
     * 通过cookie容器获取cookie
     *
     * @param cookies cookie容器
     * @return cookie存储对象
     */
    private static CookieStore getCookieStore(Map<String, String> cookies) {
        CookieStore cookieStore = new BasicCookieStore();
        if(!MapUtils.isEmpty(cookies)){
            logger.debug("REQUEST COOKIES: " + cookies.toString());
            for(Map.Entry<String, String> entry : cookies.entrySet()){
                cookieStore.addCookie(new BasicClientCookie(entry.getKey(), entry.getValue()));
            }
        }
        return cookieStore;
    }

    /**
     * 通过header容器获取header
     *
     * @param headersMap header容器
     * @return header数组
     */
    private static Header[] getHeaders(Map<String, String> headersMap) {
        Header[] headers;
        if(!MapUtils.isEmpty(headersMap)){
            headers = new Header[headersMap.size() + 1];
            int i = 1;
            for(Map.Entry<String, String> entry : headersMap.entrySet()){
                headers[i] = new BasicHeader(entry.getKey(), entry.getValue());
                i++;
            }
        }else{
            headers = new Header[1];
        }
        headers[0] = new BasicHeader("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");
        logger.debug("REQUEST HEADERS: " + Arrays.toString(headers));
        return headers;
    }

    /**
     * 通过header容器获取header
     *
     * @param headersMap header容器
     * @return header数组
     */
    private static Header[] getHeadersNew(Map<String, String> headersMap) {
        Header[] headers;
        if(!MapUtils.isEmpty(headersMap)){
            headers = new Header[headersMap.size()];
            int i = 0;
            for(Map.Entry<String, String> entry : headersMap.entrySet()){
                headers[i] = new BasicHeader(entry.getKey(), entry.getValue());
                i++;
            }
            logger.debug("REQUEST HEADERS: " + Arrays.toString(headers));
            return headers;
        }

        return null;
    }

    /**
     * 处理参数容器
     *
     * @param params 参数容器
     * @param actionSubject 动作主体
     */
    private static void manageParams(Map<String, String> params, Object actionSubject){
        MultiValueMap valueMap;
        String key;
        String value;
        if(!MapUtils.isEmpty(params)) {
            if (params instanceof MultiValueMap) {
                valueMap = (MultiValueMap) params;
                for (Object aKey : valueMap.keySet()) {
                    try {
                        key = (String) aKey;
                        Collection collection = valueMap.getCollection(key);
                        for (Object val : collection) {
                            value = "";
                            if (val != null) {
                                value = val.toString();
                            }
                            loadParams(actionSubject, key, value);
                        }
                    } catch (ClassCastException e) {
                        throw new ActionException("请求参数获取失败：MultiValueMap的key必须为String类型！");
                    }
                }
            } else {
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    key = entry.getKey();
                    value = entry.getValue() == null ? "" : entry.getValue();
                    loadParams(actionSubject, key, value);
                }
            }
        }
    }

    /**
     * 将参数加载进动作主体
     *
     * @param actionSubject 加载目标主体
     * @param key 参数名
     * @param value 参数值
     */
    private static void loadParams(Object actionSubject, String key, String value) {
        StringBuilder sb;
        List<NameValuePair> list;
        if(actionSubject instanceof StringBuilder){
            sb = (StringBuilder) actionSubject;
            sb.append(key).append("=").append(value).append("&");
        }else if(actionSubject instanceof List){
            //noinspection unchecked
            list = (List<NameValuePair>) actionSubject;
            list.add(new BasicNameValuePair(key, value));
        }
    }

    /**
     * 组装url查询字符串
     *
     * @param params 请求参数集合
     * @return 查询字符串
     */
    private static String assembleQueryString(Map<String, String> params) {
        StringBuilder queryString = new StringBuilder("");
        manageParams(params, queryString);
        return queryString.length() == 0 ? queryString.toString() : queryString.substring(0, queryString.length() - 1);
    }

    /**
     * 通过请求参数容器获取参数键值对集合
     *
     * @param params 参数容器
     * @return 参数键值对集合
     */
    private static List<? extends NameValuePair> getValuePairs(Map<String, String> params) {
        int size = params == null ? 0 : params.size();
        List<NameValuePair> nameValuePairs = new ArrayList<>(size);
        manageParams(params, nameValuePairs);
        return nameValuePairs;
    }

    /**
     * 设置cookie
     *
     * @param cookies cookie集合
     * @return http上下文对象
     */
    public static HttpContext loadCookies(Map<String,String> cookies) {
        HttpContext httpContext = new BasicHttpContext();
        httpContext.setAttribute(HttpClientContext.COOKIE_STORE, getCookieStore(cookies));
        return httpContext;
    }

    /**
     * 设置请求体
     *
     * @param httpPost post请求对象
     * @param params 参数对象
     */
    private static void loadStringEntity(HttpPost httpPost, Object params) {
        if(params != null){
            String entity = JSON.toJSONStringWithDateFormat(params, "yyyy-MM-dd HH:mm:ss");
            logger.debug("REQUEST ENTITY: " + entity);
            httpPost.setEntity(new StringEntity(entity, "utf-8"));
        }
    }

    /**
     * 创建get请求对象
     *
     * @param url 目标url
     * @return get请求对象
     */
    private static HttpGet createHttpGet(String url) {
        if(Strings.isNullOrEmpty(url)){
            throw new ActionException("请求地址不能为空！");
        }
        HttpGet httpGet = new HttpGet(URI.create(url));

        // 45秒超时
        setTimeout(httpGet, TIMEOUT);

        return httpGet;
    }

    /**
     * 设置请求超时时间
     *
     * @param httpRequest 请求对象
     * @param timeout 超时时间（毫秒）
     */
    private static void setTimeout(HttpRequestBase httpRequest, int timeout) {
        RequestConfig.Builder builder = RequestConfig.custom();
        builder.setConnectTimeout(timeout);
        builder.setSocketTimeout(timeout);
        httpRequest.setConfig(builder.build());
        logger.debug("CONNECTION TIMEOUT: " + timeout/1000 + " seconds");
        logger.debug("READ TIMEOUT: " + timeout/1000 + " seconds");
    }

}