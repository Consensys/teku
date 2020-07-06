package tech.pegasys.teku.protoarray;

import tech.pegasys.teku.util.async.SafeFuture;

import java.util.Optional;

public class StubProtoArrayStorageChannel implements ProtoArrayStorageChannel{
  @Override
  public void onProtoArrayUpdate(ProtoArraySnapshot protoArraySnapshot) {}

  @Override
  public SafeFuture<Optional<ProtoArraySnapshot>> getProtoArraySnapshot() {
    return SafeFuture.completedFuture(Optional.empty());
  }
}
