package tech.pegasys.teku.beaconrestapi.v1.validator;

import com.fasterxml.jackson.core.JsonProcessingException;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.schema.altair.SyncCommitteeSubnetSubscription;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostSyncCommitteeSubscriptions;
import tech.pegasys.teku.spec.SpecMilestone;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

public class PostSyncCommitteeSubscriptionsIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {
  @Test
  void shouldPostSubscriptions() throws IOException {
    final List<SyncCommitteeSubnetSubscription> validators = List.of(new SyncCommitteeSubnetSubscription(ONE, List.of(ONE), ONE));
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);
    Response response =
        post(PostSyncCommitteeSubscriptions.ROUTE, jsonProvider.objectToJSON(validators));
    assertThat(response.code()).isEqualTo(SC_OK);
  }
}
