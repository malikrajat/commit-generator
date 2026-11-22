package com.rajatmalik.commitgenerator.ui;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Java implementation of the tool window factory.
 *
 * <p>The Kotlin compiler generates bridge overrides for the platform's Kotlin
 * default methods, which the plugin verifier reports as internal API usages.
 * Implementing this small factory in Java keeps the plugin on public API only.
 */
public final class CommitGeneratorToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        CommitGeneratorToolWindowPanel panel = new CommitGeneratorToolWindowPanel(project);
        Content content = ContentFactory.getInstance().createContent(panel, "", false);
        content.setDisposer(panel);
        toolWindow.getContentManager().addContent(content);
        toolWindow.setToHideOnEmptyContent(false);
    }
}
