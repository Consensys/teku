package tech.pegasys.teku.beaconrestapi.handlers.v1.events;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecarSchema;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;

import java.util.List;

import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.KZG_COMMITMENT_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

public class DataColumnSidecarEvent extends Event<DataColumnSidecarEvent.DataColumnSidecarData> {

    public static final SerializableTypeDefinition<DataColumnSidecarData> DATA_COLUMN_SIDECAR_EVENT_TYPE =
        SerializableTypeDefinition.object(DataColumnSidecarData.class)
            .name("DataColumnSidecarEvent")
            .withField("block_root", BYTES32_TYPE, DataColumnSidecarData::getBlockRoot)
            .withField("index", UINT64_TYPE, DataColumnSidecarData::getIndex)
            .withField("slot", UINT64_TYPE, DataColumnSidecarData::getSlot)
            .withField("kzg_commitments", DeserializableTypeDefinition.listOf(KZG_COMMITMENT_TYPE), DataColumnSidecarData::getKzgCommitments)
            .build();

    private DataColumnSidecarEvent(final Bytes32 blockRoot,
                                   final UInt64 index,
                                   final UInt64 slot,
                                   final List<KZGCommitment> kzgCommitments) {

        super(DATA_COLUMN_SIDECAR_EVENT_TYPE,
                new DataColumnSidecarData(blockRoot,index,slot, kzgCommitments));
    }


    public static DataColumnSidecarEvent create(final DataColumnSidecar dataColumnSidecar) {
        return new DataColumnSidecarEvent(dataColumnSidecar.getBlockRoot(),
                dataColumnSidecar.getIndex(),
                dataColumnSidecar.getSlot(),
                dataColumnSidecar.getSszKZGCommitments().asList().stream()
                        .map(SszKZGCommitment::getKZGCommitment)
                        .toList());
    }

    public static class DataColumnSidecarData{
        private final Bytes32 blockRoot;
        private final UInt64 index;
        private final UInt64 slot;
        private final List<KZGCommitment> kzgCommitments;

        DataColumnSidecarData(
                final Bytes32 blockRoot,
                final UInt64 index,
                final UInt64 slot,
                final List<KZGCommitment> kzgCommitments) {
            this.blockRoot = blockRoot;
            this.index = index;
            this.slot = slot;
            this.kzgCommitments = kzgCommitments;
        }

        public Bytes32 getBlockRoot() {
            return blockRoot;
        }

        public UInt64 getIndex() {
            return index;
        }

        public UInt64 getSlot() {
            return slot;
        }

        public List<KZGCommitment> getKzgCommitments() {
            return kzgCommitments;
        }

    }
}






