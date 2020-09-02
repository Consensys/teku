package tech.pegasys.teku.storage.store;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.util.file.SyncDataAccessor;

public class FileKeyValueStore implements KeyValueStore<String, Bytes> {

  private final Path dataDir;

  public FileKeyValueStore(Path dataDir) {
    this.dataDir = dataDir;
  }

  @Override
  public void put(String key, Bytes value) {
    Path file = dataDir.resolve(key + ".dat");
    try {
      new SyncDataAccessor().syncedWrite(file, value);
    } catch (IOException e) {
      throw new RuntimeException("Error writing file: " + file, e);
    }
  }

  @Override
  public Optional<Bytes> get(String key) {
    Path file = dataDir.resolve(key + ".dat");
    try {
      return new SyncDataAccessor().read(file);
    } catch (Exception e) {
      throw new RuntimeException("Error writing file: " + file, e);
    }
  }
}
