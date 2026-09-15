package com.byteforge.medihive.repository;
import com.byteforge.medihive.model.Medicine;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MedicineRepository extends JpaRepository<Medicine, Long> {}
