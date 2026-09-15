package com.byteforge.medihive.service;

import com.byteforge.medihive.config.Access;
import com.byteforge.medihive.dto.ApiDtos.*;
import com.byteforge.medihive.exception.NotFoundException;
import com.byteforge.medihive.model.*;
import com.byteforge.medihive.repository.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class MediHiveService {
    private final FacilityRepository facilities;
    private final MedicineRepository medicines;
    private final InventoryRepository stock;
    private final SupplyRequestRepository requests;
    private final AllocationRepository allocations;
    private final Access access;

    public MediHiveService(FacilityRepository f, MedicineRepository m, InventoryRepository i,
                           SupplyRequestRepository r, AllocationRepository a, Access access) {
        facilities = f; medicines = m; stock = i; requests = r; allocations = a; this.access = access;
    }

    public List<Facility> facilities() { return facilities.findAll(); }
    public List<Medicine> medicines() { return medicines.findAll(); }
    private Facility facility(Long id) {
        return facilities.findById(id).orElseThrow(() -> new NotFoundException("Facility not found"));
    }
    private Medicine medicine(Long id) {
        return medicines.findById(id).orElseThrow(() -> new NotFoundException("Medicine not found"));
    }
    private SupplyRequest request(Long id) {
        return requests.findById(id).orElseThrow(() -> new NotFoundException("Request not found"));
    }
    private SupplyRequest locked(Long id) {
        return requests.lockRequest(id).orElseThrow(() -> new NotFoundException("Request not found"));
    }
    private Inventory stock(Long facility, Long medicine) {
        return stock.lockStock(facility, medicine)
            .orElseThrow(() -> new IllegalArgumentException("This facility has no stock record for this medicine"));
    }
    private List<Allocation> parts(SupplyRequest r) { return allocations.findByRequestIdOrderById(r.getId()); }
    private boolean closed(SupplyRequest r) {
        return r.getStatus() == RequestStatus.CANCELLED || r.getStatus() == RequestStatus.DELIVERED
            || r.getStatus() == RequestStatus.REJECTED;
    }
    private void requireOpen(SupplyRequest r) {
        if (closed(r)) throw new IllegalArgumentException("This request is closed. Refresh the dashboard.");
    }
    private int reserved(Long sourceId, Long medicineId) {
        return allocations.findBySourceIdAndState(sourceId, Allocation.State.APPROVED).stream()
            .filter(a -> a.getRequest().getMedicine().getId().equals(medicineId))
            .mapToInt(Allocation::getQuantity).sum();
    }
    private int available(Inventory i) {
        return StockPolicy.transferable(i, reserved(i.getFacility().getId(), i.getMedicine().getId()));
    }
    private int committed(SupplyRequest r) {
        return parts(r).stream().filter(a -> a.getState() == Allocation.State.APPROVED
            || a.getState() == Allocation.State.DISPATCHED || a.getState() == Allocation.State.DELIVERED)
            .mapToInt(Allocation::getQuantity).sum();
    }
    private int unapproved(SupplyRequest r) { return Math.max(0, r.getQuantity() - committed(r)); }
    private void requireAvailable(Inventory i, int quantity, String action) {
        int max = available(i);
        if (quantity > max) throw new IllegalArgumentException(action + " exceeds the available safe stock. Maximum: "
            + max + " units. Keep " + StockPolicy.safetyFloor(i) + " safety units"
            + (StockPolicy.extraReserve(i) > 0 ? " plus 50 extra hospital units" : "")
            + ", as well as stock reserved for other approved transfers.");
    }

    public List<InventoryView> inventory(Long facilityId) {
        access.require(facility(facilityId));
        return stock.findByFacilityIdOrderByMedicineName(facilityId).stream().map(this::view).toList();
    }
    public List<RequestView> requests(Long facilityId) {
        access.require(facility(facilityId));
        return requests.findAll().stream().filter(r -> access.warehouse()
            || r.getRequester().getId().equals(facilityId)
            || parts(r).stream().anyMatch(a -> a.getSource().getId().equals(facilityId))
            || r.getPriority() == Priority.CRITICAL)
            .sorted(Comparator.comparing(SupplyRequest::getCreatedAt).reversed()).map(this::view).toList();
    }
    public DashboardView dashboard(Long facilityId) {
        Facility f = facility(facilityId);
        List<InventoryView> inv = inventory(facilityId);
        List<RequestView> req = requests(facilityId);
        return new DashboardView(f.getName(), f.getType(), inv.size(),
            (int) inv.stream().filter(v -> v.risk().equals("CRITICAL") || v.risk().equals("OUT_OF_STOCK")).count(),
            (int) inv.stream().filter(v -> !v.expiryDate().isAfter(LocalDate.now().plusDays(60))).count(),
            (int) req.stream().filter(v -> v.status() != RequestStatus.DELIVERED
                && v.status() != RequestStatus.CANCELLED && v.status() != RequestStatus.REJECTED).count(), inv, req);
    }
    public List<SuggestionView> suggestions(Long requesterId, Long medicineId) {
        access.require(facility(requesterId)); medicine(medicineId);
        return facilities.findAll().stream().filter(f -> !f.getId().equals(requesterId))
            .map(f -> stock.findByFacilityIdAndMedicineId(f.getId(), medicineId).orElse(null))
            .filter(Objects::nonNull)
            .map(i -> new SuggestionView(i.getFacility().getId(), i.getFacility().getName(), i.getQuantity(), available(i),
                StockPolicy.extraReserve(i) > 0
                    ? "Keeps the safety reserve plus 50 extra hospital units and excludes approved reservations."
                    : "Keeps the safety reserve and excludes approved reservations."))
            .filter(v -> v.safeTransferQuantity() > 0)
            .sorted(Comparator.comparingInt(SuggestionView::safeTransferQuantity).reversed()).toList();
    }

    @Transactional
    public InventoryView updateInventory(Long id, InventoryUpdate u) {
        // Lock the same stock row as approvals/dispatches so an edit cannot invalidate a concurrent promise.
        Inventory i = stock.lockById(id).orElseThrow(() -> new NotFoundException("Stock record not found"));
        access.manageInventory(i.getFacility());
        if (!Double.isFinite(u.dailyConsumption())) throw new IllegalArgumentException("Daily consumption must be finite");
        int held = reserved(i.getFacility().getId(), i.getMedicine().getId());
        int retained = StockPolicy.safetyFloor(u.reorderLevel(), u.dailyConsumption()) + StockPolicy.extraReserve(i);
        if (held > 0 && (u.expiryDate().isBefore(LocalDate.now()) || u.quantity() - retained < held))
            throw new IllegalArgumentException("This edit would use stock reserved for approved transfers. Keep at least "
                + (retained + held) + " unexpired units, including the safety reserve.");
        i.update(u.quantity(), u.dailyConsumption(), u.reorderLevel(), u.expiryDate());
        return view(stock.saveAndFlush(i));
    }

    @Transactional
    public RequestView createRequest(RequestCreate u) {
        Facility f = facility(u.requesterId()); access.requester(f);
        Medicine m = medicine(u.medicineId());
        SupplyRequest r = requests.save(new SupplyRequest(f, m, u.quantity(), u.priority(), u.notes()));
        if (u.priority() != Priority.CRITICAL) {
            Facility wh = facilities.findByType(FacilityType.WAREHOUSE).stream().findFirst().orElseThrow();
            allocations.save(new Allocation(r, wh, u.quantity()));
        } else {
            int need = u.quantity();
            for (SuggestionView s : suggestions(f.getId(), m.getId())) {
                if (need == 0) break;
                int take = Math.min(need, s.safeTransferQuantity());
                allocations.save(new Allocation(r, facility(s.sourceFacilityId()), take));
                need -= take;
            }
        }
        return view(r);
    }

    public OfferOptions offerOptions(Long id, Long sourceId) {
        SupplyRequest r = request(id);
        if (r.getPriority() != Priority.CRITICAL) throw new IllegalArgumentException("Offers are for critical requests only");
        Facility source = facility(sourceId); access.supply(source);
        if (sourceId.equals(r.getRequester().getId())) throw new IllegalArgumentException("A facility cannot supply itself");
        Inventory i = stock.findByFacilityIdAndMedicineId(sourceId, r.getMedicine().getId()).orElse(null);
        int held = reserved(sourceId, r.getMedicine().getId());
        int free = i == null ? 0 : StockPolicy.transferable(i, held);
        int remaining = closed(r) ? 0 : unapproved(r);
        int proposed = parts(r).stream().filter(a -> a.getSource().getId().equals(sourceId)
            && a.getState() == Allocation.State.PENDING).mapToInt(Allocation::getQuantity).findFirst().orElse(0);
        return new OfferOptions(sourceId, source.getName(), i == null ? 0 : i.getQuantity(),
            i == null ? 0 : StockPolicy.safetyFloor(i),
            source.getType() == FacilityType.HOSPITAL ? StockPolicy.HOSPITAL_EXTRA_RESERVE : 0,
            held, free, remaining, Math.min(free, remaining), proposed);
    }

    @Transactional
    public RequestView offer(Long id, OfferInput u) {
        SupplyRequest r = locked(id); requireOpen(r);
        if (r.getPriority() != Priority.CRITICAL) throw new IllegalArgumentException("Offers are for critical requests only");
        Facility source = facility(u.sourceId()); access.supply(source);
        if (source.getId().equals(r.getRequester().getId())) throw new IllegalArgumentException("A facility cannot supply itself");
        int remaining = unapproved(r);
        if (u.quantity() > remaining) throw new IllegalArgumentException("Only " + remaining
            + " units still need approval. Pending suggestions do not reserve stock.");
        Inventory i = stock(source.getId(), r.getMedicine().getId());
        requireAvailable(i, u.quantity(), "Offer");
        // Pending entries are proposals, not commitments. A suggestion must not block a valid alternative offer.
        // Repeated offers by one source edit its existing proposal instead of creating duplicate proposals.
        Optional<Allocation> existing = parts(r).stream().filter(a -> a.getSource().getId().equals(u.sourceId())
            && a.getState() == Allocation.State.PENDING).findFirst();
        if (existing.isPresent()) existing.get().decide(Allocation.State.PENDING, u.quantity());
        else allocations.save(new Allocation(r, source, u.quantity()));
        r.setStatus(r.getStatus());
        return view(r);
    }

    private record LockedPart(SupplyRequest request, Allocation allocation) {}
    private LockedPart lockedPart(Long id) {
        // Acquire the request lock BEFORE loading the allocation state, including for duplicate dispatch clicks.
        Long requestId = allocations.requestIdFor(id).orElseThrow(() -> new NotFoundException("Allocation not found"));
        SupplyRequest r = locked(requestId);
        Allocation a = allocations.findById(id).orElseThrow(() -> new NotFoundException("Allocation not found"));
        return new LockedPart(r, a);
    }
    private void refreshStatus(SupplyRequest r) {
        List<Allocation> list = parts(r);
        int delivered = list.stream().filter(a -> a.getState() == Allocation.State.DELIVERED).mapToInt(Allocation::getQuantity).sum();
        if (delivered >= r.getQuantity()) r.setStatus(RequestStatus.DELIVERED);
        else if (list.stream().anyMatch(a -> a.getState() == Allocation.State.DISPATCHED || a.getState() == Allocation.State.DELIVERED))
            r.setStatus(RequestStatus.DISPATCHED);
        else if (list.stream().anyMatch(a -> a.getState() == Allocation.State.APPROVED)) r.setStatus(RequestStatus.APPROVED);
        else r.setStatus(RequestStatus.REQUESTED);
    }

    @Transactional
    public RequestView decide(Long id, int quantity, boolean approve) {
        LockedPart pair = lockedPart(id); SupplyRequest r = pair.request(); Allocation a = pair.allocation();
        access.supply(a.getSource()); requireOpen(r);
        if (a.getState() != Allocation.State.PENDING) throw new IllegalArgumentException("This proposal has already been decided");
        if (approve) {
            if (quantity < 1 || quantity > Math.min(a.getQuantity(), 100000))
                throw new IllegalArgumentException("Approval must be between 1 and the proposed amount");
            Inventory i = stock(a.getSource().getId(), r.getMedicine().getId());
            requireAvailable(i, quantity, "Approval");
            if (quantity > unapproved(r)) throw new IllegalArgumentException("Only " + unapproved(r)
                + " units still need approval. Other sources have already covered the rest.");
            a.decide(Allocation.State.APPROVED, quantity);
        } else a.decide(Allocation.State.REJECTED, a.getQuantity());
        refreshStatus(r);
        return view(r);
    }

    @Transactional
    public RequestView dispatch(Long id) {
        LockedPart pair = lockedPart(id); SupplyRequest r = pair.request(); Allocation a = pair.allocation();
        access.supply(a.getSource()); requireOpen(r);
        if (a.getState() != Allocation.State.APPROVED) throw new IllegalArgumentException("Approve this allocation before dispatching it once");
        Inventory i = stock(a.getSource().getId(), r.getMedicine().getId());
        int held = reserved(a.getSource().getId(), r.getMedicine().getId());
        // held includes THIS allocation. Protect every other approval as well as the donor's reserve.
        if (a.getQuantity() > StockPolicy.transferable(i, Math.max(0, held - a.getQuantity())))
            throw new IllegalArgumentException("Dispatch would breach the safety reserve"
                + (StockPolicy.extraReserve(i) > 0 ? " plus 50 extra hospital units" : "")
                + " or stock reserved for another approved transfer. Refresh and check current stock.");
        i.removeQuantity(a.getQuantity());
        a.decide(Allocation.State.DISPATCHED, a.getQuantity());
        refreshStatus(r);
        return view(r);
    }

    @Transactional
    public RequestView deliver(Long id) {
        LockedPart pair = lockedPart(id); SupplyRequest r = pair.request(); Allocation a = pair.allocation();
        if (!access.owns(r.getRequester())) throw new AccessDeniedException("Only the receiving administrator confirms delivery");
        requireOpen(r);
        if (a.getState() != Allocation.State.DISPATCHED) throw new IllegalArgumentException("This allocation must be dispatched and received only once");
        Inventory i = stock(r.getRequester().getId(), r.getMedicine().getId());
        i.addQuantity(a.getQuantity());
        a.decide(Allocation.State.DELIVERED, a.getQuantity());
        refreshStatus(r);
        return view(r);
    }

    @Transactional
    public RequestView cancel(Long id) {
        SupplyRequest r = locked(id); access.requester(r.getRequester()); requireOpen(r);
        if (parts(r).stream().anyMatch(a -> a.getState() == Allocation.State.DISPATCHED))
            throw new IllegalArgumentException("Confirm all deliveries already in transit before cancelling the remaining request");
        for (Allocation a : parts(r)) if (a.getState() == Allocation.State.APPROVED || a.getState() == Allocation.State.PENDING)
            a.decide(Allocation.State.REJECTED, a.getQuantity());
        r.setStatus(RequestStatus.CANCELLED);
        return view(r);
    }

    private InventoryView view(Inventory i) {
        double rawDays = i.getDailyConsumption() == 0 ? Double.POSITIVE_INFINITY : i.getQuantity() / i.getDailyConsumption();
        double days = Double.isInfinite(rawDays) ? 999 : Math.round(rawDays * 10.0) / 10.0;
        long expiry = ChronoUnit.DAYS.between(LocalDate.now(), i.getExpiryDate());
        // Use unrounded days for risk so rounding cannot mark a protected hospital as critical.
        String risk = i.getQuantity() == 0 ? "OUT_OF_STOCK"
            : i.getQuantity() <= i.getReorderLevel() || rawDays <= 5 ? "CRITICAL" : rawDays <= 12 ? "HIGH" : "HEALTHY";
        String note = expiry < 0 ? "Expired: isolate stock" : expiry <= 60 ? "Expiry within 60 days"
            : risk.equals("CRITICAL") || risk.equals("OUT_OF_STOCK") ? "Urgent replenishment needed"
            : risk.equals("HIGH") ? "Plan replenishment" : "Stock is healthy";
        int held = reserved(i.getFacility().getId(), i.getMedicine().getId());
        return new InventoryView(i.getId(), i.getFacility().getId(), i.getFacility().getName(), i.getMedicine().getId(),
            i.getMedicine().getName(), i.getMedicine().getGenericName(), i.getMedicine().getUnit(), i.getQuantity(),
            i.getDailyConsumption(), i.getReorderLevel(), i.getExpiryDate(), i.getLastUpdated(), days,
            StockPolicy.transferable(i, held), risk, note, StockPolicy.safetyFloor(i), StockPolicy.extraReserve(i), held);
    }
    private RequestView view(SupplyRequest r) {
        List<Allocation> a = parts(r);
        int approved = a.stream().filter(x -> x.getState() == Allocation.State.APPROVED || x.getState() == Allocation.State.DISPATCHED
            || x.getState() == Allocation.State.DELIVERED).mapToInt(Allocation::getQuantity).sum();
        int dispatched = a.stream().filter(x -> x.getState() == Allocation.State.DISPATCHED || x.getState() == Allocation.State.DELIVERED)
            .mapToInt(Allocation::getQuantity).sum();
        int delivered = a.stream().filter(x -> x.getState() == Allocation.State.DELIVERED).mapToInt(Allocation::getQuantity).sum();
        return new RequestView(r.getId(), r.getRequester().getId(), r.getRequester().getName(), r.getMedicine().getId(),
            r.getMedicine().getName(), r.getQuantity(), r.getPriority(), r.getStatus(), r.getNotes(), r.getCreatedAt(), r.getUpdatedAt(),
            approved, dispatched, delivered, Math.max(0, r.getQuantity() - delivered),
            a.stream().map(x -> new AllocationView(x.getId(), x.getSource().getId(), x.getSource().getName(),
                x.getQuantity(), x.getState(), x.getUpdatedAt())).toList());
    }
}
