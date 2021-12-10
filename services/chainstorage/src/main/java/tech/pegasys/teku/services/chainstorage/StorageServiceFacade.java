package tech.pegasys.teku.services.chainstorage;

import tech.pegasys.teku.service.serviceutils.ServiceFacade;
import tech.pegasys.teku.storage.server.ChainStorageFacade;

public interface StorageServiceFacade extends ServiceFacade {

  ChainStorageFacade getChainStorage();
}
