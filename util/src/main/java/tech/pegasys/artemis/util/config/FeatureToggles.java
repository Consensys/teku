package tech.pegasys.artemis.util.config;

/**
 * Defines feature toggles to enable or disable new functionality which may not be fully ready yet.
 * Allows larger pieces of work to be delivered in smaller PRs without breaking functionality while
 * they are under development.
 */
public class FeatureToggles {

  /**
   * Controls whether the ValidatorClientService is used to generate blocks and attestations or if
   * ValidatorCoordinator performs those duties.
   */
  public static final boolean USE_VALIDATOR_CLIENT_SERVICE = false;
}
