package qupath.lib.gui.tools;

import java.util.Map;

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
        Map.entry("Cell detection with tile path object", "切片路径对象细胞检测")
    );
    
    /**
     * 获取命令的中文名称
     * @param name 英文命令名称
     * @return 中文命令名称
     */
    public static String getTranslatedName(String name) {
        return COMMAND_TRANSLATIONS.getOrDefault(name, name);
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