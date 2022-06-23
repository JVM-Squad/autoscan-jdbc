package io.firebolt.jdbc.statement;

import io.firebolt.jdbc.connection.FireboltConnectionImpl;
import io.firebolt.jdbc.connection.settings.FireboltProperties;
import io.firebolt.jdbc.resultset.FireboltResultSet;
import io.firebolt.jdbc.service.FireboltQueryService;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FireboltStatementImplTest {

  @Mock private FireboltQueryService fireboltQueryService;

  @Captor ArgumentCaptor<FireboltProperties> fireboltPropertiesArgumentCaptor;

  @Captor ArgumentCaptor<String> sqlArgumentCaptor;

  @Captor ArgumentCaptor<String> queryIdArgumentCaptor;

  @Captor ArgumentCaptor<Boolean> booleanArgumentCaptor;

  @Test
  void shouldExecuteQueryAndCreateResultSet() throws SQLException {
    try (MockedConstruction<FireboltResultSet> mocked =
        Mockito.mockConstruction(FireboltResultSet.class)) {
      FireboltProperties fireboltProperties =
          FireboltProperties.builder().additionalProperties(new HashMap<>()).build();
      FireboltStatementImpl fireboltStatement =
          FireboltStatementImpl.builder()
              .fireboltQueryService(fireboltQueryService)
              .sessionProperties(fireboltProperties)
              .accessToken("token")
              .build();

      when(fireboltQueryService.executeQuery(
              eq("show database"), anyBoolean(), anyString(), eq("token"), eq(fireboltProperties)))
          .thenReturn(mock(InputStream.class));
      fireboltStatement.executeQuery("show database");
      assertTrue(fireboltProperties.getAdditionalProperties().isEmpty());
      verify(fireboltQueryService)
          .executeQuery(
              eq("show database"), anyBoolean(), anyString(), eq("token"), eq(fireboltProperties));
      assertEquals(1, mocked.constructed().size());
      assertEquals(-1, fireboltStatement.getUpdateCount());
    }
  }

  @Test
  void shouldExtractAdditionalProperties() throws SQLException {
    try (MockedConstruction<FireboltResultSet> mockedResultSet =
        Mockito.mockConstruction(FireboltResultSet.class)) {
      FireboltConnectionImpl connection = mock(FireboltConnectionImpl.class);
      FireboltProperties fireboltProperties =
          FireboltProperties.builder().additionalProperties(new HashMap<>()).build();

      FireboltStatementImpl fireboltStatement =
          FireboltStatementImpl.builder()
              .fireboltQueryService(fireboltQueryService)
              .sessionProperties(fireboltProperties)
              .accessToken("token")
              .connection(connection)
              .build();

      fireboltStatement.executeQuery("set custom_1 = 1");
      verifyNoMoreInteractions(fireboltQueryService);
      verify(connection).addProperty(new ImmutablePair<>("custom_1", "1"));
      assertEquals(0, mockedResultSet.constructed().size());
    }
  }

  @Test
  void shouldCancelByQueryWhenAggressiveCancelIsEnabled() throws SQLException, IOException {
    try (MockedConstruction<FireboltResultSet> mockedResultSet =
        Mockito.mockConstruction(FireboltResultSet.class)) {

      FireboltProperties fireboltProperties =
          FireboltProperties.builder().database("db").additionalProperties(new HashMap<>()).build();
      fireboltProperties.addProperty("aggressive_cancel", "1");

      FireboltStatementImpl fireboltStatement =
          FireboltStatementImpl.builder()
              .fireboltQueryService(fireboltQueryService)
              .sessionProperties(fireboltProperties)
              .accessToken("token")
              .build();

      when(fireboltQueryService.executeQuery(any(), anyBoolean(), any(), any(), any()))
          .thenReturn(mock(InputStream.class));
      fireboltStatement.executeQuery("SHOW DATABASE"); // call once to create queryId
      fireboltStatement.cancel();
      verify(fireboltQueryService, times(2))
          .executeQuery(
              sqlArgumentCaptor.capture(),
              booleanArgumentCaptor.capture(),
              queryIdArgumentCaptor.capture(),
              any(),
              fireboltPropertiesArgumentCaptor.capture());
      assertEquals(
          "KILL QUERY ON CLUSTER sql_cluster WHERE initial_query_id='"
              + queryIdArgumentCaptor.getAllValues().get(0)
              + "'",
          sqlArgumentCaptor.getAllValues().get(1));
      assertEquals(
          "0",
          fireboltPropertiesArgumentCaptor
              .getValue()
              .getAdditionalProperties()
              .get("use_standard_sql"));
      assertFalse(booleanArgumentCaptor.getValue());
    }
  }

  @Test
  void shouldCancelByApiCallWhenAggressiveCancelIsDisabled() throws SQLException, IOException {
    try (MockedConstruction<FireboltResultSet> mockedResultSet =
        Mockito.mockConstruction(FireboltResultSet.class)) {

      FireboltProperties fireboltProperties =
          FireboltProperties.builder().database("db").additionalProperties(new HashMap<>()).build();
      fireboltProperties.addProperty("aggressive_cancel", "0");

      FireboltStatementImpl fireboltStatement =
          FireboltStatementImpl.builder()
              .fireboltQueryService(fireboltQueryService)
              .sessionProperties(fireboltProperties)
              .accessToken("token")
              .build();

      when(fireboltQueryService.executeQuery(any(), anyBoolean(), any(), any(), any()))
          .thenReturn(mock(InputStream.class));
      fireboltStatement.executeQuery("SHOW DATABASE"); // call once to create queryId
      fireboltStatement.cancel();
      verify(fireboltQueryService).cancelQuery(any(), eq(fireboltProperties));
    }
  }

  @Test
  void shouldCloseInputStreamOnClose() throws SQLException, IOException {
    try (MockedConstruction<FireboltResultSet> mockedResultSet =
        Mockito.mockConstruction(FireboltResultSet.class)) {
      FireboltProperties fireboltProperties =
          FireboltProperties.builder().additionalProperties(new HashMap<>()).build();
      FireboltConnectionImpl connection = mock(FireboltConnectionImpl.class);
      FireboltStatementImpl fireboltStatement =
          FireboltStatementImpl.builder()
              .fireboltQueryService(fireboltQueryService)
              .sessionProperties(fireboltProperties)
              .connection(connection)
              .accessToken("token")
              .build();

      when(fireboltQueryService.executeQuery(any(), anyBoolean(), any(), any(), any()))
          .thenReturn(mock(InputStream.class));

      fireboltStatement.executeQuery("show database");
      fireboltStatement.close();
      verify(mockedResultSet.constructed().get(0)).close();
      verify(connection).removeClosedStatement(fireboltStatement);
    }
  }
}
