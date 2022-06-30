package io.firebolt.jdbc.client.query;

import io.firebolt.jdbc.connection.settings.FireboltProperties;
import io.firebolt.jdbc.exception.FireboltException;

import java.io.InputStream;

public interface QueryClient {

  InputStream postSqlQuery(
      String sql, boolean isSelect, String queryId, FireboltProperties fireboltProperties)
      throws FireboltException;

  void postCancelSqlQuery(String queryId, FireboltProperties fireboltProperties)
      throws FireboltException;
}
