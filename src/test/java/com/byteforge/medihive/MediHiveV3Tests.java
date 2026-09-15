package com.byteforge.medihive;

import com.byteforge.medihive.model.*;
import com.byteforge.medihive.repository.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MediHiveV3Tests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired InventoryRepository stocks;
    @Autowired FacilityRepository facilities;
    @Autowired MedicineRepository medicines;
    @Autowired SupplyRequestRepository requests;
    @Autowired AllocationRepository allocations;

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String username) {
        String role = username.equals("warehouse_admin") ? "WAREHOUSE" : username.equals("citycare_admin") ? "PHARMACY" : "HOSPITAL";
        return b.with(user(username).roles(role));
    }
    private JsonNode getJson(String url, String who) throws Exception {
        return mapper.readTree(mvc.perform(as(get(url), who)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private JsonNode postJson(String url, Object body, String who) throws Exception {
        var b = as(post(url).with(csrf()), who);
        if (body != null) b.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        return mapper.readTree(mvc.perform(b).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private void rejected(String url, Object body, String who) throws Exception {
        var b = as(post(url).with(csrf()), who);
        if (body != null) b.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        mvc.perform(b).andExpect(status().isBadRequest());
    }
    private Inventory hospitalStock(int quantity, double daily, int reorder) {
        Inventory i = stocks.findByFacilityIdAndMedicineId(3L, 1L).orElseThrow();
        i.update(quantity, daily, reorder, LocalDate.now().plusDays(365));
        return stocks.saveAndFlush(i);
    }
    private SupplyRequest critical(int quantity) {
        return requests.saveAndFlush(new SupplyRequest(facilities.findById(2L).orElseThrow(),
            medicines.findById(1L).orElseThrow(), quantity, Priority.CRITICAL, "Reserve boundary test"));
    }
    private long offer(SupplyRequest r, int quantity) throws Exception {
        JsonNode json = postJson("/api/requests/" + r.getId() + "/offers", Map.of("sourceId", 3, "quantity", quantity), "city_hospital_admin");
        for (JsonNode a : json.get("allocations")) if (a.get("sourceId").asLong() == 3 && a.get("state").asText().equals("PENDING")) return a.get("id").asLong();
        throw new AssertionError("No hospital proposal returned");
    }
    private JsonNode stockView(long facilityId, long medicineId, String who) throws Exception {
        for (JsonNode i : getJson("/api/inventory?facilityId=" + facilityId, who)) if (i.get("medicineId").asLong() == medicineId) return i;
        throw new AssertionError("No stock");
    }

    @Test void hospitalKeepsExactlySafetyReservePlusFiftyAfterDelivery() throws Exception {
        hospitalStock(300, 10, 100); // max(100, 50) + 50 extra = 150 retained
        SupplyRequest r = critical(200);
        int receiverBefore = stocks.findByFacilityIdAndMedicineId(2L, 1L).orElseThrow().getQuantity();
        rejected("/api/requests/" + r.getId() + "/offers", Map.of("sourceId", 3, "quantity", 151), "city_hospital_admin");
        long id = offer(r, 150);
        JsonNode option = getJson("/api/requests/" + r.getId() + "/offer-options?sourceId=3", "city_hospital_admin");
        assertEquals(150, option.get("maxOfferQuantity").asInt());
        assertEquals(50, option.get("hospitalBuffer").asInt());
        postJson("/api/allocations/" + id + "/approve", Map.of("quantity", 150), "city_hospital_admin");
        assertEquals(0, stockView(3, 1, "city_hospital_admin").get("safeTransferQuantity").asInt());
        postJson("/api/allocations/" + id + "/dispatch", null, "city_hospital_admin");
        rejected("/api/allocations/" + id + "/dispatch", null, "city_hospital_admin");
        postJson("/api/allocations/" + id + "/deliver", null, "unity_admin");
        rejected("/api/allocations/" + id + "/deliver", null, "unity_admin");
        JsonNode donor = stockView(3, 1, "city_hospital_admin");
        assertEquals(150, donor.get("quantity").asInt());
        assertNotEquals("CRITICAL", donor.get("risk").asText());
        assertEquals(receiverBefore + 150, stockView(2, 1, "unity_admin").get("quantity").asInt());
    }

    @Test void pendingAutomaticSuggestionsDoNotBlockAnotherSourceAndRepeatedOffersDoNotDuplicate() throws Exception {
        hospitalStock(300, 10, 100);
        JsonNode created = postJson("/api/requests", Map.of("requesterId", 2, "medicineId", 1, "quantity", 100, "priority", "CRITICAL"), "unity_admin");
        long r = created.get("id").asLong();
        assertEquals(100, created.get("allocations").get(0).get("quantity").asInt());
        postJson("/api/requests/" + r + "/offers", Map.of("sourceId", 3, "quantity", 40), "city_hospital_admin");
        JsonNode changed = postJson("/api/requests/" + r + "/offers", Map.of("sourceId", 3, "quantity", 60), "city_hospital_admin");
        long city = 0, warehouse = 0; int hospitalProposals = 0;
        for (JsonNode a : changed.get("allocations")) {
            if (a.get("sourceId").asInt() == 3) { city = a.get("id").asLong(); hospitalProposals++; }
            if (a.get("sourceId").asInt() == 1) warehouse = a.get("id").asLong();
        }
        assertEquals(1, hospitalProposals);
        postJson("/api/allocations/" + city + "/approve", Map.of("quantity", 60), "city_hospital_admin");
        rejected("/api/allocations/" + warehouse + "/approve", Map.of("quantity", 100), "warehouse_admin");
        JsonNode finalApproval = postJson("/api/allocations/" + warehouse + "/approve", Map.of("quantity", 40), "warehouse_admin");
        assertEquals(100, finalApproval.get("approvedQuantity").asInt());
        assertEquals(0, getJson("/api/requests/" + r + "/offer-options?sourceId=3", "city_hospital_admin").get("maxOfferQuantity").asInt());
        rejected("/api/requests/" + r + "/offers", Map.of("sourceId", 3, "quantity", 1), "city_hospital_admin");
    }

    @Test void otherApprovedTransfersReduceOffersAndDispatchProtectsAllReservations() throws Exception {
        Inventory i = hospitalStock(300, 10, 100);
        SupplyRequest r1 = critical(100), r2 = critical(100);
        long a1 = offer(r1, 100);
        long a2 = offer(r2, 100);
        postJson("/api/allocations/" + a1 + "/approve", Map.of("quantity", 100), "city_hospital_admin");
        assertEquals(50, getJson("/api/requests/" + r2.getId() + "/offer-options?sourceId=3", "city_hospital_admin").get("maxOfferQuantity").asInt());
        rejected("/api/allocations/" + a2 + "/approve", Map.of("quantity", 51), "city_hospital_admin");
        postJson("/api/allocations/" + a2 + "/approve", Map.of("quantity", 50), "city_hospital_admin");
        // Simulate consumption or an external DB adjustment after approval.
        i.update(299, 10, 100, LocalDate.now().plusDays(365)); stocks.saveAndFlush(i);
        rejected("/api/allocations/" + a2 + "/dispatch", null, "city_hospital_admin");
        i.update(300, 10, 100, LocalDate.now().plusDays(365)); stocks.saveAndFlush(i);
        postJson("/api/allocations/" + a1 + "/dispatch", null, "city_hospital_admin");
        postJson("/api/allocations/" + a2 + "/dispatch", null, "city_hospital_admin");
        assertEquals(150, stockView(3, 1, "city_hospital_admin").get("quantity").asInt());
    }

    @Test void consumptionFloorExpiredStockAndLowStockHaveNoUnsafeSurplus() throws Exception {
        Inventory i = hospitalStock(300, 40, 100); // five days = 200, plus 50
        SupplyRequest r = critical(100);
        assertEquals(50, getJson("/api/requests/" + r.getId() + "/offer-options?sourceId=3", "city_hospital_admin").get("maxOfferQuantity").asInt());
        i.update(249, 40, 100, LocalDate.now().plusDays(365)); stocks.saveAndFlush(i);
        assertEquals(0, stockView(3, 1, "city_hospital_admin").get("safeTransferQuantity").asInt());
        rejected("/api/requests/" + r.getId() + "/offers", Map.of("sourceId", 3, "quantity", 1), "city_hospital_admin");
        i.update(1000, 10, 100, LocalDate.now().minusDays(1)); stocks.saveAndFlush(i);
        rejected("/api/requests/" + r.getId() + "/offers", Map.of("sourceId", 3, "quantity", 1), "city_hospital_admin");
    }

    @Test void warehouseCanReadButCannotEditOrOfferAnotherFacilitysStock() throws Exception {
        SupplyRequest r = critical(100);
        for (long f : new long[] {2, 3, 4, 5}) {
            getJson("/api/inventory?facilityId=" + f, "warehouse_admin");
            long id = stocks.findByFacilityIdAndMedicineId(f, 1L).orElseThrow().getId();
            mvc.perform(as(put("/api/inventory/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("quantity", 500, "dailyConsumption", 2, "reorderLevel", 40,
                    "expiryDate", LocalDate.now().plusDays(365).toString()))), "warehouse_admin")).andExpect(status().isForbidden());
        }
        mvc.perform(as(post("/api/requests/" + r.getId() + "/offers").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"sourceId\":3,\"quantity\":1}"), "warehouse_admin")).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/requests/" + r.getId() + "/offer-options?sourceId=3"), "warehouse_admin")).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/inventory?facilityId=3"), "unity_admin")).andExpect(status().isForbidden());
    }

    @Test void warehouseCanRecordShortageButCannotBreakApprovedReservations() throws Exception {
        Inventory wh = stocks.findByFacilityIdAndMedicineId(1L, 1L).orElseThrow();
        Map<String, Object> body = Map.of("quantity", 10, "dailyConsumption", 10, "reorderLevel", 100,
            "expiryDate", LocalDate.now().plusDays(365).toString());
        mvc.perform(as(put("/api/inventory/" + wh.getId()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(body)), "warehouse_admin")).andExpect(status().isOk());
        wh.update(300, 10, 100, LocalDate.now().plusDays(365)); stocks.saveAndFlush(wh);
        SupplyRequest r = critical(100);
        Allocation a = allocations.saveAndFlush(new Allocation(r, wh.getFacility(), 100));
        postJson("/api/allocations/" + a.getId() + "/approve", Map.of("quantity", 100), "warehouse_admin");
        mvc.perform(as(put("/api/inventory/" + wh.getId()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(body)), "warehouse_admin")).andExpect(status().isBadRequest());
        assertEquals(300, stockView(1, 1, "warehouse_admin").get("quantity").asInt());
    }

    @Test void addingApprovalAfterDispatchDoesNotMoveStatusBackwards() throws Exception {
        hospitalStock(300, 10, 100);
        SupplyRequest r = critical(100);
        long city = offer(r, 40);
        postJson("/api/allocations/" + city + "/approve", Map.of("quantity", 40), "city_hospital_admin");
        postJson("/api/allocations/" + city + "/dispatch", null, "city_hospital_admin");
        Allocation a = allocations.saveAndFlush(new Allocation(r, facilities.findById(1L).orElseThrow(), 60));
        assertEquals("DISPATCHED", postJson("/api/allocations/" + a.getId() + "/approve", Map.of("quantity", 60), "warehouse_admin").get("status").asText());
    }
}
