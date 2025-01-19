package com.weakviord.filetagger.ui;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.weakviord.filetagger.service.TagStorageService;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class FileTagDecorator implements ProjectViewNodeDecorator {
    @Override
    public void decorate(@NotNull ProjectViewNode<?> node, @NotNull PresentationData presentation) {
        VirtualFile file = node.getVirtualFile();
        if (file == null) return;

        Project project = node.getProject();
        if (project == null) return;

        TagStorageService tagService = project.getService(TagStorageService.class);
        Set<String> tags = tagService.getFileTags(file);
        
        if (tags != null && !tags.isEmpty()) {
            String originalText = presentation.getPresentableText();
            if (originalText == null) return;

            presentation.clearText();
            presentation.addText(originalText, SimpleTextAttributes.REGULAR_ATTRIBUTES);
            
            // 将标签转换为TagInfo并排序
            List<TagStorageService.TagInfo> tagInfos = new ArrayList<>();
            for (String tagName : tags) {
                TagStorageService.TagInfo tagInfo = tagService.getTagInfo(tagName);
                if (tagInfo != null) {
                    tagInfos.add(tagInfo);
                }
            }

            // 按使用数量降序排序，数量相同时按创建时间升序排序
            tagInfos.sort((a, b) -> {
                int countA = tagService.getTagUsageCount(a.name);
                int countB = tagService.getTagUsageCount(b.name);
                if (countA != countB) {
                    return Integer.compare(countB, countA); // 降序
                }
                return Long.compare(a.order, b.order); // 升序
            });

            // 按排序后的顺序显示标签
            for (TagStorageService.TagInfo tagInfo : tagInfos) {
                presentation.addText(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
                presentation.addText(tagInfo.name, new SimpleTextAttributes(
                    SimpleTextAttributes.STYLE_BOLD,
                    tagInfo.getColor()
                ));
            }
        }
    }

    @Override
    public void decorate(@NotNull PackageDependenciesNode node, @NotNull ColoredTreeCellRenderer cellRenderer) {
        // 这个方法已经被标记为过时，但我们仍需要实现它
    }

    private Color getTagColor(String tag) {
        // 根据标签生成一个稳定的颜色
        int hash = tag.hashCode();
        float hue = (hash & 0xFF) / 255.0f;  // 使用哈希值的低8位作为色相
        return Color.getHSBColor(hue, 0.7f, 0.9f);  // 使用固定的饱和度和亮度
    }
} 