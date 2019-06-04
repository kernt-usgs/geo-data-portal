URS Authentication via GDP
==========================

The URSLoginProvider class implements HttpLoginProvider for the URS login scheme.
There is currently an example use of this in the InterrogateDatasetAlgorithm that is part
of the gdp-utility-wps.  The general concept is to set up a proxy that will intermediate
all web requests to services that are protected by URS services.

In order to allow access to URS, there are three variables that must be set in the environment

`gdp.login.urs.username`
`gdp.login.urs.password`
`gdp.login.urs.host`

and a client must register the provider with the proxy and route through that path.  The
InterrogateDatasetAlgorithm handles registration and generating the proxy URL, but this
should most likely be done outside of the application logic.  That will be left for
future clients to implement.

Registration
------------

The proxy implementation is called DynamicRegistrationProxyServlet and extends a generic
proxy servlet (borrowed from the GDP proxy).  This allows for a single servlet to maintain
configuration for several routes each with a specific key.  Access to this registration
is gained by the following

```java
ProxyRegistrator registrator = ProxyRegistrator.getInstance();
ProxyRegistry proxyRegistry = registrator.getRegistry(REGISTRY_NAME);
```

and then routes can be added via

```java
URSLoginProvider provider= new URSLoginProvider();
provider.checkResource(resource);
proxyRegistry.setRegistryEntry(key, proxyTo, provider);
```

(Note: the checkResource call in this case has a side effect that the login is performed,
so it is required in order to fully initialize the provider)

Proxying
--------

To call the target resource, replace the proxyTo part of the url with the
proxy path, something like:

```java
proxiedPath.append(server.getProtocol())
	.append("://")
	.append(server.getHostname())
	.append(":")
	.append(server.getHostport())
	.append("/")
	.append(server.getWebappPath())
	.append("/")
	.append(REGISTRY_NAME)
	.append("/")
	.append(key) // up to here is the proxyPath
	.append(targetPath) // this is the rest of the request
```

One thing to note here is that the login being used is not that of the user, but
a service account set up on the server.  If per user authorization is needed,
another provider will be required, and there may need some additional code at the
proxy level.