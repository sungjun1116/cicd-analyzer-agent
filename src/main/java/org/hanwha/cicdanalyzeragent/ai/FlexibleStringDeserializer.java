package org.hanwha.cicdanalyzeragent.ai;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class FlexibleStringDeserializer extends JsonDeserializer<String> {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonToken token = parser.currentToken();

        if (token == JsonToken.VALUE_NULL) {
            return null;
        }

        if (token == JsonToken.START_ARRAY) {
            List<String> values = new ArrayList<>();
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                String value = parser.getValueAsString();
                if (value != null && !value.isBlank()) {
                    values.add(value);
                }
            }
            return values.isEmpty() ? null : String.join(", ", values);
        }

        return parser.getValueAsString();
    }
}
