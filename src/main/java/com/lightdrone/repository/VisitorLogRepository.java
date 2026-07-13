package com.lightdrone.repository;

import com.lightdrone.domain.VisitorLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface VisitorLogRepository extends JpaRepository<VisitorLog, Long> {

    boolean existsByVisitDateAndIpHash(LocalDate date, String ipHash);

    long countByVisitDate(LocalDate date);

    @Query("SELECT v.visitDate, COUNT(v) FROM VisitorLog v WHERE v.visitDate >= :start GROUP BY v.visitDate ORDER BY v.visitDate ASC")
    List<Object[]> countByDateSince(@Param("start") LocalDate start);
}
