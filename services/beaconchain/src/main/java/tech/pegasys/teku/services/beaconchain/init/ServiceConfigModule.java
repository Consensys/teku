package tech.pegasys.teku.services.beaconchain.init;

import dagger.Module;
import dagger.Provides;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;

import javax.inject.Qualifier;
import javax.inject.Singleton;
import java.util.function.IntSupplier;

@Module
public interface ServiceConfigModule {

  @Qualifier
  @interface RejectedExecutionCountSupplier {}

  @Provides
  static DataDirLayout provideDataDirLayout(ServiceConfig config){
    return config.getDataDirLayout();
  }

  @Provides
  static AsyncRunnerFactory provideAsyncRunnerFactory(ServiceConfig config){
    return config.getAsyncRunnerFactory();
  }

  @Provides
  static TimeProvider provideTimeProvider(ServiceConfig config){
    return config.getTimeProvider();
  }

  @Provides
  static EventChannels provideEventChannels(ServiceConfig config){
    return config.getEventChannels();
  }

  @Provides
  static MetricsSystem provideMetricsSystem(ServiceConfig config){
    return config.getMetricsSystem();
  }

  @Provides
  @Singleton
  @RejectedExecutionCountSupplier
  static IntSupplier provideRejectedExecutionCountSupplier(ServiceConfig config) {
    return config.getRejectedExecutionsSupplier();
  }

}
