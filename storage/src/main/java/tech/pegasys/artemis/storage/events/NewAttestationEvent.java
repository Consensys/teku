package tech.pegasys.artemis.storage.events;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.storage.LatestMessage;

public class NewAttestationEvent {
  private final BeaconStateWithCache state;
  private final Checkpoint checkpoint;
  private final List<Pair<UnsignedLong, LatestMessage>> attesterLatestMessages;

  public NewAttestationEvent(
      final BeaconStateWithCache state,
      final Checkpoint checkpoint,
      final List<Pair<UnsignedLong, LatestMessage>> attesterLatestMessages) {
    this.state = state;
    this.checkpoint = checkpoint;
    this.attesterLatestMessages = attesterLatestMessages;
  }

  public BeaconStateWithCache getState() {
    return state;
  }

  public Checkpoint getCheckpoint() {
    return checkpoint;
  }

  public List<Pair<UnsignedLong, LatestMessage>> getAttesterLatestMessages() {
    return attesterLatestMessages;
  }
}
