package com.hippo.ehviewer.util;




import android.content.Context;
import android.util.Base64;
import android.widget.Toast;

import com.hippo.ehviewer.R;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class GZIPUtils {

    private static final long MIB = 1024L * 1024L;

    /** Conservative limits for downloaded gallery archives. */
    static final ExtractionLimits DEFAULT_EXTRACTION_LIMITS =
            new ExtractionLimits(4096, 256L * MIB, 2L * 1024L * MIB, 1000.0d);

    static final class ExtractionLimits {
        final int maxEntries;
        final long maxEntryBytes;
        final long maxTotalBytes;
        final double maxCompressionRatio;

        ExtractionLimits(int maxEntries, long maxEntryBytes, long maxTotalBytes,
                double maxCompressionRatio) {
            if (maxEntries <= 0 || maxEntryBytes <= 0 || maxTotalBytes <= 0
                    || maxCompressionRatio <= 0.0d) {
                throw new IllegalArgumentException("Extraction limits must be positive");
            }
            this.maxEntries = maxEntries;
            this.maxEntryBytes = maxEntryBytes;
            this.maxTotalBytes = maxTotalBytes;
            this.maxCompressionRatio = maxCompressionRatio;
        }
    }


    /**
     *
     * 使用gzip进行压缩
     */
    public static String compress(String primStr) {
        if (primStr == null || primStr.length() == 0) {
            return primStr;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        GZIPOutputStream gzip = null;
        try {
            gzip = new GZIPOutputStream(out);
            gzip.write(primStr.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (gzip != null) {
                try {
                    gzip.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return new String(Base64.encode(out.toByteArray(),Base64.DEFAULT));
    }

    public static String uncompress(String compressedStr) {
        if (compressedStr == null) {
            return null;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayInputStream in = null;
        GZIPInputStream ginzip = null;
        byte[] compressed = null;
        String decompressed = null;
        try {
            compressed = Base64.decode(compressedStr.getBytes(),Base64.DEFAULT);
            in = new ByteArrayInputStream(compressed);
            ginzip = new GZIPInputStream(in);

            byte[] buffer = new byte[1024];
            int offset = -1;
            while ((offset = ginzip.read(buffer)) != -1) {
                out.write(buffer, 0, offset);
            }
            decompressed = out.toString();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (ginzip != null) {
                try {
                    ginzip.close();
                } catch (IOException e) {
                }
            }
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                }
            }
        }
        return decompressed;
    }

    /**
     * DeCompress the ZIP to the path
     * @param zipFileString  name of ZIP
     * @param outPathString   path to be unZIP
     * @throws Exception
     */
    public static boolean UnZipFolder(String zipFileString, String outPathString) {
        return UnZipFolder(zipFileString, outPathString, DEFAULT_EXTRACTION_LIMITS);
    }

    /**
     * Extracts an archive only after every entry has passed path and declared-size validation.
     * The destination must be new or empty so a failed extraction can be cleaned up without
     * deleting unrelated files.
     */
    static boolean UnZipFolder(String zipFileString, String outPathString,
            ExtractionLimits limits) {
        if (zipFileString == null || outPathString == null || limits == null) {
            return false;
        }

        File outputRoot = new File(outPathString);
        try {
            if (outputRoot.exists()) {
                if (!outputRoot.isDirectory()) {
                    return false;
                }
                File[] children = outputRoot.listFiles();
                if (children == null || children.length != 0) {
                    return false;
                }
            } else if (!outputRoot.mkdirs()) {
                return false;
            }

            outputRoot = outputRoot.getCanonicalFile();
            try (ZipFile zipFile = new ZipFile(zipFileString)) {
                List<ZipEntry> entries = preflight(zipFile, outputRoot, limits);
                extract(zipFile, entries, outputRoot, limits);
            }
            return true;
        } catch (IOException | SecurityException e) {
            deleteContents(outputRoot);
            return false;
        }
    }

    private static List<ZipEntry> preflight(ZipFile zipFile, File outputRoot,
            ExtractionLimits limits) throws IOException {
        List<ZipEntry> entries = new ArrayList<>();
        Set<String> targetPaths = new HashSet<>();
        long declaredTotal = 0L;
        Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
        while (enumeration.hasMoreElements()) {
            ZipEntry entry = enumeration.nextElement();
            if (entries.size() >= limits.maxEntries) {
                throw new ZipException("Archive contains too many entries");
            }

            File target = resolveEntry(outputRoot, entry);
            if (!targetPaths.add(target.getPath())) {
                throw new ZipException("Archive contains duplicate paths");
            }

            if (!entry.isDirectory()) {
                long size = entry.getSize();
                long compressedSize = entry.getCompressedSize();
                if (size > limits.maxEntryBytes) {
                    throw new ZipException("Archive entry exceeds size limit");
                }
                if (size >= 0L) {
                    if (declaredTotal > limits.maxTotalBytes - size) {
                        throw new ZipException("Archive exceeds total size limit");
                    }
                    declaredTotal += size;
                }
                if (size > 0L && compressedSize == 0L) {
                    throw new ZipException("Archive entry has an invalid compression ratio");
                }
                if (size > 0L && compressedSize > 0L
                        && (double) size / (double) compressedSize
                        > limits.maxCompressionRatio) {
                    throw new ZipException("Archive entry exceeds compression ratio limit");
                }
            }
            entries.add(entry);
        }
        return entries;
    }

    private static void extract(ZipFile zipFile, List<ZipEntry> entries, File outputRoot,
            ExtractionLimits limits) throws IOException {
        long totalWritten = 0L;
        byte[] buffer = new byte[16 * 1024];
        for (ZipEntry entry : entries) {
            File target = resolveEntry(outputRoot, entry);
            if (entry.isDirectory()) {
                if (!target.isDirectory() && !target.mkdirs()) {
                    throw new IOException("Unable to create archive directory");
                }
                continue;
            }

            File parent = target.getParentFile();
            if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
                throw new IOException("Unable to create archive parent directory");
            }
            long entryWritten = 0L;
            try (InputStream input = new BufferedInputStream(zipFile.getInputStream(entry));
                    OutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (entryWritten > limits.maxEntryBytes - read
                            || totalWritten > limits.maxTotalBytes - read) {
                        throw new ZipException("Archive expanded beyond configured limits");
                    }
                    output.write(buffer, 0, read);
                    entryWritten += read;
                    totalWritten += read;
                }
            }
        }
    }

    private static File resolveEntry(File outputRoot, ZipEntry entry) throws IOException {
        String name = entry.getName();
        if (name == null || name.isEmpty() || name.indexOf('\0') >= 0
                || name.startsWith("/") || name.startsWith("\\") || name.indexOf('\\') >= 0
                || (name.length() >= 2 && Character.isLetter(name.charAt(0))
                && name.charAt(1) == ':')) {
            throw new ZipException("Archive contains an unsafe path");
        }
        String[] segments = name.split("/", -1);
        for (String segment : segments) {
            if ("..".equals(segment)) {
                throw new ZipException("Archive path escapes destination");
            }
        }
        File target = new File(outputRoot, name).getCanonicalFile();
        if (!isWithinRoot(outputRoot, target)) {
            throw new ZipException("Archive path escapes destination");
        }
        return target;
    }

    private static boolean isWithinRoot(File root, File file) {
        String rootPath = root.getPath();
        String filePath = file.getPath();
        return filePath.equals(rootPath)
                || filePath.startsWith(rootPath.endsWith(File.separator)
                ? rootPath : rootPath + File.separator);
    }

    private static void deleteContents(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return;
        }
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                deleteContents(child);
            }
            child.delete();
        }
    }

    /**
     * Compress file and folder
     * @param srcFileString   file or folder to be Compress
     * @param zipFileString   the path name of result ZIP
     * @throws Exception
     */
    public static void ZipFolder(String srcFileString, String zipFileString)throws Exception {
        //create ZIP
        ZipOutputStream outZip = new ZipOutputStream(new FileOutputStream(zipFileString));
        //create the file
        File file = new File(srcFileString);
        //compress
        ZipFiles(file.getParent()+File.separator, file.getName(), outZip);
        //finish and close
        outZip.finish();
        outZip.close();
    }

    /**
     * compress files
     * @param folderString
     * @param fileString
     * @param zipOutputSteam
     * @throws Exception
     */
    private static void ZipFiles(String folderString, String fileString, ZipOutputStream zipOutputSteam)throws Exception{
        if(zipOutputSteam == null)
            return;
        File file = new File(folderString+fileString);
        if (file.isFile()) {
            ZipEntry zipEntry =  new ZipEntry(fileString);
            FileInputStream inputStream = new FileInputStream(file);
            zipOutputSteam.putNextEntry(zipEntry);
            int len;
            byte[] buffer = new byte[4096];
            while((len=inputStream.read(buffer)) != -1)
            {
                zipOutputSteam.write(buffer, 0, len);
            }
            zipOutputSteam.closeEntry();
        }
        else {
            //folder
            String fileList[] = file.list();
            //no child file and compress
            if (fileList.length <= 0) {
                ZipEntry zipEntry =  new ZipEntry(fileString+File.separator);
                zipOutputSteam.putNextEntry(zipEntry);
                zipOutputSteam.closeEntry();
            }
            //child files and recursion
            for (int i = 0; i < fileList.length; i++) {
                ZipFiles(folderString, fileString+java.io.File.separator+fileList[i], zipOutputSteam);
            }//end of for
        }
    }

    /**
     * return the InputStream of file in the ZIP
     * @param zipFileString  name of ZIP
     * @param fileString     name of file in the ZIP
     * @return InputStream
     * @throws Exception
     */
    public static InputStream UpZip(String zipFileString, String fileString)throws Exception {
        ZipFile zipFile = new ZipFile(zipFileString);
        ZipEntry zipEntry = zipFile.getEntry(fileString);
        return zipFile.getInputStream(zipEntry);
    }

    /**
     * return files list(file and folder) in the ZIP
     * @param zipFileString     ZIP name
     * @param bContainFolder    contain folder or not
     * @param bContainFile      contain file or not
     * @return
     * @throws Exception
     */
    public static List<File> GetFileList(String zipFileString, boolean bContainFolder, boolean bContainFile)throws Exception {
        List<File> fileList = new ArrayList<>();
        ZipInputStream inZip = new ZipInputStream(new FileInputStream(zipFileString));
        ZipEntry zipEntry;
        String szName;
        while ((zipEntry = inZip.getNextEntry()) != null) {
            szName = zipEntry.getName();
            if (zipEntry.isDirectory()) {
                // get the folder name of the widget
                szName = szName.substring(0, szName.length() - 1);
                File folder = new File(szName);
                if (bContainFolder) {
                    fileList.add(folder);
                }

            } else {
                File file = new File(szName);
                if (bContainFile) {
                    fileList.add(file);
                }
            }
        }
        inZip.close();
        return fileList;
    }

}
