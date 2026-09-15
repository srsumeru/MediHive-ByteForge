package com.byteforge.medihive.repository;
import com.byteforge.medihive.model.Inventory;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface InventoryRepository extends JpaRepository<Inventory,Long> {
    List<Inventory> findByFacilityIdOrderByMedicineName(Long facilityId);
    Optional<Inventory> findByFacilityIdAndMedicineId(Long facilityId,Long medicineId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.facility.id=:facility and i.medicine.id=:medicine")
    Optional<Inventory> lockStock(@Param("facility") Long facility,@Param("medicine") Long medicine);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.id = :id")
    Optional<Inventory> lockById(@Param("id") Long id);
}
