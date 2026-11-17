package com.campusos.ui;

import com.campusos.session.Session;

/**
 * Contract for every view controller loaded into the shell.
 * The shell injects context BEFORE initialize() runs and calls onShow()
 * after the node is on screen, so controllers load fresh data per visit.
 */
public interface ViewController {

    /** Called once right after construction, before initialize(). */
    void initContext(Session session, Navigator navigator);

    /** Called every time the view becomes visible. */
    void onShow();
}
