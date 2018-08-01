package com.ucloudlink.refact.business.log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Zip压缩单个文件
 * {@link ZipUtils#zip(String src, String dest)}
 * src:需要压缩的文件(包含完整文件路径)
 * dest:压缩生成的文件(包含完整文件路径)
 * 示例：ZipUtils.zip("/sdcard/uaflogs/UAF_logcat.log","/sdcard/uaflogs/test_APP_100@163.com_356166060460523_201608301509.zip")
 */
public class ZipUtils {
    private static final String TAG = "ZipUtils";
    private static final int BUFF_SIZE = 2 * 1024 * 1024; // 2M Byte

    public static boolean zip(String src, String dest) {
        // 提供了一个数据项压缩成一个ZIP归档输出流
        ZipOutputStream out = null;
        try {
            File outFile = new File(dest);// 源文件或者目录
            File fileOrDirectory = new File(src);// 压缩文件路径
            out = new ZipOutputStream(new FileOutputStream(outFile));
            // 如果此文件是一个文件，否则为false。
            if (fileOrDirectory.isFile()) {
                return zipFileOrDirectory(out, fileOrDirectory, "");
            } else {
                return false;
//                // 返回一个文件或空阵列。
//                File[] entries = fileOrDirectory.listFiles();
//                for (int i = 0; i < entries.length; i++) {
//                    // 递归压缩，更新curPaths
//                    zipFileOrDirectory(out, entries[i], "");
//                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            return false;
        } finally {
            // 关闭输出流
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    public static boolean zipFileOrDirectory(ZipOutputStream out,
                                             File fileOrDirectory, String curPath) {
        // 从文件中读取字节的输入流
        FileInputStream in = null;
        try {
            // 如果此文件是一个目录，否则返回false。
            if (!fileOrDirectory.isDirectory()) {
                // 压缩文件
                byte[] buffer = new byte[4096];
                int bytes_read;
                in = new FileInputStream(fileOrDirectory);
                // 实例代表一个条目内的ZIP归档
                ZipEntry entry = new ZipEntry(curPath
                        + fileOrDirectory.getName());
                // 条目的信息写入底层流
                out.putNextEntry(entry);
                while ((bytes_read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytes_read);
                }
                out.closeEntry();
                return true;
            } else {
                return false;
                // 压缩目录
//                File[] entries = fileOrDirectory.listFiles();
//                for (int i = 0; i < entries.length; i++) {
//                    // 递归压缩，更新curPaths
//                    zipFileOrDirectory(out, entries[i], curPath
//                            + fileOrDirectory.getName() + "/");
//                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            return false;
            // throw ex;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    /**
     * 压缩指定文件夹下所有文件，注意dest不能在该文件夹下。
     *
     * @param src
     * @param dest
     */
    public static void zipDir(String src, String dest) {
        // 提供了一个数据项压缩成一个ZIP归档输出流
        ZipOutputStream out = null;
        try {
            File outFile = new File(dest);// 源文件或者目录
            File fileOrDirectory = new File(src);// 压缩文件路径
            out = new ZipOutputStream(new FileOutputStream(outFile));
            // 如果此文件是一个文件，否则为false。
            if (fileOrDirectory.isFile()) {
                zipFileOrDirectory(out, fileOrDirectory, "");
            } else {
                // 返回一个文件或空阵列。
                File[] entries = fileOrDirectory.listFiles();
                for (int i = 0; i < entries.length; i++) {
                    // 递归压缩，更新curPaths
                    zipFileOrDirectory(out, entries[i], "");
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            // 关闭输出流
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    private static void zipSingle(ZipOutputStream out, File file) throws IOException {
        byte[] buffer = new byte[1024];
        if (file.exists()) {
            if (file.isDirectory()) {
                File[] list = file.listFiles();
                for (File f : list) {
                    zipSingle(out, f);
                }
            } else {
                FileInputStream fis = new FileInputStream(file);
                out.putNextEntry(new ZipEntry(file.getName()));
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
                out.closeEntry();
                fis.close();
            }
        }
    }

    /**
     * 压缩文件或目录列表
     */
    public static void zipFiles(ArrayList<String> files, String dest, boolean del) throws IOException {
        ZipOutputStream out = null;
        File outfile = null;

        outfile = new File(dest);
        out = new ZipOutputStream(new FileOutputStream(outfile));
        File src = null;
        for (String f : files) {
            src = new File(f);
            zipSingle(out, src);
            if (del) {
                src.delete();
            }
        }
        out.close();
    }

    /**
     * 压缩整个文件夹中的所有文件，生成指定名称的zip压缩包
     *
     * @param filepath 文件所在目录
     * @param zippath  压缩后zip文件名称
     * @param dirFlag  zip文件中第一层是否包含一级目录，true包含；false没有
     */
    public static void zipMultiFile(String filepath, String zippath, boolean dirFlag) {
        try {
            File file = new File(filepath);// 要被压缩的文件夹
            File zipFile = new File(zippath);
            ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(zipFile));
            if (file.isDirectory()) {
                File[] files = file.listFiles();
                for (File fileSec : files) {
                    if (dirFlag) {
                        recursionZip(zipOut, fileSec, file.getName() + File.separator);
                    } else {
                        recursionZip(zipOut, fileSec, "");
                    }
                }
            }
            zipOut.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static File zipMultiFile(List<File> files, String zipPath) {
        ZipOutputStream zipOut = null;
        InputStream input = null;
        try {
            File zipFile = new File(zipPath);
            zipOut = new ZipOutputStream(new FileOutputStream(zipFile));
            byte[] buf = new byte[2048];
            for (File f : files) {
                zipOut.putNextEntry(new ZipEntry(f.getPath()));
                input = new FileInputStream(f);
                int len;
                while ((len = input.read(buf)) != -1) {
                    zipOut.write(buf, 0, len);
                }
                input.close();
            }
            return zipFile;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (input != null) {
                    input.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                if (zipOut != null) {
                    zipOut.flush();
                    zipOut.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private static void recursionZip(ZipOutputStream zipOut, File file, String baseDir) throws Exception {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            for (File fileSec : files) {
                recursionZip(zipOut, fileSec, baseDir + file.getName() + File.separator);
            }
        } else {
            byte[] buf = new byte[1024];
            InputStream input = new FileInputStream(file);
            zipOut.putNextEntry(new ZipEntry(baseDir + file.getName()));
            int len;
            while ((len = input.read(buf)) != -1) {
                zipOut.write(buf, 0, len);
            }
            input.close();
        }
    }
}
