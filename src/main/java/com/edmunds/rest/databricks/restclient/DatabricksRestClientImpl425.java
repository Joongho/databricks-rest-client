/*
 * Copyright 2018 Edmunds.com, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.edmunds.rest.databricks.restclient;

import com.edmunds.rest.databricks.DatabricksRestException;
import com.edmunds.rest.databricks.RequestMethod;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.util.Map;
import javax.net.ssl.SSLContext;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.AutoRetryHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.BasicClientConnectionManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

/**
 * This implementation uses an older version of httpclient (version 4.2.5)
 * Which allows it to be used on certain systems that enforce older versions of the library.
 * This version will be eventually removed, so please don't use it unless you have to.
 */
@Deprecated
public final class DatabricksRestClientImpl425 extends AbstractDatabricksRestClientImpl {

  private static Logger logger = Logger.getLogger(DatabricksRestClientImpl425.class.getName());

  private DatabricksRestClientImpl425(String host,
      String apiVersion, int maxRetry, long retryInterval) {
    super(host, apiVersion, maxRetry, retryInterval);
  }

  /**
   * Constructs a older http-client version of user/password authentication rest client.
   */
  public static DatabricksRestClientImpl425 createClientWithUserPassword(String username,
      String password, String host,
      String apiVersion, int maxRetry, long retryInterval) {
    DatabricksRestClientImpl425 client = new DatabricksRestClientImpl425(host, apiVersion, maxRetry,
        retryInterval);
    client.initClientWithUserPassword(username, password);
    return client;
  }

  protected void initClientWithUserPassword(String username, String password) {
    try {

      SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
      sslContext.init(null, null, new SecureRandom());

      SSLSocketFactory sf = new SSLSocketFactory(sslContext);
      Scheme httpsScheme = new Scheme("https", HTTPS_PORT, sf);
      SchemeRegistry schemeRegistry = new SchemeRegistry();
      schemeRegistry.register(httpsScheme);
      ClientConnectionManager cm = new BasicClientConnectionManager(schemeRegistry);

      HttpParams params = new BasicHttpParams();
      HttpConnectionParams.setConnectionTimeout(params, CONNECTION_TIMEOUT);
      HttpConnectionParams.setSoTimeout(params, SOCKET_TIMEOUT);

      DefaultHttpClient defaultHttpClient = new DefaultHttpClient(cm, params);
      defaultHttpClient.setHttpRequestRetryHandler(retryHandler);

      defaultHttpClient.getCredentialsProvider().setCredentials(
          new AuthScope(host, HTTPS_PORT),
          new UsernamePasswordCredentials(username, password));

      client = new AutoRetryHttpClient(defaultHttpClient, retryStrategy);

    } catch (Exception e) {
      logger.error("", e);
    }

    url = String.format("https://%s/api/%s", host, apiVersion);
    mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_DEFAULT);
  }

  @Override
  public byte[] performQuery(RequestMethod requestMethod, String path, Map<String, Object> data)
      throws
      DatabricksRestException {

    HttpRequestBase method = null;
    try {
      method = makeHttpMethod(requestMethod, path, data);
      HttpResponse httpResponse = client.execute(method);

      byte[] response = extractContent(httpResponse);

      EntityUtils.consumeQuietly(httpResponse.getEntity());

      return response;

    } catch (DatabricksRestException dre) {
      throw dre;
    } catch (Exception e) {
      throw new DatabricksRestException(e);
    } finally {
      if (method != null) {
        method.releaseConnection();
      }
    }
  }
}