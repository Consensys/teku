package tech.pegasys.teku.statetransition.datacolumns;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.stream.AsyncStream;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDbAccessor;

import java.util.Optional;

public class DataColumnSidecarCustodyStub implements DataColumnSidecarCustody {

    private final DataColumnSidecarDbAccessor db;

    public DataColumnSidecarCustodyStub(final DataColumnSidecarDbAccessor db){
        this.db = db;
    }

    @Override
    public SafeFuture<Optional<DataColumnSidecar>> getCustodyDataColumnSidecar(final DataColumnSlotAndIdentifier columnId) {
        return db.getSidecar(columnId);
    }

    @Override
    public SafeFuture<Boolean> hasCustodyDataColumnSidecar(final DataColumnSlotAndIdentifier columnId) {
        return db.getColumnIdentifiers(new SlotAndBlockRoot(columnId.slot(), columnId.blockRoot()))
                .thenApply(ids -> ids.contains(columnId));
    }

    @Override
    public SafeFuture<Void> onNewValidatedDataColumnSidecar(final DataColumnSidecar dataColumnSidecar) {
        return db.addSidecar(dataColumnSidecar);
    }

    @Override
    public AsyncStream<DataColumnSlotAndIdentifier> retrieveMissingColumns() {
        return AsyncStream.empty();
    }
}
