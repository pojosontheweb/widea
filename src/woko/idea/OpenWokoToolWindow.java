package woko.idea;

import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;

public class OpenWokoToolWindow extends AnAction {

    public void actionPerformed(AnActionEvent e) {
        final Project project = e.getProject();
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        ToolWindow wokoToolWindow = toolWindowManager.getToolWindow("Woko");
        if (wokoToolWindow!=null) {
            wokoToolWindow.show(new Runnable() {
                @Override
                public void run() {
                    WokoProjectComponent wpc = project.getComponent(WokoProjectComponent.class);
                    if (wpc!=null) {
                        wpc.refresh();
                    } else {
                        // no project component ??? display help popup
                        showHelpPopup(project);
                    }
                }
            });
        } else {
            // no tool window : display help popup
            showHelpPopup(project);
        }
    }

    private void showHelpPopup(final Project project) {
//        JBPopupFactory.getInstance().createConfirmation("Woko facet not found on project. Add it ?", new Runnable() {
//            @Override
//            public void run() {
//                // TODO
//                //To change body of implemented methods use File | Settings | File Templates.
//            }
//        }, 1).showCenteredInCurrentWindow(project);
    }

}
