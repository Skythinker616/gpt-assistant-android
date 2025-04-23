package com.skythinker.gptassistant;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;
import android.util.Xml;

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A lightweight utility class for parsing different types of documents and extracting text content.
 * Uses direct XML parsing instead of heavy libraries for Office formats.
 */
public class DocumentParser {
    private static final String TAG = "DocumentParser";
    private final Context context;
    private final ExecutorService executor;

    /**
     * Callback interface for document parsing results
     */
    public interface ParseCallback {
        void onParseSuccess(String text);
        void onParseError(Exception e);
    }

    /**
     * Constructor
     * @param context Application context for accessing ContentResolver
     */
    public DocumentParser(Context context) {
        this.context = context;
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * Parse a document from a given URI
     * @param documentUri URI of the document to parse
     * @param mimeType MIME type of the document
     * @param callback Callback to handle results
     */
    public void parseDocument(Uri documentUri, String mimeType, ParseCallback callback) {
        if (documentUri == null || mimeType == null) {
            callback.onParseError(new IllegalArgumentException("Document URI or MIME type cannot be null"));
            return;
        }

        executor.execute(() -> {
            try {
                String result;
                if (mimeType.equals("application/pdf")) {
                    result = parsePdf(documentUri);
                } else if (mimeType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {
                    result = parseWord(documentUri);
                } else if (mimeType.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation")) {
                    result = parsePowerPoint(documentUri);
                } else if (mimeType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {
                    result = parseExcel(documentUri);
                } else if (mimeType.equals("text/plain")) {
                    result = parseTextFile(documentUri);
                } else {
                    throw new UnsupportedOperationException("Unsupported document type: " + mimeType);
                }
                callback.onParseSuccess(result);
            } catch (Exception e) {
                Log.e(TAG, "Error parsing document", e);
                callback.onParseError(e);
            }
        });
    }

    /**
     * Parse Word documents (DOC and DOCX)
     */
    private String parseWord(Uri documentUri) throws IOException {
        String fileName = getFileNameFromUri(documentUri);
        if (fileName.toLowerCase().endsWith(".docx")) {
            return parseDocx(documentUri);
        } else {
            // For DOC files, we can't easily parse them without a library
            // Consider suggesting user to convert to DOCX
            throw new UnsupportedOperationException("Legacy DOC format parsing not supported. " +
                    "Please convert to DOCX format.");
        }
    }

    /**
     * Parse DOCX file (ZIP file containing XML)
     */
    private String parseDocx(Uri documentUri) throws IOException {
        StringBuilder text = new StringBuilder();
        InputStream inputStream = context.getContentResolver().openInputStream(documentUri);
        if (inputStream == null) {
            throw new IOException("Failed to open input stream for document");
        }

        try {
            ZipInputStream zipStream = new ZipInputStream(inputStream);
            ZipEntry zipEntry;

            while ((zipEntry = zipStream.getNextEntry()) != null) {
                if (zipEntry.getName().equals("word/document.xml")) {
                    XmlPullParser parser = Xml.newPullParser();
                    try {
                        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                        parser.setInput(zipStream, "UTF-8");

                        boolean inTextElement = false;
                        int eventType = parser.getEventType();

                        Log.d("DocumentParser", "Parsing document.xml");

                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            if (eventType == XmlPullParser.START_TAG && parser.getName().equals("w:t")) {
                                inTextElement = true;
                            } else if (eventType == XmlPullParser.END_TAG && parser.getName().equals("w:t")) {
                                inTextElement = false;
                            } else if (eventType == XmlPullParser.TEXT && inTextElement) {
                                text.append(parser.getText());
                                // Check if we should add space or newline
                                if (text.length() > 0 && text.charAt(text.length() - 1) != ' ' &&
                                        text.charAt(text.length() - 1) != '\n') {
                                    text.append(" ");
                                }
                            } else if (eventType == XmlPullParser.END_TAG && parser.getName().equals("w:p")) {
                                // End of paragraph
                                text.append("\n");
                            }
                            eventType = parser.next();
                        }
                    } catch (XmlPullParserException e) {
                        throw new IOException("Error parsing DOCX XML", e);
                    }
                    break;
                }
            }
            zipStream.close();
        } finally {
            inputStream.close();
        }

        return text.toString().replaceAll("\r\n", "\n").replaceAll("\n{3,}", "\n\n");
    }

    /**
     * Parse Excel documents (XLS and XLSX)
     */
    private String parseExcel(Uri documentUri) throws IOException {
        String fileName = getFileNameFromUri(documentUri);
        if (fileName.toLowerCase().endsWith(".xlsx")) {
            return parseXlsx(documentUri);
        } else {
            // For XLS files, we can't easily parse them without a library
            throw new UnsupportedOperationException("Legacy XLS format parsing not supported. " +
                    "Please convert to XLSX format.");
        }
    }

    /**
     * Parse XLSX file (ZIP file containing XML)
     */
    private String parseXlsx(Uri documentUri) throws IOException {
        StringBuilder text = new StringBuilder();
        InputStream inputStream = context.getContentResolver().openInputStream(documentUri);
        if (inputStream == null) {
            throw new IOException("Failed to open input stream for document");
        }

        try {
            ZipInputStream zipStream = new ZipInputStream(inputStream);
            ZipEntry zipEntry;

            // First, extract the shared strings table if it exists
            List<String> sharedStrings = new ArrayList<>();
            while ((zipEntry = zipStream.getNextEntry()) != null) {
                if (zipEntry.getName().equals("xl/sharedStrings.xml")) {
                    XmlPullParser parser = Xml.newPullParser();
                    try {
                        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                        parser.setInput(zipStream, "UTF-8");

                        boolean inSIElement = false;
                        boolean inTElement = false;
                        StringBuilder currentString = new StringBuilder();
                        int eventType = parser.getEventType();

                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            if (eventType == XmlPullParser.START_TAG) {
                                if (parser.getName().equals("si")) {
                                    inSIElement = true;
                                    currentString = new StringBuilder();
                                } else if (inSIElement && parser.getName().equals("t")) {
                                    inTElement = true;
                                }
                            } else if (eventType == XmlPullParser.END_TAG) {
                                if (parser.getName().equals("si")) {
                                    inSIElement = false;
                                    sharedStrings.add(currentString.toString());
                                } else if (parser.getName().equals("t")) {
                                    inTElement = false;
                                }
                            } else if (eventType == XmlPullParser.TEXT && inTElement) {
                                currentString.append(parser.getText());
                            }
                            eventType = parser.next();
                        }
                    } catch (XmlPullParserException e) {
                        throw new IOException("Error parsing XLSX shared strings", e);
                    }
                    break;
                }
            }

            // Reopen the ZIP to process worksheets
            inputStream.close();
            inputStream = context.getContentResolver().openInputStream(documentUri);
            zipStream = new ZipInputStream(inputStream);

            while ((zipEntry = zipStream.getNextEntry()) != null) {
                if (zipEntry.getName().startsWith("xl/worksheets/sheet") &&
                        zipEntry.getName().endsWith(".xml")) {

                    // Extract sheet name from path
                    String sheetName = zipEntry.getName().substring(zipEntry.getName().lastIndexOf("/") + 1);
                    sheetName = sheetName.replace("sheet", "Sheet ").replace(".xml", "");
                    text.append(sheetName).append(":\n");

                    // Parse the worksheet
                    XmlPullParser parser = Xml.newPullParser();
                    try {
                        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                        parser.setInput(zipStream, "UTF-8");

                        boolean inRowElement = false;
                        boolean inCellElement = false;
                        boolean inValueElement = false;
                        String cellType = "";
                        String cellValue = "";
                        int eventType = parser.getEventType();

                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            if (eventType == XmlPullParser.START_TAG) {
                                String tagName = parser.getName();
                                if (tagName.equals("row")) {
                                    inRowElement = true;
                                } else if (inRowElement && tagName.equals("c")) {
                                    inCellElement = true;
                                    cellType = "";
                                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                                        if (parser.getAttributeName(i).equals("t")) {
                                            cellType = parser.getAttributeValue(i);
                                            break;
                                        }
                                    }
                                } else if (inCellElement && tagName.equals("v")) {
                                    inValueElement = true;
                                }
                            } else if (eventType == XmlPullParser.END_TAG) {
                                String tagName = parser.getName();
                                if (tagName.equals("row")) {
                                    inRowElement = false;
                                    text.append("\n");
                                } else if (tagName.equals("c")) {
                                    inCellElement = false;
                                    text.append("\t");
                                } else if (tagName.equals("v")) {
                                    inValueElement = false;
                                    // Process the cell value
                                    if ("s".equals(cellType) && !cellValue.isEmpty()) {
                                        try {
                                            int index = Integer.parseInt(cellValue);
                                            if (index >= 0 && index < sharedStrings.size()) {
                                                text.append(sharedStrings.get(index));
                                            }
                                        } catch (NumberFormatException e) {
                                            text.append(cellValue);
                                        }
                                    } else {
                                        text.append(cellValue);
                                    }
                                    cellValue = "";
                                }
                            } else if (eventType == XmlPullParser.TEXT && inValueElement) {
                                cellValue = parser.getText();
                            }
                            eventType = parser.next();
                        }
                    } catch (XmlPullParserException e) {
                        throw new IOException("Error parsing XLSX worksheet", e);
                    }

                    text.append("\n\n");
                }
            }
            zipStream.close();
        } finally {
            inputStream.close();
        }

        return text.toString().replaceAll("\r\n", "\n").replaceAll("\n{3,}", "\n\n");
    }

    /**
     * Parse PowerPoint documents (PPT and PPTX)
     */
    private String parsePowerPoint(Uri documentUri) throws IOException {
        String fileName = getFileNameFromUri(documentUri);
        if (fileName.toLowerCase().endsWith(".pptx")) {
            return parsePptx(documentUri);
        } else {
            // For PPT files, we can't easily parse them without a library
            throw new UnsupportedOperationException("Legacy PPT format parsing not supported. " +
                    "Please convert to PPTX format.");
        }
    }

    /**
     * Parse PPTX file (ZIP file containing XML)
     */
    private String parsePptx(Uri documentUri) throws IOException {
        StringBuilder text = new StringBuilder();
        InputStream inputStream = context.getContentResolver().openInputStream(documentUri);
        if (inputStream == null) {
            throw new IOException("Failed to open input stream for document");
        }

        try {
            ZipInputStream zipStream = new ZipInputStream(inputStream);
            ZipEntry zipEntry;
            int slideNumber = 0;

            while ((zipEntry = zipStream.getNextEntry()) != null) {
                if (zipEntry.getName().matches("ppt/slides/slide[0-9]+\\.xml")) {
                    slideNumber++;
                    text.append("Slide ").append(slideNumber).append(":\n");

                    XmlPullParser parser = Xml.newPullParser();
                    try {
                        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                        parser.setInput(zipStream, "UTF-8");

                        boolean inTextElement = false;
                        int eventType = parser.getEventType();

                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            if (eventType == XmlPullParser.START_TAG && parser.getName().equals("a:t")) {
                                inTextElement = true;
                            } else if (eventType == XmlPullParser.END_TAG && parser.getName().equals("a:t")) {
                                inTextElement = false;
                                text.append("\n");
                            } else if (eventType == XmlPullParser.TEXT && inTextElement) {
                                text.append(parser.getText());
                            }
                            eventType = parser.next();
                        }
                    } catch (XmlPullParserException e) {
                        throw new IOException("Error parsing PPTX slide", e);
                    }

                    text.append("\n");
                }
            }
            zipStream.close();
        } finally {
            inputStream.close();
        }

        return text.toString().replaceAll("\r\n", "\n").replaceAll("\n{3,}", "\n\n");
    }

    /**
     * Parse PDF documents
     */
    private String parsePdf(Uri documentUri) throws IOException {
        StringBuilder text = new StringBuilder();
        InputStream inputStream = context.getContentResolver().openInputStream(documentUri);
        if (inputStream == null) {
            throw new IOException("Failed to open input stream for document");
        }

        // Create a temporary file to store the PDF
        File tempFile = null;
        try {
            tempFile = File.createTempFile("temp_pdf", ".pdf", context.getCacheDir());
            FileOutputStream fos = new FileOutputStream(tempFile);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
            fos.close();
            inputStream.close();

            // Extract text using PdfReader
            PdfReader reader = new PdfReader(tempFile.getAbsolutePath());

            for (int i = 1; i <= reader.getNumberOfPages(); i++) {
                text.append(PdfTextExtractor.getTextFromPage(reader, i)).append("\n\n");
            }

            reader.close();
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }

        return text.toString().replaceAll("\r\n", "\n").replaceAll("\n{3,}", "\n\n");
    }

    /**
     * Parse plain text files
     */
    private String parseTextFile(Uri documentUri) throws IOException {
        StringBuilder text = new StringBuilder();
        InputStream inputStream = context.getContentResolver().openInputStream(documentUri);
        if (inputStream == null) {
            throw new IOException("Failed to open input stream for document");
        }

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append("\n");
            }

            reader.close();
        } finally {
            inputStream.close();
        }

        return text.toString();
    }

    /**
     * Get filename from URI
     */
    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (columnIndex >= 0) {
                        result = cursor.getString(columnIndex);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting filename from URI", e);
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    /**
     * Clean up resources when no longer needed
     */
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}
