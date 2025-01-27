package com.weakviord.filetagger.action;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.weakviord.filetagger.ui.TagManagerDialog;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.actionSystem.CommonDataKeys;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.Component;

public class EditTagsAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(EditTagsAction.class);

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile file = getTargetFile(e);
        
        if (project == null || file == null) {
            return;
        }

        TagManagerDialog dialog = new TagManagerDialog(project, file);
        dialog.show();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile file = getTargetFile(e);
        boolean enabled = project != null && file != null;
        
        LOG.info("Action update called: " + 
                "file=" + file + 
                ", project=" + project + 
                ", enabled=" + enabled + 
                ", actionPlace=" + e.getPlace() + 
                ", isPopup=" + e.getPlace().equals("ProjectViewPopup") + 
                ", dataContext=" + dumpDataContext(e.getDataContext()));
                
        boolean visible = enabled && "ProjectViewPopup".equals(e.getPlace());
        e.getPresentation().setEnabledAndVisible(visible);
    }

    private VirtualFile getTargetFile(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return null;
        }

        // 1. 首先尝试直接获取虚拟文件
        VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
        if (file != null) {
            return file;
        }

        // 2. 从项目视图获取选中节点
        ProjectView projectView = ProjectView.getInstance(project);
        AbstractProjectViewPane currentPane = projectView.getCurrentProjectViewPane();
        if (currentPane != null) {
            PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(e.getDataContext());
            if (element != null) {
                if (element instanceof PsiFile) {
                    return ((PsiFile) element).getVirtualFile();
                }
                PsiFile containingFile = element.getContainingFile();
                if (containingFile != null) {
                    VirtualFile vf = containingFile.getVirtualFile();
                    if (vf != null) {
                        return vf;
                    }
                }
            }
        }

        // 3. 尝试从树组件获取
        Component component = PlatformDataKeys.CONTEXT_COMPONENT.getData(e.getDataContext());
        if (component instanceof Tree) {
            Tree tree = (Tree) component;
            TreePath path = tree.getSelectionPath();
            if (path != null) {
                Object node = path.getLastPathComponent();
                if (node instanceof DefaultMutableTreeNode) {
                    Object userObject = ((DefaultMutableTreeNode) node).getUserObject();
                    if (userObject instanceof AbstractTreeNode) {
                        Object value = ((AbstractTreeNode<?>) userObject).getValue();
                        if (value instanceof PsiFile) {
                            return ((PsiFile) value).getVirtualFile();
                        }
                    }
                }
            }
        }

        // 4. 最后尝试从文件数组获取
        VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(e.getDataContext());
        if (files != null && files.length > 0) {
            return files[0];
        }

        return null;
    }

    private String dumpDataContext(DataContext dataContext) {
        StringBuilder sb = new StringBuilder("DataContext contents:\n");
        sb.append("Project = ").append(CommonDataKeys.PROJECT.getData(dataContext)).append("\n")
          .append("Virtual File = ").append(CommonDataKeys.VIRTUAL_FILE.getData(dataContext)).append("\n")
          .append("PSI File = ").append(CommonDataKeys.PSI_FILE.getData(dataContext)).append("\n")
          .append("PSI Element = ").append(CommonDataKeys.PSI_ELEMENT.getData(dataContext)).append("\n")
          .append("Context Component = ").append(PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext)).append("\n")
          .append("Selected Items = ").append(PlatformDataKeys.SELECTED_ITEMS.getData(dataContext)).append("\n");
        return sb.toString();
    }
} 