package com.byteforge.medihive.controller;
import com.byteforge.medihive.dto.ApiDtos.*;
import com.byteforge.medihive.model.*;
import com.byteforge.medihive.service.MediHiveService;
import com.byteforge.medihive.config.Access;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api")
public class MediHiveController {
 private final MediHiveService service; private final Access access;
 public MediHiveController(MediHiveService s,Access a){service=s;access=a;}
 @GetMapping("/me") public Map<String,String> me(){return Map.of("role",access.role(),"username",access.username(),"facilityName",access.facilityName());}
 @GetMapping("/facilities") public List<Facility> facilities(){return service.facilities();}
 @GetMapping("/medicines") public List<Medicine> medicines(){return service.medicines();}
 @GetMapping("/dashboard/{id}") public DashboardView dashboard(@PathVariable Long id){return service.dashboard(id);}
 @GetMapping("/inventory") public List<InventoryView> inventory(@RequestParam Long facilityId){return service.inventory(facilityId);}
 @PutMapping("/inventory/{id}") public InventoryView update(@PathVariable Long id,@Valid @RequestBody InventoryUpdate u){return service.updateInventory(id,u);}
 @GetMapping("/requests") public List<RequestView> requests(@RequestParam Long facilityId){return service.requests(facilityId);}
 @PostMapping("/requests") public RequestView create(@Valid @RequestBody RequestCreate u){return service.createRequest(u);}
 @PostMapping("/requests/{id}/offers") public RequestView offer(@PathVariable Long id,@Valid @RequestBody OfferInput u){return service.offer(id,u);}
 @GetMapping("/requests/{id}/offer-options") public OfferOptions offerOptions(@PathVariable Long id,@RequestParam Long sourceId){return service.offerOptions(id,sourceId);}
 @PostMapping("/allocations/{id}/approve") public RequestView approve(@PathVariable Long id,@Valid @RequestBody QuantityInput u){return service.decide(id,u.quantity(),true);}
 @PostMapping("/allocations/{id}/reject") public RequestView reject(@PathVariable Long id){return service.decide(id,0,false);}
 @PostMapping("/allocations/{id}/dispatch") public RequestView dispatch(@PathVariable Long id){return service.dispatch(id);}
 @PostMapping("/allocations/{id}/deliver") public RequestView deliver(@PathVariable Long id){return service.deliver(id);}
 @PostMapping("/requests/{id}/cancel") public RequestView cancel(@PathVariable Long id){return service.cancel(id);}
 @GetMapping("/suggestions") public List<SuggestionView> suggestions(@RequestParam Long requesterId,@RequestParam Long medicineId){return service.suggestions(requesterId,medicineId);}
 @GetMapping("/health") public Map<String,String> health(){return Map.of("status","UP","application","MediHive");}
}
