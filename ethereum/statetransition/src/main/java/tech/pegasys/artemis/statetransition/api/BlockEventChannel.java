package tech.pegasys.artemis.statetransition.api;

import tech.pegasys.artemis.statetransition.events.block.ImportedBlockEvent;

public interface BlockEventChannel {
  void onImportedBlock(ImportedBlockEvent event);
}
