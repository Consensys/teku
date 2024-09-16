package tech.pegasys.teku.infrastructure.json.types;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.StringWriter;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

public class StringBasedHeaderTypeDefinitionTest {

    @Test
    public void serializeOpenApiType() throws IOException {
        // Create a builder for StringBasedHeaderTypeDefinition
        final StringBasedHeaderTypeDefinition.Builder<String> builder = new StringBasedHeaderTypeDefinition.Builder<>();

        // Set the title, description, required, and example
        builder.title("String-Header");
        builder.description("This is a string header");
        builder.required(true);
        builder.example("test string");

        // Set the parser and formatter functions
        builder.parser(s -> s);
        builder.formatter(s -> s);

        // Build the StringBasedHeaderTypeDefinition
        final StringBasedHeaderTypeDefinition<String> definition = builder.build();

        // Create a StringWriter and a JsonGenerator
        final StringWriter writer = new StringWriter();
        final JsonGenerator gen = new ObjectMapper().getFactory().createGenerator(writer);

        gen.writeStartObject();
        definition.serializeOpenApiType(gen);
        gen.writeEndObject();
        gen.close();

        final String json = writer.toString();

        final String expectedJson = "{\"String-Header\":{\"description\":\"This is a string header\",\"required\":true,\"schema\":{\"type\":\"string\",\"example\":\"test string\"}}}";
        assertEquals(expectedJson, json);
    }
}