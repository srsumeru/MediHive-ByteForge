package com.byteforge.medihive.dto;
import com.byteforge.medihive.model.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;
public final class ApiDtos {
 private ApiDtos(){}
 public record InventoryView(Long id,Long facilityId,String facilityName,Long medicineId,String medicineName,String genericName,String unit,int quantity,double dailyConsumption,int reorderLevel,LocalDate expiryDate,LocalDateTime lastUpdated,double daysRemaining,int safeTransferQuantity,String risk,String recommendation,int safetyFloor,int hospitalBuffer,int reservedQuantity){}
 public record InventoryUpdate(@Min(0) @Max(1000000) int quantity,@DecimalMin("0.0") @DecimalMax("100000.0") double dailyConsumption,@Min(0) @Max(1000000) int reorderLevel,@NotNull LocalDate expiryDate){}
 public record RequestCreate(@NotNull Long requesterId,@NotNull Long medicineId,@Min(1) @Max(100000) int quantity,@NotNull Priority priority,@Size(max=500) String notes){}
 public record QuantityInput(@Min(1) @Max(100000) int quantity){}
 public record OfferInput(@NotNull Long sourceId,@Min(1) @Max(100000) int quantity){}
 public record OfferOptions(Long sourceId,String sourceName,int currentQuantity,int safetyFloor,int hospitalBuffer,int reservedQuantity,int safeTransferQuantity,int unapprovedQuantity,int maxOfferQuantity,int proposedQuantity){}
 public record AllocationView(Long id,Long sourceId,String sourceName,int quantity,Allocation.State state,LocalDateTime updatedAt){}
 public record RequestView(Long id,Long requesterId,String requesterName,Long medicineId,String medicineName,int quantity,Priority priority,RequestStatus status,String notes,LocalDateTime createdAt,LocalDateTime updatedAt,int approvedQuantity,int dispatchedQuantity,int deliveredQuantity,int remainingQuantity,List<AllocationView> allocations){}
 public record DashboardView(String facilityName,FacilityType facilityType,int medicinesTracked,int criticalAlerts,int expiringSoon,int openRequests,List<InventoryView> inventory,List<RequestView> requests){}
 public record SuggestionView(Long sourceFacilityId,String sourceFacilityName,int availableQuantity,int safeTransferQuantity,String explanation){}
}
