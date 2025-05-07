package qupath.lib.gui.tools;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 工作流命令名称翻译工具类
 */
public class QuPathTranslator {
    
    // 命令名称翻译映射表
    private static final Map<String, String> COMMAND_TRANSLATIONS = Map.ofEntries(
        Map.entry("Set image type", "设置图像类型"),
        Map.entry("Set color deconvolution stains", "设置颜色反卷积染色"),
        Map.entry("Create annotations", "创建标注"),
        Map.entry("Detect cells", "检测细胞"),
        Map.entry("Classify objects", "分类对象"),
        Map.entry("Measure objects", "测量对象"),
        Map.entry("Create detection", "创建检测"),
        Map.entry("Create annotation", "创建标注"),
        Map.entry("Create measurement", "创建测量"),
        Map.entry("Create classification", "创建分类"),
        Map.entry("Create hierarchy", "创建层次结构"),
        Map.entry("Create ROI", "创建ROI"),
        Map.entry("Create path object", "创建路径对象"),
        Map.entry("Create tile", "创建切片"),
        Map.entry("Create tile object", "创建切片对象"),
        Map.entry("Create tile annotation", "创建切片标注"),
        Map.entry("Create tile detection", "创建切片检测"),
        Map.entry("Create tile measurement", "创建切片测量"),
        Map.entry("Create tile classification", "创建切片分类"),
        Map.entry("Create tile hierarchy", "创建切片层次结构"),
        Map.entry("Create tile ROI", "创建切片ROI"),
        Map.entry("Create tile path object", "创建切片路径对象"),
        Map.entry("Save TMA measurements", "保存TMA测量结果"),
        Map.entry("Save annotation measurements", "保存标注测量结果"),
        Map.entry("Save detection measurements", "保存检测测量结果"),
        Map.entry("Save cell measurements", "保存细胞测量结果"),
        Map.entry("Save tile measurements", "保存切片测量结果"),
        Map.entry("Delete all measurements", "删除所有测量结果"),
        Map.entry("Remove measurements", "移除测量结果"),
        Map.entry("Transform all objects", "转换所有对象"),
        Map.entry("Transform selected objects", "转换选中的对象"),
        Map.entry("Export TMA data", "导出TMA数据"),
        Map.entry("Relabel TMA grid", "重新标记TMA网格"),
        Map.entry("Delete child objects on bounds", "删除边界上的子对象"),
        Map.entry("Delete objects on bounds", "删除边界上的对象"),
        Map.entry("Create full image annotation", "创建全图像标注"),
        Map.entry("Reset TMA metadata", "重置TMA元数据"),
        Map.entry("Merge selected annotations", "合并选中的标注"),
        Map.entry("Duplicate selected annotations", "复制选中的标注"),
        Map.entry("Copy selected annotations to plane", "将选中的标注复制到平面"),
        Map.entry("Invert selected annotation", "反转选中的标注"),
        Map.entry("Select objects by classification", "按分类选择对象"),
        Map.entry("Delete all objects", "删除所有对象"),
        Map.entry("Remove TMA Grid", "移除TMA网格"),
        Map.entry("Delete detections", "删除检测结果"),
        Map.entry("Delete annotations", "删除标注"),
        Map.entry("Delete TMA grid", "删除TMA网格"),
        Map.entry("Add shape measurements", "添加形状测量"),
        Map.entry("Convert detections to points", "将检测结果转换为点"),
        Map.entry("Simplify selected annotations", "简化选中的标注"),
        Map.entry("Select objects on plane", "选择平面上的对象"),
        Map.entry("Select all objects", "选择所有对象"),
        Map.entry("Select objects by class", "按类别选择对象"),
        Map.entry("Reset selection", "重置选择"),
        Map.entry("Reset classifications", "重置分类"),
        Map.entry("Refresh duplicate IDs", "刷新重复ID"),
        Map.entry("Run object classifier", "运行对象分类器"),
        Map.entry("Density map find hotspots", "密度图查找热点"),
        Map.entry("Density map create annotations", "密度图创建标注"),
        Map.entry("Write density map image", "写入密度图图像"),
        Map.entry("Write prediction image", "写入预测图像"),
        Map.entry("Classify detections by centroid", "按质心分类检测结果"),
        Map.entry("Pixel classifier create detections", "像素分类器创建检测结果"),
        Map.entry("Pixel classifier create annotations", "像素分类器创建标注"),
        Map.entry("Pixel classifier measurements", "像素分类器测量"),
        Map.entry("Set detection intensity classifications", "设置检测强度分类"),
        Map.entry("Set cell intensity classifications", "设置细胞强度分类"),
        Map.entry("ImageJ script", "ImageJ脚本"),
        Map.entry("Smooth object features", "平滑对象特征"),
        Map.entry("Cell detection", "细胞检测"),
        Map.entry("Positive cell detection", "阳性细胞检测"),
        Map.entry("Watershed cell detection", "分水岭细胞检测"),
        Map.entry("Cell and membrane detection", "细胞和膜检测"),
        Map.entry("Default cell detection", "默认细胞检测"),
        Map.entry("Cell detection with membrane", "带膜细胞检测"),
        Map.entry("Cell detection with nuclear", "带核细胞检测"),
        Map.entry("Cell detection with cytoplasmic", "带胞质细胞检测"),
        Map.entry("Cell detection with hematoxylin", "苏木精细胞检测"),
        Map.entry("Cell detection with DAB", "DAB细胞检测"),
        Map.entry("Cell detection with eosin", "伊红细胞检测"),
        Map.entry("Cell detection with fluorescence", "荧光细胞检测"),
        Map.entry("Cell detection with brightfield", "明场细胞检测"),
        Map.entry("Cell detection with multiplex", "多重细胞检测"),
        Map.entry("Cell detection with z-stack", "Z轴堆叠细胞检测"),
        Map.entry("Cell detection with time series", "时间序列细胞检测"),
        Map.entry("Cell detection with tile", "切片细胞检测"),
        Map.entry("Cell detection with ROI", "ROI细胞检测"),
        Map.entry("Cell detection with annotation", "标注细胞检测"),
        Map.entry("Cell detection with detection", "检测细胞检测"),
        Map.entry("Cell detection with measurement", "测量细胞检测"),
        Map.entry("Cell detection with classification", "分类细胞检测"),
        Map.entry("Cell detection with hierarchy", "层次结构细胞检测"),
        Map.entry("Cell detection with path object", "路径对象细胞检测"),
        Map.entry("Cell detection with tile object", "切片对象细胞检测"),
        Map.entry("Cell detection with tile annotation", "切片标注细胞检测"),
        Map.entry("Cell detection with tile detection", "切片检测细胞检测"),
        Map.entry("Cell detection with tile measurement", "切片测量细胞检测"),
        Map.entry("Cell detection with tile classification", "切片分类细胞检测"),
        Map.entry("Cell detection with tile hierarchy", "切片层次结构细胞检测"),
        Map.entry("Cell detection with tile ROI", "切片ROI细胞检测"),
        Map.entry("Cell detection with tile path object", "切片路径对象细胞检测"),
        Map.entry("Set pixel size µm", "设置像素大小 µm"),
        Map.entry("Image type", "图像类型"),
        Map.entry("Brightfield (H-DAB)", "明场 (H-DAB)"),
        Map.entry("Name", "名称"),
        Map.entry("H-DAB default", "H-DAB 默认"),
        Map.entry("Stain 1", "染色 1"),
        Map.entry("Hematoxylin", "苏木精"),
        Map.entry("Values 1", "值 1"),
        Map.entry("Stain 2", "染色 2"),
        Map.entry("DAB", "DAB"),
        Map.entry("Values 2", "值 2"),
        Map.entry("Background", "背景"),
        Map.entry("H-DAB modified", "H-DAB 修改"),
        Map.entry("Type", "类型"),
        Map.entry("true", "是"),
        Map.entry("false", "否"),
        Map.entry("Annotation", "标注"),
        Map.entry("detectionImageBrightfield", "检测图像明场"),
        Map.entry("Hematoxylin OD", "苏木精光密度"),
        Map.entry("requestedPixelSizeMicrons", "请求的像素大小(微米)"),
        Map.entry("backgroundRadiusMicrons", "背景半径(微米)"),
        Map.entry("backgroundByReconstruction", "通过重建背景"),
        Map.entry("medianRadiusMicrons", "中值滤波器半径(微米)"),
        Map.entry("sigmaMicrons", "Sigma(微米)"),
        Map.entry("minAreaMicrons", "最小面积(微米)"),
        Map.entry("maxAreaMicrons", "最大面积(微米)"),
        Map.entry("threshold", "阈值"),
        Map.entry("maxBackground", "最大背景"),
        Map.entry("watershedPostProcess", "分水岭后处理"),
        Map.entry("excludeDAB", "排除DAB"),
        Map.entry("cellExpansionMicrons", "细胞扩展(微米)"),
        Map.entry("includeNuclei", "包含细胞核"),
        Map.entry("smoothBoundaries", "平滑边界"),
        Map.entry("makeMeasurements", "进行测量"),
        Map.entry("fwhmMicrons", "半高全宽(微米)"),
        Map.entry("smoothWithinClasses", "在类别内平滑"),
        Map.entry("pixelHeightMicrons", "像素高度(微米)"),
        Map.entry("pixelWidthMicrons", "像素宽度(微米)"),
        Map.entry("Tumor", "肿瘤"),
        Map.entry("Stroma", "间质"),
        Map.entry("Immune cells", "免疫细胞"),
        Map.entry("Ignore*", "忽略*"),
        Map.entry("Ignore", "忽略"),
        Map.entry("Image", "图像"),
        Map.entry("Necrosis", "坏疽"),
        Map.entry("Other", "其他"),
        Map.entry("Region*", "区域*"),
        Map.entry("Region", "区域"),
        Map.entry("Positive", "阳性"),
        Map.entry("Negative", "阴性"),
        Map.entry("Cell", "细胞"),
        
        // 以下是从untranslated_keywords.txt添加的翻译
        Map.entry("Area µm^2", "面积 µm^2"),
        Map.entry("Cell: Area", "细胞: 面积"),
        Map.entry("Cell: Circularity", "细胞: 圆度"),
        Map.entry("Cell: DAB OD max", "细胞: DAB光密度最大值"),
        Map.entry("Cell: DAB OD mean", "细胞: DAB光密度平均值"),
        Map.entry("Cell: DAB OD min", "细胞: DAB光密度最小值"),
        Map.entry("Cell: DAB OD std dev", "细胞: DAB光密度标准差"),
        Map.entry("Cell: Eccentricity", "细胞: 离心率"),
        Map.entry("Cell: Hematoxylin OD max", "细胞: 苏木精光密度最大值"),
        Map.entry("Cell: Hematoxylin OD mean", "细胞: 苏木精光密度平均值"),
        Map.entry("Cell: Hematoxylin OD min", "细胞: 苏木精光密度最小值"),
        Map.entry("Cell: Hematoxylin OD std dev", "细胞: 苏木精光密度标准差"),
        Map.entry("Cell: Max caliper", "细胞: 最大卡尺距离"),
        Map.entry("Cell: Min caliper", "细胞: 最小卡尺距离"),
        Map.entry("Cell: Perimeter", "细胞: 周长"),
        Map.entry("Centroid X µm", "质心X坐标 µm"),
        Map.entry("Centroid Y µm", "质心Y坐标 µm"),
        Map.entry("Classification", "分类"),
        Map.entry("Cytoplasm: DAB OD max", "细胞质: DAB光密度最大值"),
        Map.entry("Cytoplasm: DAB OD mean", "细胞质: DAB光密度平均值"),
        Map.entry("Cytoplasm: DAB OD min", "细胞质: DAB光密度最小值"),
        Map.entry("Cytoplasm: DAB OD std dev", "细胞质: DAB光密度标准差"),
        Map.entry("Cytoplasm: Hematoxylin OD max", "细胞质: 苏木精光密度最大值"),
        Map.entry("Cytoplasm: Hematoxylin OD mean", "细胞质: 苏木精光密度平均值"),
        Map.entry("Cytoplasm: Hematoxylin OD min", "细胞质: 苏木精光密度最小值"),
        Map.entry("Cytoplasm: Hematoxylin OD std dev", "细胞质: 苏木精光密度标准差"),
        Map.entry("Delete selected objects", "删除选中的对象"),
        Map.entry("Length µm", "长度 µm"),
        Map.entry("Nucleus/Cell area ratio", "细胞核/细胞面积比"),
        Map.entry("Nucleus: Area", "细胞核: 面积"),
        Map.entry("Nucleus: Circularity", "细胞核: 圆度"),
        Map.entry("Nucleus: DAB OD max", "细胞核: DAB光密度最大值"),
        Map.entry("Nucleus: DAB OD mean", "细胞核: DAB光密度平均值"),
        Map.entry("Nucleus: DAB OD min", "细胞核: DAB光密度最小值"),
        Map.entry("Nucleus: DAB OD range", "细胞核: DAB光密度范围"),
        Map.entry("Nucleus: DAB OD std dev", "细胞核: DAB光密度标准差"),
        Map.entry("Nucleus: DAB OD sum", "细胞核: DAB光密度总和"),
        Map.entry("Nucleus: Eccentricity", "细胞核: 离心率"),
        Map.entry("Nucleus: Hematoxylin OD max", "细胞核: 苏木精光密度最大值"),
        Map.entry("Nucleus: Hematoxylin OD mean", "细胞核: 苏木精光密度平均值"),
        Map.entry("Nucleus: Hematoxylin OD min", "细胞核: 苏木精光密度最小值"),
        Map.entry("Nucleus: Hematoxylin OD range", "细胞核: 苏木精光密度范围"),    
        Map.entry("Nucleus: Hematoxylin OD std dev", "细胞核: 苏木精光密度标准差"),
        Map.entry("Nucleus: Hematoxylin OD sum", "细胞核: 苏木精光密度总和"),
        Map.entry("Nucleus: Max caliper", "细胞核: 最大卡尺距离"),
        Map.entry("Nucleus: Min caliper", "细胞核: 最小卡尺距离"),
        Map.entry("Nucleus: Perimeter", "细胞核: 周长"),
        Map.entry("Num Detections", "检测数量"),
        Map.entry("Num Negative", "阴性数量"),
        Map.entry("Num Positive", "阳性数量"),
        Map.entry("Num Positive per mm^2", "每平方毫米阳性数量"),
        Map.entry("Object ID", "对象ID"),
        Map.entry("Object type", "对象类型"),
        Map.entry("Parent", "父对象"),
        Map.entry("Perimeter µm", "周长 µm"),
        Map.entry("Positive %", "阳性百分比")
    );
    
    // 用于记录未翻译的关键字
    private static final Set<String> untranslatedKeywords = new HashSet<>();
    private static final String UNTRANSLATED_FILE = "untranslated_keywords.txt";
    
    static {
        // 注册关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // saveUntranslatedKeywords();
        }));
    }
    
    /**
     * 获取命令的中文名称
     * @param name 英文命令名称
     * @return 中文命令名称
     */
    public static String getTranslatedName(String name) {
        if (name == null) {
            return null;
        }
        
        String translation = COMMAND_TRANSLATIONS.get(name);
        if (translation == null) {
            // 记录未翻译的关键字
            synchronized (untranslatedKeywords) {
                if (untranslatedKeywords.add(name)) {
                    logUntranslatedKeyword(name);
                }
            }
            return name;
        }
        return translation;
    }
    
    /**
     * 记录未翻译的关键字到文件
     * @param keyword 未翻译的关键字
     */
    private static void logUntranslatedKeyword(String keyword) {
        try {
            Path filePath = Paths.get(UNTRANSLATED_FILE);
            boolean fileExists = Files.exists(filePath);
            
            try (PrintWriter writer = new PrintWriter(new FileWriter(UNTRANSLATED_FILE, true))) {
                if (!fileExists) {
                    writer.println("// 未翻译的关键字列表");
                    writer.println("// 格式: 英文关键字");
                    writer.println();
                }
                writer.println(keyword);
            }
        } catch (IOException e) {
            System.err.println("无法写入未翻译关键字到文件: " + e.getMessage());
        }
    }
    
    /**
     * 保存未翻译的关键字到文件
     */
    private static void saveUntranslatedKeywords() {
        if (untranslatedKeywords.isEmpty()) {
            return;
        }
        
        try {
            // 清空文件内容
            try (PrintWriter writer = new PrintWriter(UNTRANSLATED_FILE)) {
                writer.println("// 未翻译的关键字列表");
                writer.println("// 格式: 英文关键字");
                writer.println();
            }
            
            // 写入新的内容
            try (PrintWriter writer = new PrintWriter(new FileWriter(UNTRANSLATED_FILE, true))) {
                // 按字母顺序排序并过滤
                untranslatedKeywords.stream()
                    .filter(keyword -> {
                        if (keyword == null || keyword.trim().isEmpty()) {
                            return false;
                        }
                        
                        String trimmed = keyword.trim();
                        
                        // 排除纯数字（包括小数和空格分隔的数字）
                        if (trimmed.matches("^[\\d\\s.]+$")) {
                            return false;
                        }
                        
                        // 排除中文
                        if (trimmed.matches(".*[\\u4e00-\\u9fa5].*")) {
                            return false;
                        }
                        
                        // 排除脚本语句
                        if (trimmed.contains("setImageType") || 
                            trimmed.contains("setColorDeconvolutionStains") ||
                            trimmed.contains("setPixelSizeMicrons") ||
                            trimmed.contains("runPlugin") ||
                            trimmed.contains("selectAnnotations") ||
                            trimmed.startsWith("'") || trimmed.endsWith("'") ||
                            trimmed.startsWith("\"") || trimmed.endsWith("\"")) {
                            return false;
                        }
                        
                        // 排除RGB值
                        if (trimmed.matches("^\\d+\\s+\\d+\\s+\\d+$")) {
                            return false;
                        }
                        
                        return true;
                    })
                    .sorted()
                    .forEach(writer::println);
            }
        } catch (IOException e) {
            System.err.println("无法写入未翻译关键字到文件: " + e.getMessage());
        }
    }
    
    /**
     * 添加新的翻译映射
     * @param english 英文命令名称
     * @param chinese 中文命令名称
     */
    public static void addTranslation(String english, String chinese) {
        // 注意：由于Map.ofEntries创建的Map是不可变的，这里需要修改实现方式
        // 可以使用HashMap或其他可变Map来实现
        // 这里仅作为示例，实际实现需要根据具体需求调整
        throw new UnsupportedOperationException("当前实现不支持动态添加翻译映射");
    }
} 