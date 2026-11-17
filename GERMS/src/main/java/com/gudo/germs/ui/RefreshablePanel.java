package com.gudo.germs.ui;

/** A screen that can reload its server-backed data without rebuilding its UI. */
public interface RefreshablePanel {
    void refreshData();
}
