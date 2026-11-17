package com.campusos;

import javafx.application.Application;

/**
 * Entry point. Launching through a plain (non-Application) main class is the
 * recommended pattern for running JavaFX from Maven/exec without module-path quirks.
 */
public class Launcher {

    public static void main(String[] args) {
        Application.launch(Main.class, args);
    }
}
