package pk.edu.nu.aafatlink.ui.shell;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import pk.edu.nu.aafatlink.app.SessionManager;
import pk.edu.nu.aafatlink.app.navigation.NavigationItem;
import pk.edu.nu.aafatlink.application.dashboard.DashboardEntry;
import pk.edu.nu.aafatlink.application.dashboard.DashboardController;
import pk.edu.nu.aafatlink.domain.account.UserRole;

import static pk.edu.nu.aafatlink.app.navigation.NavigationItem.*;

public final class DashboardViewController {
    private record Queue(String key, String title, NavigationItem destination, String empty) {}
    private final SessionManager sessions;
    private final DashboardController dashboard;
    private final BiConsumer<NavigationItem, UUID> navigate;
    private List<Queue> queues;

    @FXML private Label heading;
    @FXML private Label message;
    @FXML private Button refresh;
    @FXML private FlowPane actions;
    @FXML private FlowPane cards;

    public DashboardViewController(SessionManager sessions, DashboardController dashboard,
            BiConsumer<NavigationItem, UUID> navigate) {
        this.sessions = sessions;
        this.dashboard = dashboard;
        this.navigate = navigate;
    }

    @FXML
    private void initialize() {
        UserRole role = sessions.currentUser().orElseThrow().role();
        switch (role) {
            case REQUESTER -> {
                heading.setText("Your assistance requests");
                action(SUBMIT_REQUEST);
                queues = List.of(new Queue("returned", "More information needed", MY_REQUESTS, "No requests need an update."),
                        new Queue("requests", "My requests", MY_REQUESTS, "You haven’t submitted a request yet."));
            }
            case VOLUNTEER -> {
                heading.setText("Your field work");
                action(READINESS_PROFILE);
                queues = List.of(new Queue("offers", "Awaiting your response", ASSIGNMENTS, "No assignments are waiting for a response."),
                        new Queue("tasks", "Active tasks", TASK_PROGRESS, "You have no active tasks."));
            }
            case COORDINATOR -> {
                heading.setText("Response queues");
                action(RECORD_REQUEST);
                action(REPORTS);
                queues = List.of(new Queue("verify", "Awaiting verification", VERIFICATION_QUEUE, "No requests await verification."),
                        new Queue("priority", "Need a priority", PRIORITY_QUEUE, "All verified requests have a priority."),
                        new Queue("match", "Ready for assignment", VOLUNTEER_ASSIGNMENT, "No requests are ready for assignment."),
                        new Queue("close", "Awaiting closure", CLOSURE_REVIEW, "No completions await review."));
            }
            case ADMINISTRATOR -> {
                heading.setText("Account administration");
                action(ACCESS_MANAGEMENT);
                action(REFERENCE_DATA);
                action(REPORTS);
                queues = List.of(new Queue("approvals", "Volunteer approvals", ACCESS_MANAGEMENT, "No active volunteers await approval."));
            }
        }
        reload();
    }

    private void action(NavigationItem item) {
        Button button = new Button(item.label());
        button.getStyleClass().add("outline-button");
        button.setOnAction(event -> navigate.accept(item, null));
        actions.getChildren().add(button);
    }

    @FXML
    private void reload() {
        var user = sessions.currentUser().orElseThrow();
        cards.getChildren().clear();
        if (user.demonstrationAccount()) {
            refresh.setVisible(false);
            refresh.setManaged(false);
            message.setText("Preview mode. Sign in to see live records.");
            render(Map.of(), true);
            return;
        }
        var session = sessions.authSession().orElseThrow();
        refresh.setDisable(true);
        message.setText("Loading dashboard…");
        Task<Map<String, DashboardEntry>> task = new Task<>() {
            @Override protected Map<String, DashboardEntry> call() {
                return dashboard.load(session, user.role()).stream().collect(
                        Collectors.toMap(DashboardEntry::queue, Function.identity()));
            }
        };
        task.setOnSucceeded(event -> {
            refresh.setDisable(false);
            refresh.setText("Refresh");
            message.setText("");
            render(task.getValue(), false);
        });
        task.setOnFailed(event -> {
            refresh.setDisable(false);
            refresh.setText("Retry");
            message.setText("Couldn’t load your dashboard. Check your connection or sign in again.");
        });
        Thread worker = new Thread(task, "aafatlink-dashboard");
        worker.setDaemon(true);
        worker.start();
    }

    private void render(Map<String, DashboardEntry> entries, boolean demo) {
        for (Queue queue : queues) {
            DashboardEntry entry = entries.get(queue.key());
            VBox card = new VBox(10);
            card.setPrefWidth(246);
            card.getStyleClass().add("summary-card");
            Label title = new Label(queue.title());
            title.getStyleClass().add("banner-title");
            title.setWrapText(true);
            card.getChildren().add(title);
            if (!demo) {
                Label count = new Label(entry == null ? "0" : Long.toString(entry.total()));
                count.getStyleClass().add("card-value");
                card.getChildren().add(count);
                if (entry == null) {
                    Label empty = new Label(queue.empty());
                    empty.setWrapText(true);
                    card.getChildren().add(empty);
                } else {
                    Button record = new Button(entry.caption());
                    record.getStyleClass().add("text-button");
                    record.setWrapText(true);
                    record.setMaxWidth(Double.MAX_VALUE);
                    record.setOnAction(event -> navigate.accept(queue.destination(), entry.recordId()));
                    card.getChildren().add(record);
                }
            }
            Button open = new Button(queue.destination() == VOLUNTEER_ASSIGNMENT ? "Assign a volunteer"
                    : "Open " + queue.destination().label().toLowerCase(java.util.Locale.ROOT));
            open.getStyleClass().add("outline-button");
            open.setOnAction(event -> navigate.accept(queue.destination(), null));
            card.getChildren().add(open);
            cards.getChildren().add(card);
        }
    }
}
