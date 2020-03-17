package tech.pegasys.artemis.storage;

import com.google.common.primitives.UnsignedLong;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.storage.events.StoreDiskUpdateEvent;

public class RocksDbDatabase implements Database {

  static {
    RocksDbUtil.loadNativeLibrary();
  }

  public static Database createOnDisk(
    final File directory, final StateStorageMode stateStorageMode) {
    return new RocksDbDatabase();
  }

  private RocksDbDatabase() {

  }

  @Override
  public void storeGenesis(final Store store) {
    throw new UnsupportedOperationException();
  }

  @Override
  public DatabaseUpdateResult update(final StoreDiskUpdateEvent event) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<Store> createMemoryStore() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<Bytes32> getFinalizedRootAtSlot(final UnsignedLong slot) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<Bytes32> getLatestFinalizedRootAtSlot(final UnsignedLong slot) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<SignedBeaconBlock> getSignedBlock(final Bytes32 root) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<BeaconState> getState(final Bytes32 root) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void close() throws IOException {

  }
}
