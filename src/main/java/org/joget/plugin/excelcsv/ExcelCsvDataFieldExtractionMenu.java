package org.joget.plugin.excelcsv;

import au.com.bytecode.opencsv.CSVReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormDataDeletableBinder;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormService;
import org.joget.apps.form.service.FormUtil;
import org.joget.apps.userview.model.PwaOfflineNotSupported;
import org.joget.apps.userview.model.UserviewMenu;
import org.joget.commons.util.FileLimitException;
import org.joget.commons.util.FileStore;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.ResourceBundleUtil;
import org.joget.commons.util.StringUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.util.WorkflowUtil;
import org.mozilla.universalchardet.Constants;
import org.mozilla.universalchardet.UniversalDetector;
import org.springframework.web.multipart.MultipartFile;

public class ExcelCsvDataFieldExtractionMenu extends UserviewMenu implements PwaOfflineNotSupported {

    public static final String CATEGORY_MARKETPLACE = "Marketplace";
    private static final int PREVIEW_PAGE_SIZE = 10;
    private static final String SESSION_UPLOADED_ATTR = ExcelCsvDataFieldExtractionMenu.class.getName() + ".uploaded";
    private static final String SESSION_UPLOADED_FILENAME = "filenameUpper";
    private static final String SESSION_UPLOADED_DISPLAY_FILENAME = "displayFilename";
    private static final String SESSION_UPLOADED_BYTES = "bytes";

    public static final String MODE_NEW = "NEW";
    public static final String MODE_NEW_UPDATE = "NEW & UPDATE";
    public static final String MODE_DELETE = "DELETE";

    private AppService appService;
    private FormService formService;
    private FormDataDao formDataDao;

    private Form form;

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getLabel() {
        return "Excel/CSV Data Import";
    }

    @Override
    public String getIcon() {
        return "<i class=\"fas fa-file-import\"></i>";
    }

    public String getName() {
        return "Excel/CSV Data Import";
    }

    public String getVersion() {
        return "5.0.0";
    }

    public String getDescription() {
        return "";
    }

    public String getPropertyOptions() {
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        String appId = appDef.getId();
        String appVersion = appDef.getVersion().toString();
        Object[] arguments = new Object[]{appId, appVersion};
        return AppUtil.readPluginResource(getClass().getName(), "/properties/userview/excelCsvDataFieldExtractionMenu.json", arguments, true, getClass().getName());
    }

    @Override
    public String getDecoratedMenu() {
        return null;
    }

    @Override
    public boolean isHomePageSupported() {
        return true;
    }

    @Override
    public String getCategory() {
        return CATEGORY_MARKETPLACE;
    }

    @Override
    public String getRenderPage() {
        String action = getRequestParameterString("action");

        String url = getUrl() + "?action=submit";
        setProperty("url", url);

        Map model = new HashMap();
        if ("submit".equals(action)) {
            HttpServletRequest request = WorkflowUtil.getHttpServletRequest();
            if (request != null && !"POST".equalsIgnoreCase(request.getMethod())) {
                PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
                return pluginManager.getPluginFreeMarkerTemplate(new HashMap(), getClass().getName(), "/templates/unauthorized.ftl", getClass().getName());
            }

            handleSubmit(model);
        } else {
            displayForm();
        }

        model.put("request", getRequestParameters());
        model.put("element", this);

        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        return pluginManager.getPluginFreeMarkerTemplate(model, getClass().getName(), "/templates/excelCsvDataFieldExtraction.ftl", getClass().getName());
    }

    protected void displayForm() {
        form = getForm();
        if (form == null) {
            PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
            setProperty("error", "true");
            setProperty("messageOnError", pluginManager.getMessage("userview.excelcsvdatafieldextraction.error.invalidForm", getClass().getName(), getClass().getName()));
        } else if (!(form.getLoadBinder() instanceof FormDataDeletableBinder)) {
            setProperty("disabledDelete", "true");
        }
        setProperty("view", "displayForm");
    }

    protected void handleSubmit(Map model) {
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        setProperty("hasExtractedData", "false");
        try {
            String doAction = getRequestParameterString("doAction");
            MultipartFile importFile;
            try {
                importFile = FileStore.getFile("csvImportFile");
            } catch (FileLimitException e) {
                setProperty("error", "true");
                setProperty("messageOnError", ResourceBundleUtil.getMessage("general.error.fileSizeTooLarge", new Object[]{FileStore.getFileSizeLimit()}));
                displayForm();
                return;
            }

            HttpServletRequest req = WorkflowUtil.getHttpServletRequest();
            HttpSession session = (req != null) ? req.getSession() : null;
            UploadedFile cached = readCachedUpload(session);

            boolean hasNewUpload = !(importFile == null || importFile.isEmpty());
            if (!hasNewUpload) {
                // Browsers clear file inputs after submit; allow import using previously previewed upload.
                if (("import".equalsIgnoreCase(doAction) || "preview".equalsIgnoreCase(doAction))
                        && cached != null && cached.bytes != null && cached.filenameUpper != null) {
                    // ok; use cached
                } else {
                    setProperty("error", "true");
                    setProperty("messageOnError", pluginManager.getMessage("userview.excelcsvdatafieldextraction.error.noFile", getClass().getName(), getClass().getName()));
                    displayForm();
                    return;
                }
            }

            String rawOriginalFilename = hasNewUpload ? importFile.getOriginalFilename() : cached.displayFilename;
            rawOriginalFilename = (rawOriginalFilename != null) ? rawOriginalFilename : "";
            String filenameUpper = hasNewUpload ? rawOriginalFilename.toUpperCase(Locale.ENGLISH) : cached.filenameUpper;
            if (!filenameUpper.endsWith(".CSV") && !filenameUpper.endsWith(".XLS") && !filenameUpper.endsWith(".XLSX")) {
                setProperty("error", "true");
                setProperty("messageOnError", pluginManager.getMessage("userview.excelcsvdatafieldextraction.error.invalidFileType", getClass().getName(), getClass().getName()));
                displayForm();
                return;
            }

            if ("true".equals(getPropertyString("checkUTF8"))) {
                InputStream utf8Stream = hasNewUpload ? importFile.getInputStream() : new ByteArrayInputStream(cached.bytes);
                if (!isValidUTF8(utf8Stream)) {
                    setProperty("error", "true");
                    setProperty("messageOnError", pluginManager.getMessage("userview.excelcsvdatafieldextraction.error.utf8", getClass().getName(), getClass().getName()));
                    displayForm();
                    return;
                }
            }

            // Cache the uploaded file for follow-up import (Preview -> Import).
            UploadedFile payload;
            if (hasNewUpload) {
                payload = new UploadedFile(filenameUpper, rawOriginalFilename, importFile.getBytes());
                writeCachedUpload(session, payload);
            } else {
                payload = cached;
            }
            model.put("uploadedFilename", payload.displayFilename);

            FileType fileType = FileType.fromFilenameUpper(filenameUpper);
            int sheetIndex = 0;
            List<String> sheetNames = new ArrayList<>();
            if (fileType == FileType.XLS || fileType == FileType.XLSX) {
                sheetNames = getExcelSheetNames(payload, fileType);
                sheetIndex = parseSheetIndex(getRequestParameterString("sheetIndex"), sheetNames.size());
                model.put("isExcelFile", "true");
                model.put("sheetNames", sheetNames);
                model.put("selectedSheetIndex", sheetIndex);
            } else {
                model.put("isExcelFile", "false");
            }

            // If user just selected a file (or changed it), we only need the sheet list.
            if ("sheets".equalsIgnoreCase(doAction)) {
                displayForm();
                return;
            }

            // Always extract for preview
            ExtractionResult extracted = extractForPreview(payload, fileType, sheetIndex);
            model.put("extractedHeaders", extracted.headers);

            int totalRows = extracted.rows.size();
            int previewPage = parsePreviewPage(getRequestParameterString("previewPage"));
            if ("import".equalsIgnoreCase(doAction)) {
                previewPage = 1;
            }
            int totalPages = Math.max(1, (int) Math.ceil(totalRows / (double) PREVIEW_PAGE_SIZE));
            if (previewPage > totalPages) {
                previewPage = totalPages;
            }
            if (previewPage < 1) {
                previewPage = 1;
            }
            int from = (previewPage - 1) * PREVIEW_PAGE_SIZE;
            int to = Math.min(from + PREVIEW_PAGE_SIZE, totalRows);
            List<List<String>> pageRows = new ArrayList<>();
            if (from < totalRows) {
                pageRows = new ArrayList<>(extracted.rows.subList(from, to));
            }

            model.put("extractedRows", pageRows);
            model.put("extractedRowCount", totalRows);
            model.put("extractedColumnCount", extracted.headers.size());
            model.put("previewPage", previewPage);
            model.put("previewTotalPages", totalPages);
            model.put("previewPageSize", PREVIEW_PAGE_SIZE);
            model.put("previewFromRow", totalRows == 0 ? 0 : (from + 1));
            model.put("previewToRow", to);
            setProperty("hasExtractedData", "true");

            // Optional import action
            if ("import".equalsIgnoreCase(doAction)) {
                importToForm(payload, fileType, sheetIndex, model);
                setProperty("view", "success");
            } else {
                displayForm();
            }
        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "");
            setProperty("error", "true");
            setProperty("messageOnError", e.getLocalizedMessage());
            displayForm();
        }
    }

    protected ExtractionResult extractForPreview(UploadedFile upload, FileType fileType, int sheetIndex) throws IOException {
        boolean firstRowHeader = !"false".equalsIgnoreCase(getPropertyString("treatFirstRowAsHeader"));

        List<List<String>> allRows;
        if (fileType == FileType.CSV) {
            allRows = extractCsvRows(upload);
        } else if (fileType == FileType.XLS) {
            allRows = extractXlsRows(upload, sheetIndex);
        } else {
            allRows = extractXlsxRows(upload, sheetIndex);
        }

        if (allRows.isEmpty()) {
            return new ExtractionResult(new ArrayList<>(), new ArrayList<>());
        }

        // normalize column count for display
        int maxCols = 0;
        for (List<String> r : allRows) {
            maxCols = Math.max(maxCols, r.size());
        }
        for (List<String> r : allRows) {
            while (r.size() < maxCols) {
                r.add("");
            }
        }

        List<String> headers;
        List<List<String>> rows;
        if (firstRowHeader) {
            headers = uniqueHeaders(allRows.get(0), maxCols);
            rows = new ArrayList<>(allRows.subList(1, allRows.size()));
        } else {
            headers = syntheticHeaders(maxCols);
            rows = new ArrayList<>(allRows);
        }
        rows.removeIf(ExcelCsvDataFieldExtractionMenu::isRowAllBlank);

        return new ExtractionResult(headers, rows);
    }

    protected void importToForm(UploadedFile upload, FileType fileType, int sheetIndex, Map model) throws IOException {
        Object[] columnMapping = (Object[]) getProperty("columnMapping");
        form = getForm();
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");

        if (form == null) {
            setProperty("error", "true");
            setProperty("messageOnError", pluginManager.getMessage("userview.excelcsvdatafieldextraction.error.invalidForm", getClass().getName(), getClass().getName()));
            displayForm();
            return;
        }

        if (MODE_DELETE.equals(getRequestParameterString("mode")) && !(form.getLoadBinder() instanceof FormDataDeletableBinder)) {
            setProperty("error", "true");
            setProperty("messageOnError", pluginManager.getMessage("deleteModeIsNotSupported", getClass().getName(), getClass().getName()));
            displayForm();
            return;
        }

        if (columnMapping == null || columnMapping.length == 0) {
            setProperty("error", "true");
            setProperty("messageOnError", pluginManager.getMessage("userview.excelcsvdatafieldextraction.error.noMapping", getClass().getName(), getClass().getName()));
            displayForm();
            return;
        }

        if (getPropertyString("rowStart").isEmpty()) {
            setProperty("rowStart", "0");
        }

        resetImportCounters();

        if (fileType == FileType.CSV) {
            importCsv(columnMapping, upload);
        } else if (fileType == FileType.XLS) {
            importXls(columnMapping, upload, sheetIndex);
        } else {
            importXlsx(columnMapping, upload, sheetIndex);
        }

        model.put("successImportedCount", successImportedCount);
        model.put("successImportedRows", StringUtils.join(successImportedRows, ", "));
        model.put("successUpdatedCount", successUpdatedCount);
        model.put("successUpdatedRows", StringUtils.join(successUpdatedRows, ", "));
        model.put("skippedCount", skippedCount);
        model.put("skippedRows", StringUtils.join(skippedRows, ", "));
        model.put("successDeletedCount", successDeletedCount);
        model.put("successDeletedRows", StringUtils.join(successDeletedRows, ", "));
        model.put("validationErrorCount", validationErrorCount);
        model.put("validationErrorRows", validationErrorRows);

        setProperty("mode", getRequestParameterString("mode"));
    }

    protected boolean isValidUTF8(InputStream fis) {
        boolean result = false;

        UniversalDetector detector = new UniversalDetector(null);
        byte[] buf = new byte[4096];
        try {
            int nread;
            while ((nread = fis.read(buf)) > 0 && !detector.isDone()) {
                detector.handleData(buf, 0, nread);
            }
            detector.dataEnd();

            String encoding = detector.getDetectedCharset();
            if (encoding != null && Constants.CHARSET_UTF_8.equals(encoding)) {
                result = true;
            }
        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "");
        } finally {
            detector.reset();
            try {
                fis.close();
            } catch (Exception e) {
            }
        }

        return result;
    }

    protected List<List<String>> extractCsvRows(UploadedFile upload) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        InputStream fis = new ByteArrayInputStream(upload.bytes);

        try {
            char delimiter = CSVReader.DEFAULT_SEPARATOR;
            char quote = CSVReader.DEFAULT_QUOTE_CHARACTER;

            if (!getPropertyString("customCsvDelimiter").isEmpty()) {
                delimiter = getPropertyString("customCsvDelimiter").charAt(0);
            }
            if (!getPropertyString("customCsvQuote").isEmpty()) {
                quote = getPropertyString("customCsvQuote").charAt(0);
            }

            CSVReader reader = new CSVReader(new InputStreamReader(fis, "UTF-8"), delimiter, quote);
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                List<String> line = new ArrayList<>();
                for (String cell : nextLine) {
                    line.add(cell != null ? cell : "");
                }
                rows.add(line);
            }
        } finally {
            try {
                fis.close();
            } catch (Exception e) {
            }
        }
        return rows;
    }

    protected List<List<String>> extractXlsRows(UploadedFile upload, int sheetIndex) throws IOException {
        InputStream fis = new ByteArrayInputStream(upload.bytes);
        try {
            HSSFWorkbook workbook = new HSSFWorkbook(fis);
            int idx = Math.min(Math.max(0, sheetIndex), Math.max(0, workbook.getNumberOfSheets() - 1));
            HSSFSheet sheet = workbook.getSheetAt(idx);
            return extractExcelSheetRows(sheet.iterator());
        } finally {
            try {
                fis.close();
            } catch (Exception e) {
            }
        }
    }

    protected List<List<String>> extractXlsxRows(UploadedFile upload, int sheetIndex) throws IOException {
        InputStream fis = new ByteArrayInputStream(upload.bytes);
        try {
            XSSFWorkbook workbook = new XSSFWorkbook(fis);
            int idx = Math.min(Math.max(0, sheetIndex), Math.max(0, workbook.getNumberOfSheets() - 1));
            XSSFSheet sheet = workbook.getSheetAt(idx);
            return extractExcelSheetRows(sheet.iterator());
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "");
            throw new IOException(e.getMessage(), e);
        } finally {
            try {
                fis.close();
            } catch (Exception e) {
            }
        }
    }

    protected List<List<String>> extractExcelSheetRows(Iterator<Row> rowIterator) {
        List<List<String>> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        while (rowIterator.hasNext()) {
            Row row = rowIterator.next();
            int last = row.getLastCellNum();
            if (last < 0) {
                continue;
            }
            List<String> cells = new ArrayList<>();
            for (int c = 0; c < last; c++) {
                Cell cell = row.getCell(c);
                cells.add(cell == null ? "" : formatter.formatCellValue(cell));
            }
            if (!isRowAllBlank(cells)) {
                rows.add(cells);
            }
        }
        return rows;
    }

    protected static boolean isRowAllBlank(List<String> row) {
        if (row == null || row.isEmpty()) {
            return true;
        }
        for (String cell : row) {
            if (cell != null && !cell.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    protected AppService getAppService() {
        if (appService == null) {
            appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
        }
        return appService;
    }

    protected FormService getFormService() {
        if (formService == null) {
            formService = (FormService) AppUtil.getApplicationContext().getBean("formService");
        }
        return formService;
    }

    protected FormDataDao getFormDataDao() {
        if (formDataDao == null) {
            formDataDao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
        }
        return formDataDao;
    }

    protected Form getForm() {
        FormDefinitionDao formDefinitionDao = (FormDefinitionDao) AppUtil.getApplicationContext().getBean("formDefinitionDao");
        String formDefId = getPropertyString("formDefId");
        if (formDefId != null) {
            AppDefinition appDef = AppUtil.getCurrentAppDefinition();
            FormDefinition formDef = formDefinitionDao.loadById(formDefId, appDef);
            if (formDef != null) {
                String formJson = formDef.getJson();
                if (formJson != null) {
                    return (Form) getFormService().createElementFromJson(formJson, true);
                }
            }
        }
        return null;
    }

    // ---- import logic (from original menu) ----
    private int successImportedCount = 0;
    private Collection<Integer> successImportedRows = new ArrayList<Integer>();
    private int successUpdatedCount = 0;
    private Collection<Integer> successUpdatedRows = new ArrayList<Integer>();
    private int skippedCount = 0;
    private Collection<Integer> skippedRows = new ArrayList<Integer>();
    private int successDeletedCount = 0;
    private Collection<Integer> successDeletedRows = new ArrayList<Integer>();
    private int validationErrorCount = 0;
    private Map<String, String> validationErrorRows = new LinkedHashMap<String, String>();

    protected void resetImportCounters() {
        successImportedCount = 0;
        successImportedRows.clear();
        successUpdatedCount = 0;
        successUpdatedRows.clear();
        skippedCount = 0;
        skippedRows.clear();
        successDeletedCount = 0;
        successDeletedRows.clear();
        validationErrorCount = 0;
        validationErrorRows.clear();
    }

    protected void importCsv(Object[] columnMapping, UploadedFile upload) throws IOException {
        InputStream fis = new ByteArrayInputStream(upload.bytes);

        try {
            char delimiter = CSVReader.DEFAULT_SEPARATOR;
            char quote = CSVReader.DEFAULT_QUOTE_CHARACTER;

            if (!getPropertyString("customCsvDelimiter").isEmpty()) {
                delimiter = getPropertyString("customCsvDelimiter").charAt(0);
            }
            if (!getPropertyString("customCsvQuote").isEmpty()) {
                quote = getPropertyString("customCsvQuote").charAt(0);
            }

            CSVReader reader = new CSVReader(new InputStreamReader(fis, "UTF-8"), delimiter, quote);
            String[] nextLine;

            int rowCount = 0;
            int start = 0;

            try {
                start = Integer.parseInt(getPropertyString("rowStart"));
            } catch (Exception e) {/* ignored */}
            start = getEffectiveImportStartRow(start);

            while ((nextLine = reader.readNext()) != null) {
                if (rowCount >= start) {
                    if (isRowAllBlank(nextLine)) {
                        rowCount++;
                        continue;
                    }
                    FormData formData = new FormData();

                    if (getPropertyString("key") != null && getPropertyString("key").trim().length() > 0) {
                        int colNum = Integer.parseInt(getPropertyString("key"));
                        if (colNum < nextLine.length && !nextLine[colNum].isEmpty()) {
                            formData.setPrimaryKeyValue(nextLine[colNum]);
                        }
                    }

                    for (Object o : columnMapping) {
                        Map column = (HashMap) o;
                        int colNum = Integer.parseInt(column.get("col").toString());
                        if (colNum < nextLine.length && !nextLine[colNum].isEmpty()) {
                            formData.addRequestParameterValues(column.get("field").toString(), new String[]{nextLine[colNum]});
                        }
                    }

                    if (getKey() != null && getKey().trim().length() > 0 && getPropertyString("userviewKey") != null && getPropertyString("userviewKey").trim().length() > 0) {
                        formData.addRequestParameterValues(getPropertyString("userviewKey"), new String[]{getKey()});
                    }

                    String paramName = FormUtil.getElementParameterName(form);
                    formData.addRequestParameterValues(paramName + "_IMPORTED", new String[]{"true"});

                    importData(rowCount, formData);
                }
                rowCount++;
            }
        } finally {
            try {
                fis.close();
            } catch (Exception e) {
            }
        }
    }

    protected void importXls(Object[] columnMapping, UploadedFile upload, int sheetIndex) throws IOException {
        InputStream fis = new ByteArrayInputStream(upload.bytes);
        try {
            HSSFWorkbook workbook = new HSSFWorkbook(fis);
            int idx = Math.min(Math.max(0, sheetIndex), Math.max(0, workbook.getNumberOfSheets() - 1));
            HSSFSheet sheet = workbook.getSheetAt(idx);

            Iterator<Row> rowIterator = sheet.iterator();
            iterateExcelData(columnMapping, rowIterator);
        } finally {
            try {
                fis.close();
            } catch (Exception e) {
            }
        }
    }

    protected void importXlsx(Object[] columnMapping, UploadedFile upload, int sheetIndex) throws IOException {
        InputStream fis = new ByteArrayInputStream(upload.bytes);
        try {
            XSSFWorkbook workbook = new XSSFWorkbook(fis);
            int idx = Math.min(Math.max(0, sheetIndex), Math.max(0, workbook.getNumberOfSheets() - 1));
            XSSFSheet sheet = workbook.getSheetAt(idx);

            Iterator<Row> rowIterator = sheet.iterator();
            iterateExcelData(columnMapping, rowIterator);
        } finally {
            try {
                fis.close();
            } catch (Exception e) {
            }
        }
    }

    protected void iterateExcelData(Object[] columnMapping, Iterator<Row> rowIterator) {
        int rowCount = 0;
        int start = 0;

        try {
            start = Integer.parseInt(getPropertyString("rowStart"));
        } catch (Exception e) {/* ignored */}
        start = getEffectiveImportStartRow(start);

        while (rowIterator.hasNext()) {
            Row row = rowIterator.next();
            if (rowCount >= start) {
                // skip completely empty rows
                if (row == null || isExcelRowAllBlank(row)) {
                    rowCount++;
                    continue;
                }
                FormData formData = new FormData();

                if (getPropertyString("key") != null && getPropertyString("key").trim().length() > 0) {
                    int colNum = Integer.parseInt(getPropertyString("key"));
                    if (row.getCell(colNum) != null) {
                        String pk = getExcelCellValue(row.getCell(colNum));
                        if (!pk.isEmpty()) {
                            formData.setPrimaryKeyValue(pk);
                        }
                    }
                }

                for (Object o : columnMapping) {
                    Map column = (HashMap) o;
                    int colNum = Integer.parseInt(column.get("col").toString());
                    if (row.getCell(colNum) != null) {
                        String value = getExcelCellValue(row.getCell(colNum));
                        if (!value.isEmpty()) {
                            formData.addRequestParameterValues(column.get("field").toString(), new String[]{value});
                        }
                    }
                }

                if (getKey() != null && getKey().trim().length() > 0 && getPropertyString("userviewKey") != null && getPropertyString("userviewKey").trim().length() > 0) {
                    formData.addRequestParameterValues(getPropertyString("userviewKey"), new String[]{getKey()});
                }

                String paramName = FormUtil.getElementParameterName(form);
                formData.addRequestParameterValues(paramName + "_IMPORTED", new String[]{"true"});

                importData(rowCount, formData);
            }
            rowCount++;
        }
    }

    protected int getEffectiveImportStartRow(int configuredStart) {
        int start = Math.max(0, configuredStart);
        boolean firstRowHeader = !"false".equalsIgnoreCase(getPropertyString("treatFirstRowAsHeader"));
        if (firstRowHeader) {
            // rowCount is 0-based; ensure we never import the header row (row 0)
            start = Math.max(start, 1);
        }
        return start;
    }

    protected String getExcelCellValue(Cell cell) {
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell);
    }

    protected boolean isExcelRowAllBlank(Row row) {
        DataFormatter formatter = new DataFormatter();
        short last = row.getLastCellNum();
        if (last < 0) {
            return true;
        }
        for (int c = 0; c < last; c++) {
            Cell cell = row.getCell(c);
            if (cell != null) {
                String v = formatter.formatCellValue(cell);
                if (v != null && !v.trim().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    protected void importData(int rowNumber, FormData formData) {
        try {
            String mode = getRequestParameterString("mode");

            if (MODE_NEW.equals(mode) || MODE_NEW_UPDATE.equals(mode)) {
                boolean isExist = false;
                if (formData.getPrimaryKeyValue() != null && !formData.getPrimaryKeyValue().isEmpty()) {
                    formData = FormUtil.executeLoadBinders(form, formData);
                    FormRowSet rowset = formData.getLoadBinderData(form);
                    if (rowset != null && !rowset.isEmpty() && !rowset.get(0).isEmpty()) {
                        isExist = true;
                    }
                }

                if (isExist && MODE_NEW.equals(mode)) {
                    skippedCount++;
                    skippedRows.add(rowNumber);
                } else {
                    boolean validateData = !"true".equals(getRequestParameterString("validateData"));
                    formData = getAppService().submitForm(form, formData, validateData);

                    if (!formData.getFormErrors().isEmpty()) {
                        validationErrorCount++;
                        String error = "";
                        for (String key : formData.getFormErrors().keySet()) {
                            Element e = FormUtil.findElement(key, form, formData);
                            error += StringUtil.stripHtmlRelaxed(e.getPropertyString(FormUtil.PROPERTY_LABEL) + " : " + formData.getFormError(key)) + "<br/>";
                        }
                        validationErrorRows.put(String.valueOf(rowNumber), error);
                    } else {
                        if (isExist) {
                            successUpdatedCount++;
                            successUpdatedRows.add(rowNumber);
                        } else {
                            successImportedCount++;
                            successImportedRows.add(rowNumber);
                        }
                    }
                }
            } else if (MODE_DELETE.equals(mode)) {
                if (formData.getPrimaryKeyValue() != null && !formData.getPrimaryKeyValue().isEmpty()) {
                    formData = FormUtil.executeLoadBinders(form, formData);
                    FormRowSet rowset = formData.getLoadBinderData(form);

                    if (rowset != null && !rowset.isEmpty() && !rowset.get(0).isEmpty()) {
                        FormDataDeletableBinder binder = (FormDataDeletableBinder) form.getLoadBinder();
                        getFormDataDao().delete(binder.getFormId(), binder.getTableName(), rowset);

                        successDeletedCount++;
                        successDeletedRows.add(rowNumber);
                    } else {
                        skippedCount++;
                        skippedRows.add(rowNumber);
                    }
                } else {
                    skippedCount++;
                    skippedRows.add(rowNumber);
                }
            }
        } catch (Exception e) {
            validationErrorCount++;
            validationErrorRows.put(String.valueOf(rowNumber), e.getLocalizedMessage());
        }
    }

    protected List<String> syntheticHeaders(int columnCount) {
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < columnCount; i++) {
            headers.add("Column " + (i + 1));
        }
        return headers;
    }

    protected List<String> uniqueHeaders(List<String> firstRow, int columnCount) {
        Map<String, Integer> counts = new HashMap<>();
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < columnCount; i++) {
            String raw = i < firstRow.size() ? firstRow.get(i) : "";
            String base = (raw == null || raw.trim().isEmpty()) ? ("Column " + (i + 1)) : raw.trim();
            int c = counts.getOrDefault(base, 0);
            counts.put(base, c + 1);
            if (c == 0) {
                headers.add(base);
            } else {
                headers.add(base + " (" + (c + 1) + ")");
            }
        }
        return headers;
    }

    @Override
    public String getOfflineOptions() {
        return null;
    }

    protected static class ExtractionResult {
        final List<String> headers;
        final List<List<String>> rows;

        ExtractionResult(List<String> headers, List<List<String>> rows) {
            this.headers = headers;
            this.rows = rows;
        }
    }

    protected static class UploadedFile {
        final String filenameUpper;
        final String displayFilename;
        final byte[] bytes;

        UploadedFile(String filenameUpper, String displayFilename, byte[] bytes) {
            this.filenameUpper = filenameUpper;
            this.displayFilename = (displayFilename != null && !displayFilename.isEmpty()) ? displayFilename : filenameUpper;
            this.bytes = bytes;
        }
    }

    private static boolean isRowAllBlank(String[] row) {
        if (row == null || row.length == 0) {
            return true;
        }
        for (String cell : row) {
            if (cell != null && !cell.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    protected List<String> getExcelSheetNames(UploadedFile upload, FileType fileType) throws IOException {
        if (fileType == FileType.XLS) {
            InputStream fis = new ByteArrayInputStream(upload.bytes);
            try {
                HSSFWorkbook workbook = new HSSFWorkbook(fis);
                List<String> names = new ArrayList<>();
                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    names.add(workbook.getSheetName(i));
                }
                return names;
            } finally {
                try {
                    fis.close();
                } catch (Exception e) {
                }
            }
        } else if (fileType == FileType.XLSX) {
            InputStream fis = new ByteArrayInputStream(upload.bytes);
            try {
                XSSFWorkbook workbook = new XSSFWorkbook(fis);
                List<String> names = new ArrayList<>();
                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    names.add(workbook.getSheetName(i));
                }
                return names;
            } finally {
                try {
                    fis.close();
                } catch (Exception e) {
                }
            }
        }
        return new ArrayList<>();
    }

    protected int parseSheetIndex(String sheetIndexStr, int sheetCount) {
        if (sheetCount <= 0) {
            return 0;
        }
        try {
            int idx = Integer.parseInt(sheetIndexStr);
            if (idx < 0) {
                return 0;
            }
            if (idx >= sheetCount) {
                return sheetCount - 1;
            }
            return idx;
        } catch (Exception e) {
            return 0;
        }
    }

    protected int parsePreviewPage(String previewPageStr) {
        try {
            int p = Integer.parseInt(previewPageStr);
            return Math.max(1, p);
        } catch (Exception e) {
            return 1;
        }
    }

    protected enum FileType {
        CSV, XLS, XLSX;

        static FileType fromFilenameUpper(String filenameUpper) {
            if (filenameUpper != null) {
                if (filenameUpper.endsWith(".XLSX")) {
                    return XLSX;
                }
                if (filenameUpper.endsWith(".XLS")) {
                    return XLS;
                }
            }
            return CSV;
        }
    }

    /**
     * Cache is stored as a Map in session to survive plugin redeploys.
     * (Storing a plugin class instance causes ClassCastException across OSGi classloaders.)
     */
    protected UploadedFile readCachedUpload(HttpSession session) {
        if (session == null) {
            return null;
        }
        try {
            Object raw = session.getAttribute(SESSION_UPLOADED_ATTR);
            if (raw instanceof Map) {
                Map m = (Map) raw;
                Object f = m.get(SESSION_UPLOADED_FILENAME);
                Object df = m.get(SESSION_UPLOADED_DISPLAY_FILENAME);
                Object b = m.get(SESSION_UPLOADED_BYTES);
                if (f instanceof String && b instanceof byte[]) {
                    String filenameUpper = ((String) f).toUpperCase(Locale.ENGLISH);
                    String displayFilename = (df instanceof String) ? (String) df : filenameUpper;
                    return new UploadedFile(filenameUpper, displayFilename, (byte[]) b);
                }
            }
        } catch (Exception e) {
            // ignore and clear below
        }
        // Clear incompatible/old cache objects
        try {
            session.removeAttribute(SESSION_UPLOADED_ATTR);
        } catch (Exception e) {
        }
        return null;
    }

    protected void writeCachedUpload(HttpSession session, UploadedFile payload) {
        if (session == null || payload == null) {
            return;
        }
        try {
            Map<String, Object> m = new HashMap<>();
            m.put(SESSION_UPLOADED_FILENAME, payload.filenameUpper);
            m.put(SESSION_UPLOADED_DISPLAY_FILENAME, payload.displayFilename);
            m.put(SESSION_UPLOADED_BYTES, payload.bytes);
            session.setAttribute(SESSION_UPLOADED_ATTR, m);
        } catch (Exception e) {
            // ignore cache failures
        }
    }
}
