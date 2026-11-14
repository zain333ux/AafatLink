package pk.edu.nu.aafatlink;

import javafx.application.Application;

/** Conventional main class kept separate from the JavaFX Application subclass. */
public final class Launcher {
    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(AafatLinkApplication.class, args);
    }
}
