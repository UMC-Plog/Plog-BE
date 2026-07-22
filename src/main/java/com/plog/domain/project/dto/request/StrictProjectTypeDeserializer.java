package com.plog.domain.project.dto.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.plog.domain.project.entity.ProjectType;
import java.io.IOException;

final class StrictProjectTypeDeserializer extends JsonDeserializer<ProjectType> {

    @Override
    public ProjectType deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return (ProjectType) context.handleUnexpectedToken(ProjectType.class, parser);
        }

        String value = parser.getValueAsString();
        try {
            return ProjectType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return (ProjectType) context.handleWeirdStringValue(
                    ProjectType.class,
                    value,
                    "지원하지 않는 프로젝트 유형입니다."
            );
        }
    }
}
