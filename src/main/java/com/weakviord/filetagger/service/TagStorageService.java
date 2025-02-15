package com.weakviord.filetagger.service;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ide.projectView.ProjectView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileMoveEvent;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;

import java.awt.Color;
import java.util.*;

@Service(Service.Level.PROJECT)
@State(
    name = "FileTaggerSettings",
    storages = {@Storage("fileTagger.xml")}
)
public final class TagStorageService implements PersistentStateComponent<TagStorageService.State>, Disposable {
    private State myState = new State();
    private final Project project;
    private final MessageBusConnection messageBusConnection;

    public TagStorageService(Project project) {
        this.project = project;
        this.messageBusConnection = project.getMessageBus().connect();
        
        VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileListener() {
            @Override
            public void beforePropertyChange(@NotNull VirtualFilePropertyEvent event) {
                if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
                    VirtualFile file = event.getFile();
                    String oldPath = file.getParent().getPath() + "/" + event.getOldValue();
                    String newPath = file.getParent().getPath() + "/" + event.getNewValue();
                    
                    if (file.isDirectory()) {
                        handleDirectoryPathChange(oldPath, newPath);
                    } else {
                        handleFileMove(oldPath, newPath);
                    }
                }
            }

            @Override
            public void beforeFileMovement(@NotNull VirtualFileMoveEvent event) {
                VirtualFile file = event.getFile();
                String oldPath = event.getOldParent().getPath() + "/" + file.getName();
                String newPath = event.getNewParent().getPath() + "/" + file.getName();
                
                if (file.isDirectory()) {
                    handleDirectoryPathChange(oldPath, newPath);
                } else {
                    handleFileMove(oldPath, newPath);
                }
            }
        }, project);
    }

    private void handleDirectoryPathChange(String oldDirPath, String newDirPath) {
        Map<String, Set<String>> updatedMap = new HashMap<>();
        
        for (Map.Entry<String, Set<String>> entry : myState.fileTagsMap.entrySet()) {
            String filePath = entry.getKey();
            Set<String> tags = entry.getValue();
            
            if (filePath.startsWith(oldDirPath + "/")) {
                String newFilePath = newDirPath + filePath.substring(oldDirPath.length());
                updatedMap.put(newFilePath, new HashSet<>(tags));
            } else {
                updatedMap.put(filePath, tags);
            }
        }
        
        myState.fileTagsMap = updatedMap;
        ProjectView.getInstance(project).refresh();
    }

    private void handleFileMove(String oldPath, String newPath) {
        Set<String> tags = myState.fileTagsMap.get(oldPath);
        if (tags != null && !tags.isEmpty()) {
            myState.fileTagsMap.remove(oldPath);
            myState.fileTagsMap.put(newPath, new HashSet<>(tags));
            ProjectView.getInstance(project).refresh();
        }
    }

    @Override
    public void dispose() {
        messageBusConnection.disconnect();
    }

    public static class TagInfo {
        public String name;
        public long timestamp;
        public float colorHue;
        public float colorSaturation = 0.7f;
        public float colorBrightness = 0.9f;
        public float colorAlpha = 1.0f;  // 添加透明度，1.0表示完全不透明
        public long order;

        // 用于序列化的无参构造函数
        public TagInfo() {
        }

        public TagInfo(String name) {
            this.name = name;
            this.timestamp = System.currentTimeMillis();
            this.order = System.currentTimeMillis();
            int hash = name.hashCode();
            this.colorHue = ((hash * 31 + System.nanoTime()) & 0xFFFF) / (float)0xFFFF;
        }

        @Transient
        public Color getColor() {
            Color hsbColor = Color.getHSBColor(colorHue, colorSaturation, colorBrightness);
            int alpha = Math.round(colorAlpha * 255);
            return new Color(hsbColor.getRed(), hsbColor.getGreen(), hsbColor.getBlue(), alpha);
        }

        @Transient
        public void setColor(Color color) {
            float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
            this.colorHue = hsb[0];
            this.colorSaturation = hsb[1];
            this.colorBrightness = hsb[2];
            this.colorAlpha = color.getAlpha() / 255.0f;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static class State {
        public Map<String, TagInfo> availableTags = new HashMap<>();
        public Map<String, Set<String>> fileTagsMap = new HashMap<>();
    }

    @Override
    public @Nullable State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        myState = state;
    }

    public List<TagInfo> getAllTags() {
        return new ArrayList<>(myState.availableTags.values());
    }

    public int getTagUsageCount(String tagName) {
        int count = 0;
        for (Set<String> fileTags : myState.fileTagsMap.values()) {
            if (fileTags.contains(tagName)) {
                count++;
            }
        }
        return count;
    }

    public boolean addTag(String tag) {
        if (tag == null || tag.trim().isEmpty()) {
            return false;
        }
        tag = tag.trim();
        if (myState.availableTags.containsKey(tag)) {
            return false;
        }
        myState.availableTags.put(tag, new TagInfo(tag));
        return true;
    }

    public boolean renameTag(String oldTag, String newTag) {
        if (newTag == null || newTag.trim().isEmpty() || !myState.availableTags.containsKey(oldTag)) {
            return false;
        }
        newTag = newTag.trim();
        if (myState.availableTags.containsKey(newTag)) {
            return false;
        }

        // 更新标签信息，保持所有原有属性
        TagInfo tagInfo = myState.availableTags.remove(oldTag);
        tagInfo.name = newTag;
        tagInfo.timestamp = System.currentTimeMillis();
        // 保持所有颜色属性和创建顺序不变
        // colorHue, colorSaturation, colorBrightness, order 保持原值
        myState.availableTags.put(newTag, tagInfo);

        // 更新所有文件的标签
        for (Set<String> fileTags : myState.fileTagsMap.values()) {
            if (fileTags.remove(oldTag)) {
                fileTags.add(newTag);
            }
        }

        ProjectView.getInstance(project).refresh();
        return true;
    }

    public boolean deleteTag(String tag) {
        if (!myState.availableTags.containsKey(tag)) {
            return false;
        }

        myState.availableTags.remove(tag);

        for (Set<String> fileTags : myState.fileTagsMap.values()) {
            fileTags.remove(tag);
        }

        myState.fileTagsMap.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        ProjectView.getInstance(project).refresh();
        return true;
    }

    public Set<String> getFileTags(VirtualFile file) {
        return new HashSet<>(myState.fileTagsMap.getOrDefault(file.getPath(), new HashSet<>()));
    }

    public void setFileTags(VirtualFile file, Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            myState.fileTagsMap.remove(file.getPath());
        } else {
            Set<String> validTags = new HashSet<>(tags);
            validTags.retainAll(myState.availableTags.keySet());
            if (!validTags.isEmpty()) {
                myState.fileTagsMap.put(file.getPath(), validTags);
            } else {
                myState.fileTagsMap.remove(file.getPath());
            }
        }
        ProjectView.getInstance(project).refresh();
    }

    public TagInfo getTagInfo(String tagName) {
        return myState.availableTags.get(tagName);
    }
} 