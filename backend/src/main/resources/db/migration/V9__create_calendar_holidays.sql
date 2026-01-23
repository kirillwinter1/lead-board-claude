-- Таблица для хранения праздничных дней (для закрытых контуров или кэша)
CREATE TABLE calendar_holidays (
    id BIGSERIAL PRIMARY KEY,
    date DATE NOT NULL,
    name VARCHAR(255),
    country VARCHAR(2) NOT NULL DEFAULT 'RU',
    year INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),

    CONSTRAINT uq_calendar_holidays_date_country UNIQUE (date, country)
);

CREATE INDEX idx_calendar_holidays_date ON calendar_holidays(date);
CREATE INDEX idx_calendar_holidays_country_year ON calendar_holidays(country, year);

COMMENT ON TABLE calendar_holidays IS 'Производственный календарь - праздничные и выходные дни';
COMMENT ON COLUMN calendar_holidays.date IS 'Дата праздника/выходного';
COMMENT ON COLUMN calendar_holidays.name IS 'Название праздника (опционально)';
COMMENT ON COLUMN calendar_holidays.country IS 'Код страны ISO 3166-1 alpha-2';
COMMENT ON COLUMN calendar_holidays.year IS 'Год (для быстрой фильтрации)';
