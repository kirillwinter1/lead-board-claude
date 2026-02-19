package com.leadboard.team;

import com.leadboard.team.dto.AbsenceDto;
import com.leadboard.team.dto.CreateAbsenceRequest;
import com.leadboard.team.dto.UpdateAbsenceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AbsenceServiceTest {

    @Mock
    private MemberAbsenceRepository absenceRepository;

    @Mock
    private TeamMemberRepository memberRepository;

    private AbsenceService absenceService;

    @BeforeEach
    void setUp() {
        absenceService = new AbsenceService(absenceRepository, memberRepository);
    }

    // ==================== CRUD Tests ====================

    @Test
    void createAbsenceSuccessfully() {
        Long teamId = 1L;
        Long memberId = 10L;
        TeamMemberEntity member = createMember(memberId, teamId, "user1");

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(absenceRepository.existsOverlapping(eq(memberId), any(), any(), isNull())).thenReturn(false);
        when(absenceRepository.save(any())).thenAnswer(inv -> {
            MemberAbsenceEntity e = inv.getArgument(0);
            e.setId(100L);
            e.setCreatedAt(OffsetDateTime.now());
            return e;
        });

        CreateAbsenceRequest request = new CreateAbsenceRequest(
                AbsenceType.VACATION,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 10),
                "Spring vacation"
        );

        AbsenceDto result = absenceService.createAbsence(teamId, memberId, request);

        assertNotNull(result);
        assertEquals(100L, result.id());
        assertEquals(memberId, result.memberId());
        assertEquals(AbsenceType.VACATION, result.absenceType());
        assertEquals(LocalDate.of(2026, 3, 1), result.startDate());
        assertEquals(LocalDate.of(2026, 3, 10), result.endDate());
        assertEquals("Spring vacation", result.comment());
    }

    @Test
    void createAbsenceThrowsOnOverlap() {
        Long teamId = 1L;
        Long memberId = 10L;
        TeamMemberEntity member = createMember(memberId, teamId, "user1");

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(absenceRepository.existsOverlapping(eq(memberId), any(), any(), isNull())).thenReturn(true);

        CreateAbsenceRequest request = new CreateAbsenceRequest(
                AbsenceType.VACATION,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 10),
                null
        );

        assertThrows(AbsenceService.AbsenceOverlapException.class, () ->
                absenceService.createAbsence(teamId, memberId, request));
    }

    @Test
    void createAbsenceThrowsOnInvalidDates() {
        CreateAbsenceRequest request = new CreateAbsenceRequest(
                AbsenceType.VACATION,
                LocalDate.of(2026, 3, 10),
                LocalDate.of(2026, 3, 1),
                null
        );

        assertThrows(AbsenceService.InvalidAbsenceDatesException.class, () ->
                absenceService.createAbsence(1L, 10L, request));
    }

    @Test
    void createAbsenceThrowsOnMemberNotFound() {
        when(memberRepository.findById(99L)).thenReturn(Optional.empty());

        CreateAbsenceRequest request = new CreateAbsenceRequest(
                AbsenceType.VACATION,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 10),
                null
        );

        assertThrows(TeamService.TeamMemberNotFoundException.class, () ->
                absenceService.createAbsence(1L, 99L, request));
    }

    @Test
    void updateAbsenceSuccessfully() {
        Long teamId = 1L;
        Long memberId = 10L;
        Long absenceId = 100L;

        MemberAbsenceEntity existing = createAbsenceEntity(absenceId, memberId, teamId,
                AbsenceType.VACATION, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 10));

        when(absenceRepository.findById(absenceId)).thenReturn(Optional.of(existing));
        when(absenceRepository.existsOverlapping(eq(memberId), any(), any(), eq(absenceId))).thenReturn(false);
        when(absenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateAbsenceRequest request = new UpdateAbsenceRequest(
                AbsenceType.SICK_LEAVE, null, null, "Got sick"
        );

        AbsenceDto result = absenceService.updateAbsence(teamId, memberId, absenceId, request);

        assertEquals(AbsenceType.SICK_LEAVE, result.absenceType());
        assertEquals("Got sick", result.comment());
        assertEquals(LocalDate.of(2026, 3, 1), result.startDate()); // unchanged
    }

    @Test
    void deleteAbsenceSuccessfully() {
        Long teamId = 1L;
        Long memberId = 10L;
        Long absenceId = 100L;

        MemberAbsenceEntity existing = createAbsenceEntity(absenceId, memberId, teamId,
                AbsenceType.DAY_OFF, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 1));

        when(absenceRepository.findById(absenceId)).thenReturn(Optional.of(existing));

        absenceService.deleteAbsence(teamId, memberId, absenceId);

        verify(absenceRepository).delete(existing);
    }

    @Test
    void deleteAbsenceThrowsWhenNotFound() {
        when(absenceRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(AbsenceService.AbsenceNotFoundException.class, () ->
                absenceService.deleteAbsence(1L, 10L, 999L));
    }

    // ==================== getTeamAbsenceDates Tests ====================

    @Test
    void getTeamAbsenceDatesReturnsCorrectDates() {
        Long teamId = 1L;
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);

        MemberAbsenceEntity absence1 = createAbsenceEntity(1L, 10L, teamId,
                AbsenceType.VACATION, LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 7));
        absence1.getMember().setJiraAccountId("user-a");

        MemberAbsenceEntity absence2 = createAbsenceEntity(2L, 20L, teamId,
                AbsenceType.SICK_LEAVE, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 10));
        absence2.getMember().setJiraAccountId("user-b");

        when(absenceRepository.findByTeamIdAndDateRange(teamId, from, to))
                .thenReturn(List.of(absence1, absence2));

        Map<String, Set<LocalDate>> result = absenceService.getTeamAbsenceDates(teamId, from, to);

        assertEquals(2, result.size());

        Set<LocalDate> userADates = result.get("user-a");
        assertEquals(3, userADates.size());
        assertTrue(userADates.contains(LocalDate.of(2026, 3, 5)));
        assertTrue(userADates.contains(LocalDate.of(2026, 3, 6)));
        assertTrue(userADates.contains(LocalDate.of(2026, 3, 7)));

        Set<LocalDate> userBDates = result.get("user-b");
        assertEquals(1, userBDates.size());
        assertTrue(userBDates.contains(LocalDate.of(2026, 3, 10)));
    }

    @Test
    void getTeamAbsenceDatesClipsToRange() {
        Long teamId = 1L;
        LocalDate from = LocalDate.of(2026, 3, 3);
        LocalDate to = LocalDate.of(2026, 3, 5);

        // Absence spans beyond the range
        MemberAbsenceEntity absence = createAbsenceEntity(1L, 10L, teamId,
                AbsenceType.VACATION, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 10));
        absence.getMember().setJiraAccountId("user-a");

        when(absenceRepository.findByTeamIdAndDateRange(teamId, from, to))
                .thenReturn(List.of(absence));

        Map<String, Set<LocalDate>> result = absenceService.getTeamAbsenceDates(teamId, from, to);

        Set<LocalDate> dates = result.get("user-a");
        assertEquals(3, dates.size()); // only 3, 4, 5
        assertTrue(dates.contains(LocalDate.of(2026, 3, 3)));
        assertTrue(dates.contains(LocalDate.of(2026, 3, 4)));
        assertTrue(dates.contains(LocalDate.of(2026, 3, 5)));
        assertFalse(dates.contains(LocalDate.of(2026, 3, 2)));
        assertFalse(dates.contains(LocalDate.of(2026, 3, 6)));
    }

    @Test
    void getUpcomingAbsencesReturnsCurrentAndFuture() {
        Long memberId = 10L;
        MemberAbsenceEntity absence = createAbsenceEntity(1L, memberId, 1L,
                AbsenceType.VACATION, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 10));

        when(absenceRepository.findUpcomingByMemberId(eq(memberId), any()))
                .thenReturn(List.of(absence));

        List<AbsenceDto> result = absenceService.getUpcomingAbsences(memberId);

        assertEquals(1, result.size());
        assertEquals(AbsenceType.VACATION, result.get(0).absenceType());
    }

    // ==================== Helpers ====================

    private TeamMemberEntity createMember(Long memberId, Long teamId, String accountId) {
        TeamEntity team = new TeamEntity();
        team.setId(teamId);
        team.setName("Test Team");

        TeamMemberEntity member = new TeamMemberEntity();
        member.setId(memberId);
        member.setTeam(team);
        member.setJiraAccountId(accountId);
        member.setDisplayName("User " + accountId);
        return member;
    }

    private MemberAbsenceEntity createAbsenceEntity(Long id, Long memberId, Long teamId,
                                                      AbsenceType type, LocalDate start, LocalDate end) {
        TeamMemberEntity member = createMember(memberId, teamId, "user-" + memberId);

        MemberAbsenceEntity entity = new MemberAbsenceEntity();
        entity.setId(id);
        entity.setMember(member);
        entity.setAbsenceType(type);
        entity.setStartDate(start);
        entity.setEndDate(end);
        entity.setCreatedAt(OffsetDateTime.now());
        return entity;
    }
}
