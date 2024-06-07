package tech.pegasys.teku.services.beaconchain.init;

import dagger.Module;
import dagger.Provides;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.services.beaconchain.BeaconChainController;

import javax.inject.Singleton;

@Module
public interface LoggingModule {

  record InitLogger(Logger logger) {}

  @Provides
  @Singleton
  static StatusLogger statusLogger() {
    return StatusLogger.STATUS_LOG;
  }

  @Provides
  @Singleton
  static EventLogger eventLogger() {
    return EventLogger.EVENT_LOG;
  }

  @Provides
  @Singleton
  static InitLogger initLogger() {
    return new InitLogger(LogManager.getLogger(BeaconChainController.class));
  }
}
