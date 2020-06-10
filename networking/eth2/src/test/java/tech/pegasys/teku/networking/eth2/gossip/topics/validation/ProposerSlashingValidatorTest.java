package tech.pegasys.teku.networking.eth2.gossip.topics.validation;

import com.google.common.eventbus.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.core.operationsignatureverifiers.ProposerSlashingSignatureVerifier;
import tech.pegasys.teku.core.operationstatetransitionvalidators.ProposerSlashingStateTransitionValidator;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.datastructures.util.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult.IGNORE;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult.REJECT;

public class ProposerSlashingValidatorTest {
  private static final List<BLSKeyPair> VALIDATOR_KEYS =
          new MockStartValidatorKeyPairFactory().generateKeyPairs(0, 25);
  private DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private RecentChainData recentChainData;
  private BeaconChainUtil beaconChainUtil;
  private ProposerSlashingValidator proposerSlashingValidator;
  private ProposerSlashingStateTransitionValidator stateTransitionValidator;
  private ProposerSlashingSignatureVerifier signatureVerifier;

  @BeforeEach
  void beforeEach() {
    recentChainData = MemoryOnlyRecentChainData.create(new EventBus());
    beaconChainUtil = BeaconChainUtil.create(recentChainData, VALIDATOR_KEYS, true);
    stateTransitionValidator = mock(ProposerSlashingStateTransitionValidator.class);
    signatureVerifier = mock(ProposerSlashingSignatureVerifier.class);
    proposerSlashingValidator = new ProposerSlashingValidator(
            recentChainData,
            stateTransitionValidator,
            signatureVerifier
    );
  }

  @Test
  public void shouldAcceptValidProposerSlashing() throws Exception {
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(6);
    ProposerSlashing slashing = dataStructureUtil.randomProposerSlashing();
    when(stateTransitionValidator.validateSlashing(recentChainData.getBestState().orElseThrow(), slashing))
            .thenReturn(Optional.empty());
    when(signatureVerifier.verifySignature(
            recentChainData.getBestState().orElseThrow(),
            slashing,
            BLSSignatureVerifier.SIMPLE)
    ).thenReturn(true);
    assertThat(proposerSlashingValidator.validate(slashing)).isEqualTo(ACCEPT);
  }

  @Test
  public void shouldRejectInvalidProposerSlashing() throws Exception {
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(6);
    ProposerSlashing slashing = dataStructureUtil.randomProposerSlashing();
    when(stateTransitionValidator.validateSlashing(recentChainData.getBestState().orElseThrow(), slashing))
            .thenReturn(Optional.of(ProposerSlashingStateTransitionValidator.ProposerSlashingInvalidReason.PROPOSER_INDICES_DIFFERENT));
    when(signatureVerifier.verifySignature(
            recentChainData.getBestState().orElseThrow(),
            slashing,
            BLSSignatureVerifier.SIMPLE)
    ).thenReturn(true);
    assertThat(proposerSlashingValidator.validate(slashing)).isEqualTo(REJECT);
  }

  @Test
  public void shouldRejectProposerSlashingWithInvalidSignature() throws Exception {
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(6);
    ProposerSlashing slashing = dataStructureUtil.randomProposerSlashing();
    when(stateTransitionValidator.validateSlashing(recentChainData.getBestState().orElseThrow(), slashing))
            .thenReturn(Optional.empty());
    when(signatureVerifier.verifySignature(
            recentChainData.getBestState().orElseThrow(),
            slashing,
            BLSSignatureVerifier.SIMPLE)
    ).thenReturn(false);
    assertThat(proposerSlashingValidator.validate(slashing)).isEqualTo(REJECT);
  }

  @Test
  public void shouldIgnoreProposerSlashingForTheSameProposer() throws Exception {
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(6);
    ProposerSlashing slashing1 = dataStructureUtil.randomProposerSlashing();
    ProposerSlashing slashing2 = new ProposerSlashing(slashing1.getHeader_1(), slashing1.getHeader_2());
    when(stateTransitionValidator.validateSlashing(eq(recentChainData.getBestState().orElseThrow()), any()))
            .thenReturn(Optional.empty());
    when(signatureVerifier.verifySignature(
            eq(recentChainData.getBestState().orElseThrow()),
            any(),
            eq(BLSSignatureVerifier.SIMPLE))
    ).thenReturn(true);
    assertThat(proposerSlashingValidator.validate(slashing1)).isEqualTo(ACCEPT);
    assertThat(proposerSlashingValidator.validate(slashing2)).isEqualTo(IGNORE);
  }
}

