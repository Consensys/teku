package tech.pegasys.teku.infrastructure.json.types;



import java.io.IOException;
import java.io.StringWriter;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonGenerator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BooleanHeaderTypeDefinitionTest {

    @Test
    public void serializeOpenApiType() throws IOException {

        final BooleanHeaderTypeDefinition definition = new BooleanHeaderTypeDefinition(
                "MyBoolean",
                Optional.of(true),
                "This is a boolean header"
        );

        final StringWriter writer = new StringWriter();
        final JsonGenerator gen = new ObjectMapper().getFactory().createGenerator(writer);

        gen.writeStartObject();
        definition.serializeOpenApiType(gen);
        gen.writeEndObject();
        gen.close();

        final String json = writer.toString();

        final String expectedJson = "{\"MyBoolean\":{\"description\":\"This is a boolean header\",\"required\":true,\"schema\":{\"type\":\"boolean\"}}}";
        assertEquals(expectedJson, json);
    }
}