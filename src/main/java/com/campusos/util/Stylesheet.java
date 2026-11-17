package com.campusos.util;

import javafx.scene.Parent;

import java.net.URL;

/**
 * Locates and applies the application stylesheet to scenes and dialogs.
 */
public final class Stylesheet {

    private static final String PATH = "/css/app.css";

    private Stylesheet() { }

    public static String path() {
        URL url = Stylesheet.class.getResource(PATH);
        if (url == null) {
            throw new IllegalStateException("Stylesheet missing from resources: " + PATH);
        }
        return url.toExternalForm();
    }

    public static void applyTo(Parent parent) {
        parent.getStylesheets().add(path());
    }
}
