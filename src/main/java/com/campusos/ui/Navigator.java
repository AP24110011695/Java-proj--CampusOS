package com.campusos.ui;

/**
 * Navigation capability that the shell provides to view controllers.
 * Implemented by ShellController; injected into every ViewController.
 */
public interface Navigator {

    /** Opens another view (if the current role is allowed). */
    void navigate(View view);

    /** Reloads the currently visible view (fresh data from MySQL). */
    void refreshCurrentView();
}
