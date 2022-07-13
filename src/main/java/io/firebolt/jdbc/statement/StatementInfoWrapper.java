package io.firebolt.jdbc.statement;

import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.tuple.Pair;

@Value
@Builder
public class StatementInfoWrapper {
  String sql;
  String id;
  StatementType type;
  String cleanSql;
  Pair<String, String> param;
  String tablePair;

  public enum StatementType {
    PARAM_SETTING, // SET
    QUERY, // eg: SELECT, SHOW
    NON_QUERY // eg: INSERT
  }
}
