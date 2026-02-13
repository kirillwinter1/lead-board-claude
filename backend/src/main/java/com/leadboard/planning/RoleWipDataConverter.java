package com.leadboard.planning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Map;

/**
 * JPA AttributeConverter for storing Map<String, RoleWipEntry> as JSONB.
 * Converts between Java Map and PostgreSQL JSONB column.
 *
 * JSON format:
 * {
 *   "SA": {"limit": 3, "current": 2},
 *   "DEV": {"limit": 4, "current": 3},
 *   "QA": {"limit": 2, "current": 1}
 * }
 */
@Converter
public class RoleWipDataConverter implements AttributeConverter<Map<String, WipSnapshotEntity.RoleWipEntry>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, WipSnapshotEntity.RoleWipEntry>> TYPE_REF =
            new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, WipSnapshotEntity.RoleWipEntry> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize roleWipData to JSON", e);
        }
    }

    @Override
    public Map<String, WipSnapshotEntity.RoleWipEntry> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(dbData, TYPE_REF);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize roleWipData from JSON: " + dbData, e);
        }
    }
}
