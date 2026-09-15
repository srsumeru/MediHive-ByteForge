package com.byteforge.medihive.config;
import com.byteforge.medihive.model.*;
import com.byteforge.medihive.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import java.time.LocalDate;
import java.util.*;
@Configuration public class DemoDataConfig {
 @Bean CommandLineRunner seed(FacilityRepository facilities,MedicineRepository medicines,InventoryRepository inventory,SupplyRequestRepository requests,AllocationRepository allocations){return args->{
 if(facilities.count()>0)return;
 Facility wh=facilities.save(new Facility("Central Medical Warehouse",FacilityType.WAREHOUSE,"Bengaluru"));
 Facility unity=facilities.save(new Facility("Unity Hospital",FacilityType.HOSPITAL,"Bengaluru"));
 Facility city=facilities.save(new Facility("City Hospital",FacilityType.HOSPITAL,"Bengaluru"));
 Facility care=facilities.save(new Facility("CityCare Pharmacy",FacilityType.PHARMACY,"Bengaluru"));
 Facility plus=facilities.save(new Facility("MediPlus Pharmacy",FacilityType.PHARMACY,"Bengaluru"));
 List<Facility> sites=List.of(wh,unity,city,care,plus);
 List<Medicine> meds=List.of(new Medicine("Paracetamol 500 mg","Paracetamol","Pain and fever","tablets"),new Medicine("Human Insulin","Soluble insulin","Diabetes","vials"),new Medicine("Amoxicillin 500 mg","Amoxicillin","Antibiotic","capsules"),new Medicine("Normal Saline 500 ml","Sodium chloride","IV fluid","bottles"),new Medicine("Adrenaline 1 mg/ml","Epinephrine","Emergency","ampoules"));meds=medicines.saveAll(meds);
 int[][] qty={{5000,700,2600,1300,900},{410,32,95,46,8},{350,160,140,85,120},{260,18,75,24,12},{180,110,85,60,55}};
 int[][] min={{350,100,250,180,130},{80,40,60,45,30},{70,35,55,45,25},{60,25,45,25,20},{55,25,40,22,18}};
 for(int j=0;j<sites.size();j++)for(int k=0;k<meds.size();k++)inventory.save(new Inventory(sites.get(j),meds.get(k),qty[j][k],j==0?10+k*2:2+k,min[j][k],LocalDate.now().plusDays(k==2?42:540)));
 SupplyRequest critical=requests.save(new SupplyRequest(unity,meds.get(4),100,Priority.CRITICAL,"Emergency unit is below the safety level; multiple sources may assist."));
 allocations.save(new Allocation(critical,wh,60));allocations.save(new Allocation(critical,city,40));
 SupplyRequest high=requests.save(new SupplyRequest(care,meds.get(1),120,Priority.HIGH,"Demand increased during the last seven days."));allocations.save(new Allocation(high,wh,120));
 };}
}
