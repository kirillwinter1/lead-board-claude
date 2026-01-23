package com.leadboard.calendar;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Repository
public interface CalendarHolidayRepository extends JpaRepository<CalendarHolidayEntity, Long> {

    List<CalendarHolidayEntity> findByCountryAndYear(String country, Integer year);

    List<CalendarHolidayEntity> findByCountryAndDateBetween(String country, LocalDate from, LocalDate to);

    @Query("SELECT h.date FROM CalendarHolidayEntity h WHERE h.country = :country AND h.date BETWEEN :from AND :to")
    Set<LocalDate> findHolidayDatesByCountryAndDateBetween(
            @Param("country") String country,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    boolean existsByCountryAndYear(String country, Integer year);

    void deleteByCountryAndYear(String country, Integer year);
}
