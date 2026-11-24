package pk.edu.nu.aafatlink.ui.request;

import java.awt.Desktop;
import java.util.UUID;
import java.util.function.Consumer;
import javafx.concurrent.Task;
import pk.edu.nu.aafatlink.application.request.RequestReviewController;
import pk.edu.nu.aafatlink.domain.auth.AuthSession;

final class ReviewEvidenceOpener {
    private ReviewEvidenceOpener() {}

    static void open(RequestReviewController reviews, AuthSession session, UUID evidenceId, Consumer<String> error) {
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                Desktop.getDesktop().browse(reviews.evidenceLink(session, evidenceId));
                return null;
            }
        };
        task.setOnFailed(event -> error.accept("Could not open the attachment. Refresh and try again."));
        Thread worker = new Thread(task, "aafatlink-open-evidence");
        worker.setDaemon(true);
        worker.start();
    }
}
