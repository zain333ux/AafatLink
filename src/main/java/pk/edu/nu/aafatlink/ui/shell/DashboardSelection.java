package pk.edu.nu.aafatlink.ui.shell;

import java.util.UUID;
import java.util.function.Function;
import javafx.scene.control.TableView;

/** Applies a dashboard link once, after a workflow loads its current queue. */
public final class DashboardSelection {
    public interface Destination {
        DashboardSelection initialSelection();
    }

    private UUID requested;

    public void request(UUID id) {
        requested = id;
    }

    public <T> void select(TableView<T> table, Function<T, UUID> identify) {
        UUID id = requested;
        requested = null;
        if (id == null) {
            table.getSelectionModel().selectFirst();
            return;
        }
        table.getItems().stream().filter(item -> id.equals(identify.apply(item))).findFirst()
                .ifPresent(item -> {
                    table.getSelectionModel().select(item);
                    table.scrollTo(item);
                });
    }
}
