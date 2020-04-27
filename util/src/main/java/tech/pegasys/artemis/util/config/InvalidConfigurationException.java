package tech.pegasys.artemis.util.config;

public class InvalidConfigurationException extends RuntimeException {
  public InvalidConfigurationException(final String message) {
    super(message);
  }
}
