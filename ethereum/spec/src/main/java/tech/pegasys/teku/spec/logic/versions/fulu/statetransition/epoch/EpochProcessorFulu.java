package tech.pegasys.teku.spec.logic.versions.fulu.statetransition.epoch;

import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.BeaconStateFulu;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.BeaconStateFuluImpl;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.MutableBeaconStateFulu;
import tech.pegasys.teku.spec.datastructures.state.versions.fulu.ProposerLookahead;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatusFactory;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateAccessorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.MiscHelpersElectra;
import tech.pegasys.teku.spec.logic.versions.electra.statetransition.epoch.EpochProcessorElectra;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.BeaconStateAccessorsFulu;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsCapella;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;

import java.util.List;

public class EpochProcessorFulu extends EpochProcessorElectra {
    private final BeaconStateAccessorsFulu stateAccessorsFulu;
    private final SchemaDefinitions schemaDefinitions;

    public EpochProcessorFulu(
            final SpecConfigFulu specConfig,
            final MiscHelpersFulu miscHelpers,
            final BeaconStateAccessorsAltair beaconStateAccessors,
            final BeaconStateMutators beaconStateMutators,
            final ValidatorsUtil validatorsUtil,
            final BeaconStateUtil beaconStateUtil,
            final ValidatorStatusFactory validatorStatusFactory,
            final SchemaDefinitions schemaDefinitions,
            final TimeProvider timeProvider) {
        super(
                specConfig,
                miscHelpers,
                beaconStateAccessors,
                beaconStateMutators,
                validatorsUtil,
                beaconStateUtil,
                validatorStatusFactory,
                schemaDefinitions,
                timeProvider);
        this.stateAccessorsFulu = BeaconStateAccessorsFulu.required(beaconStateAccessors);
        this.schemaDefinitions = schemaDefinitions;
    }

    /** process_proposer_lookahead */
    public void processProposerLookahead(final MutableBeaconState state) {
        final MutableBeaconStateFulu stateFulu = MutableBeaconStateFulu.required(state);
        final int slotsPerEpoch = specConfig.getSlotsPerEpoch();
        final int minSeedLookahead = specConfig.getMinSeedLookahead();


        final int lastEpochStart = stateFulu.getProposerLookahead().size() - slotsPerEpoch;
        final List<UInt64> lastEpochProposers = stateAccessorsFulu.getBeaconProposerIndices(state, beaconStateAccessors
                .getCurrentEpoch(state).plus(minSeedLookahead).plus(1))
                .stream().map(UInt64::valueOf).toList();

        final ProposerLookahead.ProposerLookaheadSchema proposerLookaheadSchema = SchemaDefinitionsFulu.required(schemaDefinitions).getProposerLookaheadSchema();
        final SszUInt64List proposerLookaheadList = lastEpochProposers.stream().collect(proposerLookaheadSchema.getLookaheadSchema().collectorUnboxed());
        stateFulu.getProposerLookahead().appendAll(proposerLookaheadList);
    }
}
