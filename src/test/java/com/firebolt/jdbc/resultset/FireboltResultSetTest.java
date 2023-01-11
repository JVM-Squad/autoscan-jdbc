package com.firebolt.jdbc.resultset;

import static java.sql.ResultSet.TYPE_FORWARD_ONLY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.TimeZone;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junitpioneer.jupiter.DefaultTimeZone;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import com.firebolt.jdbc.LoggerUtil;
import com.firebolt.jdbc.exception.FireboltException;
import com.firebolt.jdbc.statement.FireboltStatement;

@ExtendWith(MockitoExtension.class)
@DefaultTimeZone("UTC")
class FireboltResultSetTest {

	private static final TimeZone UTC_TZ = TimeZone.getTimeZone("UTC");
	private final static Calendar EST_CALENDAR = Calendar.getInstance(TimeZone.getTimeZone("EST"));
	private final static Calendar UTC_CALENDAR = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
	private InputStream inputStream;
	private ResultSet resultSet;
	@Mock
	private FireboltStatement fireboltStatement;

	@AfterEach
	void afterEach() throws SQLException {
		try {
			inputStream.close();
		} catch (Exception e) {
		}
		resultSet.close();
	}

	@Test
	void shouldReturnMetadata() throws SQLException {
		// This only tests that Metadata is available with the resultSet.
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535, false,
				fireboltStatement, false);
		assertNotNull(resultSet.getMetaData());
		assertEquals("any_name", resultSet.getMetaData().getTableName(1));
		assertEquals("array_db", resultSet.getMetaData().getCatalogName(1));
	}

	@Test
	void shouldNotBeLastWhenThereIsMoreData() throws SQLException {
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		assertFalse(resultSet.isLast());
	}

	@Test
	void shouldNotBeLastAtLastLine() throws SQLException {
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		resultSet.next();
		resultSet.next();
		assertTrue(resultSet.isLast());
	}

	@Test
	void shouldReadAllTheData() throws SQLException {
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		resultSet.next();
		assertEquals(1L, resultSet.getObject(1));
		String[][][] firstArray = { { { "1", "2" }, { "3", "4" } } };
		assertArrayEquals(firstArray, ((String[][][]) resultSet.getObject(2)));

		resultSet.next();
		assertEquals(2L, resultSet.getObject(1));
		String[][][] secondArray = { { { "1", "2" }, { "3", "4" } }, { { "5", "6" }, { "7", "8", null } } };
		assertArrayEquals(secondArray, ((String[][][]) resultSet.getObject(2)));
	}

	@Test
	void shouldBeBeforeFirstIfFirstRowNotRead() throws SQLException {
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		assertTrue(resultSet.isBeforeFirst());
		resultSet.next();
		assertFalse(resultSet.isBeforeFirst());
	}

	@Test
	void shouldGetBigDecimal() throws SQLException {
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		resultSet.next();
		assertEquals(new BigDecimal("1"), resultSet.getBigDecimal(1));
		assertEquals(new BigDecimal("1"), resultSet.getBigDecimal("id"));
	}

	@Test
	void shouldGetBigDecimalWithScale() throws SQLException {
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		resultSet.next();
		assertEquals(new BigDecimal("1").setScale(2, RoundingMode.HALF_UP), resultSet.getBigDecimal(1, 2));
		assertEquals(new BigDecimal("1").setScale(2, RoundingMode.HALF_UP), resultSet.getBigDecimal("id", 2));
	}

	@Test
	void shouldBeFirstWhenNextRecordIsTheFirstToRead() throws SQLException {
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		resultSet.next();
		assertTrue(resultSet.isFirst());
	}

	@Test
	void shouldBeAfterReadingTheLast() throws SQLException {
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		while (resultSet.next()) {
			// just read everything
		}
		assertTrue(resultSet.isAfterLast());
		assertFalse(resultSet.isLast());
	}

	@Test
	void shouldReturnFalseWhenCallingWasNullAfterRead() throws SQLException {
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		resultSet.next();
		resultSet.getInt(1);
		assertFalse(resultSet.wasNull());
	}

	@Test
	void shouldThrowExceptionWhenCallingWasNullBeforeAnyGet() throws SQLException {
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		resultSet.next();
		assertThrows(IllegalArgumentException.class, () -> resultSet.wasNull(),
				"A column must be read before checking nullability");
	}

	@Test
	void shouldReturnTrueWhenLastValueGotWasNull() throws SQLException {
		// This only tests that Metadata is available with the resultSet.
		inputStream = getInputStreamWithNulls();
		resultSet = new FireboltResultSet(inputStream);
		resultSet.next();
		resultSet.getObject(2);
		assertTrue(resultSet.wasNull());
		resultSet.next();
		assertTrue(resultSet.wasNull());
	}

	@Test
	void shouldReturnInt() throws SQLException {
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		resultSet.next();
		assertEquals(1, resultSet.getInt(1));
		assertEquals(1, resultSet.getInt("id"));
		resultSet.next();
		assertEquals(2, resultSet.getInt(1));
		assertEquals(2, resultSet.getInt("id"));
	}

	@Test
	void shouldReturnFloat() throws SQLException {
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "a_table", "a_db", 65535);
		resultSet.next();
		assertEquals(14.6f, resultSet.getFloat(6));
		assertEquals(14.6f, resultSet.getFloat("a_double"));
		resultSet.next();
		assertEquals(0, resultSet.getFloat(6));
		assertEquals(0, resultSet.getFloat("a_double"));
	}

	@Test
	void shouldReturnDouble() throws SQLException {
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "a_table", "a_db", 65535);
		resultSet.next();
		assertEquals(14.6d, resultSet.getDouble(6));
		assertEquals(14.6d, resultSet.getDouble("a_double"));
		resultSet.next();
		assertEquals(0, resultSet.getDouble(6));
		assertEquals(0, resultSet.getDouble("a_double"));
	}

	@Test
	void shouldReturnString() throws SQLException {
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		resultSet.next();
		assertEquals("Taylor's Prime Steak House", resultSet.getString(3));
		assertEquals("Taylor's Prime Steak House", resultSet.getString("name"));
	}

	@Test
	void shouldReturnShort() throws SQLException {
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		resultSet.next();
		assertEquals(5, resultSet.getShort("an_integer"));
		assertEquals(5, resultSet.getShort(7));
		resultSet.next();
		assertEquals(0, resultSet.getShort("an_integer"));
		assertEquals(0, resultSet.getShort(7));
	}

	@Test
	void shouldReturnTypeForward() throws SQLException {
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		assertEquals(TYPE_FORWARD_ONLY, resultSet.getType());
	}

	@Test
	void shouldReturnBytes() throws SQLException {
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		resultSet.next();
		assertArrayEquals("Taylor\\'s Prime Steak House".getBytes(), resultSet.getBytes(3));
		assertArrayEquals("Taylor\\'s Prime Steak House".getBytes(), resultSet.getBytes("name"));
		resultSet.next();
		assertNull(resultSet.getBytes(3));
	}

	@Test
	void shouldReturnNullWhenValueIsNull() throws SQLException {
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		resultSet.next();
		resultSet.next();
		assertNull(resultSet.getBytes(3));
	}

	@Test
	void shouldReturnByte() throws SQLException {
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		resultSet.next();
		assertEquals(1, resultSet.getByte(1));
		assertEquals(1, resultSet.getByte("id"));
	}

	@Test
	void shouldReturn0ByteWhenValueIsNull() throws SQLException {
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		resultSet.next();
		resultSet.next();
		assertEquals((byte) 0, resultSet.getByte(3));
	}

	@Test
	void shouldReturnNullWhenValueStringIsNull() throws SQLException {
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		resultSet.next();
		resultSet.next(); // second line contains \N which represents a null value
		assertNull(resultSet.getString(3));
		assertNull(resultSet.getString("name"));
	}

	@Test
	void shouldReturnDate() throws SQLException {
		Date expectedDate = Date.valueOf(LocalDate.of(2022, 5, 10));
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		resultSet.next();

		assertEquals(expectedDate, resultSet.getDate(4));
		assertEquals(expectedDate, resultSet.getDate("a_date"));
	}

	@Test
	void shouldReturnTimeStamp() throws SQLException, ParseException {
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
		java.util.Date parsedDate = dateFormat.parse("2022-05-10 13:01:02");
		Timestamp timestamp = new Timestamp(parsedDate.getTime());

		inputStream = getInputStreamWithDates();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		resultSet.next();

		assertEquals(timestamp, resultSet.getTimestamp(2));
		assertEquals(timestamp, resultSet.getTimestamp("a_datetime"));
	}

	@Test
	void shouldReturnBoolean() throws SQLException {
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		resultSet.next();

		assertTrue(resultSet.getBoolean(5));
		assertTrue(resultSet.getBoolean("is_online"));
		resultSet.next();
		assertFalse(resultSet.getBoolean(5));
		assertFalse(resultSet.getBoolean("is_online"));
	}

	@Test
	void shouldReturnTime() throws SQLException {
		Time expectedTime = new Time(
				ZonedDateTime.of(1970, 1, 1, 13, 1, 2, 0, UTC_TZ.toZoneId()).toInstant().toEpochMilli());
		inputStream = getInputStreamWithDates();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		resultSet.next();
		assertEquals(expectedTime, resultSet.getTime(2));
		assertEquals(expectedTime, resultSet.getTime("a_datetime"));
	}

	@Test
	void shouldGetArray() throws SQLException {
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		resultSet.next();
		String[][][] firstArray = { { { "1", "2" }, { "3", "4" } } };
		assertArrayEquals(firstArray, ((String[][][]) (resultSet.getArray("arr")).getArray()));
		assertArrayEquals(firstArray, ((String[][][]) (resultSet.getArray(2)).getArray()));
	}

	@Test
	void shouldReturnUnescapedString() throws SQLException {
		String expected = "[0] [Aggregate] GroupBy: [] Aggregates: [COUNT(DISTINCT FB_NODE_2.a1), APPROX_COUNT_DISTINCT(FB_NODE_2.a1)] @ FB_NODE_1\n \\_[1] [StoredTable] Name: 'ft', used 1/1 column(s) FACT @ FB_NODE_2\n";
		inputStream = getInputStreamWitExplain();
		resultSet = new FireboltResultSet(inputStream, "any", "any", 65535);
		resultSet.next();
		assertEquals(expected, resultSet.getString(1));
		assertEquals(expected, resultSet.getObject(1));
	}

	@Test
	void shouldThrowException() throws SQLException {
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "empty_test", "empty_test", 65535);
		resultSet.next();
		resultSet.next(); // second line contains \N which represents a null value
		assertNull(resultSet.getString(3));
		assertNull(resultSet.getString("name"));
	}

	@Test
	void shouldThrowExceptionWhenCheckingWasNullAfterClose() throws SQLException {
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		resultSet.close();
		assertThrows(SQLException.class, resultSet::wasNull);
	}

	@Test
	void shouldThrowExceptionWhenGettingValueAfterClose() throws SQLException {
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		resultSet.close();
		assertThrows(SQLException.class, () -> resultSet.getObject(1));
	}

	@Test
	void shouldThrowSQLExceptionWhenGettingValueWithInvalidColumnIndex() throws SQLException {
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		resultSet.close();
		assertThrows(SQLException.class, () -> resultSet.getObject("INVALID_COLUMN"));
	}

	@Test
	void shouldCloseStream() throws SQLException {
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		assertFalse(resultSet.isClosed());
		resultSet.close();
		assertTrue(resultSet.isClosed());
	}

	@Test
	void shouldCloseStatementWhenCloseOnCompletion() throws SQLException {
		when(fireboltStatement.isCloseOnCompletion()).thenReturn(true);
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535, fireboltStatement);
		resultSet.close();
		verify(fireboltStatement).close();
	}

	@Test
	void shouldNotCloseStatementWhenNotCloseOnCompletion() throws SQLException {
		when(fireboltStatement.isCloseOnCompletion()).thenReturn(false);
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535, fireboltStatement);
		resultSet.close();
		verifyNoMoreInteractions(fireboltStatement);
	}

	@Test
	void shouldNotThrowExceptionWhenClosingTwice() throws SQLException {
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		resultSet.close();
		assertTrue(resultSet.isClosed());
		try {
			resultSet.close();
		} catch (Exception e) {
			fail();
		}
	}

	@Test
	void shouldThrowExceptionWhenColumnDoesNotExist() throws SQLException {
		inputStream = getInputStreamWithCommonResponseExample();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		assertThrows(SQLException.class, () -> resultSet.getObject(50));
	}

	@Test
	void shouldReturnEmptyWhenValueFoundIsEmpty() throws SQLException {
		inputStream = getInputStreamWithEmpty();
		resultSet = new FireboltResultSet(inputStream, "table_with_empty", "db_with_emtpy", 65535);
		resultSet.next();
		assertEquals(StringUtils.EMPTY, resultSet.getObject("name"));
		assertEquals(StringUtils.EMPTY, resultSet.getObject("city"));
	}

	@Test
	void shouldLogResultSet() throws SQLException {
		// This only tests that Metadata is available with the resultSet.
		inputStream = getInputStreamWithCommonResponseExample();
		try (MockedStatic<LoggerUtil> loggerUtilMockedStatic = mockStatic(LoggerUtil.class)) {
			loggerUtilMockedStatic.when(() -> LoggerUtil.logInputStream(inputStream)).thenReturn(inputStream);
			resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535, false,
					fireboltStatement, true);
			loggerUtilMockedStatic.verify(() -> LoggerUtil.logInputStream(inputStream));
		}
	}

	@Test
	void shouldGetTimeWithTimezoneFromCalendar() throws SQLException {
		inputStream = getInputStreamWithDates();
		try (MockedStatic<LoggerUtil> loggerUtilMockedStatic = mockStatic(LoggerUtil.class)) {
			loggerUtilMockedStatic.when(() -> LoggerUtil.logInputStream(inputStream)).thenReturn(inputStream);
			resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535, false,
					fireboltStatement, true);
			resultSet.next();

			Time firstExpectedTime = new Time(
					ZonedDateTime.of(1970, 1, 1, 18, 1, 2, 0, UTC_TZ.toZoneId()).toInstant().toEpochMilli());

			Time secondExpectedTime = new Time(
					ZonedDateTime.of(1970, 1, 1, 13, 1, 2, 0, UTC_TZ.toZoneId()).toInstant().toEpochMilli());

			assertEquals(firstExpectedTime, resultSet.getTime("a_datetime", EST_CALENDAR));
			assertEquals(secondExpectedTime, resultSet.getTime("a_datetime", UTC_CALENDAR));
			assertEquals(secondExpectedTime, resultSet.getTime("a_datetime", null));

		}
	}

	@Test
	void shouldGetTimestampWithTimezoneFromCalendar() throws SQLException {
		inputStream = getInputStreamWithDates();
		try (MockedStatic<LoggerUtil> loggerUtilMockedStatic = mockStatic(LoggerUtil.class)) {
			loggerUtilMockedStatic.when(() -> LoggerUtil.logInputStream(inputStream)).thenReturn(inputStream);
			resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535, false,
					fireboltStatement, true);
			resultSet.next();
			Timestamp firstTimeStampFromEST = Timestamp
					.valueOf(ZonedDateTime.of(2022, 5, 10, 18, 1, 2, 0, UTC_TZ.toZoneId()).toLocalDateTime());
			Timestamp firstTimeStampFromUTC = Timestamp
					.valueOf(ZonedDateTime.of(2022, 5, 10, 13, 1, 2, 0, UTC_TZ.toZoneId()).toLocalDateTime());
			Timestamp secondTimeStampFromUTC = Timestamp
					.valueOf(ZonedDateTime.of(2022, 5, 11, 4, 1, 2, 0, UTC_TZ.toZoneId()).toLocalDateTime());

			assertEquals(firstTimeStampFromEST, resultSet.getTimestamp("a_datetime", EST_CALENDAR));
			assertEquals(firstTimeStampFromUTC, resultSet.getTimestamp("a_datetime", UTC_CALENDAR));
			assertEquals(firstTimeStampFromUTC, resultSet.getTimestamp("a_datetime", null));
			resultSet.next();
			assertEquals(secondTimeStampFromUTC, resultSet.getTimestamp("a_datetime", EST_CALENDAR));
		}
	}

	@Test
	void shouldGetTimeObjectsWithTimeZoneFromResponse() throws SQLException {
		inputStream = getInputStreamWithDates();
		try (MockedStatic<LoggerUtil> loggerUtilMockedStatic = mockStatic(LoggerUtil.class)) {
			loggerUtilMockedStatic.when(() -> LoggerUtil.logInputStream(inputStream)).thenReturn(inputStream);
			resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535, false,
					fireboltStatement, false);
			resultSet.next();
			ZonedDateTime zonedDateTime = ZonedDateTime.of(2022, 5, 10, 18, 1, 2, 0, UTC_TZ.toZoneId());

			Timestamp expectedTimestamp = new Timestamp(zonedDateTime.toInstant().toEpochMilli());

			Time expectedTime = new Time(zonedDateTime.withYear(1970).withMonth(1).withDayOfMonth(1).toInstant().toEpochMilli());
			Date expectedDate = new Date(ZonedDateTime
					.of(2022, 5, 10, 5, 0, 0, 0, TimeZone.getTimeZone("UTC").toZoneId()).toInstant().toEpochMilli());

			// The timezone returned by the db is always used regardless of the timezone
			// passed as an argument
			assertEquals(expectedTime, resultSet.getTime("a_datetime64_with_tz", EST_CALENDAR));
			assertEquals(expectedTime, resultSet.getTime("a_datetime64_with_tz", UTC_CALENDAR));
			assertEquals(expectedTime, resultSet.getTime("a_datetime64_with_tz", null));

			assertEquals(expectedTimestamp, resultSet.getTimestamp("a_datetime64_with_tz", EST_CALENDAR));
			assertEquals(expectedTimestamp, resultSet.getTimestamp("a_datetime64_with_tz", UTC_CALENDAR));
			assertEquals(expectedTimestamp, resultSet.getTimestamp("a_datetime64_with_tz", null));
			resultSet.next();
			assertEquals(expectedDate, resultSet.getDate("a_datetime64_with_tz", UTC_CALENDAR));
		}
	}

	@Test
	void shouldGetDateWithTimezoneFromCalendar() throws SQLException {
		inputStream = getInputStreamWithDates();
		try (MockedStatic<LoggerUtil> loggerUtilMockedStatic = mockStatic(LoggerUtil.class)) {
			loggerUtilMockedStatic.when(() -> LoggerUtil.logInputStream(inputStream)).thenReturn(inputStream);
			resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535, false,
					fireboltStatement, true);
			resultSet.next();
			Date firstExpectedDateFromEST = new Date(ZonedDateTime
					.of(2022, 5, 10, 5, 0, 0, 0, TimeZone.getTimeZone("UTC").toZoneId()).toInstant().toEpochMilli());
			Date secondExpectedDateFromEST = new Date(ZonedDateTime
					.of(2022, 5, 10, 5, 0, 0, 0, TimeZone.getTimeZone("UTC").toZoneId()).toInstant().toEpochMilli());
			Date secondExpectedDateFromUTC = new Date(ZonedDateTime
					.of(2022, 5, 10, 0, 0, 0, 0, TimeZone.getTimeZone("UTC").toZoneId()).toInstant().toEpochMilli());

			assertEquals(firstExpectedDateFromEST, resultSet.getDate("a_datetime", EST_CALENDAR));
			resultSet.next();
			assertEquals(secondExpectedDateFromEST, resultSet.getDate("a_datetime", EST_CALENDAR));
			assertEquals(secondExpectedDateFromUTC, resultSet.getDate("a_datetime", UTC_CALENDAR));
		}
	}

	@Test
	void shouldFindNullByteA() throws SQLException {
		inputStream = getInputStreamWithByteA();
		resultSet = new FireboltResultSet(inputStream, "any", "any", 65535);
		resultSet.next();
		assertNull(resultSet.getObject("null_bytea"));
	}

	@Test
	void shouldFindByteAWithValue() throws SQLException {
		inputStream = getInputStreamWithByteA();
		resultSet = new FireboltResultSet(inputStream, "any", "any", 65535);
		resultSet.next();
		assertArrayEquals(new byte[] { -34, -83, -66, -17 }, (byte[]) resultSet.getObject("a_bytea"));
		assertEquals("\\xdeadbeef", resultSet.getString("a_bytea"));
		resultSet.next();
		assertArrayEquals(new byte[] { 0, -85 }, (byte[]) resultSet.getObject("a_bytea"));
		assertEquals("\\x00ab", resultSet.getString("a_bytea"));
	}

	@Test
	void shouldFindEmptyByteA() throws SQLException {
		inputStream = getInputStreamWithByteA();
		resultSet = new FireboltResultSet(inputStream, "any", "any", 65535);
		resultSet.next();
		assertArrayEquals(new byte[] {}, (byte[]) resultSet.getObject("an_empty_bytea"));
		assertEquals("", resultSet.getString("an_empty_bytea"));
	}

	@Test
	void shouldReturnTrueWhenBooleanFoundIsFalse() throws SQLException {
		inputStream = getInputStreamWithBooleans();
		resultSet = new FireboltResultSet(inputStream, "any", "any", 65535);
		resultSet.next();
		assertTrue((Boolean)resultSet.getObject("true_boolean"));
		assertTrue(resultSet.getBoolean("true_boolean"));
		resultSet.next();
		assertTrue((Boolean)resultSet.getObject("true_boolean"));
		assertTrue(resultSet.getBoolean("true_boolean"));
	}

	@Test
	void shouldReturnFalseWhenBooleanFoundIsFalse() throws SQLException {
		inputStream = getInputStreamWithBooleans();
		resultSet = new FireboltResultSet(inputStream, "any", "any", 65535);
		resultSet.next();
		assertFalse((Boolean)resultSet.getObject("false_boolean"));
		assertFalse(resultSet.getBoolean("false_boolean"));
		resultSet.next();
		assertFalse((Boolean)resultSet.getObject("false_boolean"));
		assertFalse(resultSet.getBoolean("false_boolean"));
	}

	@Test
	void shouldReturnFalseWhenBooleanFoundIsNull() throws SQLException {
		inputStream = getInputStreamWithBooleans();
		resultSet = new FireboltResultSet(inputStream, "any", "any", 65535);
		resultSet.next();
		assertNull(resultSet.getObject("null_boolean"));
		assertFalse(resultSet.getBoolean("null_boolean"));
	}

	@Test
	void shouldThrowExceptionWhenBooleanValueIsInvalid() throws SQLException {
		inputStream = getInputStreamWithBooleans();
		resultSet = new FireboltResultSet(inputStream, "any", "any", 65535);
		resultSet.next();
		assertThrows(FireboltException.class, () -> resultSet.getObject("invalid_boolean"));
	}

	@Test
	void shouldReturnTimestampFromTimestampntz() throws SQLException {
		Timestamp expectedTimestamp = new Timestamp(
				ZonedDateTime.of(2022, 5, 10, 23, 1, 2, 0, UTC_TZ.toZoneId()).toInstant().toEpochMilli());

		inputStream = getInputStreamWithDates();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		resultSet.next();

		assertEquals(expectedTimestamp, resultSet.getTimestamp("timestampntz"));
		assertEquals(expectedTimestamp, resultSet.getObject("timestampntz"));
		Timestamp expectedTimestampWithDifferentTz = new Timestamp(
				ZonedDateTime.of(2022, 5, 11, 4, 1, 2, 0, UTC_TZ.toZoneId()).toInstant().toEpochMilli());
		assertEquals(expectedTimestampWithDifferentTz, resultSet.getTimestamp("timestampntz", EST_CALENDAR));
	}

	@Test
	void shouldReturnDateFromTimestampntz() throws SQLException {
		Date expectedDate = new Date(ZonedDateTime
				.of(2022, 5, 10, 0, 0, 0, 0,  UTC_TZ.toZoneId()).toInstant().toEpochMilli());

		inputStream = getInputStreamWithDates();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		resultSet.next();

		assertEquals(expectedDate, resultSet.getDate("timestampntz"));

		Date expectedDateEST = new Date(ZonedDateTime
				.of(2022, 5, 10, 5, 0, 0, 0,  UTC_TZ.toZoneId()).toInstant().toEpochMilli());
		assertEquals(expectedDateEST, resultSet.getDate("timestampntz", EST_CALENDAR));
	}

	@Test
	void shouldReturnTimeFromTimestampntz() throws SQLException {
		Time expectedTime = new Time(
				ZonedDateTime.of(1970, 1, 1, 23, 1, 2, 0, UTC_TZ.toZoneId()).toInstant().toEpochMilli());
		Time expectedTimeFromEST = new Time(
				ZonedDateTime.of(1970, 1, 2, 4, 1, 2, 0, UTC_TZ.toZoneId()).toInstant().toEpochMilli());
		inputStream = getInputStreamWithDates();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		resultSet.next();
		assertEquals(expectedTime, resultSet.getTime("timestampntz"));
		assertEquals(expectedTimeFromEST, resultSet.getTime("timestampntz", EST_CALENDAR));
	}

	@Test
	void shouldReturnTimeFromTimestamptz() throws SQLException {
		Time expectedTime = new Time(
				ZonedDateTime.of(1970, 1, 1, 6, 1, 2, 0, UTC_TZ.toZoneId()).toInstant().toEpochMilli());
		Time expectedTimeFromEST = new Time(
				ZonedDateTime.of(1970, 1, 1, 6, 1, 2, 0, UTC_TZ.toZoneId()).toInstant().toEpochMilli());
		inputStream = getInputStreamWithDates();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		resultSet.next();
		assertEquals(expectedTime, resultSet.getTime("timestamptz"));
		assertEquals(expectedTimeFromEST, resultSet.getTime("timestamptz", EST_CALENDAR));
	}

	@Test
	void shouldReturnTimestampFromTimestamptz() throws SQLException, ParseException {
		Timestamp expectedTimestamp = new Timestamp(
				ZonedDateTime.of(2022, 5, 11, 6, 1, 2, 0, UTC_TZ.toZoneId()).toInstant().toEpochMilli());

		inputStream = getInputStreamWithDates();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		resultSet.next();

		assertEquals(expectedTimestamp, resultSet.getTimestamp("timestamptz"));
		assertEquals(expectedTimestamp, resultSet.getObject("timestamptz"));
		assertEquals(expectedTimestamp, resultSet.getTimestamp("timestamptz", EST_CALENDAR));
	}

	@Test
	void shouldReturnDateFromTimestamptz() throws SQLException {
		Date expectedDate = new Date(ZonedDateTime
				.of(2022, 5, 11, 0, 0, 0, 0,  UTC_TZ.toZoneId()).toInstant().toEpochMilli());

		inputStream = getInputStreamWithDates();
		resultSet = new FireboltResultSet(inputStream, "any_name", "array_db", 65535);
		resultSet.next();

		assertEquals(expectedDate, resultSet.getDate("timestamptz"));

		Date expectedDateEST = new Date(ZonedDateTime
				.of(2022, 5, 11, 5, 0, 0, 0,  UTC_TZ.toZoneId()).toInstant().toEpochMilli());
		assertEquals(expectedDateEST, resultSet.getDate("timestamptz", EST_CALENDAR));
	}

	@Test
	void shouldReturnNullForTimeTypesWithNullValues() throws SQLException, ParseException {
		inputStream = getInputStreamWithDates();
		resultSet = new FireboltResultSet(inputStream, "any", "any", 65535);
		resultSet.next();
		resultSet.next();
		assertNull(resultSet.getTimestamp("timestamptz"));
		assertNull(resultSet.getTime("timestamptz"));
		assertNull(resultSet.getDate("timestamptz"));
		assertNull(resultSet.getTimestamp("timestamptz", EST_CALENDAR));
		assertNull(resultSet.getTime("timestamptz", EST_CALENDAR));
		assertNull(resultSet.getDate("timestamptz", EST_CALENDAR));
	}

	private InputStream getInputStreamWithCommonResponseExample() {
		return FireboltResultSetTest.class.getResourceAsStream("/responses/firebolt-response-example");
	}

	private InputStream getInputStreamWithEmpty() {
		return FireboltResultSetTest.class.getResourceAsStream("/responses/firebolt-response-with-empty");
	}

	private InputStream getInputStreamWithNulls() {
		return FireboltResultSetTest.class.getResourceAsStream("/responses/firebolt-response-with-nulls");
	}

	private InputStream getInputStreamWitExplain() {
		return FireboltResultSetTest.class.getResourceAsStream("/responses/firebolt-response-with-escape-characters");
	}

	private InputStream getInputStreamWithByteA() {
		return FireboltResultSetTest.class.getResourceAsStream("/responses/firebolt-response-with-bytea");
	}

	private InputStream getInputStreamWithBooleans() {
		return FireboltResultSetTest.class.getResourceAsStream("/responses/firebolt-response-with-booleans");
	}

	private InputStream getInputStreamWithDates() {
		return FireboltResultSetTest.class.getResourceAsStream("/responses/firebolt-response-with-dates");
	}
}
