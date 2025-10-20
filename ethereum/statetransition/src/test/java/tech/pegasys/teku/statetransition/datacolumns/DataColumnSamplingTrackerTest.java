package tech.pegasys.teku.statetransition.datacolumns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.ExecutionException;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;


class DataColumnSamplingTrackerTest {
  private static final UInt64 SLOT = UInt64.valueOf(42);
  private static final Bytes32 BLOCK_ROOT =
          Bytes32.fromHexString("0x0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20");
  private static final RemoteOrigin MOCK_ORIGIN = mock(RemoteOrigin.class);
  private static final List<UInt64> SAMPLING_REQUIREMENT =
          List.of(UInt64.valueOf(5), UInt64.valueOf(10), UInt64.valueOf(15));

  private final CustodyGroupCountManager custodyGroupCountManager = mock(CustodyGroupCountManager.class);

  private DataColumnSamplingTracker tracker;

  @BeforeEach
  void setUp() {
    when(custodyGroupCountManager.getSamplingColumnIndices()).thenReturn(SAMPLING_REQUIREMENT);

    tracker = DataColumnSamplingTracker.create(SLOT, BLOCK_ROOT, custodyGroupCountManager);
  }

  @Test
  void create_shouldInitializeWithAllColumnsMissing() {
    assertThat(tracker.slot()).isEqualTo(SLOT);
    assertThat(tracker.blockRoot()).isEqualTo(BLOCK_ROOT);
    assertThat(tracker.samplingRequirement()).isEqualTo(SAMPLING_REQUIREMENT);
    assertThat(tracker.missingColumns()).containsExactlyInAnyOrderElementsOf(SAMPLING_REQUIREMENT);
    assertThat(tracker.completionFuture()).isNotDone();
  }

  @Test
  void add_shouldReturnTrueAndRemoveColumnWhenItIsMissing() {
    final UInt64 columnIndexToAdd = SAMPLING_REQUIREMENT.get(0); // Index 5
    final List<UInt64> remainingColumns =
            SAMPLING_REQUIREMENT.subList(1, SAMPLING_REQUIREMENT.size());
    final DataColumnSlotAndIdentifier columnIdentifier =
            new DataColumnSlotAndIdentifier(SLOT, BLOCK_ROOT, columnIndexToAdd);


    final boolean result = tracker.add(columnIdentifier, MOCK_ORIGIN);

    assertThat(result).isTrue();
    assertThat(tracker.missingColumns()).containsExactlyInAnyOrderElementsOf(remainingColumns);
    assertThat(tracker.completionFuture()).isNotDone();
  }

  @Test
  void add_shouldCompleteFutureWhenLastColumnIsAdded()
          throws ExecutionException, InterruptedException {
    for (int i = 0; i < SAMPLING_REQUIREMENT.size() - 1; i++) {
      final UInt64 index = SAMPLING_REQUIREMENT.get(i);
      tracker.add(new DataColumnSlotAndIdentifier(SLOT, BLOCK_ROOT, index), MOCK_ORIGIN);
    }
    assertThat(tracker.missingColumns()).hasSize(1);
    assertThat(tracker.completionFuture()).isNotDone();

    final UInt64 lastColumnIndex = SAMPLING_REQUIREMENT.get(SAMPLING_REQUIREMENT.size() - 1);
    final DataColumnSlotAndIdentifier lastColumnIdentifier =
            new DataColumnSlotAndIdentifier(SLOT, BLOCK_ROOT, lastColumnIndex);
    final boolean result = tracker.add(lastColumnIdentifier, MOCK_ORIGIN);

    assertThat(result).isTrue();
    assertThat(tracker.missingColumns()).isEmpty();
    assertThat(tracker.completionFuture()).isCompleted();
    assertThat(tracker.completionFuture().get()).isEqualTo(SAMPLING_REQUIREMENT);
  }

  @Test
  void add_shouldReturnFalseForMismatchedSlot() {
    final UInt64 mismatchedSlot = SLOT.plus(1);
    final DataColumnSlotAndIdentifier columnIdentifier =
            new DataColumnSlotAndIdentifier(mismatchedSlot, BLOCK_ROOT, SAMPLING_REQUIREMENT.get(0));

    final boolean result = tracker.add(columnIdentifier, MOCK_ORIGIN);

    assertThat(result).isFalse();
    assertThat(tracker.missingColumns()).hasSize(SAMPLING_REQUIREMENT.size());
    assertThat(tracker.completionFuture()).isNotDone();
  }

  @Test
  void add_shouldReturnFalseForMismatchedBlockRoot() {
    final Bytes32 mismatchedRoot = Bytes32.fromHexString("0xff");
    final DataColumnSlotAndIdentifier columnIdentifier =
            new DataColumnSlotAndIdentifier(SLOT, mismatchedRoot, SAMPLING_REQUIREMENT.get(0));

    final boolean result = tracker.add(columnIdentifier, MOCK_ORIGIN);

    assertThat(result).isFalse();
    assertThat(tracker.missingColumns()).hasSize(SAMPLING_REQUIREMENT.size());
    assertThat(tracker.completionFuture()).isNotDone();
  }

  @Test
  void add_shouldReturnFalseWhenAddingSameColumnTwice() {
    final UInt64 columnIndexToAdd = SAMPLING_REQUIREMENT.get(0);
    final DataColumnSlotAndIdentifier columnIdentifier =
            new DataColumnSlotAndIdentifier(SLOT, BLOCK_ROOT, columnIndexToAdd);

    final boolean firstResult = tracker.add(columnIdentifier, MOCK_ORIGIN);
    assertThat(firstResult).isTrue();
    assertThat(tracker.missingColumns()).hasSize(SAMPLING_REQUIREMENT.size() - 1);

    final boolean secondResult = tracker.add(columnIdentifier, MOCK_ORIGIN);

    assertThat(secondResult).isFalse();
    assertThat(tracker.missingColumns()).hasSize(SAMPLING_REQUIREMENT.size() - 1);
  }

  @Test
  void add_shouldReturnFalseForColumnNotInOriginalRequirement() {
    final UInt64 nonRequiredIndex = UInt64.valueOf(999);
    final DataColumnSlotAndIdentifier columnIdentifier =
            new DataColumnSlotAndIdentifier(SLOT, BLOCK_ROOT, nonRequiredIndex);

    final boolean result = tracker.add(columnIdentifier, MOCK_ORIGIN);

    assertThat(result).isFalse();
    assertThat(tracker.missingColumns()).hasSize(SAMPLING_REQUIREMENT.size());
  }

  @Test
  void getMissingColumnIdentifiers_shouldReturnFullListWhenNew() {
    final List<DataColumnSlotAndIdentifier> missing = tracker.getMissingColumnIdentifiers();

    assertThat(missing)
            .hasSize(SAMPLING_REQUIREMENT.size())
            .extracting(DataColumnSlotAndIdentifier::columnIndex)
            .containsExactlyInAnyOrderElementsOf(SAMPLING_REQUIREMENT);

    // Verify all identifiers have the correct slot and root
    assertThat(missing).allMatch(id -> id.slot().equals(SLOT) && id.blockRoot().equals(BLOCK_ROOT));
  }

  @Test
  void getMissingColumnIdentifiers_shouldReturnPartialListAfterAddingOne() {
    final UInt64 addedIndex = SAMPLING_REQUIREMENT.get(1); // index 10
    tracker.add(new DataColumnSlotAndIdentifier(SLOT, BLOCK_ROOT, addedIndex), MOCK_ORIGIN);

    final List<DataColumnSlotAndIdentifier> missing = tracker.getMissingColumnIdentifiers();

    assertThat(missing)
            .hasSize(SAMPLING_REQUIREMENT.size() - 1)
            .extracting(DataColumnSlotAndIdentifier::columnIndex)
            .containsExactly(SAMPLING_REQUIREMENT.get(0), SAMPLING_REQUIREMENT.get(2));
  }

  @Test
  void getMissingColumnIdentifiers_shouldReturnEmptyListWhenComplete() {
    for (UInt64 index : SAMPLING_REQUIREMENT) {
      tracker.add(new DataColumnSlotAndIdentifier(SLOT, BLOCK_ROOT, index), MOCK_ORIGIN);
    }
    assertThat(tracker.missingColumns()).isEmpty();

    final List<DataColumnSlotAndIdentifier> missing = tracker.getMissingColumnIdentifiers();

    assertThat(missing).isEmpty();
  }
}
