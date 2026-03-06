package com.leadboard.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        EncryptionService service = EncryptionService.getInstance();
        if (service == null) return attribute;
        return service.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        EncryptionService service = EncryptionService.getInstance();
        if (service == null) return dbData;
        return service.decryptSafe(dbData);
    }
}
