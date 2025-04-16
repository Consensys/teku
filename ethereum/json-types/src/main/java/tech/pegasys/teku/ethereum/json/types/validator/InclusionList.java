package tech.pegasys.teku.ethereum.json.types.validator;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.schema.Root;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;

import java.util.List;
import java.util.Objects;

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

public class InclusionList {
    public static final DeserializableTypeDefinition<InclusionList> INCLUSION_LIST_TYPE =
            DeserializableTypeDefinition.object(InclusionList.class, InclusionList.Builder.class)
                    .name("InclusionList")
                    .initializer(InclusionList.Builder::new)
                    .finisher(InclusionList.Builder::build)
                   .withField(
                            "validator_index",
                            UINT64_TYPE,
                            InclusionList::getValidatorIndex,
                            InclusionList.Builder::validatorIndex)
                    .withField(
                            "slot",
                            UINT64_TYPE,
                            InclusionList::getSlot,
                            InclusionList.Builder::slot)
                    .withField(
                            "inclusion_list_committee_root",
                            BYTES32_TYPE,
                            InclusionList::getInclusionListCommitteeRoot,
                            InclusionList.Builder::inclusionListCommitteeRoot)
                    .withField(
                            "transactions",
                            DeserializableTypeDefinition.listOf(TRA),
                            InclusionList::getTransactions,
                            InclusionList.Builder::transactions)

                    .build();

    private final UInt64 validatorIndex;
    private final UInt64 slot;
    private final Bytes32 inclusionListCommitteeRoot;
    private final List<Transaction> transactions;

    private InclusionList(final UInt64 validatorIndex, final UInt64 slot, final Bytes32 inclusionListCommitteeRoot, final List<Transaction> transactions) {
        this.slot = slot;
        this.validatorIndex = validatorIndex;
        this.inclusionListCommitteeRoot = inclusionListCommitteeRoot;
        this.transactions = transactions;
    }

    public UInt64 getValidatorIndex() {
        return validatorIndex;
    }

    public UInt64 getSlot() {
        return slot;
    }

    public Bytes32 getInclusionListCommitteeRoot() {
        return inclusionListCommitteeRoot;
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }


    public static InclusionList.Builder builder() {
        return new InclusionList.Builder();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final InclusionList that = (InclusionList) o;
        return validatorIndex == that.validatorIndex
                && Objects.equals(slot, that.slot)
                && Objects.equals(inclusionListCommitteeRoot, that.inclusionListCommitteeRoot)
                && Objects.equals(transactions, that.transactions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(validatorIndex, slot, inclusionListCommitteeRoot);
    }

    public static class Builder {

        private UInt64 validatorIndex;
        private UInt64 slot;
        private Bytes32 inclusionListCommitteeRoot;
        private List<Transaction> transactions;

        public InclusionList.Builder validatorIndex(final UInt64 validatorIndex) {
            this.validatorIndex = validatorIndex;
            return this;
        }

        public InclusionList.Builder slot(final UInt64 slot) {
            this.slot = slot;
            return this;
        }

        public InclusionList.Builder inclusionListCommitteeRoot(final Bytes32 inclusionListCommitteeRoot) {
            this.inclusionListCommitteeRoot = inclusionListCommitteeRoot;
            return this;
        }

        public InclusionList.Builder transactions(final List<Transaction> transactions) {
            this.transactions = transactions;
            return this;
        }

        public InclusionList build() {
            return new InclusionList(validatorIndex, slot, inclusionListCommitteeRoot, transactions);
        }
    }

}
