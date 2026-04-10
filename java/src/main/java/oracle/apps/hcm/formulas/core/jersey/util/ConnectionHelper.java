package oracle.apps.hcm.formulas.core.jersey.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.nio.charset.StandardCharsets;

import oracle.apps.fnd.applcore.log.AppsLogger;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;

public class ConnectionHelper {
    
    ProxyInfo proxyInfo;

    public ConnectionHelper() {
        initializeProxy();
    }

    private void initializeProxy() {
        if (proxyInfo == null) {
            if (System.getProperties().containsKey("http.proxyHost")) {
                proxyInfo = new ProxyInfo();
                proxyInfo.setHost(System.getProperty("http.proxyHost"));
                proxyInfo.setPort(Integer.getInteger("http.proxyPort", 80));

                if (System.getProperties().containsKey("http.proxyUser")) {
                    proxyInfo.setUsername(System.getProperty("http.proxyUser"));
                    proxyInfo.setPassword(System.getProperty("http.proxyPassword"));
                }
            }
        }
        logInfo("proxyInfo::" + proxyInfo + ", proxyHost::" + System.getProperty("http.proxyHost") + ", proxyUser::" +
                System.getProperty("http.proxyUser") + ", proxyPassword::" + System.getProperty("http.proxyPassword") +
                ", http.proxyPort::" + System.getProperty("http.proxyPort") + ", http.nonProxyHost::" +
                System.getProperty("http.nonProxyHosts"));
    }
    
    private HttpPost buildPostMethod(String pRestURL, String pAuthentificationToken) {
        logDebug("Method Start: buildPostMethod");
        HttpPost postRequest = new HttpPost(pRestURL);
        postRequest.setHeader("Authorization", "Bearer"+ " " + pAuthentificationToken);
        postRequest.setHeader("x-li-forma", "json");
        postRequest.setHeader("Content-Type", "application/json");
        postRequest.setHeader("Accept", "application/json");
        logDebug("Method End: buildPostMethod");
        return postRequest;
    }
    
    
    /**
     * Does the value match the pattern? The pattern may:
     * <ul>
     * <li> begin with "*", so compare with the end of the value
     * <li> end with a "*", so compare with the start of the value
     * <li> contains no "*", so compare with the entire value
     * </ul>
     *
     * The pattern may not contain multiple "*"
     *
     * @param pattern The (possibly) wildcarded pattern to match
     * @param host The value being tested against the pattern
     * @return True if the value matches the pattern, else false
     */
    private boolean isHostPatternMatch(String pattern, String host) {
        boolean matches = false;
        logDebug("isHostPatternMatch starts pattern::" + pattern + ", host::" + host);
        pattern = pattern.toLowerCase().trim();
        host = host.toLowerCase().trim();

        // Cannot match if there is more than one "*" in the pattern
        // String[] tmp = pattern.split("*");
        if (pattern.contains("*")) {
            // Starts with "*" - match end of the value
            if (pattern.startsWith("*")) {
                pattern = pattern.replace("*", "");

                matches = host.endsWith(pattern);
            } else
            // Ends with "*" - match beginning of the value
            if (pattern.endsWith("*")) {
                pattern = pattern.replace("*", "");
                matches = host.startsWith(pattern);
            }
        } else {
            matches = host.equals(pattern);
        }
        logDebug("isHostPatternMatch ends matches::" + matches);
        return matches;
    }

    

    /**
     * isNonProxyHost - finds if the url has a non proxy host
     */
    private boolean isNonProxyHost(String hostUrl) {
        boolean isNonProxyHost = false;
        logDebug("isNonProxyHost starts hostUrl::" + hostUrl);
        String nonProxyHosts = System.getProperty("http.nonProxyHosts");

        if (nonProxyHosts != null && !"".equals(nonProxyHosts)) {
            String host = hostUrl.replace("https://", "");
            host = host.replace("http://", "");
            String[] url = host.split("/");
            host = url[0];
            // Remove optional port number if specified
            int port = host.indexOf(":");
            if (port > 0) {
                host = host.substring(0, port);
            }
            // Look for a match in the nonProxyHosts string
            String[] tmp = nonProxyHosts.split("\\|");
            logDebug("isNonProxyHost nonProxyHosts::" + nonProxyHosts + ", tmp[] ::" + tmp);
            for (int i = 0; i < tmp.length; i++) {
                String pattern = tmp[i];
                if (isHostPatternMatch(pattern, host)) {
                    isNonProxyHost = true;
                    break;
                }
            }
        }
        logDebug("isNonProxyHost ends isNonProxyHost::" + isNonProxyHost);
        return isNonProxyHost;
    }
    
    /**
     * This methods return HttpClient with proxy settings if any otherwise
     * returns back default HttpClient passed.
     *
     * @param pRestURL
     * @return
     */
    private CloseableHttpClient updateProxy(String pRestURL) {
        logDebug("Method Start: updateProxy");
        CloseableHttpClient httpClient = null;
        HttpHost proxy;
        if (proxyInfo != null && !isNonProxyHost(pRestURL)) {
            logDebug("Proxy is enabled . Setting Proxy");
            if (proxyInfo.getUsername() != null) {
                logDebug("Setting Proxy Credentials.");
                proxy = new HttpHost(proxyInfo.getHost(), proxyInfo.getPort());
                Credentials credentials = new UsernamePasswordCredentials(proxyInfo.getUsername(), proxyInfo.getPassword().toCharArray());
                AuthScope authScope = new AuthScope(proxyInfo.getHost(), proxyInfo.getPort());
                BasicCredentialsProvider provider = new BasicCredentialsProvider();
                provider.setCredentials(authScope, credentials);
                httpClient = HttpClients.custom()
                                        .setProxy(proxy)
                                        .setDefaultCredentialsProvider(provider)
                                        .build();

                logDebug("Setting Proxy Credentials Done.");
            }
            logDebug("Setting Proxy Done");
        }

        if (httpClient == null) {
            httpClient = HttpClientBuilder.create()
                                          .useSystemProperties()
                                          .build();
        }

        logDebug("Method End: updateProxy");
        return httpClient;
    }


    
    /**
     * doPost
     * @param pRestURL
     * @param pJsonContent
     * @param authenticationToken
     * @return Object
     * 
     */
    public Object doPost(String pRestURL, String pJsonContent, String authenticationToken) throws Exception {
        logDebug("Method Start: doPost");
        HttpPost postRequest = null;
        CloseableHttpClient httpClient = null;
        try {
            

            postRequest = buildPostMethod(pRestURL, authenticationToken);
            StringEntity requestEntity =
                new StringEntity(pJsonContent, ContentType.APPLICATION_JSON, "UTF-8", false);
            postRequest.setEntity(requestEntity);
            logDebug("doPost: updating proxy");
            httpClient = updateProxy(pRestURL);
            
            long time1 = System.currentTimeMillis();
            HttpResponse response = httpClient.execute(postRequest);
            logDebug("Total time to get AI suggestions: " + (System.currentTimeMillis() - time1));
            int responseStatus = response.getCode();
            ByteArrayOutputStream bos=new ByteArrayOutputStream();
            ((ClassicHttpResponse) response).getEntity().writeTo(bos);
            String stringUsingnewcode=bos.toString(StandardCharsets.UTF_8.name());
            String responseAsString=stringUsingnewcode;
           // String responseAsString = EntityUtils.toString(((ClassicHttpResponse) response).getEntity(), "UTF-8");
            
            logDebug("String from response after custom code=   "+ stringUsingnewcode+"\n");
           // logDebug("String from response using existing code=   "+ responseAsString+"\n");
            
            
            logDebug("Response Status: " + responseStatus);
            if (HttpStatus.SC_OK != responseStatus) {
                logError("ConnectionHelper.doPost StateCode: " + responseStatus + 
                         " | StatusText: " + response.getReasonPhrase() + 
                         "\nResponse Body: " + responseAsString);
                /*CAUTION: Do not change the format of the RuntimeException constructor string as integrators are  
                 relying on this format to process for error code and error message.*/
                throw new RuntimeException("ConnectionHelper.doPost StateCode: " + responseStatus + 
                         " | StatusText: " + response.getReasonPhrase() + 
                         "\nResponse Body: " + responseAsString);
            }
            logDebug("Method Just Before End: ConnectionHelper.doPost");
            return responseAsString;

//        } catch (ParseException e) {
//            logException(e);
//            throw new RuntimeException(e);
        } catch (IOException e) {
            logException(e);
            throw new RuntimeException(e);
        } finally {
            closeHttpClient(httpClient);
            logDebug("Method End: doPost");
        }
    }
    

    private void closeHttpClient(CloseableHttpClient httpClient) {
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (Exception e) {
                logException(e);
            }
        }
    }

    
    private void logDebug(String pMessage) {
        if (AppsLogger.isEnabled(AppsLogger.FINER)) {
            AppsLogger.write(this, pMessage, AppsLogger.FINER);
        }
    }

    private static void logInfo(String pMessage) {
        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(ConnectionHelper.class, pMessage, AppsLogger.INFO);
        }
    }

    // WARNING-level helpers — PSR's java-appslogger-severe-level rule rejects
    // AppsLogger.SEVERE outside catch blocks, and static analysis can't tell
    // that these helpers are only called from catch contexts. If you genuinely
    // need SEVERE for a caught Throwable, inline the AppsLogger.write call
    // inside the catch block instead of going through this helper.

    private void logError(String pMessage) {
        if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
            AppsLogger.write(this, pMessage, AppsLogger.WARNING);
        }
    }

    private void logException(Exception pException) {
        String shortStackTrace = pException.getMessage();
        String fullStackTrace = ExceptionUtils.getStackTrace(pException);
        if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
            AppsLogger.write(this, shortStackTrace, AppsLogger.WARNING);
            AppsLogger.write(this, fullStackTrace, AppsLogger.WARNING);
        }
    }

    class ProxyInfo {
        private String pHost;
        private Integer pPort;
        private String pUsername;
        private String pPassword;

        public void setHost(String pHost) {
            this.pHost = pHost;
        }

        public String getHost() {
            return pHost;
        }

        public void setPort(Integer pPort) {
            this.pPort = pPort;
        }

        public Integer getPort() {
            return pPort;
        }

        public void setUsername(String pUsername) {
            this.pUsername = pUsername;
        }

        public String getUsername() {
            return pUsername;
        }

        public void setPassword(String pPassword) {
            this.pPassword = pPassword;
        }

        public String getPassword() {
            return pPassword;
        }
    }
}