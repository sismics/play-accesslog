# play-accesslog plugin

This module allows simple logging of HTTP requests to the console.

It is based off Brian Nesbitt's logging module: https://www.playframework.com/modules/accesslog
# How to use

####  Add the dependency to your `dependencies.yml` file

```
require:
    - accesslog -> accesslog 1.0.0

repositories:
    - sismicsNexusRaw:
        type: http
        artifact: "https://nexus.sismics.com/repository/sismics/[module]-[revision].zip"
        contains:
            - accesslog -> *

```
####  Add the configuration parameter to your `application.conf` file

```
accesslog.enabled=true
```

####  Enable logging of body of POST requests

```
accesslog.logPost=true
```

####  Enable logging of HTTP request headers


```
accesslog.logRequestHeaders=true
```

####  Enable logging of body of HTTP response

The following parameter will log the body of HTTP responses, only if the response has an error status code (4xx or 5xx)

```
accesslog.logResponse=true
```

# Access the logs console

The logs console allows you to modify configuration parameters, and monitor log events at runtime.

Add the following parameter to enable the logs console:

```
accesslog.console.enabled=true
```

Note: the console is enabled by default in Dev mode.

![Logs console](/doc/accesslog.png?raw=true)

### Secure the logs console

Add the following parameter to secure the logs console

```
accesslog.console.username=console
accesslog.console.password=pass1234
```

#  Warning

This module might have some impact on performances. It is only intended for logging in developpement and testing environments, and shouldn't be used in production.

# License

This software is released under the terms of the Apache License, Version 2.0. See `LICENSE` for more
information or see <https://opensource.org/licenses/Apache-2.0>.
