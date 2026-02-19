package com.leadboard.team;

import com.leadboard.team.dto.AbsenceDto;
import com.leadboard.team.dto.CreateAbsenceRequest;
import com.leadboard.team.dto.UpdateAbsenceRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class AbsenceService {

    private final MemberAbsenceRepository absenceRepository;
    private final TeamMemberRepository memberRepository;

    public AbsenceService(MemberAbsenceRepository absenceRepository, TeamMemberRepository memberRepository) {
        this.absenceRepository = absenceRepository;
        this.memberRepository = memberRepository;
    }

    // ==================== Exceptions ====================

    public static class AbsenceNotFoundException extends RuntimeException {
        public AbsenceNotFoundException(Long id) {
            super("Absence not found: " + id);
        }
    }

    public static class AbsenceOverlapException extends RuntimeException {
        public AbsenceOverlapException() {
            super("Absence dates overlap with an existing absence for this member");
        }
    }

    public static class InvalidAbsenceDatesException extends RuntimeException {
        public InvalidAbsenceDatesException(String message) {
            super(message);
        }
    }

    // ==================== Read operations ====================

    @Transactional(readOnly = true)
    public List<AbsenceDto> getAbsencesForTeam(Long teamId, LocalDate from, LocalDate to) {
        return absenceRepository.findByTeamIdAndDateRange(teamId, from, to).stream()
                .map(AbsenceDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AbsenceDto> getUpcomingAbsences(Long memberId) {
        return absenceRepository.findUpcomingByMemberId(memberId, LocalDate.now()).stream()
                .map(AbsenceDto::from)
                .toList();
    }

    /**
     * Returns absence dates per jiraAccountId for planning integration.
     * Key = jiraAccountId, Value = set of dates when the member is absent.
     */
    @Transactional(readOnly = true)
    public Map<String, Set<LocalDate>> getTeamAbsenceDates(Long teamId, LocalDate from, LocalDate to) {
        List<MemberAbsenceEntity> absences = absenceRepository.findByTeamIdAndDateRange(teamId, from, to);
        Map<String, Set<LocalDate>> result = new HashMap<>();

        for (MemberAbsenceEntity absence : absences) {
            String accountId = absence.getMember().getJiraAccountId();
            Set<LocalDate> dates = result.computeIfAbsent(accountId, k -> new HashSet<>());

            LocalDate current = absence.getStartDate().isBefore(from) ? from : absence.getStartDate();
            LocalDate end = absence.getEndDate().isAfter(to) ? to : absence.getEndDate();

            while (!current.isAfter(end)) {
                dates.add(current);
                current = current.plusDays(1);
            }
        }

        return result;
    }

    // ==================== Write operations ====================

    public AbsenceDto createAbsence(Long teamId, Long memberId, CreateAbsenceRequest request) {
        validateDates(request.startDate(), request.endDate());

        TeamMemberEntity member = memberRepository.findById(memberId)
                .filter(m -> m.getTeam().getId().equals(teamId))
                .orElseThrow(() -> new TeamService.TeamMemberNotFoundException("Team member not found: " + memberId));

        if (absenceRepository.existsOverlapping(memberId, request.startDate(), request.endDate(), null)) {
            throw new AbsenceOverlapException();
        }

        MemberAbsenceEntity entity = new MemberAbsenceEntity();
        entity.setMember(member);
        entity.setAbsenceType(request.absenceType());
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setComment(request.comment());

        return AbsenceDto.from(absenceRepository.save(entity));
    }

    public AbsenceDto updateAbsence(Long teamId, Long memberId, Long absenceId, UpdateAbsenceRequest request) {
        MemberAbsenceEntity entity = absenceRepository.findById(absenceId)
                .filter(a -> a.getMember().getId().equals(memberId) && a.getMember().getTeam().getId().equals(teamId))
                .orElseThrow(() -> new AbsenceNotFoundException(absenceId));

        if (request.absenceType() != null) {
            entity.setAbsenceType(request.absenceType());
        }
        if (request.startDate() != null) {
            entity.setStartDate(request.startDate());
        }
        if (request.endDate() != null) {
            entity.setEndDate(request.endDate());
        }
        if (request.comment() != null) {
            entity.setComment(request.comment());
        }

        validateDates(entity.getStartDate(), entity.getEndDate());

        if (absenceRepository.existsOverlapping(memberId, entity.getStartDate(), entity.getEndDate(), absenceId)) {
            throw new AbsenceOverlapException();
        }

        return AbsenceDto.from(absenceRepository.save(entity));
    }

    public void deleteAbsence(Long teamId, Long memberId, Long absenceId) {
        MemberAbsenceEntity entity = absenceRepository.findById(absenceId)
                .filter(a -> a.getMember().getId().equals(memberId) && a.getMember().getTeam().getId().equals(teamId))
                .orElseThrow(() -> new AbsenceNotFoundException(absenceId));

        absenceRepository.delete(entity);
    }

    // ==================== Validation ====================

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new InvalidAbsenceDatesException("End date must be on or after start date");
        }
    }
}
