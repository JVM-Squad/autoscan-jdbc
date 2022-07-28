package com.firebolt.jdbc.metadata;

import static com.firebolt.jdbc.metadata.FireboltDatabaseMetadataResult.Column;
import static com.firebolt.jdbc.metadata.MetadataColumns.*;
import static com.firebolt.jdbc.type.FireboltDataType.*;
import static java.sql.Types.VARCHAR;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import com.firebolt.jdbc.VersionUtil;
import com.firebolt.jdbc.connection.FireboltConnection;
import com.firebolt.jdbc.connection.settings.FireboltProperties;
import com.firebolt.jdbc.resultset.FireboltColumn;
import com.firebolt.jdbc.type.FireboltDataType;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FireboltDatabaseMetadata extends AbstractDatabaseMetadata {

	private final String url;
	private final FireboltConnection connection;
	private String databaseVersion;
	private static final String PUBLIC_SCHEMA_NAME = "public";
	private static final String INFORMATION_SCHEMA_NAME = "information_schema";
	private static final String CATALOG_SCHEMA_NAME = "catalog";

	public FireboltDatabaseMetadata(String url, FireboltConnection connection) {
		this.url = url;
		this.connection = connection;
	}

	@Override
	public ResultSet getSchemas() throws SQLException {
		return getSchemas(null, null);
	}

	@Override
	public ResultSet getSchemas(String catalog, String schemaPattern) throws SQLException {
		String dbName = connection.getSessionProperties().getDatabase();
		List<String> publicRow = Arrays.asList(PUBLIC_SCHEMA_NAME, dbName);
		List<String> informationSchemaRow = Arrays.asList(INFORMATION_SCHEMA_NAME, dbName);
		List<String> catalogRow = Arrays.asList(CATALOG_SCHEMA_NAME, dbName);
		return FireboltDatabaseMetadataResult.builder()
				.columns(Arrays.asList(Column.builder().name(TABLE_SCHEM).type(STRING).build(),
						Column.builder().name(TABLE_CATALOG).type(STRING).build()))
				.rows(Arrays.asList(publicRow, informationSchemaRow, catalogRow)).build().toResultSet();
	}

	@Override
	public ResultSet getTableTypes() throws SQLException {
		return FireboltDatabaseMetadataResult.builder()
				.columns(Collections.singletonList(Column.builder().name(TABLE_TYPE).type(STRING).build()))
				.rows(Arrays.asList(Arrays.asList("TABLE"), Arrays.asList("VIEW"))).build().toResultSet();
	}

	@Override
	public ResultSet getCatalogs() throws SQLException {
		return FireboltDatabaseMetadataResult.builder()
				.columns(Collections.singletonList(Column.builder().name(TABLE_CAT).type(STRING).build()))
				.rows(Collections.singletonList(Collections.singletonList(connection.getCatalog()))).build()
				.toResultSet();
	}

	@Override
	public Connection getConnection() throws SQLException {
		return connection;
	}

	@Override
	public String getDatabaseProductName() throws SQLException {
		return "Firebolt";
	}

	@Override
	public String getURL() throws SQLException {
		return url;
	}

	@Override
	public String getDriverName() throws SQLException {
		return "Firebolt JDBC Driver";
	}

	@Override
	public boolean supportsTransactionIsolationLevel(int level) throws SQLException {
		return level == Connection.TRANSACTION_NONE;
	}

	@Override
	public ResultSet getColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern)
			throws SQLException {
		List<Column> columns = Arrays.asList(Column.builder().name(TABLE_CAT).type(STRING).build(),
				Column.builder().name(TABLE_SCHEM).type(STRING).build(),
				Column.builder().name(TABLE_NAME).type(STRING).build(),
				Column.builder().name(COLUMN_NAME).type(STRING).build(),
				Column.builder().name(DATA_TYPE).type(INT_32).build(),
				Column.builder().name(TYPE_NAME).type(STRING).build(),
				Column.builder().name(COLUMN_SIZE).type(INT_32).build(),
				Column.builder().name(BUFFER_LENGTH).type(INT_32).build(),
				Column.builder().name(DECIMAL_DIGITS).type(INT_32).build(),
				Column.builder().name(NUM_PREC_RADIX).type(INT_32).build(),
				Column.builder().name(NULLABLE).type(INT_32).build(),
				Column.builder().name(REMARKS).type(STRING).build(),
				Column.builder().name(COLUMN_DEF).type(STRING).build(),
				Column.builder().name(SQL_DATA_TYPE).type(INT_32).build(),
				Column.builder().name(SQL_DATETIME_SUB).type(INT_32).build(),
				Column.builder().name(CHAR_OCTET_LENGTH).type(INT_32).build(),
				Column.builder().name(ORDINAL_POSITION).type(INT_32).build(),
				Column.builder().name(IS_NULLABLE).type(STRING).build(),
				Column.builder().name(SCOPE_CATALOG).type(STRING).build(),
				Column.builder().name(SCOPE_SCHEMA).type(STRING).build(),
				Column.builder().name(SCOPE_TABLE).type(STRING).build(),
				Column.builder().name(SOURCE_DATA_TYPE).type(INT_32).build(),
				Column.builder().name(IS_AUTOINCREMENT).type(STRING).build(),
				Column.builder().name(IS_GENERATEDCOLUMN).type(STRING).build());

		List<List<?>> rows = new ArrayList<>();
		String query = MetadataUtil.getColumnsQuery(schemaPattern, tableNamePattern, columnNamePattern);
		try (Statement statement = this.createStatementWithRequiredPropertiesToQuerySystem();
				ResultSet columnDescription = statement.executeQuery(query)) {
			while (columnDescription.next()) {
				List<?> row;
				FireboltColumn columnInfo = FireboltColumn.of(columnDescription.getString("data_type"),
						columnDescription.getString("column_name"));
				row = Arrays.asList(connection.getCatalog(), columnDescription.getString("table_schema"), // schema
						columnDescription.getString("table_name"), // table name
						columnDescription.getString("column_name"), // column name
						String.valueOf(columnInfo.getDataType().getSqlType()), // sql data type
						columnInfo.getCompactTypeName(), // shorter type name
						String.valueOf(columnInfo.getPrecision()), null, // buffer length (not used, see Javadoc)
						String.valueOf(columnInfo.getScale()), String.valueOf(COMMON_RADIX), // radix
						columnDescription.getInt("is_nullable") == 1 ? columnNullable : columnNoNulls,
						columnInfo.getColumnName(), // description of the column
						StringUtils.isNotBlank(columnDescription.getString("column_default"))
								? columnDescription.getString("column_default")
								: null, // default value for the column: null,
						null, // SQL_DATA_TYPE - reserved for future use (see javadoc)
						null, // SQL_DATETIME_SUB - reserved for future use (see javadoc)
						null, // CHAR_OCTET_LENGTH - The maximum length of binary and character based
						// columns (null for others)
						columnDescription.getInt("ordinal_position"), // The ordinal position starting from 1
						columnDescription.getInt("is_nullable") == 1 ? "YES" : "NO", null, // "SCOPE_CATALOG - Unused
						null, // "SCOPE_SCHEMA" - Unused
						null, // "SCOPE_TABLE" - Unused
						null, // "SOURCE_DATA_TYPE" - Unused
						"NO", // IS_AUTOINCREMENT - Not supported
						"NO"); // IS_GENERATEDCOLUMN - Not supported
				rows.add(row);
			}
			return FireboltDatabaseMetadataResult.builder().rows(rows).columns(columns).build().toResultSet();
		}
	}

	@Override
	public ResultSet getTables(String catalog, String schemaPattern, String tableNamePattern, String[] typesArr)
			throws SQLException {
		List<List<?>> rows = Stream
				.of(this.getTables(catalog, schemaPattern, tableNamePattern, typesArr, false),
						this.getTables(catalog, schemaPattern, tableNamePattern, typesArr, true))
				.flatMap(Collection::stream).collect(Collectors.toList());

		return FireboltDatabaseMetadataResult.builder()
				.columns(Arrays.asList(Column.builder().name(TABLE_CAT).type(STRING).build(),
						Column.builder().name(TABLE_SCHEM).type(STRING).build(),
						Column.builder().name(TABLE_NAME).type(STRING).build(),
						Column.builder().name(TABLE_TYPE).type(STRING).build(),
						Column.builder().name(REMARKS).type(STRING).build(),
						Column.builder().name(TYPE_CAT).type(STRING).build(),
						Column.builder().name(TYPE_SCHEM).type(STRING).build(),
						Column.builder().name(TYPE_NAME).type(STRING).build(),
						Column.builder().name(SELF_REFERENCING_COL_NAME).type(STRING).build(),
						Column.builder().name(REF_GENERATION).type(STRING).build()))
				.rows(rows).build().toResultSet();
	}

	private List<List<?>> getTables(String catalog, String schemaPattern, String tableNamePattern, String[] typesArr,
			boolean isView) throws SQLException {
		List<List<?>> rows = new ArrayList<>();

		String query = isView ? MetadataUtil.getViewsQuery(catalog, schemaPattern, tableNamePattern)
				: MetadataUtil.getTablesQuery(catalog, schemaPattern, tableNamePattern);
		try (Statement statement = this.createStatementWithRequiredPropertiesToQuerySystem();
				ResultSet tables = statement.executeQuery(query)) {

			Set<String> types = typesArr != null ? new HashSet<>(Arrays.asList(typesArr)) : null;
			while (tables.next()) {
				List<String> row = new ArrayList<>();
				row.add(connection.getCatalog());
				row.add(tables.getString("table_schema"));
				row.add(tables.getString("table_name"));
				String tableType = isView ? "VIEW" : "TABLE";
				row.add(tableType);
				for (int i = 3; i < 9; i++) {
					row.add(null);
				}
				if (types == null || types.contains(tableType)) {
					rows.add(row);
				}
			}
		}
		return rows;
	}

	@Override
	public ResultSet getTypeInfo() throws SQLException {
		List<Column> columns = Arrays.asList(Column.builder().name(TYPE_NAME).type(STRING).build(),
				Column.builder().name(DATA_TYPE).type(INT_32).build(),
				Column.builder().name(PRECISION).type(INT_32).build(),
				Column.builder().name(LITERAL_PREFIX).type(STRING).build(),
				Column.builder().name(LITERAL_SUFFIX).type(STRING).build(),
				Column.builder().name(CREATE_PARAMS).type(STRING).build(),
				Column.builder().name(NULLABLE).type(INT_32).build(),
				Column.builder().name(CASE_SENSITIVE).type(U_INT_8).build(),
				Column.builder().name(SEARCHABLE).type(INT_32).build(),
				Column.builder().name(UNSIGNED_ATTRIBUTE).type(U_INT_8).build(),
				Column.builder().name(FIXED_PREC_SCALE).type(U_INT_8).build(),
				Column.builder().name(AUTO_INCREMENT).type(U_INT_8).build(),
				Column.builder().name(LOCAL_TYPE_NAME).type(STRING).build(),
				Column.builder().name(MINIMUM_SCALE).type(INT_32).build(),
				Column.builder().name(MAXIMUM_SCALE).type(INT_32).build(),
				Column.builder().name(SQL_DATA_TYPE).type(INT_32).build(),
				Column.builder().name(SQL_DATETIME_SUB).type(INT_32).build(),
				Column.builder().name(NUM_PREC_RADIX).type(INT_32).build());

		List<List<?>> rows = new ArrayList<>();
		List<FireboltDataType> usableTypes = Arrays.asList(INT_32, INT_64, FLOAT_32, FLOAT_64, STRING, DATE, DATE_32,
				DATE_TIME, DATE_TIME_64, DECIMAL, ARRAY, U_INT_8, TUPLE);
		usableTypes
				.forEach(type -> rows.add(Arrays.asList(type.getDisplayName(), type.getSqlType(),
						type.getDefaultPrecision(), type.getSqlType() == VARCHAR ? "'" : null, // LITERAL_PREFIX - ' for
																								// VARCHAR
						type.getSqlType() == VARCHAR ? "'" : null, // LITERAL_SUFFIX - ' for VARCHAR
						null, // Description of the creation parameters - can be null (can set if needed
								// in the future)
						typeNullableUnknown, // It depends - A type can be nullable or not depending on
						// the presence of the additional keyword Nullable()
						type.isCaseSensitive() ? 1 : 0, type.getSqlType() == VARCHAR ? typeSearchable
								: typePredBasic, /*
													 * SEARCHABLE - LIKE can only be used for VARCHAR
													 */
						type.isSigned() ? 1 : 0, 0, // FIXED_PREC_SCALE - indicates if the type can be a money value.
													// Always
													// false as we do not have a money type
						0, // AUTO_INCREMENT
						null, // LOCAL_TYPE_NAME
						null, // MINIMUM_SCALE - There is no minimum scale
						type.getDefaultScale(), null, // SQL_DATA_TYPE - Not needed - reserved for future use
						null, // SQL_DATETIME_SUB - Not needed - reserved for future use
						COMMON_RADIX)));

		return FireboltDatabaseMetadataResult.builder().columns(columns).rows(rows).build().toResultSet();
	}

	private Statement createStatementWithRequiredPropertiesToQuerySystem() throws SQLException {
		FireboltConnection fireboltConnection = (FireboltConnection) this.getConnection();
		String useStandardSql = fireboltConnection.getSessionProperties().getAdditionalProperties()
				.get("use_standard_sql");
		if ("0".equals(useStandardSql)) {
			FireboltProperties properties = fireboltConnection.getSessionProperties();
			FireboltProperties tmpProperties = FireboltProperties.copy(properties);
			tmpProperties.addProperty("use_standard_sql", "1");
			return fireboltConnection.createStatement(tmpProperties);
		} else {
			return fireboltConnection.createStatement();
		}
	}

	@Override
	public int getDriverMajorVersion() {
		return VersionUtil.getMajorDriverVersion();
	}

	@Override
	public int getDriverMinorVersion() {
		return VersionUtil.getDriverMinorVersion();
	}

	@Override
	public String getDatabaseProductVersion() throws SQLException {
		if (this.databaseVersion == null) {
			String engine = this.connection.getEngine();
			try (Statement statement = createStatementWithRequiredPropertiesToQuerySystem()) {
				String query = MetadataUtil.getDatabaseVersionQuery(engine);
				ResultSet rs = statement.executeQuery(query);
				rs.next();
				this.databaseVersion = rs.getString(1);
			}
		}
		return this.databaseVersion;
	}

	@Override
	public int getDatabaseMajorVersion() throws SQLException {
		return VersionUtil.extractMajorVersion(getDatabaseProductVersion());
	}

	@Override
	public int getDatabaseMinorVersion() throws SQLException {
		return VersionUtil.extractMinorVersion(getDatabaseProductVersion());
	}

	@Override
	public int getJDBCMajorVersion() throws SQLException {
		return VersionUtil.getMajorDriverVersion();
	}

	@Override
	public int getJDBCMinorVersion() throws SQLException {
		return VersionUtil.getDriverMinorVersion();
	}

	@Override
	public String getDriverVersion() throws SQLException {
		return VersionUtil.getDriverVersion();
	}

	@Override
	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		return iface.isAssignableFrom(getClass());
	}

	@Override
	public <T> T unwrap(Class<T> iface) throws SQLException {
		if (iface.isAssignableFrom(getClass())) {
			return iface.cast(this);
		}
		throw new SQLException("Cannot unwrap to " + iface.getName());
	}
}