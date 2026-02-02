package com.leadboard.epic;

import com.leadboard.config.RoughEstimateProperties;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EpicServiceTest {

    @Mock
    private JiraIssueRepository issueRepository;

    @Mock
    private RoughEstimateProperties roughEstimateProperties;

    private EpicService epicService;

    @BeforeEach
    void setUp() {
        epicService = new EpicService(issueRepository, roughEstimateProperties);

        // Common setup
        when(roughEstimateProperties.isEnabled()).thenReturn(true);
        when(roughEstimateProperties.getAllowedEpicStatuses()).thenReturn(List.of("Новое", "Requirements", "Rough Estimate"));
        when(roughEstimateProperties.getStepDays()).thenReturn(BigDecimal.valueOf(0.5));
        when(roughEstimateProperties.getMinDays()).thenReturn(BigDecimal.ZERO);
        when(roughEstimateProperties.getMaxDays()).thenReturn(BigDecimal.valueOf(100));
    }

    // ==================== getRoughEstimateConfig() Tests ====================

    @Nested
    @DisplayName("getRoughEstimateConfig()")
    class GetConfigTests {

        @Test
        @DisplayName("should return rough estimate config")
        void shouldReturnConfig() {
            RoughEstimateConfigDto config = epicService.getRoughEstimateConfig();

            assertTrue(config.enabled());
            assertEquals(List.of("Новое", "Requirements", "Rough Estimate"), config.allowedEpicStatuses());
            assertEquals(BigDecimal.valueOf(0.5), config.stepDays());
            assertEquals(BigDecimal.ZERO, config.minDays());
            assertEquals(BigDecimal.valueOf(100), config.maxDays());
        }
    }

    // ==================== updateRoughEstimate() Tests ====================

    @Nested
    @DisplayName("updateRoughEstimate()")
    class UpdateRoughEstimateTests {

        @Test
        @DisplayName("should update SA rough estimate")
        void shouldUpdateSaEstimate() {
            JiraIssueEntity epic = createEpic("LB-1", "Test Epic", "Новое");
            RoughEstimateRequestDto request = new RoughEstimateRequestDto(BigDecimal.valueOf(5), "user@test.com");

            when(issueRepository.findByIssueKey("LB-1")).thenReturn(Optional.of(epic));
            when(roughEstimateProperties.isStatusAllowed("Новое")).thenReturn(true);
            when(issueRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            RoughEstimateResponseDto response = epicService.updateRoughEstimate("LB-1", "sa", request);

            assertEquals("LB-1", response.epicKey());
            assertEquals("sa", response.role());
            assertEquals(BigDecimal.valueOf(5), response.updatedDays());
            assertEquals(BigDecimal.valueOf(5), response.saDays());
        }

        @Test
        @DisplayName("should update DEV rough estimate")
        void shouldUpdateDevEstimate() {
            JiraIssueEntity epic = createEpic("LB-1", "Test Epic", "Новое");
            RoughEstimateRequestDto request = new RoughEstimateRequestDto(BigDecimal.valueOf(10), "user@test.com");

            when(issueRepository.findByIssueKey("LB-1")).thenReturn(Optional.of(epic));
            when(roughEstimateProperties.isStatusAllowed("Новое")).thenReturn(true);
            when(issueRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            RoughEstimateResponseDto response = epicService.updateRoughEstimate("LB-1", "dev", request);

            assertEquals("dev", response.role());
            assertEquals(BigDecimal.valueOf(10), response.devDays());
        }

        @Test
        @DisplayName("should update QA rough estimate")
        void shouldUpdateQaEstimate() {
            JiraIssueEntity epic = createEpic("LB-1", "Test Epic", "Новое");
            RoughEstimateRequestDto request = new RoughEstimateRequestDto(BigDecimal.valueOf(3), "user@test.com");

            when(issueRepository.findByIssueKey("LB-1")).thenReturn(Optional.of(epic));
            when(roughEstimateProperties.isStatusAllowed("Новое")).thenReturn(true);
            when(issueRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            RoughEstimateResponseDto response = epicService.updateRoughEstimate("LB-1", "qa", request);

            assertEquals("qa", response.role());
            assertEquals(BigDecimal.valueOf(3), response.qaDays());
        }

        @Test
        @DisplayName("should throw when feature is disabled")
        void shouldThrowWhenDisabled() {
            when(roughEstimateProperties.isEnabled()).thenReturn(false);
            RoughEstimateRequestDto request = new RoughEstimateRequestDto(BigDecimal.valueOf(5), "user");

            RoughEstimateException exception = assertThrows(RoughEstimateException.class, () ->
                    epicService.updateRoughEstimate("LB-1", "sa", request));

            assertTrue(exception.getMessage().contains("disabled"));
        }

        @Test
        @DisplayName("should throw for invalid role")
        void shouldThrowForInvalidRole() {
            RoughEstimateRequestDto request = new RoughEstimateRequestDto(BigDecimal.valueOf(5), "user");

            RoughEstimateException exception = assertThrows(RoughEstimateException.class, () ->
                    epicService.updateRoughEstimate("LB-1", "invalid", request));

            assertTrue(exception.getMessage().contains("Invalid role"));
        }

        @Test
        @DisplayName("should throw when epic not found")
        void shouldThrowWhenEpicNotFound() {
            when(issueRepository.findByIssueKey("LB-999")).thenReturn(Optional.empty());
            RoughEstimateRequestDto request = new RoughEstimateRequestDto(BigDecimal.valueOf(5), "user");

            RoughEstimateException exception = assertThrows(RoughEstimateException.class, () ->
                    epicService.updateRoughEstimate("LB-999", "sa", request));

            assertTrue(exception.getMessage().contains("Epic not found"));
        }

        @Test
        @DisplayName("should throw when issue is not an Epic")
        void shouldThrowWhenNotEpic() {
            JiraIssueEntity story = new JiraIssueEntity();
            story.setIssueKey("LB-1");
            story.setIssueType("Story");

            when(issueRepository.findByIssueKey("LB-1")).thenReturn(Optional.of(story));
            RoughEstimateRequestDto request = new RoughEstimateRequestDto(BigDecimal.valueOf(5), "user");

            RoughEstimateException exception = assertThrows(RoughEstimateException.class, () ->
                    epicService.updateRoughEstimate("LB-1", "sa", request));

            assertTrue(exception.getMessage().contains("not an Epic"));
        }

        @Test
        @DisplayName("should throw when status not allowed")
        void shouldThrowWhenStatusNotAllowed() {
            JiraIssueEntity epic = createEpic("LB-1", "Test Epic", "Готово");

            when(issueRepository.findByIssueKey("LB-1")).thenReturn(Optional.of(epic));
            when(roughEstimateProperties.isStatusAllowed("Готово")).thenReturn(false);
            RoughEstimateRequestDto request = new RoughEstimateRequestDto(BigDecimal.valueOf(5), "user");

            RoughEstimateException exception = assertThrows(RoughEstimateException.class, () ->
                    epicService.updateRoughEstimate("LB-1", "sa", request));

            assertTrue(exception.getMessage().contains("Cannot edit rough estimate"));
        }

        @Test
        @DisplayName("should throw when value below minimum")
        void shouldThrowWhenBelowMin() {
            JiraIssueEntity epic = createEpic("LB-1", "Test Epic", "Новое");

            when(issueRepository.findByIssueKey("LB-1")).thenReturn(Optional.of(epic));
            when(roughEstimateProperties.isStatusAllowed("Новое")).thenReturn(true);
            when(roughEstimateProperties.getMinDays()).thenReturn(BigDecimal.ONE);

            RoughEstimateRequestDto request = new RoughEstimateRequestDto(BigDecimal.valueOf(0.5), "user");

            RoughEstimateException exception = assertThrows(RoughEstimateException.class, () ->
                    epicService.updateRoughEstimate("LB-1", "sa", request));

            assertTrue(exception.getMessage().contains("must be >="));
        }

        @Test
        @DisplayName("should throw when value above maximum")
        void shouldThrowWhenAboveMax() {
            JiraIssueEntity epic = createEpic("LB-1", "Test Epic", "Новое");

            when(issueRepository.findByIssueKey("LB-1")).thenReturn(Optional.of(epic));
            when(roughEstimateProperties.isStatusAllowed("Новое")).thenReturn(true);
            when(roughEstimateProperties.getMaxDays()).thenReturn(BigDecimal.valueOf(50));

            RoughEstimateRequestDto request = new RoughEstimateRequestDto(BigDecimal.valueOf(100), "user");

            RoughEstimateException exception = assertThrows(RoughEstimateException.class, () ->
                    epicService.updateRoughEstimate("LB-1", "sa", request));

            assertTrue(exception.getMessage().contains("must be <="));
        }

        @Test
        @DisplayName("should accept Russian Epic type")
        void shouldAcceptRussianEpicType() {
            JiraIssueEntity epic = new JiraIssueEntity();
            epic.setIssueKey("LB-1");
            epic.setIssueType("Эпик");
            epic.setStatus("Новое");

            when(issueRepository.findByIssueKey("LB-1")).thenReturn(Optional.of(epic));
            when(roughEstimateProperties.isStatusAllowed("Новое")).thenReturn(true);
            when(issueRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            RoughEstimateRequestDto request = new RoughEstimateRequestDto(BigDecimal.valueOf(5), "user");

            RoughEstimateResponseDto response = epicService.updateRoughEstimate("LB-1", "sa", request);

            assertEquals(BigDecimal.valueOf(5), response.saDays());
        }

        @Test
        @DisplayName("should save entity with updated timestamp")
        void shouldSaveWithTimestamp() {
            JiraIssueEntity epic = createEpic("LB-1", "Test Epic", "Новое");

            when(issueRepository.findByIssueKey("LB-1")).thenReturn(Optional.of(epic));
            when(roughEstimateProperties.isStatusAllowed("Новое")).thenReturn(true);
            when(issueRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            RoughEstimateRequestDto request = new RoughEstimateRequestDto(BigDecimal.valueOf(5), "user@test.com");
            epicService.updateRoughEstimate("LB-1", "sa", request);

            ArgumentCaptor<JiraIssueEntity> captor = ArgumentCaptor.forClass(JiraIssueEntity.class);
            verify(issueRepository).save(captor.capture());

            assertNotNull(captor.getValue().getRoughEstimateUpdatedAt());
            assertEquals("user@test.com", captor.getValue().getRoughEstimateUpdatedBy());
        }
    }

    // ==================== Helper Methods ====================

    private JiraIssueEntity createEpic(String key, String summary, String status) {
        JiraIssueEntity entity = new JiraIssueEntity();
        entity.setIssueKey(key);
        entity.setIssueId("id-" + key);
        entity.setSummary(summary);
        entity.setStatus(status);
        entity.setIssueType("Epic");
        entity.setProjectKey("LB");
        return entity;
    }
}
