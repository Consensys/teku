package tech.pegasys.teku.services.beaconchain.init;

import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = {
    AsyncRunnerModule.class,
    BeaconConfigModule.class,
    BeaconModule.class,
    BlobModule.class,
    ChannelsModule.class,
    CryptoModule.class,
    DataProviderModule.class,
    ExternalDependenciesModule.class,
    ForkChoiceModule.class,
    LoggingModule.class,
    MainModule.class,
    MetricsModule.class,
    NetworkModule.class,
    PoolAndCachesModule.class,
    PowModule.class,
    ServiceConfigModule.class,
    SpecModule.class,
    StorageModule.class,
    SubnetsModule.class,
    SyncModule.class,
    ValidatorModule.class,
    VerifyModule.class,
    WSModule.class
})
public interface BeaconChainControllerComponent {

  MainModule.ServiceStarter starter();

  MainModule.ServiceStopper stopper();

}
