package net.swofty.dreammanhunt.server.engine;

public class SavestateException extends Exception {
	public SavestateException(String message) {
		super(message);
	}

	public SavestateException(String message, Throwable cause) {
		super(message, cause);
	}
}
