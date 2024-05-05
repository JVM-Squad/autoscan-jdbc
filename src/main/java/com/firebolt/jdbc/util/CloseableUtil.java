package com.firebolt.jdbc.util;

import java.io.Closeable;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import lombok.CustomLog;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CloseableUtil {
	private static final Logger log = Logger.getLogger(CloseableUtil.class.getName());

	/**
	 * Closes the {@link Closeable} and log any potential {@link IOException}
	 * 
	 * @param closeable the closeable to close
	 */
	public void close(Closeable closeable) {
		if (closeable != null) {
			try {
				closeable.close();
			} catch (IOException e) {
				log.log(Level.SEVERE, "An error happened while closing the closeable: {0}", e.getMessage());
			}
		}
	}
}
