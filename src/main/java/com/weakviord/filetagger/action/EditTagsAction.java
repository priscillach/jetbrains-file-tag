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
            LOG.info("Action not performed: project=" + project + ", file=" + file);
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
            LOG.info("Project is null");
            return null;
        }

        // 1. 尝试从项目视图树获取选中节点
        Component component = e.getData(PlatformDataKeys.CONTEXT_COMPONENT);
        if (component instanceof Tree) {
            Tree tree = (Tree) component;
            TreePath path = tree.getSelectionPath();
            if (path != null) {
                DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) path.getLastPathComponent();
                Object userObject = treeNode.getUserObject();
                LOG.info("Selected tree node: " + treeNode + ", userObject: " + userObject);
                
                if (userObject instanceof AbstractTreeNode) {
                    Object value = ((AbstractTreeNode<?>) userObject).getValue();
                    LOG.info("Node value: " + value);
                    
                    if (value instanceof PsiFile) {
                        VirtualFile vf = ((PsiFile) value).getVirtualFile();
                        LOG.info("Found file via PsiFile: " + vf.getPath());
                        return vf;
                    }
                }
            }
        }

        // 2. 尝试从项目视图获取选中节点
        ProjectView projectView = ProjectView.getInstance(project);
        AbstractProjectViewPane currentPane = projectView.getCurrentProjectViewPane();
        if (currentPane != null) {
            Object[] selectedElements = currentPane.getSelectedElements();
            if (selectedElements != null && selectedElements.length > 0) {
                Object element = selectedElements[0];
                LOG.info("Selected element: " + element);
                
                if (element instanceof PsiFile) {
                    VirtualFile vf = ((PsiFile) element).getVirtualFile();
                    LOG.info("Found file via selected PsiFile: " + vf.getPath());
                    return vf;
                }
            }
        }

        // 3. 尝试从数据上下文获取选中的文件
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (files != null && files.length > 0) {
            LOG.info("Found file via VIRTUAL_FILE_ARRAY: " + files[0].getPath());
            return files[0];
        }

        // 4. 尝试从 PSI_ELEMENT 获取
        PsiElement psiElement = e.getData(CommonDataKeys.PSI_ELEMENT);
        if (psiElement != null) {
            LOG.info("Found PSI_ELEMENT: " + psiElement);
            if (psiElement instanceof PsiFile) {
                VirtualFile vf = ((PsiFile) psiElement).getVirtualFile();
                LOG.info("Found file via PsiFile: " + vf.getPath());
                return vf;
            }
            if (psiElement.getContainingFile() != null) {
                VirtualFile vf = psiElement.getContainingFile().getVirtualFile();
                LOG.info("Found file via PSI_ELEMENT: " + vf.getPath());
                return vf;
            }
        }

        LOG.info("Failed to find file through any method");
        return null;
    }

    private String dumpDataContext(DataContext dataContext) {
        StringBuilder sb = new StringBuilder("DataContext contents:\n");
        String[] keys = {
            CommonDataKeys.PROJECT.getName(),
            CommonDataKeys.VIRTUAL_FILE.getName(),
            CommonDataKeys.PSI_FILE.getName(),
            CommonDataKeys.PSI_ELEMENT.getName(),
            PlatformDataKeys.CONTEXT_COMPONENT.getName(),
            PlatformDataKeys.SELECTED_ITEMS.getName()
        };
        
        for (String key : keys) {
            Object value = dataContext.getData(key);
            sb.append(key).append(" = ").append(value).append("\n");
            if (value instanceof Component) {
                sb.append("Component type: ").append(value.getClass().getName()).append("\n");
            }
        }
        return sb.toString();
    }
} 