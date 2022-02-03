package tech.pegasys.teku.validator.client.restapi.apis.schema;

import com.google.common.base.MoreObjects;
import tech.pegasys.teku.validator.client.Validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PostRemoteKeysRequest {
    private List<Validator> validators = new ArrayList<>();

    public PostRemoteKeysRequest() {}

    public PostRemoteKeysRequest(final List<Validator> validators) {
        this.validators = validators;
    }

    public List<Validator> getValidators() {
        return validators;
    }

    public void setValidators(final List<Validator> validators) {
        this.validators = validators;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final PostRemoteKeysRequest that = (PostRemoteKeysRequest) o;
        return Objects.equals(validators, that.validators);
    }

    @Override
    public int hashCode() {
        return Objects.hash(validators);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("validators", validators).toString();
    }
}
