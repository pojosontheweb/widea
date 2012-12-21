package woko.idea;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;

public class OpenWokoToolWindow extends AnAction {
    public void actionPerformed(AnActionEvent e) {
        final Project project = e.getProject();
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        toolWindowManager.getToolWindow("Woko").show(new Runnable() {
            @Override
            public void run() {
                WokoProjectComponent wpc = project.getComponent(WokoProjectComponent.class);
                wpc.refresh();
            }
        });
    }
}
