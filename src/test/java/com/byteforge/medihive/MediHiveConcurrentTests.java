package com.byteforge.medihive;

import com.byteforge.medihive.model.*;
import com.byteforge.medihive.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MediHiveConcurrentTests {
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager manager;
    @Autowired FacilityRepository facilities;
    @Autowired MedicineRepository medicines;
    @Autowired InventoryRepository stocks;
    @Autowired SupplyRequestRepository requests;
    @Autowired AllocationRepository allocations;

    private long[] fixture(boolean approved) {
        return new TransactionTemplate(manager).execute(status -> {
            Facility donor = facilities.findById(3L).orElseThrow(), receiver = facilities.findById(2L).orElseThrow();
            Medicine medicine = medicines.save(new Medicine("Concurrency fixture " + UUID.randomUUID(), "Test stock", "Test", "units"));
            Inventory stock = stocks.save(new Inventory(donor, medicine, 300, 10, 100, LocalDate.now().plusDays(365)));
            SupplyRequest r1 = requests.save(new SupplyRequest(receiver, medicine, 100, Priority.CRITICAL, "Test"));
            SupplyRequest r2 = requests.save(new SupplyRequest(receiver, medicine, 100, Priority.CRITICAL, "Test"));
            Allocation a1 = allocations.save(new Allocation(r1, donor, 100));
            Allocation a2 = allocations.save(new Allocation(r2, donor, 100));
            if (approved) a1.decide(Allocation.State.APPROVED, 100);
            return new long[]{a1.getId(), a2.getId(), stock.getId()};
        });
    }
    private int call(long id, String action) throws Exception {
        var request = post("/api/allocations/" + id + "/" + action).with(csrf()).with(user("city_hospital_admin").roles("HOSPITAL"));
        if (action.equals("approve")) request.contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":100}");
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }
    private List<Integer> concurrently(Callable<Integer> one, Callable<Integer> two) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> a = pool.submit(() -> { start.await(); return one.call(); });
            Future<Integer> b = pool.submit(() -> { start.await(); return two.call(); });
            start.countDown();
            List<Integer> result = new ArrayList<>(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)));
            Collections.sort(result); return result;
        } finally { pool.shutdownNow(); }
    }
    @Test void simultaneousApprovalsCannotSpendTheHospitalReserveTwice() throws Exception {
        long[] f = fixture(false);
        assertEquals(List.of(200, 400), concurrently(() -> call(f[0], "approve"), () -> call(f[1], "approve")));
        Integer reserved = new TransactionTemplate(manager).execute(s -> allocations.findAll().stream()
            .filter(a -> (a.getId().equals(f[0]) || a.getId().equals(f[1])) && a.getState() == Allocation.State.APPROVED)
            .mapToInt(Allocation::getQuantity).sum());
        assertEquals(100, reserved);
    }
    @Test void simultaneousDispatchClicksDeductInventoryOnce() throws Exception {
        long[] f = fixture(true);
        assertEquals(List.of(200, 400), concurrently(() -> call(f[0], "dispatch"), () -> call(f[0], "dispatch")));
        assertEquals(200, stocks.findById(f[2]).orElseThrow().getQuantity());
    }
}
