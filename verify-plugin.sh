#!/bin/bash

# 从 build.gradle.kts 中读取版本号
VERSION=$(grep "version = " build.gradle.kts | cut -d'"' -f2)
# 插件路径
PLUGIN_PATH="build/distributions/jetbrains-file-tag-${VERSION}.zip"

# 创建临时目录
TEMP_DIR="temp"
IDE_DIR="$TEMP_DIR/ides"
mkdir -p "$IDE_DIR"

# 自动检测和设置 JAVA_HOME
if [ -z "$JAVA_HOME" ]; then
    if [ "$(uname)" == "Darwin" ]; then  # macOS
        # 尝试从 Homebrew 安装的 Java 获取
        if [ -d "/opt/homebrew/opt/openjdk@17" ]; then
            # 修复: 使用 readlink 获取实际路径
            JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
        elif [ -d "/Library/Java/JavaVirtualMachines" ]; then
            # 尝试从系统 Java 安装获取
            JAVA_PATH=$(find /Library/Java/JavaVirtualMachines -name 'jdk*17*' | head -n 1)
            if [ -n "$JAVA_PATH" ]; then
                export JAVA_HOME="$JAVA_PATH/Contents/Home"
            fi
        fi
    elif [ "$(expr substr $(uname -s) 1 5)" == "Linux" ]; then  # Linux
        # 尝试从常见位置获取
        if [ -d "/usr/lib/jvm/java-17-openjdk" ]; then
            export JAVA_HOME="/usr/lib/jvm/java-17-openjdk"
        elif [ -d "/usr/lib/jvm/java-17-openjdk-amd64" ]; then
            export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
        fi
    fi
fi

# 验证 JAVA_HOME 是否有效
if [ -z "$JAVA_HOME" ] || [ ! -d "$JAVA_HOME" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
    echo "Error: Could not determine JAVA_HOME automatically"
    echo "Please set JAVA_HOME environment variable to point to JDK 17"
    echo "Current JAVA_HOME: $JAVA_HOME"
    exit 1
fi

echo "Using JAVA_HOME: $JAVA_HOME"
echo "Java version:"
"$JAVA_HOME/bin/java" -version

# 更新到最新版本的验证器
VERIFIER_VERSION="1.381"
VERIFIER_DIR="tools/verifier"
VERIFIER_JAR="$VERIFIER_DIR/verifier-cli-${VERIFIER_VERSION}-all.jar"

# 检查验证器是否存在
if [ ! -f "$VERIFIER_JAR" ]; then
    echo "Error: Verifier jar not found at: $VERIFIER_JAR"
    echo "Please ensure the verifier jar is placed in the correct location"
    exit 1
fi

echo "Using verifier: $VERIFIER_JAR"

# 下载并解压 IDE
download_ide() {
    local version=$1
    local ide_path="$IDE_DIR/ideaIC-$version"
    
    if [ ! -d "$ide_path" ]; then
        echo "Downloading IntelliJ IDEA $version..."
        local download_url="https://download.jetbrains.com/idea/ideaIC-$version.tar.gz"
        echo "Download URL: $download_url"
        
        # 创建临时文件
        local temp_file="$TEMP_DIR/idea-$version.tar.gz"
        
        # 下载到临时文件
        if curl -L --fail "$download_url" -o "$temp_file"; then
            echo "Download successful, extracting..."
            mkdir -p "$IDE_DIR"
            
            # 解压到临时目录
            local temp_extract_dir="$IDE_DIR/temp_extract_$version"
            mkdir -p "$temp_extract_dir"
            tar xzf "$temp_file" -C "$temp_extract_dir" || {
                echo "Failed to extract IDE archive"
                rm -f "$temp_file"
                rm -rf "$temp_extract_dir"
                return 1
            }
            rm -f "$temp_file"
            
            # 查找解压后的目录
            local extracted_dir=$(find "$temp_extract_dir" -maxdepth 1 -type d -name "idea-IC*" -o -name "idea-IU*" | head -n 1)
            if [ -n "$extracted_dir" ]; then
                rm -rf "$ide_path"  # 确保目标目录不存在
                mv "$extracted_dir" "$ide_path"
                rm -rf "$temp_extract_dir"
                echo "IDE extracted to: $ide_path"
            else
                echo "Failed to find extracted IDE directory"
                rm -rf "$temp_extract_dir"
                return 1
            fi
        else
            echo "Failed to download IDE from $download_url"
            rm -f "$temp_file"
            return 1
        fi
    else
        echo "Using existing IDE at: $ide_path"
    fi
    
    # 验证目录是否存在且有效
    if [ ! -d "$ide_path" ] || [ ! -d "$ide_path/lib" ] || [ ! -f "$ide_path/bin/idea.sh" ]; then
        echo "Error: Invalid IDE installation at $ide_path"
        return 1
    fi
    
    # 返回绝对路径
    echo "$ide_path"
}

# 要验证的 IDE 版本
VERSIONS=(
    "2023.1.5"
    "2023.2.5"
    "2023.3.2"
)

# 清理旧的 IDE 目录
rm -rf "$IDE_DIR"/*

# 准备 IDE 路径数组
IDE_PATHS=()
for version in "${VERSIONS[@]}"; do
    echo "Processing IDE version: $version"
    if ide_path=$(download_ide "$version"); then
        if [ -d "$ide_path" ] && [ -d "$ide_path/lib" ] && [ -f "$ide_path/bin/idea.sh" ]; then
            IDE_PATHS+=("$ide_path")
            echo "Added IDE path: $ide_path"
        else
            echo "Warning: Invalid IDE installation at: $ide_path"
        fi
    else
        echo "Warning: Failed to process IDE version: $version"
    fi
done

# 确保至少有一个有效的 IDE 路径
if [ ${#IDE_PATHS[@]} -eq 0 ]; then
    echo "Error: No valid IDE paths found"
    exit 1
fi

# 运行验证
echo "Running plugin verification..."
echo "Using plugin: $PLUGIN_PATH"
echo "Using IDE paths:"
printf '%s\n' "${IDE_PATHS[@]}"
echo "Using JAVA_HOME: $JAVA_HOME"
echo "Using verifier: $VERIFIER_JAR"

# 创建验证报告目录
mkdir -p "build/reports/verification"

# 运行验证，确保每个 IDE 路径都是有效的目录
valid_ide_paths=()
for ide_path in "${IDE_PATHS[@]}"; do
    if [ -d "$ide_path" ]; then
        valid_ide_paths+=("$ide_path")
    else
        echo "Warning: Skipping invalid IDE path: $ide_path"
    fi
done

if [ ${#valid_ide_paths[@]} -eq 0 ]; then
    echo "Error: No valid IDE paths available for verification"
    exit 1
fi

# 运行验证
java -jar "$VERIFIER_JAR" check-plugin \
    "$PLUGIN_PATH" \
    "${valid_ide_paths[@]}" \
    -runtime-dir "$JAVA_HOME" \
    -verification-reports-dir "build/reports/verification" \
    -team-city || {
    echo "Plugin verification failed"
    exit 1
} 