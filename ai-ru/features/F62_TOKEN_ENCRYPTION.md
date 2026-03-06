# F62: OAuth Token Encryption

## Статус: Done
## Версия: 0.62.0
## Дата: 2026-03-06

## Проблема

OAuth access/refresh токены и Jira API токены хранились в БД в открытом виде (plaintext). При утечке базы данных злоумышленник получил бы доступ ко всем OAuth-сессиям и Jira API.

## Решение

AES-256 шифрование чувствительных полей через JPA AttributeConverter + Spring Security Crypto.

### Зашифрованные поля

| Таблица | Колонка | Схема |
|---------|---------|-------|
| `oauth_tokens` | `access_token` | public |
| `oauth_tokens` | `refresh_token` | public |
| `tenant_jira_config` | `jira_api_token` | tenant |

### Архитектура

```
EncryptionService (Spring bean, singleton)
  ├── Encryptors.text() — AES/CBC с random IV
  ├── Encryptors.noOpText() — fallback без ключа
  └── decryptSafe() — обратная совместимость (plaintext passthrough)

EncryptedStringConverter (JPA AttributeConverter)
  ├── convertToDatabaseColumn → encrypt
  └── convertToEntityAttribute → decryptSafe

TokenEncryptionMigrationService (@EventListener ApplicationReady)
  ├── Читает raw значения через JDBC (обходит JPA converter)
  ├── Определяет plaintext через isLikelyEncrypted()
  └── Шифрует и обновляет через JDBC
```

### Конфигурация

Переменная окружения `TOKEN_ENCRYPTION_KEY`:
- Не задана / пустая — шифрование отключено (noOp mode), полная обратная совместимость
- Задана — AES-256 шифрование, автоматическая миграция существующих токенов при старте

### Миграция

- Автоматическая при первом старте с `TOKEN_ENCRYPTION_KEY`
- Идемпотентная — безопасно запускать повторно
- Обрабатывает public schema (oauth_tokens) и все tenant schemas (tenant_jira_config)

## Файлы

### Создано
- `backend/src/main/java/com/leadboard/config/EncryptionService.java`
- `backend/src/main/java/com/leadboard/config/EncryptedStringConverter.java`
- `backend/src/main/java/com/leadboard/config/TokenEncryptionMigrationService.java`
- `backend/src/test/java/com/leadboard/config/EncryptionServiceTest.java`

### Изменено
- `AppProperties.java` — добавлен Encryption inner class
- `application.yml` — `app.encryption.token-key`
- `OAuthTokenEntity.java` — `@Convert` на access_token, refresh_token
- `TenantJiraConfigEntity.java` — `@Convert` на jira_api_token

## Тесты

9 тестов в `EncryptionServiceTest`:
- Encrypt/decrypt roundtrip
- Разный ciphertext при повторном шифровании (random IV)
- Null handling
- Empty string handling
- decryptSafe с plaintext passthrough
- decryptSafe с зашифрованными значениями
- isLikelyEncrypted detection
- NoOp mode (пустой ключ)
- NoOp mode (null ключ)
