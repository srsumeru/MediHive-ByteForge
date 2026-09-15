package com.byteforge.medihive.repository;
import com.byteforge.medihive.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
public interface AllocationRepository extends JpaRepository<Allocation,Long> {
    List<Allocation> findByRequestIdOrderById(Long requestId);
    List<Allocation> findBySourceIdAndState(Long sourceId, Allocation.State state);
    @Query("select a.request.id from Allocation a where a.id = :id")
    Optional<Long> requestIdFor(@Param("id") Long id);
}
