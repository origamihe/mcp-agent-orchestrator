package com.mcp.tools.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * XLSX 生成器 — 基于 Apache POI 生成 Excel 表格。
 *
 * 输入格式（LLM JSON）：
 * {
 *   "title": "报表标题",
 *   "sheets": [
 *     {
 *       "name": "Sheet1",
 *       "headers": ["列1", "列2", "列3"],
 *       "rows": [["值1", "值2", "值3"], ...],
 *       "colWidths": [20, 30, 15]
 *     }
 *   ]
 * }
 */
@Slf4j
@Component
public class XlsxGeneratorTool implements DocumentGenerator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${xlsx.output.dir:./generated/xlsx}")
    private String outputDir;

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @PostConstruct
    public void init() {
        log.info("[XlsxGen] Output directory: {}", outputDir);
    }

    @Override
    public DocResult generate(String llmResponse, String userTitle) throws Exception {
        Path dir = Path.of(outputDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);

        JsonNode root = objectMapper.readTree(llmResponse);
        String title = root.has("title") ? root.get("title").asText() : userTitle;
        JsonNode sheets = root.has("sheets") ? root.get("sheets") : objectMapper.createArrayNode();

        String fileName = sanitizeFileName(title) + "_" + TS_FMT.format(LocalDateTime.now()) + ".xlsx";
        Path filePath = dir.resolve(fileName);

        try (Workbook workbook = new XSSFWorkbook()) {
            if (sheets.isEmpty()) {
                Sheet sheet = workbook.createSheet("Sheet1");
                fillSheetFromMarkdown(sheet, llmResponse);
            } else {
                for (JsonNode sheetNode : sheets) {
                    String sheetName = sheetNode.has("name") ? sheetNode.get("name").asText() : "Sheet";
                    Sheet sheet = workbook.createSheet(safeSheetName(sheetName));
                    populateSheet(sheet, sheetNode);
                }
            }

            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                workbook.write(fos);
            }
        }

        long size = Files.size(filePath);
        log.info("[XlsxGen] Generated: {} ({} bytes)", fileName, size);
        return new DocResult(fileName, "/files/xlsx/" + fileName,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", size,
                "XLSX 生成成功: " + fileName);
    }

    @Override
    public DocResult generateFromMarkdown(String markdownContent, String title) throws Exception {
        Path dir = Path.of(outputDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);

        String fileName = sanitizeFileName(title) + "_" + TS_FMT.format(LocalDateTime.now()) + ".xlsx";
        Path filePath = dir.resolve(fileName);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            fillSheetFromMarkdown(sheet, markdownContent);

            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                workbook.write(fos);
            }
        }

        long size = Files.size(filePath);
        return new DocResult(fileName, "/files/xlsx/" + fileName,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", size,
                "XLSX 生成成功: " + fileName);
    }

    @Override
    public boolean supports(String format) {
        return "xlsx".equalsIgnoreCase(format) || "excel".equalsIgnoreCase(format);
    }

    private void populateSheet(Sheet sheet, JsonNode sheetNode) {
        CellStyle headerStyle = createHeaderStyle(sheet.getWorkbook());
        CellStyle dataStyle = createDataStyle(sheet.getWorkbook());

        JsonNode headers = sheetNode.get("headers");
        JsonNode rows = sheetNode.get("rows");
        JsonNode colWidths = sheetNode.get("colWidths");

        int rowIdx = 0;

        if (headers != null && headers.isArray()) {
            Row headerRow = sheet.createRow(rowIdx++);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i).asText());
                cell.setCellStyle(headerStyle);
            }
        }

        if (rows != null && rows.isArray()) {
            for (JsonNode rowNode : rows) {
                Row dataRow = sheet.createRow(rowIdx++);
                if (rowNode.isArray()) {
                    for (int i = 0; i < rowNode.size(); i++) {
                        Cell cell = dataRow.createCell(i);
                        JsonNode cellValue = rowNode.get(i);
                        if (cellValue.isNumber()) {
                            cell.setCellValue(cellValue.asDouble());
                        } else {
                            cell.setCellValue(cellValue.asText());
                        }
                        cell.setCellStyle(dataStyle);
                    }
                }
            }
        }

        if (colWidths != null && colWidths.isArray()) {
            for (int i = 0; i < colWidths.size(); i++) {
                sheet.setColumnWidth(i, colWidths.get(i).asInt() * 256);
            }
        }
    }

    private void fillSheetFromMarkdown(Sheet sheet, String markdown) {
        CellStyle headerStyle = createHeaderStyle(sheet.getWorkbook());
        CellStyle dataStyle = createDataStyle(sheet.getWorkbook());

        String[] lines = markdown.split("\n");
        int rowIdx = 0;
        boolean headerWritten = false;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) continue;

            if (line.startsWith("|") && line.contains("|")) {
                String[] cells = line.split("\\|");
                java.util.List<String> filtered = new java.util.ArrayList<>();
                for (String cell : cells) {
                    String trimmed = cell.trim();
                    if (!trimmed.isEmpty() && !trimmed.matches("^[-:]+$")) {
                        filtered.add(trimmed);
                    }
                }
                if (filtered.isEmpty()) continue;

                Row row = sheet.createRow(rowIdx++);
                for (int i = 0; i < filtered.size(); i++) {
                    Cell cell = row.createCell(i);
                    cell.setCellValue(filtered.get(i));
                    cell.setCellStyle(!headerWritten ? headerStyle : dataStyle);
                }
                if (!headerWritten && !filtered.isEmpty()) {
                    headerWritten = true;
                }
            } else {
                Row row = sheet.createRow(rowIdx++);
                Cell cell = row.createCell(0);
                cell.setCellValue(line);
                cell.setCellStyle(dataStyle);
            }
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        return style;
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) return "document";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").substring(0, Math.min(name.length(), 50));
    }

    private String safeSheetName(String name) {
        return name.replaceAll("[\\\\/:*?\\[\\]]", "_").substring(0, Math.min(name.length(), 31));
    }
}