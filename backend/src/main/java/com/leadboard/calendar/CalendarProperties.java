package com.leadboard.calendar;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "planning.calendar")
public class CalendarProperties {

    /**
     * Источник данных календаря: api, file, database
     */
    private String source = "api";

    /**
     * Путь к файлу с праздниками (для source=file)
     */
    private String filePath;

    /**
     * TTL кэша в секундах (по умолчанию 24 часа)
     */
    private long cacheTtl = 86400;

    /**
     * Код страны по умолчанию
     */
    private String country = "RU";

    // Getters and Setters
    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public long getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(long cacheTtl) {
        this.cacheTtl = cacheTtl;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }
}
