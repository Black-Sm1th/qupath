package qupath.lib.common;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Scanner;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class LicenseManager {
    private static final Logger logger = LoggerFactory.getLogger(LicenseManager.class);
    private static final String LICENSE_FILE_NAME = "QuPath_license.lic";
    private static final byte[] ENCRYPTION_KEY = "0SHe/LZpEd80lHqLyDKAfY9H5RvIPG9ZhSIMqxrJVc4=".getBytes(StandardCharsets.UTF_8);
    private static final String SOFTWARE_NAME = "QuPath";
    private static final Gson gson = new Gson();

    public static class LicenseInfo {
        private final LicenseStatus status;
        private final String expiryDate;
        private final String message;

        public LicenseInfo(LicenseStatus status, String expiryDate, String message) {
            this.status = status;
            this.expiryDate = expiryDate;
            this.message = message;
        }

        public LicenseStatus getStatus() {
            return status;
        }

        public String getExpiryDate() {
            return expiryDate;
        }

        public String getMessage() {
            return message;
        }
    }

    public enum LicenseStatus {
        NOT_FOUND("未找到许可证文件"),
        INVALID_FORMAT("许可证格式无效"),
        MACHINE_MISMATCH("许可证与当前机器不匹配"),
        EXPIRED("许可证已过期"),
        SOFTWARE_MISMATCH("不是当前软件的许可证"),
        VALID("许可证验证成功");

        private final String message;

        LicenseStatus(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    public static LicenseInfo checkLicenseStatus() {
        try {
            // 获取当前机器UUID
            String currentUuid = getMachineUuid();
            if (currentUuid == null) {
                return new LicenseInfo(LicenseStatus.MACHINE_MISMATCH, "", "无法获取机器UUID");
            }

            // 检查许可证文件
            File licenseFile = getLicenseFile();
            if (!licenseFile.exists()) {
                return new LicenseInfo(LicenseStatus.NOT_FOUND, "", "未找到许可证文件");
            }

            return validateLicenseFile(licenseFile, currentUuid);
        } catch (Exception e) {
            logger.error("许可证验证失败: " + e.getMessage());
            return new LicenseInfo(LicenseStatus.INVALID_FORMAT, "", "许可证验证失败: " + e.getMessage());
        }
    }

    private static LicenseInfo validateLicenseFile(File licenseFile, String currentUuid) {
        try {
            // 读取并解密许可证
            String licenseContent = new String(Files.readAllBytes(licenseFile.toPath()));
            String decryptedContent = decryptData(Base64.getDecoder().decode(licenseContent));
            
            // 解析JSON格式的许可证内容
            JsonObject licenseJson = gson.fromJson(decryptedContent, JsonObject.class);
            if (licenseJson == null || !licenseJson.has("uuid") || !licenseJson.has("extension_name") || !licenseJson.has("expiry_date")) {
                return new LicenseInfo(LicenseStatus.INVALID_FORMAT, "", "许可证格式无效");
            }

            String licenseUuid = licenseJson.get("uuid").getAsString();
            String softwareName = licenseJson.get("extension_name").getAsString();
            String expiryDate = licenseJson.get("expiry_date").getAsString();

            // 验证软件名称
            if (!SOFTWARE_NAME.equals(softwareName)) {
                return new LicenseInfo(LicenseStatus.SOFTWARE_MISMATCH, "", "不是当前软件的许可证");
            }
            // 验证UUID
            if (!currentUuid.equals(licenseUuid)) {
                return new LicenseInfo(LicenseStatus.MACHINE_MISMATCH, "", "许可证与当前机器不匹配");
            }

            // 验证过期时间
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date expiry = sdf.parse(expiryDate);
            if (expiry.before(new Date())) {
                return new LicenseInfo(LicenseStatus.EXPIRED, expiryDate, "许可证已过期");
            }

            return new LicenseInfo(LicenseStatus.VALID, expiryDate, "许可证验证成功");
        } catch (Exception e) {
            logger.error("许可证验证失败: " + e.getMessage());
            return new LicenseInfo(LicenseStatus.INVALID_FORMAT, "", e.getMessage());
        }
    }

    public static boolean importLicense() {
        JFileChooser fileChooser = new JFileChooser();
        // 设置默认打开桌面目录
        String desktopPath = System.getProperty("user.home") + File.separator + "Desktop";
        fileChooser.setCurrentDirectory(new File(desktopPath));
        fileChooser.setFileFilter(new FileNameExtensionFilter("许可证文件 (*.lic)", "lic"));
        fileChooser.setDialogTitle("选择许可证文件");
        
        if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                // 获取当前机器UUID
                String currentUuid = getMachineUuid();
                if (currentUuid == null) {
                    showError("无法获取机器UUID，请确保您有管理员权限。");
                    return false;
                }

                // 预验证许可证文件
                LicenseInfo licenseInfo = validateLicenseFile(selectedFile, currentUuid);
                if (licenseInfo.getStatus() != LicenseStatus.VALID) {
                    showError("许可证验证失败: " + licenseInfo.getMessage());
                    return false;
                }

                // 验证通过，复制许可证文件到当前目录
                Path targetPath = Paths.get(LICENSE_FILE_NAME);
                
                // 如果目标文件已存在，先删除
                if (Files.exists(targetPath)) {
                    Files.delete(targetPath);
                }
                
                // 复制文件
                Files.copy(selectedFile.toPath(), targetPath);
                
                // 设置文件为只读
                File targetFile = targetPath.toFile();
                targetFile.setReadOnly();
                
                // 在Windows系统上设置隐藏属性
                if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                    try {
                        Process process = Runtime.getRuntime().exec("attrib +h " + targetPath.toString());
                        process.waitFor();
                    } catch (Exception e) {
                        logger.warn("设置文件隐藏属性失败: " + e.getMessage());
                    }
                }
                
                logger.info("许可证文件已成功导入并设置为只读和隐藏");
                return true;
            } catch (IOException e) {
                logger.error("导入许可证失败: " + e.getMessage());
                showError("导入许可证失败: " + e.getMessage());
                return false;
            }
        }
        return false;
    }

    private static void showError(String message) {
        JOptionPane.showMessageDialog(null, 
            message, 
            "许可证错误", 
            JOptionPane.ERROR_MESSAGE);
    }

    private static String getMachineUuid() {
        // 尝试方法1：使用wmic命令
        try {
            Process process = Runtime.getRuntime().exec("wmic csproduct get uuid");
            Scanner scanner = new Scanner(process.getInputStream());
            scanner.nextLine(); // 跳过标题行
            String uuid = scanner.nextLine().trim();
            scanner.close();
            if (uuid != null && !uuid.isEmpty() && !uuid.equals("UUID")) {
                logger.info("通过wmic获取到的机器UUID: " + uuid);
                return uuid;
            }
        } catch (Exception e) {
            logger.error("wmic获取UUID失败: " + e.getMessage());
        }

        // 尝试方法2：使用powershell命令
        try {
            Process process = Runtime.getRuntime().exec("powershell -Command \"Get-CimInstance -ClassName Win32_ComputerSystemProduct | Select-Object -ExpandProperty UUID\"");
            Scanner scanner = new Scanner(process.getInputStream());
            String uuid = scanner.nextLine().trim();
            scanner.close();
            if (uuid != null && !uuid.isEmpty()) {
                logger.info("通过powershell获取到的机器UUID: " + uuid);
                return uuid;
            }
        } catch (Exception e) {
            logger.error("powershell获取UUID失败: " + e.getMessage());
        }
        return null;
    }

    private static File getLicenseFile() {
        // 在当前目录下查找许可证文件
        return new File(LICENSE_FILE_NAME);
    }

    private static String decryptData(byte[] data) {
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ ENCRYPTION_KEY[i % ENCRYPTION_KEY.length]);
        }
        return new String(result, StandardCharsets.UTF_8);
    }
} 