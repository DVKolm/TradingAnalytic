
package com.example.ta.service;

import com.example.ta.domain.Trade;
import com.example.ta.domain.TradeStatistics;
import com.example.ta.util.NumberFormatUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelExportService {

    private final TradeService tradeService;
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long RETRY_DELAY_MS = 1000;

    /**
     * Экспорт статистики торговли в Excel
     */
    public File exportTradingStatistics(TradeStatistics statistics, LocalDate startDate, LocalDate endDate) throws IOException {
        log.info("Начинаем экспорт статистики в Excel для периода {} - {}", startDate, endDate);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet statisticsSheet = workbook.createSheet("Статистика");
            createStatisticsSheet(statisticsSheet, statistics, workbook);

            Sheet tradesSheet = workbook.createSheet("Сделки");
            List<Trade> trades = tradeService.getClosedTradesForPeriod(startDate, endDate);
            createTradesSheet(tradesSheet, trades, workbook);

            File file = createExcelFileWithRetry(workbook, statistics);

            log.info("Excel отчет успешно создан: {}", file.getAbsolutePath());
            return file;
        }
    }

    /**
     * Создание файла Excel с повторными попытками при блокировке
     */
    private File createExcelFileWithRetry(Workbook workbook, TradeStatistics statistics) throws IOException {
        IOException lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return createExcelFileSecure(workbook, statistics, attempt);
            } catch (IOException e) {
                lastException = e;
                log.warn("Попытка {} из {} неудачна: {}", attempt, MAX_RETRY_ATTEMPTS, e.getMessage());

                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try {
                        log.info("Ожидание {} мс перед следующей попыткой...", RETRY_DELAY_MS);
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Процесс создания файла прерван", ie);
                    }
                }
            }
        }

        throw new IOException("Не удалось создать Excel файл после " + MAX_RETRY_ATTEMPTS + " попыток", lastException);
    }


    /**
     * Безопасное создание файла Excel с проверкой блокировки
     */
    private File createExcelFileSecure(Workbook workbook, TradeStatistics statistics, int attempt) throws IOException {
        String fileName = generateFileName(statistics);
        Path filePath = Path.of(System.getProperty("user.home"), fileName);

        // Если файл существует и это не первая попытка, создаем уникальное имя
        if (Files.exists(filePath) && attempt > 1) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH_mm_ss"));
            String baseFileName = fileName.substring(0, fileName.lastIndexOf('.'));
            String extension = fileName.substring(fileName.lastIndexOf('.'));
            fileName = baseFileName + "_" + timestamp + extension;
            filePath = Path.of(System.getProperty("user.home"), fileName);
        }

        // Если файл все еще существует, пытаемся его удалить
        if (Files.exists(filePath)) {
            try {
                log.info("Файл {} уже существует, попытка удаления...", filePath);
                Files.delete(filePath);
            } catch (IOException e) {
                log.warn("Не удалось удалить существующий файл: {}: {}", filePath, e.getMessage());
                // Создаем уникальное имя файла с временной меткой
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH_mm_ss"));
                String baseFileName = fileName.substring(0, fileName.lastIndexOf('.'));
                String extension = fileName.substring(fileName.lastIndexOf('.'));
                fileName = baseFileName + "_" + timestamp + extension;
                filePath = Path.of(System.getProperty("user.home"), fileName);
                log.info("Используем уникальное имя файла: {}", filePath);
            }
        }

        File file = filePath.toFile();

        // Используем try-with-resources для автоматического закрытия ресурсов
        try (FileOutputStream fos = new FileOutputStream(file);
             FileChannel channel = fos.getChannel()) {

            // Получаем эксклюзивную блокировку файла
            try (FileLock lock = channel.tryLock()) {
                if (lock == null) {
                    throw new IOException("Не удалось заблокировать файл для записи: " + filePath);
                }

                log.info("Файл успешно заблокирован для записи: {}", filePath);

                // Записываем workbook в поток
                workbook.write(fos);
                fos.flush(); // Принудительно сбрасываем буфер

                log.info("Excel файл успешно создан: {}", filePath);
                return file;
            }
        } catch (IOException e) {
            // Пытаемся удалить частично созданный файл
            try {
                if (Files.exists(filePath)) {
                    Files.delete(filePath);
                }
            } catch (IOException deleteEx) {
                log.warn("Не удалось удалить частично созданный файл: {}", filePath, deleteEx);
            }
            throw e;
        }
    }

    private String generateFileName(TradeStatistics statistics) {
        LocalDate now = LocalDate.now();
        String dateStr = now.format(DateTimeFormatter.ofPattern("yyyy_MM_dd"));

        if (statistics.getPeriodStart() != null && statistics.getPeriodEnd() != null) {
            String startStr = statistics.getPeriodStart().format(DateTimeFormatter.ofPattern("yyyy_MM_dd"));
            String endStr = statistics.getPeriodEnd().format(DateTimeFormatter.ofPattern("yyyy_MM_dd"));
            return String.format("trading_statistics_%s_%s_%s.xlsx", startStr, endStr, dateStr);
        } else {
            return String.format("trading_statistics_all_time_%s.xlsx", dateStr);
        }
    }


    /**
     * Создание листа со статистикой
     */
    private void createStatisticsSheet(Sheet sheet, TradeStatistics stats, Workbook workbook) {
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle dataStyle = createDataStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);
        CellStyle centeredDataStyle = createCenteredDataStyle(workbook);

        int rowNum = 0;

        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("📊 ОТЧЕТ ПО ТОРГОВОЙ СТАТИСТИКЕ");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));

        rowNum++;
        Row periodRow = sheet.createRow(rowNum++);
        periodRow.createCell(0).setCellValue("Период:");
        String periodText = stats.getPeriodType() != null ? stats.getPeriodType().getDisplayName() : "Выбранный период";
        if (stats.getPeriodStart() != null && stats.getPeriodEnd() != null) {
            periodText += String.format(" (%s - %s)",
                    stats.getPeriodStart().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                    stats.getPeriodEnd().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        }
        periodRow.createCell(1).setCellValue(periodText);

        Row dateRow = sheet.createRow(rowNum++);
        dateRow.createCell(0).setCellValue("Дата создания отчета:");
        dateRow.createCell(1).setCellValue(LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));

        rowNum++;

        Row headerRow = sheet.createRow(rowNum++);
        Cell headerCell = headerRow.createCell(0);
        headerCell.setCellValue("ОСНОВНЫЕ ПОКАЗАТЕЛИ");
        headerCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 1));

        addStatRow(sheet, rowNum++, "💼 Всего закрытых сделок",
                NumberFormatUtil.formatIntegerWithSpaces(stats.getTotalTrades()), centeredDataStyle);
        addStatRow(sheet, rowNum++, "💰 Общая прибыль",
                NumberFormatUtil.formatCurrencyWithSpaces(stats.getTotalProfit()), centeredDataStyle);
        addStatRow(sheet, rowNum++, "📊 Общий объем",
                NumberFormatUtil.formatCurrencyWithSpaces(stats.getTotalVolume()), centeredDataStyle);
        addStatRow(sheet, rowNum++, "✅ Прибыльных сделок",
                NumberFormatUtil.formatIntegerWithSpaces(stats.getWinningTrades()), centeredDataStyle);
        addStatRow(sheet, rowNum++, "❌ Убыточных сделок",
                NumberFormatUtil.formatIntegerWithSpaces(stats.getLosingTrades()), centeredDataStyle);
        addStatRow(sheet, rowNum++, "🎯 Процент успеха",
                NumberFormatUtil.formatPercentage(stats.getWinRate()), centeredDataStyle);
        addStatRow(sheet, rowNum++, "📈 Средняя прибыль",
                NumberFormatUtil.formatCurrencyWithSpaces(stats.getAvgProfit()), centeredDataStyle);
        addStatRow(sheet, rowNum++, "🚀 Максимальная прибыль",
                NumberFormatUtil.formatCurrencyWithSpaces(stats.getMaxProfit()), centeredDataStyle);
        addStatRow(sheet, rowNum++, "📉 Максимальный убыток",
                NumberFormatUtil.formatCurrencyWithSpaces(stats.getMaxLoss()), centeredDataStyle);

        setStatisticsColumnWidths(sheet);
    }

    /**
     * Создание листа с детальными сделками
     */
    private void createTradesSheet(Sheet sheet, List<Trade> trades, Workbook workbook) {
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dataStyle = createDataStyle(workbook);
        CellStyle currencyFormattedStyle = createCurrencyFormattedStyle(workbook);
        CellStyle dateStyle = createDateStyle(workbook);
        CellStyle textWrapStyle = createTextWrapStyle(workbook);

        int rowNum = 0;

        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {"№", "Актив", "Тип", "Статус", "Дата сделки",
                "Цена входа", "Цена выхода", "Объем", "Прибыль/Убыток",
                "Причина входа", "Причина выхода", "Комментарий"};

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        for (int i = 0; i < trades.size(); i++) {
            Trade trade = trades.get(i);
            Row row = sheet.createRow(rowNum++);

            row.createCell(0).setCellValue(i + 1); // Номер
            row.createCell(1).setCellValue(trade.getAssetName() != null ? trade.getAssetName() : "");
            row.createCell(2).setCellValue(trade.getTradeType() != null ? trade.getTradeType().name() : "");
            row.createCell(3).setCellValue(trade.getStatus() != null ? trade.getStatus().name() : "");

            Cell dateCell = row.createCell(4);
            if (trade.getTradeDate() != null) {
                dateCell.setCellValue(trade.getTradeDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
            }
            dateCell.setCellStyle(dateStyle);

            setFormattedCurrencyCell(row, 5, trade.getEntryPoint(), currencyFormattedStyle);
            setFormattedCurrencyCell(row, 6, trade.getExitPoint(), currencyFormattedStyle);
            setFormattedCurrencyCell(row, 7, trade.getVolumeInCurrency(), currencyFormattedStyle);
            setFormattedCurrencyCell(row, 8, trade.getProfitLoss(), currencyFormattedStyle);

            Cell entryReasonCell = row.createCell(9);
            entryReasonCell.setCellValue(trade.getEntryReason() != null ? trade.getEntryReason() : "");
            entryReasonCell.setCellStyle(textWrapStyle);

            Cell exitReasonCell = row.createCell(10);
            exitReasonCell.setCellValue(trade.getExitReason() != null ? trade.getExitReason() : "");
            exitReasonCell.setCellStyle(textWrapStyle);

            Cell commentCell = row.createCell(11);
            commentCell.setCellValue(trade.getComment() != null ? trade.getComment() : "");
            commentCell.setCellStyle(textWrapStyle);
        }

        setTradesColumnWidths(sheet);
    }

    /**
     * Устанавливает оптимальную ширину колонок для листа статистики
     */
    private void setStatisticsColumnWidths(Sheet sheet) {
        sheet.setColumnWidth(0, 8000); // ~40 символов
        sheet.setColumnWidth(1, 4000); // ~20 символов
        log.debug("Установлена ширина колонок для листа статистики");
    }

    /**
     * Устанавливает увеличенную ширину колонок для листа сделок с добавленными причинами
     */
    private void setTradesColumnWidths(Sheet sheet) {
        int[] columnWidths = {
                2500, 6000, 3500, 3500, 4000, 5500,
                5500, 5500, 6000, 10000, 10000, 8000
        };

        for (int i = 0; i < columnWidths.length; i++) {
            sheet.setColumnWidth(i, columnWidths[i]);
        }

        log.debug("Установлена расширенная ширина колонок для листа сделок");
    }

    /**
     * Добавить строку статистики
     */
    private void addStatRow(Sheet sheet, int rowNum, String label, String value, CellStyle valueStyle) {
        Row row = sheet.createRow(rowNum);

        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(createDataStyle(sheet.getWorkbook()));

        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(value);
        valueCell.setCellStyle(valueStyle);
    }

    /**
     * Установить ячейку с отформатированным валютным значением
     */
    private void setFormattedCurrencyCell(Row row, int cellNum, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(cellNum);
        if (value != null) {
            cell.setCellValue(NumberFormatUtil.formatCurrencyWithSpaces(value));
        } else {
            cell.setCellValue("0.00 $");
        }
        cell.setCellStyle(style);
    }

    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        font.setColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createCenteredDataStyle(Workbook workbook) {
        CellStyle style = createDataStyle(workbook);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createTextWrapStyle(Workbook workbook) {
        CellStyle style = createDataStyle(workbook);
        style.setWrapText(true);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        return style;
    }

    private CellStyle createCurrencyFormattedStyle(Workbook workbook) {
        CellStyle style = createDataStyle(workbook);
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }

    private CellStyle createCurrencyStyle(Workbook workbook) {
        CellStyle style = createDataStyle(workbook);
        style.setDataFormat(workbook.createDataFormat().getFormat("$#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }

    private CellStyle createDateStyle(Workbook workbook) {
        CellStyle style = createDataStyle(workbook);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }
}