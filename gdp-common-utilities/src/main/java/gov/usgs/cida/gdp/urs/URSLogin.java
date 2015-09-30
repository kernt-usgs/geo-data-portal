package gov.usgs.cida.gdp.urs;

import gov.usgs.cida.gdp.constants.AppConstant;
import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public class URSLogin {

	private static final Logger log = LoggerFactory.getLogger(URSLogin.class);
	// WWW-Authenticate => [Basic realm="Please enter your Earthdata Login username and password"]
	private String encodedBasicLogin;
	private String ursHost;

	public URSLogin() {
		this(AppConstant.URS_USERNAME.toString(),
				AppConstant.URS_PASSWORD.toString().toCharArray(),
				AppConstant.URS_HOST.toString());
	}

	public URSLogin(String username, char[] password, String ursHost) {
		this.encodedBasicLogin = getAuthorizationHeader(username, password);
		this.ursHost = ursHost;
		CookieHandler.setDefault(
				new CookieManager(null, CookiePolicy.ACCEPT_ALL));
	}

	public boolean checkResource(URL resource) {
		boolean loggedIn = false;
		try {
			String currentResource = resource.toString();
			int statusCode = 302;
			int redirects = 0;
			while (statusCode == 302 && redirects < 10) {
				redirects++;
				URL url = new URL(currentResource);
				HttpURLConnection connection = (HttpURLConnection) url.openConnection();
				if (connection instanceof HttpsURLConnection) {
					((HttpsURLConnection)connection).setSSLSocketFactory(trustAll().getSocketFactory());
				}
				connection.setRequestMethod("GET");
				connection.setInstanceFollowRedirects(false);
				connection.setUseCaches(false);
				
				if (this.ursHost.equals(url.getHost())) {
					connection.setRequestProperty("Authorization", encodedBasicLogin);
				}
				
				statusCode = connection.getResponseCode();
				
				if (statusCode == 302) {
					currentResource = connection.getHeaderField("Location");
				}
				connection.disconnect();
			}
			
			if (statusCode == 200) {
				loggedIn = true;
			}
		} catch (IOException | NoSuchAlgorithmException | KeyManagementException ex) {
			log.error("Error getting resource", ex);
		}
		return loggedIn;
	}

	public static String getAuthorizationHeader(String username, char[] password) {
		String basicLogin = username + ":" + new String(password);
		return "Basic " + Base64.getEncoder().encodeToString(basicLogin.getBytes());
	}

	private HttpClient getClient() {
		HttpParams params = new BasicHttpParams();
		params.setParameter("http.protocol.handle-redirects", false);
		HttpClient client = new DefaultHttpClient(params);
		return client;
	}

	// Don't actually use this
	private SSLContext trustAll() throws NoSuchAlgorithmException, KeyManagementException {
		SSLContext context = SSLContext.getInstance("TLS");
		context.init(null, new X509TrustManager[]{new X509TrustManager() {
			@Override
			public void checkClientTrusted(X509Certificate[] chain,
					String authType) throws CertificateException {
			}

			@Override
			public void checkServerTrusted(X509Certificate[] chain,
					String authType) throws CertificateException {
			}

			@Override
			public X509Certificate[] getAcceptedIssuers() {
				return new X509Certificate[0];
			}

		}}, new SecureRandom());
		return context;
	}
}
