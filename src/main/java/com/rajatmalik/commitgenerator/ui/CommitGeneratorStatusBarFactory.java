package com.rajatmalik.commitgenerator.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.rajatmalik.commitgenerator.core.Product;
import org.jetbrains.annotations.NotNull;

/**
 * Factory implementation in Java to avoid Kotlin bridge overrides of the
 * platform's deprecated default methods. The plugin verifier reports those
 * bridges as deprecated/internal API usages.
 */
public final class CommitGeneratorStatusBarFactory implements StatusBarWidgetFactory {

    @Override
    public @NotNull String getId() {
        return Product.STATUS_WIDGET_ID;
    }

    @Override
    public @NotNull String getDisplayName() {
        return Product.NAME;
    }

    @Override
    public boolean isAvailable(@NotNull Project project) {
        return true;
    }

    @Override
    public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
        return new CommitGeneratorStatusBarWidget(project);
    }

    @Override
    public void disposeWidget(@NotNull StatusBarWidget widget) {
        Disposer.dispose(widget);
    }

    @Override
    public boolean canBeEnabledOn(@NotNull StatusBar statusBar) {
        return true;
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }
}
