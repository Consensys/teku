package tech.pegasys.teku.infrastructure.json.types;

import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class EnumTypeHeaderDefinition<T extends Enum<T>> extends EnumTypeDefinition<T> {
    Optional<Boolean> required = Optional.empty();
  
    public EnumTypeHeaderDefinition(final Class<T> itemType, final Function<T, String> serializer, final Optional<String> example, final Optional<String> description, final Optional<String> format, final Optional<Set<T>> excludedEnumerations, final Optional<Boolean> required) {
        super(itemType, serializer, example, description, format, excludedEnumerations);
        this.required = required;
    }

    @Override
    public void serializeOpenApiTypeFields(final JsonGenerator gen) throws IOException {


        if (description.isPresent()) {
            gen.writeStringField("description", description.get());
        }
        if(required.isPresent()){
            gen.writeBooleanField("required", required.get());
        }
        gen.writeFieldName("schema");
        gen.writeStartObject();
        gen.writeStringField("type", "string");

        gen.writeArrayFieldStart("enum");

        for (T value : itemType.getEnumConstants()) {
            if (excludedEnumerations.contains(value)) {
                continue;
            }
            gen.writeString(serializeToString(value));
        }
        gen.writeEndArray();

        if (example.isPresent()) {
            gen.writeStringField("example", example.get());
        }
        gen.writeEndObject();

    }
}
