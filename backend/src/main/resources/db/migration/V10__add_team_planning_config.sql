-- Добавляем конфигурацию планирования для команд
ALTER TABLE teams ADD COLUMN planning_config JSONB DEFAULT '{
    "gradeCoefficients": {
        "senior": 0.8,
        "middle": 1.0,
        "junior": 1.5
    },
    "riskBuffer": 0.2,
    "wipLimits": {
        "team": 6,
        "sa": 2,
        "dev": 3,
        "qa": 2
    }
}'::jsonb;

COMMENT ON COLUMN teams.planning_config IS 'Конфигурация автопланирования: коэффициенты грейдов, буфер рисков, WIP лимиты';
