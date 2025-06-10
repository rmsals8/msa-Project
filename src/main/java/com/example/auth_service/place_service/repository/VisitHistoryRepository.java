package com.example.auth_service.place_service.repository;

import com.example.auth_service.place_service.domain.VisitHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VisitHistoryRepository extends JpaRepository<VisitHistory, Long> {

    // ✅ 인덱스 활용한 최적화된 조회 쿼리
    @Query("SELECT vh FROM VisitHistory vh WHERE vh.userId = :userId ORDER BY vh.visitDate DESC")
    List<VisitHistory> findByUserIdOrderByVisitDateDesc(@Param("userId") String userId);

    // ✅ 페이징 처리된 조회 (인덱스 활용)
    @Query("SELECT vh FROM VisitHistory vh WHERE vh.userId = :userId ORDER BY vh.visitDate DESC")
    Page<VisitHistory> findByUserIdOrderByVisitDateDesc(@Param("userId") String userId, Pageable pageable);

    // ✅ 카테고리별 조회 (복합 인덱스 활용)
    @Query("SELECT vh FROM VisitHistory vh WHERE vh.userId = :userId AND vh.category = :category ORDER BY vh.visitDate DESC")
    List<VisitHistory> findByUserIdAndCategory(@Param("userId") String userId, @Param("category") String category);

    // ✅ 카테고리별 페이징 조회 (복합 인덱스 활용)
    @Query("SELECT vh FROM VisitHistory vh WHERE vh.userId = :userId AND vh.category = :category ORDER BY vh.visitDate DESC")
    Page<VisitHistory> findByUserIdAndCategoryPageable(@Param("userId") String userId, @Param("category") String category, Pageable pageable);

    // ✅ 카테고리별 통계 (집계 쿼리 최적화)
    @Query("SELECT vh.category, COUNT(vh) FROM VisitHistory vh WHERE vh.userId = :userId GROUP BY vh.category ORDER BY COUNT(vh) DESC")
    List<Object[]> countVisitsByCategory(@Param("userId") String userId);

    // ✅ 기본 조회 메서드들
    List<VisitHistory> findByUserId(String userId);
    
    Optional<VisitHistory> findByUserIdAndPlaceId(String userId, String placeId);
}