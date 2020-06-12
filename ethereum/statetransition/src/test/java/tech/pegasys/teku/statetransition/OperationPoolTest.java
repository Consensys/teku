package tech.pegasys.teku.statetransition;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.operationvalidators.OperationStateTransitionValidator;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;
import tech.pegasys.teku.util.config.Constants;

import javax.swing.text.html.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.datastructures.blocks.BeaconBlockBodyLists.createAttesterSlashings;

@SuppressWarnings("unchecked")
public class OperationPoolTest {

  DataStructureUtil dataStructureUtil = new DataStructureUtil();
  BeaconState state = mock(BeaconState.class);

  @Test
  void testEmptyPool() {
    OperationStateTransitionValidator<ProposerSlashing> validator =
            mock(OperationStateTransitionValidator.class);
    OperationPool<ProposerSlashing> pool = new OperationPool<>(ProposerSlashing.class, validator);
    when(validator.validate(any(), any())).thenReturn(Optional.empty());
    assertThat(pool.getItemsForBlock(state)).isEmpty();
  }

  @Test
  void testAddMaxItemsToPool() {
    OperationStateTransitionValidator<SignedVoluntaryExit> validator =
            mock(OperationStateTransitionValidator.class);
    OperationPool<SignedVoluntaryExit> pool = new OperationPool<>(SignedVoluntaryExit.class, validator);
    when(validator.validate(any(), any())).thenReturn(Optional.empty());
    for (int i = 0; i < Constants.MAX_VOLUNTARY_EXITS + 1; i++) {
      pool.add(dataStructureUtil.randomSignedVoluntaryExit());
    }
    assertThat(pool.getItemsForBlock(state)).hasSize(Constants.MAX_VOLUNTARY_EXITS);
    assertThat(pool.getItemsForBlock(state)).hasSize(1);
  }

  @Test
  void testRemoveItemsFromPool() {
    OperationStateTransitionValidator<AttesterSlashing> validator =
            mock(OperationStateTransitionValidator.class);
    OperationPool<AttesterSlashing> pool = new OperationPool<>(AttesterSlashing.class, validator);
    SSZMutableList<AttesterSlashing> slashingsInBlock = createAttesterSlashings();
    when(validator.validate(any(), any())).thenReturn(Optional.empty());
    for (int i = 0; i < Constants.MAX_ATTESTER_SLASHINGS; i++) {
      AttesterSlashing slashing = dataStructureUtil.randomAttesterSlashing();
      pool.add(slashing);
      slashingsInBlock.add(slashing);
    }
    pool.removeAll(slashingsInBlock);
    assertThat(pool.getItemsForBlock(state)).isEmpty();
  }
}
