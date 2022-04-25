package io.firebolt.jdbc.connection.settings;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PropertiesToFireboltPropertiesTransformerTest {

  private final PropertiesToFireboltPropertiesTransformer transformer =
      new PropertiesToFireboltPropertiesTransformer();

  @Test
  void shouldHaveDefaultPropertiesWhenOnlyTheRequiredFieldsAreSpecified() {
    FireboltProperties expectedDefaultProperties =
        FireboltProperties.builder()
            .database("db")
            .bufferSize(65536)
            .sslCertificatePath("")
            .sslMode("strict")
            .path("/")
            .port(443) // 443 by default as SSL is enabled by default
            .compress(1)
            .enableConnectionPool(0)
            .user(null)
            .password(null)
            .host("https://host") // host is appended with https:// because SSL is enabled
            .ssl(true)
            .customProperties(new Properties())
            .account(null)
            .engine(null)
            .maxConnectionsPerRoute(500)
            .timeToLiveMillis(60000)
            .validateAfterInactivityMillis(3000)
            .maxConnectionsTotal(10000)
            .maxRetries(3)
            .outputFormat(null)
            .socketTimeoutMillis(0)
            .connectionTimeoutMillis(0)
            .keepAliveTimeoutMillis(Integer.MAX_VALUE)
            .clientBufferSize(65536)
            .build();

    Properties properties = new Properties();
    properties.put("host", "host");
    properties.put("database", "db");
    assertEquals(
        expectedDefaultProperties,
        new PropertiesToFireboltPropertiesTransformer().apply(properties));
  }

  @Test
  void shouldHaveAllTheSpecifiedCustomProperties() {
    Properties properties = new Properties();
    properties.put("buffer_size", "51");
    properties.put("socket_timeout_millis", "20");
    properties.put("ssl", "true");
    properties.put("port", "13");
    properties.put("host", "myDummyHost");
    properties.put("database", "myDb");
    properties.put("ssl_certificate_path", "root_cert");
    properties.put("ssl_mode", "none");
    properties.put("path", "/example");
    properties.put("someCustomProperties", "custom_value");
    properties.put("compress", "0");

    Properties customProperties = new Properties();
    customProperties.put("someCustomProperties", "custom_value");

    FireboltProperties expectedDefaultProperties =
        FireboltProperties.builder()
            .bufferSize(51)
            .sslCertificatePath("root_cert")
            .sslMode("none")
            .path("/example")
            .port(13)
            .database("myDb")
            .compress(0)
            .enableConnectionPool(0)
            .user(null)
            .password(null)
            .host("https://myDummyHost")
            .ssl(true)
            .customProperties(customProperties)
            .account(null)
            .engine(null)
            .maxConnectionsPerRoute(500)
            .timeToLiveMillis(60000)
            .validateAfterInactivityMillis(3000)
            .maxConnectionsTotal(10000)
            .maxRetries(3)
            .outputFormat(null)
            .socketTimeoutMillis(20)
            .connectionTimeoutMillis(0)
            .keepAliveTimeoutMillis(Integer.MAX_VALUE)
            .clientBufferSize(65536)
            .build();
    assertEquals(expectedDefaultProperties, transformer.apply(properties));
  }

  @Test
  void shouldUsePathParamAsDb() {
    Properties properties = new Properties();
    properties.put("path", "/example");
    properties.put("host", "host");

    assertEquals("example", transformer.apply(properties).getDatabase());
  }

  @Test
  void shouldThrowExceptionWhenNoDbProvided() {
    Properties properties = new Properties();
    properties.put("host", "host");

    assertThrows(IllegalArgumentException.class, () -> transformer.apply(properties));
  }

  @Test
  void shouldThrowExceptionWhenHostIsNotProvided() {
    Properties properties = new Properties();
    assertThrows(IllegalArgumentException.class, () -> transformer.apply(properties));
  }

  @Test
  void shouldThrowExceptionWhenDbPathFormatIsInvalid() {
    Properties properties = new Properties();
    properties.put("path", "");
    properties.put("host", "host");

    assertThrows(IllegalArgumentException.class, () -> transformer.apply(properties));
  }
}
