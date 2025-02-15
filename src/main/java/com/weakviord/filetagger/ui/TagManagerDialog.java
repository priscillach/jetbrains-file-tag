package com.weakviord.filetagger.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ide.projectView.ProjectView;
import com.weakviord.filetagger.service.TagStorageService;
import com.weakviord.filetagger.service.TagStorageService.TagInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

import com.intellij.ui.ColorPicker;
import com.intellij.ui.ColorPickerListener;
import com.intellij.ui.JBColor;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;

public class TagManagerDialog extends DialogWrapper {
    private final Project project;
    private final VirtualFile file;
    private final TagStorageService tagService;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static class TagListItem {
        final TagInfo tagInfo;
        boolean checked;

        TagListItem(TagInfo tagInfo, boolean checked) {
            this.tagInfo = tagInfo;
            this.checked = checked;
        }
    }

    private final DefaultListModel<TagListItem> availableTagsModel;
    private final JBList<TagListItem> availableTagsList;
    private final JBTextField newTagField;
    private final Set<String> selectedTags;

    private enum SortType {
        CREATE_TIME("Creation Time"),
        NAME("Tag Name"),
        FILES("Files Count"),
        MODIFIED_TIME("Last Modified");

        private final String displayName;

        SortType(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private SortType currentSortType = SortType.CREATE_TIME;
    private boolean ascending = true;

    // 保存排序设置的键
    private static final String SORT_TYPE_KEY = "FileTagger.SortType";
    private static final String SORT_ORDER_KEY = "FileTagger.SortOrder";

    public TagManagerDialog(Project project, VirtualFile file) {
        super(project);
        this.project = project;
        this.file = file;
        this.tagService = project.getService(TagStorageService.class);
        this.selectedTags = new HashSet<>(tagService.getFileTags(file));
        this.availableTagsModel = new DefaultListModel<>();
        this.availableTagsList = new JBList<>(availableTagsModel);
        this.newTagField = new JBTextField();

        // 恢复上次的排序设置
        PropertiesComponent props = PropertiesComponent.getInstance(project);
        String savedSortType = props.getValue(SORT_TYPE_KEY);
        if (savedSortType != null) {
            try {
                currentSortType = SortType.valueOf(savedSortType);
            } catch (IllegalArgumentException e) {
                currentSortType = SortType.CREATE_TIME;
            }
        }
        ascending = "true".equals(props.getValue(SORT_ORDER_KEY, "true"));

        setTitle("Tag Manager");
        init();
        loadTags();
        updateAvailableTagsList();
    }

    private void loadTags() {
        availableTagsModel.clear();
        List<TagInfo> allTags = tagService.getAllTags();
        for (TagInfo tagInfo : allTags) {
            availableTagsModel.addElement(new TagListItem(tagInfo, selectedTags.contains(tagInfo.name)));
        }
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        
        // 设置首选大小
        mainPanel.setPreferredSize(new Dimension(500, 600));  // 调整对话框大小

        // 自定义列表渲染器
        availableTagsList.setCellRenderer(new ColoredListCellRenderer<>() {
            private final JCheckBox checkBox = new JCheckBox();
            private final JPanel colorPanel = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    // 创建一个棋盘格背景来显示透明度
                    int squareSize = 4;
                    for (int x = 0; x < getWidth(); x += squareSize) {
                        for (int y = 0; y < getHeight(); y += squareSize) {
                            g.setColor(((x + y) / squareSize % 2 == 0) ? Color.WHITE : Color.LIGHT_GRAY);
                            g.fillRect(x, y, squareSize, squareSize);
                        }
                    }

                    // 使用 Graphics2D 来支持透明度
                    if (g instanceof Graphics2D g2d) {
                        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2d.setColor(getForeground());
                        g2d.fillRect(2, 2, getWidth() - 4, getHeight() - 4);
                        // 绘制边框
                        g2d.setColor(JBColor.border());
                        g2d.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
                    }
                }
            };
            private final JPanel panel = new JPanel(new BorderLayout(5, 0));
            private final ColoredListCellRenderer<TagListItem> textRenderer = new ColoredListCellRenderer<>() {
                @Override
                protected void customizeCellRenderer(@NotNull JList<? extends TagListItem> list,
                                                   TagListItem item,
                                                   int index,
                                                   boolean selected,
                                                   boolean hasFocus) {
                    // 标签名称和颜色示例
                    append(item.tagInfo.name, 
                          new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, item.tagInfo.getColor()));
                    
                    // 使用数量
                    int usageCount = tagService.getTagUsageCount(item.tagInfo.name);
                    append("  (" + usageCount + " files)", 
                          new SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, Color.GRAY));
                    
                    // 最后修改时间
                    append("  " + DATE_FORMAT.format(new Date(item.tagInfo.timestamp)),
                          new SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, Color.GRAY));
                    
                    setBackground(selected ? list.getSelectionBackground() : list.getBackground());
                }
            };

            {
                // 初始化面板和组件
                panel.setOpaque(true);
                checkBox.setOpaque(false);
                textRenderer.setOpaque(false);
                
                // 设置颜色面板大小
                colorPanel.setPreferredSize(new Dimension(16, 16));
                colorPanel.setOpaque(false);
                
                // 创建包含复选框和颜色面板的左侧面板
                JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
                leftPanel.setOpaque(false);
                leftPanel.add(checkBox);
                leftPanel.add(colorPanel);
                
                panel.add(leftPanel, BorderLayout.WEST);
                panel.add(textRenderer, BorderLayout.CENTER);
            }

            @Override
            protected void customizeCellRenderer(@NotNull JList<? extends TagListItem> list,
                                               TagListItem item,
                                               int index,
                                               boolean selected,
                                               boolean hasFocus) {
                panel.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
                checkBox.setSelected(item.checked);
                colorPanel.setForeground(item.tagInfo.getColor());
                textRenderer.getListCellRendererComponent(list, item, index, selected, hasFocus);
            }

            @Override
            public Component getListCellRendererComponent(JList<? extends TagListItem> list,
                                                        TagListItem value,
                                                        int index,
                                                        boolean selected,
                                                        boolean hasFocus) {
                customizeCellRenderer(list, value, index, selected, hasFocus);
                return panel;
            }
        });

        // 修改鼠标监听器
        availableTagsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = availableTagsList.locationToIndex(e.getPoint());
                if (index != -1) {
                    Rectangle bounds = availableTagsList.getCellBounds(index, index);
                    if (bounds != null) {
                        // 检查是否点击了复选框区域（左侧20像素）
                        if (e.getX() <= bounds.x + 20) {
                            TagListItem item = availableTagsModel.getElementAt(index);
                            item.checked = !item.checked;
                            if (item.checked) {
                                selectedTags.add(item.tagInfo.name);
                            } else {
                                selectedTags.remove(item.tagInfo.name);
                            }
                            availableTagsList.repaint();
                            e.consume();
                        }
                        // 检查是否点击了颜色面板区域（复选框后20-40像素）
                        else if (e.getX() > bounds.x + 20 && e.getX() <= bounds.x + 40) {
                            TagListItem item = availableTagsModel.getElementAt(index);
                            showColorPicker(item);
                            e.consume();
                        }
                    }
                }
            }
        });

        // 设置多选模式
        availableTagsList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        // 标签列表面板
        JPanel listPanel = new JPanel(new BorderLayout());
        listPanel.add(new JLabel("Available Tags:"), BorderLayout.NORTH);
        listPanel.add(new JBScrollPane(availableTagsList), BorderLayout.CENTER);

        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton editButton = new JButton("Edit");
        JButton deleteButton = new JButton("Delete");
        buttonPanel.add(editButton);
        buttonPanel.add(deleteButton);
        listPanel.add(buttonPanel, BorderLayout.SOUTH);

        // 新标签面板
        JPanel newTagPanel = new JPanel(new BorderLayout(5, 0));
        newTagPanel.add(new JLabel("New Tag:"), BorderLayout.WEST);
        newTagPanel.add(newTagField, BorderLayout.CENTER);
        JButton addButton = new JButton("Add");
        newTagPanel.add(addButton, BorderLayout.EAST);

        mainPanel.add(listPanel, BorderLayout.CENTER);
        mainPanel.add(newTagPanel, BorderLayout.SOUTH);

        addButton.addActionListener(e -> {
            String newTag = newTagField.getText().trim();
            if (!newTag.isEmpty()) {
                if (tagService.addTag(newTag)) {
                    selectedTags.add(newTag);
                    TagInfo newTagInfo = tagService.getTagInfo(newTag);
                    availableTagsModel.addElement(new TagListItem(newTagInfo, true));
                    newTagField.setText("");
                    updateAvailableTagsList();
                } else {
                    Messages.showErrorDialog(
                        project,
                        "Tag '" + newTag + "' already exists!",
                        "Duplicate Tag"
                    );
                }
            }
        });

        editButton.addActionListener(e -> {
            List<TagListItem> selectedItems = availableTagsList.getSelectedValuesList();
            if (selectedItems.size() == 1) {
                TagListItem item = selectedItems.get(0);
                String oldName = item.tagInfo.name;
                String newName = Messages.showInputDialog(
                    project,
                    "Enter new name for tag '" + oldName + "':",
                    "Rename Tag",
                    Messages.getQuestionIcon(),
                    oldName,
                    null
                );
                if (newName != null && !newName.trim().isEmpty()) {
                    if (!tagService.renameTag(oldName, newName)) {
                        Messages.showErrorDialog(
                            project,
                            "Tag '" + newName + "' already exists!",
                            "Duplicate Tag"
                        );
                    } else {
                        if (selectedTags.remove(oldName)) {
                            selectedTags.add(newName);
                        }
                        int index = availableTagsList.getSelectedIndex();
                        boolean wasChecked = item.checked;
                        TagInfo updatedInfo = tagService.getTagInfo(newName);
                        availableTagsModel.setElementAt(new TagListItem(updatedInfo, wasChecked), index);
                        updateAvailableTagsList();
                    }
                }
            }
        });

        deleteButton.addActionListener(e -> {
            List<TagListItem> selectedItems = availableTagsList.getSelectedValuesList();
            if (!selectedItems.isEmpty()) {
                String message = selectedItems.size() == 1 
                    ? "Are you sure you want to delete tag '" + selectedItems.get(0).tagInfo.name + "'?"
                    : "Are you sure you want to delete " + selectedItems.size() + " tags?";
                
                int result = Messages.showYesNoDialog(
                    project,
                    message + "\nThis will remove the tag(s) from all files.",
                    "Delete Tags",
                    Messages.getQuestionIcon()
                );
                
                if (result == Messages.YES) {
                    // 删除选中的标签
                    for (TagListItem item : selectedItems) {
                        selectedTags.remove(item.tagInfo.name);
                        tagService.deleteTag(item.tagInfo.name);
                        availableTagsModel.removeElement(item);
                    }
                    updateAvailableTagsList();
                }
            }
        });

        // 创建排序面板
        JPanel sortPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        // 创建排序下拉框
        DefaultComboBoxModel<SortType> sortTypeModel = new DefaultComboBoxModel<>(SortType.values());
        JComboBox<SortType> sortTypeCombo = new JComboBox<>(sortTypeModel);
        sortTypeCombo.setToolTipText("Sort by");
        sortTypeCombo.setSelectedItem(currentSortType);  // 设置初始选择
        
        // 创建升序/降序切换按钮
        JToggleButton orderToggle = new JToggleButton(
            ascending ? AllIcons.General.ArrowUp : AllIcons.General.ArrowDown
        );
        orderToggle.setSelected(!ascending);  // 设置初始状态
        orderToggle.setToolTipText(ascending ? "Ascending" : "Descending");
        
        // 添加事件监听
        sortTypeCombo.addActionListener(e -> {
            currentSortType = (SortType) sortTypeCombo.getSelectedItem();
            PropertiesComponent.getInstance(project).setValue(SORT_TYPE_KEY, currentSortType.name());
            updateAvailableTagsList();
        });
        
        orderToggle.addActionListener(e -> {
            ascending = !ascending;
            orderToggle.setIcon(ascending ? 
                AllIcons.General.ArrowUp : 
                AllIcons.General.ArrowDown
            );
            orderToggle.setToolTipText(ascending ? "Ascending" : "Descending");
            // 修改这里：使用字符串值存储布尔值
            PropertiesComponent.getInstance(project).setValue(SORT_ORDER_KEY, String.valueOf(ascending));
            updateAvailableTagsList();
        });
        
        sortPanel.add(new JLabel("Sort by: "));
        sortPanel.add(sortTypeCombo);
        sortPanel.add(orderToggle);
        
        // 添加到工具栏
        JPanel toolbarPanel = new JPanel(new BorderLayout());
        toolbarPanel.add(sortPanel, BorderLayout.EAST);
        mainPanel.add(toolbarPanel, BorderLayout.NORTH);

        return mainPanel;
    }

    // 添加颜色选择器方法
    private void showColorPicker(TagListItem item) {
        // 保存原始颜色值
        float originalHue = item.tagInfo.colorHue;
        float originalSaturation = item.tagInfo.colorSaturation;
        float originalBrightness = item.tagInfo.colorBrightness;
        float originalAlpha = item.tagInfo.colorAlpha;
        
        ColorPickerListener listener = new ColorPickerListener() {
            @Override
            public void colorChanged(Color color) {
                item.tagInfo.setColor(color);
                availableTagsList.repaint();
            }

            @Override
            public void closed(@Nullable Color color) {
                if (color == null) {
                    item.tagInfo.colorHue = originalHue;
                    item.tagInfo.colorSaturation = originalSaturation;
                    item.tagInfo.colorBrightness = originalBrightness;
                    item.tagInfo.colorAlpha = originalAlpha;
                    availableTagsList.repaint();
                } else {
                    item.tagInfo.setColor(color);
                    ProjectView.getInstance(project).refresh();
                }
            }
        };
        
        ColorPicker.showDialog(
            availableTagsList,
            "Choose Color for " + item.tagInfo.name,
            item.tagInfo.getColor(),
            true,
            Collections.singletonList(listener),
            true
        );
    }

    private void updateAvailableTagsList() {
        List<TagListItem> currentItems = new ArrayList<>();
        for (int i = 0; i < availableTagsModel.getSize(); i++) {
            currentItems.add(availableTagsModel.getElementAt(i));
        }

        // 根据选择的排序类型进行排序
        currentItems.sort((a, b) -> {
            int result = 0;
            switch (currentSortType) {
                case NAME:
                    result = a.tagInfo.name.compareTo(b.tagInfo.name);
                    break;
                case FILES:
                    result = Integer.compare(
                        tagService.getTagUsageCount(a.tagInfo.name),
                        tagService.getTagUsageCount(b.tagInfo.name)
                    );
                    // 文件数相同时，按创建时间排序
                    if (result == 0) {
                        result = Long.compare(a.tagInfo.order, b.tagInfo.order);
                    }
                    break;
                case MODIFIED_TIME:
                    result = Long.compare(a.tagInfo.timestamp, b.tagInfo.timestamp);
                    // 修改时间相同时，按创建时间排序
                    if (result == 0) {
                        result = Long.compare(a.tagInfo.order, b.tagInfo.order);
                    }
                    break;
                case CREATE_TIME:
                default:
                    result = Long.compare(a.tagInfo.order, b.tagInfo.order);
                    break;
            }
            return ascending ? result : -result;
        });

        availableTagsModel.clear();
        for (TagListItem item : currentItems) {
            availableTagsModel.addElement(item);
        }
    }

    @Override
    protected void doOKAction() {
        tagService.setFileTags(file, selectedTags);
        super.doOKAction();
    }
} 