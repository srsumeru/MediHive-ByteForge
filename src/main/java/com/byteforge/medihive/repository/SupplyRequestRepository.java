package com.byteforge.medihive.repository;
import com.byteforge.medihive.model.SupplyRequest;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface SupplyRequestRepository extends JpaRepository<SupplyRequest,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from SupplyRequest r where r.id=:id")
    Optional<SupplyRequest> lockRequest(@Param("id") Long id);
}
