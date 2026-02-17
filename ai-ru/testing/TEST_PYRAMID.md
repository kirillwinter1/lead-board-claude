# Test Pyramid — Lead Board

Документация по тестовой пирамиде проекта и инструкции по написанию тестов.

## Текущее состояние

```
                    ╱╲
                   ╱  ╲
                  ╱ E2E╲     0 тестов
                 ╱──────╲
                ╱        ╲
               ╱Integration╲   0 тестов
              ╱────────────╲
             ╱              ╲
            ╱     Unit       ╲   ~290 тестов
           ╱──────────────────╲
```

**Проблема:** Пирамида перевёрнута — нет интеграционных и E2E тестов, только unit.

---

## Покрытие по компонентам

### ✅ Покрыты тестами

| Компонент | Тесты | Описание |
|-----------|-------|----------|
| AutoScoreCalculator | 42 | Расчёт приоритета эпиков |
| StoryAutoScoreService | 40 | Расчёт приоритета сторей |
| StatusMappingService | 34 | Маппинг статусов Jira |
| DataQualityService | 32 | Правила качества данных |
| TeamService | 19 | CRUD команд |
| IssueOrderService | 19 | Ручная сортировка |
| StoryDependencyService | 18 | Зависимости между сторями |
| WorkCalendarService | 17 | Производственный календарь |
| TeamControllerTest | 15 | API команд |
| ForecastService | 11 | Прогнозирование |

### ❌ НЕ покрыты тестами (критично)

| Сервис | LOC | Риск | Причина |
|--------|-----|------|---------|
| **SyncService** | ~400 | 🔴 | Синхронизация с Jira, был баг с FK |
| **BoardService** | 621 | 🔴 | Основная логика агрегации |
| **AutoScoreService** | ~150 | 🟡 | Обёртка над Calculator |
| **TeamSyncService** | ~200 | 🟡 | Синхронизация команд |
| **PokerSessionService** | ~300 | 🟡 | Planning Poker |
| EpicService | ~100 | 🟢 | Простой CRUD |
| OAuthService | ~150 | 🟢 | Внешняя интеграция |

---

## Типы тестов

### Unit тесты (текущий фокус)

**Расположение:** `src/test/java/com/leadboard/`

**Характеристики:**
- Мокают все зависимости через Mockito
- Быстрые (~5 сек на все)
- Не проверяют интеграцию компонентов
- Не проверяют DB constraints

**Пример:**
```java
@ExtendWith(MockitoExtension.class)
class MyServiceTest {
    @Mock
    private MyRepository repository;

    @InjectMocks
    private MyService service;

    @Test
    void shouldDoSomething() {
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        var result = service.process(1L);

        assertThat(result).isNotNull();
        verify(repository).findById(1L);
    }
}
```

### Integration тесты (нужно добавить)

**Расположение:** `src/test/java/com/leadboard/integration/`

**Характеристики:**
- Используют `@SpringBootTest`
- Тестируют с реальной PostgreSQL (Testcontainers)
- Проверяют FK constraints, транзакции
- Медленнее (~30 сек)

**Пример:**
```java
@SpringBootTest
@Testcontainers
class SyncServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @Autowired
    private SyncService syncService;

    @Autowired
    private JiraIssueRepository issueRepository;

    @Test
    void syncNewIssue_shouldSaveIssueBeforeChangelog() {
        // Given: мок JiraClient возвращает новый issue

        // When
        syncService.syncProject("LB");

        // Then: issue в БД, changelog записан
        assertThat(issueRepository.findByIssueKey("LB-1")).isPresent();
    }
}
```

### E2E тесты (опционально)

**Расположение:** `src/test/java/com/leadboard/e2e/`

**Характеристики:**
- Полный стек: API → Service → DB
- Тестируют реальные HTTP endpoints
- Самые медленные

---

## Инструкции по написанию тестов

### 1. Структура тестового класса

```java
package com.leadboard.mypackage;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MyServiceTest {

    @Mock
    private DependencyOne depOne;

    @Mock
    private DependencyTwo depTwo;

    @InjectMocks
    private MyService service;

    // === Группировка тестов по методам ===

    @Nested
    @DisplayName("methodName()")
    class MethodNameTests {

        @Test
        @DisplayName("should return X when Y")
        void shouldReturnXWhenY() {
            // Given
            var input = createTestInput();
            when(depOne.find(any())).thenReturn(something);

            // When
            var result = service.methodName(input);

            // Then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("should throw exception when invalid input")
        void shouldThrowWhenInvalidInput() {
            assertThatThrownBy(() -> service.methodName(null))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
```

### 2. Naming Convention

```
Файл:     {ClassName}Test.java
Метод:    should{Expected}When{Condition}()
Display:  @DisplayName("should return X when Y")
```

**Примеры:**
```java
void shouldCalculateScoreWhenAllFieldsPresent()
void shouldReturnEmptyWhenNoData()
void shouldThrowWhenUserNotFound()
void shouldSaveBeforeChangelog()  // Регрессия для бага
```

### 3. AAA Pattern (Arrange-Act-Assert)

```java
@Test
void shouldCalculateCorrectly() {
    // Arrange (Given)
    var entity = createTestEntity();
    when(repository.findById(1L)).thenReturn(Optional.of(entity));

    // Act (When)
    var result = service.calculate(1L);

    // Then (Assert)
    assertThat(result.getScore()).isEqualTo(BigDecimal.valueOf(42));
    assertThat(result.getFactors()).containsKey("priority");
    verify(repository).findById(1L);
    verifyNoMoreInteractions(repository);
}
```

### 4. Test Data Builders

Для сложных entity создавай builder-методы:

```java
class TestDataFactory {

    static JiraIssueEntity epicWithTeam(Long teamId) {
        var entity = new JiraIssueEntity();
        entity.setIssueKey("LB-" + System.currentTimeMillis());
        entity.setIssueType("Epic");
        entity.setStatus("Новое");
        entity.setTeamId(teamId);
        return entity;
    }

    static JiraIssueEntity storyInEpic(String epicKey) {
        var entity = new JiraIssueEntity();
        entity.setIssueKey("LB-" + System.currentTimeMillis());
        entity.setIssueType("Story");
        entity.setParentKey(epicKey);
        return entity;
    }
}
```

---

## Запуск тестов

```bash
# Все тесты
./gradlew test

# Конкретный класс
./gradlew test --tests "SyncServiceTest"

# Конкретный метод
./gradlew test --tests "SyncServiceTest.shouldSaveBeforeChangelog"

# С coverage отчётом
./gradlew test jacocoTestReport

# Только быстрые unit тесты (исключить integration)
./gradlew test -PexcludeIntegration
```

---

## Приоритеты покрытия

### P0 — Критично (добавить срочно)

1. **SyncService** — был баг, нужен регрессионный тест:
   ```java
   @Test
   void shouldSaveIssueBeforeRecordingChangelog()
   ```

2. **BoardService** — основная логика, 621 LOC без тестов

### P1 — Важно

3. **TeamSyncService** — синхронизация команд
4. **PokerSessionService** — WebSocket сессии

### P2 — Желательно

5. Integration тесты с Testcontainers
6. AutoScoreService (обёртка)

---

## CI/CD интеграция

В GitHub Actions тесты запускаются автоматически:

```yaml
# .github/workflows/test.yml
- name: Run tests
  run: ./gradlew test

- name: Upload coverage
  uses: codecov/codecov-action@v3
```

---

## Известные проблемы

| Проблема | Статус | Описание |
|----------|--------|----------|
| SyncService FK bug | ✅ Fixed | Changelog записывался до save issue |
| No integration tests | ⏳ TODO | Нужны Testcontainers |
| BoardService 0% | ⏳ TODO | 621 LOC без тестов |

---

## История изменений

| Дата | Изменение |
|------|-----------|
| 2026-02-01 | Создание документа, фикс SyncService |
