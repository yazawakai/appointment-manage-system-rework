package com.example.appointment;

import com.example.appointment.dto.BookRequest;
import com.example.appointment.dto.CancelRequest;
import com.example.appointment.entity.Provider;
import com.example.appointment.entity.SlotStatus;
import com.example.appointment.entity.TimeSlot;
import com.example.appointment.repository.AppointmentRepository;
import com.example.appointment.repository.CustomerRepository;
import com.example.appointment.repository.ProviderRepository;
import com.example.appointment.repository.TimeSlotRepository;
import com.example.appointment.service.AppointmentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppointmentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TimeSlotRepository slotRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    private final LocalDate tomorrow = LocalDate.now().plusDays(1);

    @BeforeEach
    void cleanDatabase() {
        appointmentRepository.deleteAll();
        slotRepository.deleteAll();
        customerRepository.deleteAll();
        providerRepository.deleteAll();
    }

    // ------------------------------------------------------------------ slots

    @Test
    void createSlot_overlappingSlot_returns409() throws Exception {
        long providerId = createProvider("Dr. Li", "li@example.com", "HEALTH");

        createSlot(providerId, tomorrow, "09:00", "10:00");
        mockMvc.perform(post("/api/slots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(slotJson(providerId, tomorrow, "09:30", "10:30")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_OVERLAP"));
    }

    @Test
    void createSlot_adjacentSlot_isAccepted() throws Exception {
        long providerId = createProvider("Dr. Li", "li@example.com", "HEALTH");

        createSlot(providerId, tomorrow, "09:00", "10:00");
        // Adjacent (end == start) does not overlap.
        mockMvc.perform(post("/api/slots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(slotJson(providerId, tomorrow, "10:00", "11:00")))
                .andExpect(status().isCreated());
    }

    @Test
    void createSlot_invalidTimeRange_returns400() throws Exception {
        long providerId = createProvider("Dr. Li", "li@example.com", "HEALTH");

        mockMvc.perform(post("/api/slots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(slotJson(providerId, tomorrow, "10:00", "09:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TIME_RANGE"));
    }

    @Test
    void createSlot_unknownProvider_returns404() throws Exception {
        mockMvc.perform(post("/api/slots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(slotJson(9999L, tomorrow, "09:00", "10:00")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROVIDER_NOT_FOUND"));
    }

    // -------------------------------------------------------- availability

    @Test
    void searchAvailable_excludesBookedAndExpired() throws Exception {
        long providerId = createProvider("Dr. Li", "li@example.com", "HEALTH");
        long futureA = createSlot(providerId, tomorrow, "09:00", "10:00");
        long futureB = createSlot(providerId, tomorrow, "11:00", "12:00");
        insertPastSlot(providerId); // expired, must be excluded

        long customerId = createCustomer("Alice", "alice@example.com");
        book(customerId, futureA); // booked, must be excluded

        String body = mockMvc.perform(get("/api/slots/available"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode content = objectMapper.readTree(body).get("content");
        assertEquals(1, content.size());
        assertEquals(futureB, content.get(0).get("id").asLong());
    }

    @Test
    void searchAvailable_filtersByCategory() throws Exception {
        long healthProvider = createProvider("Dr. Li", "li@example.com", "HEALTH");
        long legalProvider = createProvider("Ms. Chen", "chen@example.com", "LEGAL");
        long healthSlot = createSlot(healthProvider, tomorrow, "09:00", "10:00");
        createSlot(legalProvider, tomorrow, "09:00", "10:00");

        String body = mockMvc.perform(get("/api/slots/available").param("category", "HEALTH"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode content = objectMapper.readTree(body).get("content");
        assertEquals(1, content.size());
        assertEquals(healthSlot, content.get(0).get("id").asLong());
    }

    // ------------------------------------------------------------ booking

    @Test
    void bookSlot_missingSlot_returns404() throws Exception {
        long customerId = createCustomer("Alice", "alice@example.com");
        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookJson(customerId, 9999L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SLOT_NOT_FOUND"));
    }

    @Test
    void bookSlot_sequentialDoubleBook_returns409() throws Exception {
        long providerId = createProvider("Dr. Li", "li@example.com", "HEALTH");
        long slotId = createSlot(providerId, tomorrow, "09:00", "10:00");
        long customerId = createCustomer("Alice", "alice@example.com");

        book(customerId, slotId);

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookJson(customerId, slotId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_ALREADY_BOOKED"));
    }

    @Test
    void concurrentBooking_exactlyOneSucceeds() throws Exception {
        long providerId = createProvider("Dr. Li", "li@example.com", "HEALTH");
        long slotId = createSlot(providerId, tomorrow, "09:00", "10:00");
        long customerA = createCustomer("Alice", "alice@example.com");
        long customerB = createCustomer("Bob", "bob@example.com");

        int threadCount = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            long customerId = (i == 0) ? customerA : customerB;
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    appointmentService.book(new BookRequest(customerId, slotId));
                    success.incrementAndGet();
                } catch (Exception e) {
                    failure.incrementAndGet();
                }
            }));
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertEquals(1, success.get(), "exactly one concurrent booking must succeed");
        assertEquals(1, failure.get(), "the losing concurrent booking must fail");
        assertEquals(1, appointmentRepository.count(), "only one appointment row may exist");
    }

    // ------------------------------------------------------------ cancel

    @Test
    void cancel_notOwner_returns403() throws Exception {
        long providerId = createProvider("Dr. Li", "li@example.com", "HEALTH");
        long slotId = createSlot(providerId, tomorrow, "09:00", "10:00");
        long ownerId = createCustomer("Alice", "alice@example.com");
        long otherId = createCustomer("Bob", "bob@example.com");
        long appointmentId = book(ownerId, slotId);

        mockMvc.perform(post("/api/appointments/" + appointmentId + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelJson(otherId, "not mine")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_OWNER"));
    }

    @Test
    void cancel_owner_releasesSlotAtomically() throws Exception {
        long providerId = createProvider("Dr. Li", "li@example.com", "HEALTH");
        long slotId = createSlot(providerId, tomorrow, "09:00", "10:00");
        long customerId = createCustomer("Alice", "alice@example.com");
        long appointmentId = book(customerId, slotId);

        mockMvc.perform(post("/api/appointments/" + appointmentId + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelJson(customerId, "Schedule conflict")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.cancelReason").value("Schedule conflict"));

        // The slot is released back to AVAILABLE.
        mockMvc.perform(get("/api/slots/" + slotId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void cancel_twice_returns409() throws Exception {
        long providerId = createProvider("Dr. Li", "li@example.com", "HEALTH");
        long slotId = createSlot(providerId, tomorrow, "09:00", "10:00");
        long customerId = createCustomer("Alice", "alice@example.com");
        long appointmentId = book(customerId, slotId);

        mockMvc.perform(post("/api/appointments/" + appointmentId + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelJson(customerId, "first")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/appointments/" + appointmentId + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelJson(customerId, "second")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPOINTMENT_ALREADY_CANCELED"));
    }

    @Test
    void bookThenCancelThenReBook_sameSlot_shouldSucceed() throws Exception {
        long providerId = createProvider("Dr. Li", "li@example.com", "HEALTH");
        long slotId = createSlot(providerId, tomorrow, "09:00", "10:00");
        long customerId = createCustomer("Alice", "alice@example.com");
        long appointmentId = book(customerId, slotId);

        mockMvc.perform(post("/api/appointments/" + appointmentId + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelJson(customerId, "rebook test")))
                .andExpect(status().isOk());

        // Re-book the same slot by the same customer; this must succeed.
        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookJson(customerId, slotId)))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------ helpers

    private long createProvider(String name, String email, String category) throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("name", name, "email", email, "serviceCategory", category));
        String resp = mockMvc.perform(post("/api/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("id").asLong();
    }

    private long createCustomer(String name, String email) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("name", name, "email", email));
        String resp = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("id").asLong();
    }

    private long createSlot(long providerId, LocalDate date, String start, String end) throws Exception {
        String resp = mockMvc.perform(post("/api/slots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(slotJson(providerId, date, start, end)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("id").asLong();
    }

    private long book(long customerId, long slotId) throws Exception {
        String resp = mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookJson(customerId, slotId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("id").asLong();
    }

    private String slotJson(long providerId, LocalDate date, String start, String end) throws Exception {
        return objectMapper.writeValueAsString(Map.<String, Object>of(
                "providerId", providerId,
                "date", date.toString(),
                "startTime", start,
                "endTime", end
        ));
    }

    private String bookJson(long customerId, long slotId) throws Exception {
        return objectMapper.writeValueAsString(Map.of("customerId", customerId, "slotId", slotId));
    }

    private String cancelJson(long customerId, String reason) throws Exception {
        return objectMapper.writeValueAsString(Map.<String, Object>of("customerId", customerId, "reason", reason));
    }

    private void insertPastSlot(long providerId) {
        Provider provider = providerRepository.findById(providerId).orElseThrow();
        TimeSlot slot = new TimeSlot();
        slot.setProvider(provider);
        slot.setDate(LocalDate.now().minusDays(1));
        slot.setStartTime(LocalTime.of(9, 0));
        slot.setEndTime(LocalTime.of(10, 0));
        slot.setStatus(SlotStatus.AVAILABLE);
        slotRepository.save(slot);
        assertFalse(slot.getId() == null);
    }
}
