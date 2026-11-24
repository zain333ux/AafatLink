package pk.edu.nu.aafatlink.infrastructure.supabase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.assignment.AssignmentStatus;
import pk.edu.nu.aafatlink.domain.assignment.ProgressUpdate;
import pk.edu.nu.aafatlink.domain.request.AssistanceRequestDraft;
import pk.edu.nu.aafatlink.domain.request.DisasterType;
import pk.edu.nu.aafatlink.domain.request.RequestStatus;
import pk.edu.nu.aafatlink.domain.request.RequestSubmissionResult;
import pk.edu.nu.aafatlink.domain.request.RequesterContact;

class SupabaseWorkflowRepositoryTest {
    private static final UUID USER_ID = UUID.fromString("1260546b-6f62-4096-b705-d05be484f8d2");
    private static final UUID REQUEST_ID = UUID.fromString("14f0c9d0-c91b-4e8e-8fd5-f81bf0df4475");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("a447af55-8230-4bbb-879d-812234252c1f");
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private HttpServer server;
    private SupabaseWorkflowRepository repository;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rest/v1/rpc/submit_assistance_request", this::handleSubmit);
        server.createContext("/rest/v1/rpc/reconcile_request_operation", exchange -> respond(exchange,
                "{\"status\":\"COMMITTED\",\"result\":{\"request_id\":\"" + REQUEST_ID
                        + "\",\"status\":\"PENDING_VERIFICATION\",\"possible_duplicate\":false,\"evidence_count\":1}}"));
        server.createContext("/rest/v1/rpc/abandon_request_operation", exchange -> respond(exchange, "true"));
        server.createContext("/rest/v1/assistance_requests", this::handleRequestDetail);
        server.createContext("/rest/v1/volunteer_assignments", this::handleAssignment);
        server.createContext("/rest/v1/task_progress_updates", this::handleProgress);
        server.createContext("/rest/v1/disaster_types", exchange -> respond(exchange,
                "[{\"code\":\"flood\"},{\"code\":\"fire\"}]"));
        server.createContext("/rest/v1/rpc/record_task_progress", this::handleRecordProgress);
        server.createContext("/rest/v1/rpc/record_coordinator_assistance_request", this::handleSubmit);
        server.createContext("/rest/v1/rpc/get_request_review_details", exchange -> respond(exchange,
                "{\"requester_name\":\"Ayesha Khan\",\"requester_contact\":\"+92 300 7654321\"," +
                "\"evidence\":[{\"id\":\"" + UUID.randomUUID() + "\",\"file_name\":\"damage.jpg\"," +
                "\"content_type\":\"image/jpeg\",\"size_bytes\":2048,\"uploaded_at\":\"2026-11-15T01:05:00Z\"}]," +
                "\"possible_duplicates\":[{\"request_id\":\"" + UUID.randomUUID() + "\"," +
                "\"status\":\"VERIFIED\",\"priority\":\"HIGH\",\"submitted_at\":\"2026-11-15T00:30:00Z\"}]}"));
        server.start();
        repository = new SupabaseWorkflowRepository(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "sb_publishable_test");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void ordersTheCoordinatorQueueByUrgencyThenSubmissionTime() {
        server.removeContext("/rest/v1/assistance_requests");
        AtomicReference<String> query = new AtomicReference<>();
        server.createContext("/rest/v1/assistance_requests", exchange -> {
            query.set(exchange.getRequestURI().getQuery());
            respond(exchange, "[]");
        });
        var session = new AuthSession(USER_ID, "coordinator@example.com", "access", "refresh", Instant.MAX);
        repository.findPrioritizableRequests(session);
        assertTrue(query.get().contains("order=priority.asc.nullslast,submitted_at.asc"));
    }

    @Test
    void displaysPreviousVolunteersReassignmentReason() {
        server.removeContext("/rest/v1/volunteer_assignments");
        AtomicReference<String> query = new AtomicReference<>();
        server.createContext("/rest/v1/volunteer_assignments", exchange -> {
            query.set(exchange.getRequestURI().getQuery());
            respond(exchange, "[{\"request_id\":\"" + REQUEST_ID + "\",\"reassignment_reason\":\"Schedule changed\"}]");
        });
        var session = new AuthSession(USER_ID, "volunteer@example.com", "access", "refresh", Instant.MAX);
        assertEquals(java.util.List.of("Request " + REQUEST_ID + " reassigned: Schedule changed"),
                repository.reassignmentNotices(session));
        assertTrue(query.get().contains("volunteer_account_id=eq." + USER_ID));
        assertTrue(query.get().contains("status=eq.REASSIGNED"));
    }

    @Test
    void opensCommittedEvidenceUsingShortLivedPrivateStorageLink() {
        server.createContext("/rest/v1/request_evidence", exchange -> {
            assertTrue(exchange.getRequestURI().getQuery().contains("state=eq.COMMITTED"));
            respond(exchange, "[{\"object_path\":\"actor/operation/photo.jpg\"}]");
        });
        server.createContext("/storage/v1/object/sign/request-evidence/", exchange -> {
            assertEquals("Bearer user-access", exchange.getRequestHeaders().getFirst("Authorization"));
            assertTrue(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).contains("60"));
            respond(exchange, "{\"signedURL\":\"/object/sign/request-evidence/actor/operation/photo.jpg?token=test\"}");
        });
        AuthSession session = new AuthSession(USER_ID, "reviewer@example.com", "user-access", "refresh", Instant.MAX);
        URI link = repository.evidenceLink(session, UUID.randomUUID());
        assertEquals("127.0.0.1", link.getHost());
        assertEquals("/storage/v1/object/sign/request-evidence/actor/operation/photo.jpg", link.getPath());
    }

    @Test
    void loadsOnlyDatabaseControlledDisasterTypes() {
        AuthSession session = new AuthSession(USER_ID, "requester@example.com", "user-access", "refresh",
                Instant.parse("2026-11-15T02:00:00Z"));

        var types = repository.findActiveDisasterTypes(session);

        assertEquals(java.util.List.of(DisasterType.FLOOD, DisasterType.FIRE), types);
    }

    @Test
    void submitsValidatedFieldsThroughAuthenticatedRpc() {
        AuthSession session = new AuthSession(USER_ID, "requester@example.com", "user-access", "refresh",
                Instant.parse("2026-11-15T02:00:00Z"));
        AssistanceRequestDraft draft = new AssistanceRequestDraft(DisasterType.FLOOD, null,
                "Sector G-10, Islamabad", "Water entered the ground floor.",
                "Evacuation and drinking water", "Four adults and two children");

        RequestSubmissionResult result = repository.submitRequest(session, UUID.randomUUID(), USER_ID, draft);

        assertEquals(REQUEST_ID, result.requestId());
        assertEquals(RequestStatus.PENDING_VERIFICATION, result.status());
        assertEquals("Bearer user-access", authorization.get());
        assertTrue(body.get().contains("\"p_required_assistance\":\"Evacuation and drinking water\""));
        assertTrue(body.get().contains("\"p_requester_account_id\":\"" + USER_ID + "\""));
    }

    @Test
    void recordsCoordinatorEnteredRequestWithCallerSnapshot() {
        AuthSession session = new AuthSession(USER_ID, "coordinator@example.com", "user-access", "refresh",
                Instant.parse("2026-11-15T02:00:00Z"));
        AssistanceRequestDraft draft = new AssistanceRequestDraft(DisasterType.FLOOD, null,
                "Sector G-10, Islamabad", "Water entered the ground floor.",
                "Evacuation and drinking water", "Four adults and two children");

        repository.recordCoordinatorRequest(session, UUID.randomUUID(),
                new RequesterContact("Ayesha Khan", "+92 300 7654321"), draft);

        assertTrue(body.get().contains("\"p_requester_contact_name\":\"Ayesha Khan\""));
        assertTrue(body.get().contains("\"p_requester_contact_no\":\"+92 300 7654321\""));
    }

    @Test
    void loadsRequesterEvidenceAndDuplicateMatchesForVerification() {
        AuthSession session = new AuthSession(USER_ID, "coordinator@example.com", "user-access", "refresh",
                Instant.parse("2026-11-15T02:00:00Z"));

        var details = repository.findRequestReviewDetails(session, REQUEST_ID).orElseThrow();

        assertEquals("Ayesha Khan", details.requesterName());
        assertEquals("+92 300 7654321", details.requesterContact());
        assertEquals("damage.jpg", details.evidence().get(0).fileName());
        assertEquals(RequestStatus.VERIFIED, details.possibleDuplicates().get(0).status());
    }

    @Test
    void enrichesRequesterDetailWithLatestAssignmentAndProgress() {
        AuthSession session = new AuthSession(USER_ID, "requester@example.com", "user-access", "refresh",
                Instant.parse("2026-11-15T02:00:00Z"));

        var request = repository.findRequestForRequester(session, REQUEST_ID, USER_ID).orElseThrow();

        assertEquals("IN_PROGRESS", request.assignmentState());
        assertEquals("60% · Drinking water delivered", request.latestProgress());
    }

    @Test
    void reconcilesCommittedOperationBeforeCleanupDecision() {
        AuthSession session = new AuthSession(USER_ID, "requester@example.com", "user-access", "refresh",
                Instant.parse("2026-11-15T02:00:00Z"));
        UUID operationId = UUID.randomUUID();

        RequestSubmissionResult result = repository.reconcileRequestOperation(session, operationId).orElseThrow();

        assertEquals(REQUEST_ID, result.requestId());
        assertEquals(1, result.evidenceCount());
        assertTrue(repository.abandonRequestOperation(session, operationId));
    }

    @Test
    void recordsProgressThroughVersionCheckedRpc() {
        AuthSession session = new AuthSession(USER_ID, "volunteer@example.com", "user-access", "refresh",
                Instant.parse("2026-11-15T02:00:00Z"));
        ProgressUpdate update = new ProgressUpdate(UUID.randomUUID(), ASSIGNMENT_ID, USER_ID,
                "Reached the affected location.", 20, Instant.parse("2026-11-15T01:30:00Z"));

        var result = repository.saveProgressAndStatus(session, UUID.randomUUID(), update, REQUEST_ID, 4, 7);

        assertEquals(AssignmentStatus.IN_PROGRESS, result.assignmentStatus());
        assertEquals(5, result.assignmentVersion());
        assertTrue(body.get().contains("\"p_expected_assignment_version\":4"));
        assertTrue(body.get().contains("\"p_percentage\":20"));
    }

    private void handleSubmit(HttpExchange exchange) throws IOException {
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        String response = "{\"request_id\":\"" + REQUEST_ID
                + "\",\"status\":\"PENDING_VERIFICATION\",\"possible_duplicate\":false,\"evidence_count\":0}";
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private void handleRequestDetail(HttpExchange exchange) throws IOException {
        respond(exchange, "[{\"id\":\"" + REQUEST_ID + "\",\"disaster_type\":\"flood\"," +
                "\"disaster_type_other\":null,\"location_address\":\"Sector G-10, Islamabad\"," +
                "\"description\":\"Water entered the ground floor.\"," +
                "\"required_assistance\":\"Evacuation and drinking water\"," +
                "\"affected_person_details\":\"Four adults and two children\"," +
                "\"status\":\"IN_PROGRESS\",\"priority\":\"HIGH\",\"possible_duplicate\":false," +
                "\"submitted_at\":\"2026-11-15T01:00:00Z\",\"resubmitted_at\":null," +
                "\"version\":3,\"information_request_reason\":null,\"rejection_reason\":null}]");
    }

    private void handleAssignment(HttpExchange exchange) throws IOException {
        respond(exchange, "[{\"id\":\"" + ASSIGNMENT_ID + "\",\"status\":\"IN_PROGRESS\"}]");
    }

    private void handleProgress(HttpExchange exchange) throws IOException {
        respond(exchange, "[{\"note\":\"Drinking water delivered\",\"percentage\":60," +
                "\"created_at\":\"2026-11-15T01:30:00Z\"}]");
    }

    private void handleRecordProgress(HttpExchange exchange) throws IOException {
        body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        respond(exchange, "{\"update_id\":\"" + UUID.randomUUID()
                + "\",\"assignment_id\":\"" + ASSIGNMENT_ID
                + "\",\"assignment_status\":\"IN_PROGRESS\",\"request_id\":\"" + REQUEST_ID
                + "\",\"request_status\":\"IN_PROGRESS\",\"assignment_version\":5,"
                + "\"request_version\":8}");
    }

    private static void respond(HttpExchange exchange, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
