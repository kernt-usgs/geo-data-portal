package gov.usgs.cida.gdp.urs;

import gov.usgs.cida.gdp.constants.AppConstant;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
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

	private static final int SSL_PORT = 443;
	private CredentialsProvider credentialStore;
	private CookieStore cookieStore;
	private KeyStore trustStore;

	public URSLogin() {
		this(AppConstant.URS_USERNAME.toString(),
				AppConstant.URS_PASSWORD.toString(),
				AppConstant.URS_HOST.toString());
	}

	public URSLogin(String username, String password, String ursHost) {
		this.credentialStore = new BasicCredentialsProvider();
		this.credentialStore.setCredentials(new AuthScope(ursHost, SSL_PORT),
				new UsernamePasswordCredentials(username, password));
		this.cookieStore = new BasicCookieStore();
		try {
			this.trustStore = KeyStore.getInstance("JKS");
			initSocketFactory(trustStore);
		} catch (KeyStoreException ex) {
			log.error("Could not get access to keystore");
			this.trustStore = null;
		}
	}

	public boolean checkResource(URL resource) {
		boolean loggedIn = false;
		try {
			String currentResource = resource.toString();
			int statusCode = 302;
			int redirects = 0;
			while (statusCode == 302 && redirects < 10) {
				redirects++;

				HttpClient client = getClient();
				HttpGet get = new HttpGet(currentResource);

				HttpResponse response = client.execute(get);

				statusCode = response.getStatusLine().getStatusCode();

				if (statusCode == 302) {
					Header location = response.getLastHeader("Location");
					if (location != null) {
						currentResource = location.getValue();
					} else {
						throw new IllegalStateException("Received redirect without Location header");
					}
				}
			}

			if (statusCode == 200) {
				loggedIn = true;
			}
		} catch (IOException | IllegalStateException | KeyManagementException | CertificateException |
				KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException ex) {
			log.error("Error getting resource", ex);
		}
		return loggedIn;
	}

	public static String getAuthorizationHeader(String username, char[] password) {
		String basicLogin = username + ":" + new String(password);
		return "Basic " + Base64.getEncoder().encodeToString(basicLogin.getBytes());
	}

	private HttpClient getClient() throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException, IOException, CertificateException {
		HttpParams params = new BasicHttpParams();
		params.setParameter("http.protocol.handle-redirects", false);
		
		//this.trustStore.load(null, null);
		
		SSLSocketFactory socketFactory = new SSLSocketFactory(new TrustStrategy() {

			@Override
			public boolean isTrusted(X509Certificate[] xcs, String string) throws CertificateException {
				return true;
			}
			
		}, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
		//socketFactory.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
		
		SchemeRegistry registry = new SchemeRegistry();
		registry.register(new Scheme("http", 80, new PlainSocketFactory()));
		registry.register(new Scheme("https", 443, socketFactory));

		ClientConnectionManager ccm = new ThreadSafeClientConnManager(params, registry);
		DefaultHttpClient client = new DefaultHttpClient(ccm, params);
		client.setCookieStore(cookieStore);
		client.setCredentialsProvider(credentialStore);
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
	
	private SSLSocketFactory initSocketFactory(KeyStore trustStore) {
		//SSLContexts.custom()
		SSLSocketFactory factory = null;
		char[] password = "changeit".toCharArray();
		try (InputStream keystoreFile = this.getClass().getClassLoader().getResourceAsStream("URStrust.pks")) {
			trustStore.load(keystoreFile, password);
			SSLContext ssl = SSLContext.getInstance("TLS");
			KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
			TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
			kmf.init(trustStore, password);
			tmf.init(trustStore);
//			ssl.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
//			factory = new SSLSocketFactory(trustStore)
//			
		} catch (IOException | CertificateException | NoSuchAlgorithmException |
				KeyStoreException | UnrecoverableKeyException ex) {
			throw new RuntimeException("Could not load keystore for SSL");
		}
		return factory;
	}
	
}
