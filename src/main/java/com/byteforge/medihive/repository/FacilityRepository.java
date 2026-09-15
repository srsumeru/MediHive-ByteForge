package com.byteforge.medihive.repository;
import com.byteforge.medihive.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface FacilityRepository extends JpaRepository<Facility, Long> { List<Facility> findByType(FacilityType type); }
