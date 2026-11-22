package com.rajatmalik.commitgenerator.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.Consumer;
import com.rajatmalik.commitgenerator.core.Product;
import com.rajatmalik.commitgenerator.core.domain.GenerationMode;
import com.rajatmalik.commitgenerator.core.domain.ProviderProfile;
import com.rajatmalik.commitgenerator.services.CommitGeneratorProjectService;
import com.rajatmalik.commitgenerator.services.CommitGeneratorProjectServiceKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.awt.Component;
import java.awt.event.MouseEvent;

public final class CommitGeneratorStatusBarWidget implements StatusBarWidget, StatusBarWidget.TextPresentation {

    private final Project project;
    private StatusBar statusBar;
    private kotlin.jvm.functions.Function0<kotlin.Unit> uiListener;

    public CommitGeneratorStatusBarWidget(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public @NotNull String ID() {
        return Product.STATUS_WIDGET_ID;
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
        this.statusBar = statusBar;
        uiListener = () -> {
            ApplicationManager.getApplication().invokeLater(() -> {
                StatusBar current = this.statusBar;
                if (current != null) {
                    current.updateWidget(ID());
                }
            });
            return kotlin.Unit.INSTANCE;
        };
        CommitGeneratorProjectService service = CommitGeneratorProjectServiceKt.commitGeneratorProject(project);
        service.addUiListener(uiListener);
        service.getApp().getSettings().addListener(uiListener);
        statusBar.updateWidget(ID());
    }

    @Override
    public void dispose() {
        if (uiListener != null) {
            try {
                CommitGeneratorProjectService service = CommitGeneratorProjectServiceKt.commitGeneratorProject(project);
                service.removeUiListener(uiListener);
                service.getApp().getSettings().removeListener(uiListener);
            } catch (RuntimeException ignored) {
                // Project may already be disposed.
            }
            uiListener = null;
        }
        statusBar = null;
    }

    @Override
    public @NotNull StatusBarWidget.WidgetPresentation getPresentation() {
        return this;
    }

    @Override
    public @NotNull String getText() {
        CommitGeneratorProjectService service = CommitGeneratorProjectServiceKt.commitGeneratorProject(project);
        if (service.isGenerating()) {
            return "Commit Generator: generating...";
        }
        ProviderProfile profile = service.activeProfile();
        GenerationMode mode = service.getApp().getSettings().read().getMode();
        if (profile == null) {
            return "Commit Generator: not configured";
        }
        return "Commit Generator: " + mode.getKey() + " / " + profile.getLabel();
    }

    @Override
    public float getAlignment() {
        return Component.CENTER_ALIGNMENT;
    }

    @Override
    public @Nullable String getTooltipText() {
        CommitGeneratorProjectService service = CommitGeneratorProjectServiceKt.commitGeneratorProject(project);
        return Product.NAME + "\n" + service.repositoryLabel() + "\nClick to open Commit Generator.";
    }

    @Override
    public @Nullable Consumer<MouseEvent> getClickConsumer() {
        return event -> {
            com.intellij.openapi.wm.ToolWindow toolWindow = ToolWindowManager.getInstance(project)
                    .getToolWindow(Product.TOOL_WINDOW_ID);
            if (toolWindow != null) {
                toolWindow.show();
            }
        };
    }
}
